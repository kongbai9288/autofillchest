/**
 * 填充任务状态机（核心逻辑）。
 *
 * 流程：
 *   IDLE
 *    ├─ 容器界面已打开 -> 直接 FILLING（主路径，最可靠）
 *    ├─ 准星找到箱子   -> 尝试自动开箱 -> WAITING_OPEN
 *    └─ 都没有         -> 提示"没有对准箱子"
 *   WAITING_OPEN -> 等界面出现 -> FILLING（超时提示"请手动打开箱子后再按 H"）
 *   FILLING      -> 每 tick 发一个 QUICK_MOVE，直到箱子满 / 背包空 / 超时
 *
 * 为什么用 QUICK_MOVE 而不是自己搬 ItemStack：
 *   客户端直接改方块实体 Inventory 只在单人下有效，联机会立刻 desync 并被服务端覆盖。
 *   QUICK_MOVE 就是 vanilla 的"shift + 左键"包，服务端权威执行，
 *   内置顺序恰好是"先补满同种未满堆叠 -> 再占用空格子"，与需求等价。
 *
 * 【重要】每 tick 只发一个点击包。
 *   ServerboundContainerClickPacket 带 stateId，同一 tick 内连发多个包时 stateId 不变，
 *   服务端只会接受第一个、丢弃其余，导致大量槽位被跳过（表现就是"箱子填不满"）。
 *   所以这里严格一 tick 一包，宁可慢一点也要保证每个包都被执行。
 *
 * 箱子容量：单箱 27 格，相连大箱 54 格；界面打开后按 slots 数量算，不写死。
 */
package com.kongbai.autofillchest.client;

import com.kongbai.autofillchest.AutoFillChest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public final class ChestAutoFillTask {

    public static final ChestAutoFillTask INSTANCE = new ChestAutoFillTask();

    /** 背包在任何容器界面里都固定占最后 36 格（27 主背包 + 9 快捷栏）。 */
    private static final int PLAYER_SLOT_COUNT = 36;

    /** 玩家自己背包界面（InventoryMenu）的槽位数，用来判断"有没有打开容器"。 */
    private static final int PLAYER_INVENTORY_MENU_SLOTS = 46;

    /** 连续这么多 tick 找不到可移动的物品，认为背包已经空了。 */
    private static final int IDLE_TICKS_TO_FINISH = 20;

    private enum State {
        IDLE,
        WAITING_OPEN,
        FILLING
    }

    private enum FinishReason {
        CHEST_FULL,
        INVENTORY_EMPTY,
        NO_MORE_MOVES,
        TIMEOUT
    }

    private State state = State.IDLE;
    private BlockPos targetPos;
    private int ticks;
    private int cursor;
    private int idleTicks;
    private int openRetries;
    private int movedCount;

    private ChestAutoFillTask() {
    }

    public boolean isRunning() {
        return state != State.IDLE;
    }

    /** 按键触发：开始一次填充。 */
    public void request(Minecraft mc) {
        if (isRunning()) {
            return; // 上一次还没结束，忽略重复按键
        }

        ChestTargetFinder.Target target = ChestTargetFinder.find(mc);

        // 主路径：容器界面已经开着（玩家自己右键打开的），直接填它。
        // 这样即使准星此刻没指到方块（打开界面后 hitResult 常常失效），也能正常填充。
        if (isContainerOpen(mc)) {
            targetPos = target != null ? target.getPos() : null;
            AutoFillChest.LOGGER.info("[AutoFillChest] 界面已打开，直接填充，槽位数={}",
                    mc.player == null ? -1 : mc.player.containerMenu.slots.size());
            startFilling(mc);
            return;
        }

        if (target == null) {
            Feedback.send(mc, "message.autofillchest.no_target");
            return;
        }

        targetPos = target.getPos();
        tryOpenChest(mc, target);
        state = State.WAITING_OPEN;
        ticks = 0;
        openRetries = 0;
    }

    /** 每 tick 推进状态机。 */
    public void tick(Minecraft mc) {
        if (state == State.IDLE) {
            return;
        }
        ticks++;

        if (state == State.WAITING_OPEN) {
            if (isContainerOpen(mc)) {
                startFilling(mc);
                return;
            }
            // 中途补一次右键：移动端 / 低 TPS 下第一次可能来不及生效
            if (ticks == 30 && openRetries < 1) {
                openRetries++;
                ChestTargetFinder.Target again = ChestTargetFinder.find(mc);
                if (again != null) {
                    tryOpenChest(mc, again);
                }
            }
            if (ticks > AutoFillConfig.OPEN_TIMEOUT_TICKS) {
                AutoFillChest.LOGGER.warn("[AutoFillChest] 自动开箱超时，目标 {} 仍未出现容器界面", targetPos);
                Feedback.send(mc, "message.autofillchest.open_manual");
                reset();
            }
            return;
        }

        // ---- FILLING ----
        LocalPlayer player = mc.player;
        if (player == null || !isContainerOpen(mc)) {
            reset(); // 界面被关掉了
            return;
        }

        if (isChestFull(mc)) {
            finish(mc, FinishReason.CHEST_FULL);
            return;
        }

        List<Slot> slots = player.containerMenu.slots;
        int playerSlotStart = slots.size() - PLAYER_SLOT_COUNT;
        if (playerSlotStart < 0) {
            reset();
            return;
        }

        // 一 tick 只点一个槽，从 cursor 开始轮转找下一个非空背包槽
        boolean clicked = false;
        for (int i = 0; i < PLAYER_SLOT_COUNT; i++) {
            int offset = (cursor + i) % PLAYER_SLOT_COUNT;
            int index = playerSlotStart + offset;
            Slot slot = slots.get(index);
            if (slot.hasItem()) {
                ContainerClickHelper.quickMove(mc, index);
                cursor = (offset + 1) % PLAYER_SLOT_COUNT;
                movedCount++;
                clicked = true;
                break;
            }
        }

        if (clicked) {
            idleTicks = 0;
        } else {
            idleTicks++;
            if (idleTicks >= IDLE_TICKS_TO_FINISH) {
                finish(mc, movedCount > 0 ? FinishReason.INVENTORY_EMPTY : FinishReason.NO_MORE_MOVES);
                return;
            }
        }

        if (ticks > AutoFillConfig.MAX_FILL_TICKS) {
            finish(mc, FinishReason.TIMEOUT);
        }
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private void startFilling(Minecraft mc) {
        state = State.FILLING;
        ticks = 0;
        cursor = 0;
        idleTicks = 0;
        movedCount = 0;
        Feedback.send(mc, "message.autofillchest.started");
    }

    /**
     * 当前是否打开着一个"容器界面"。
     * 26.2 的 Minecraft 没有 public 的 screen 字段（也没有 getScreen()），
     * 所以用槽位数判断：玩家自己的背包界面固定 46 格，
     * 打开容器后是 27+36=63（单箱 / 木桶 / 潜影盒）或 54+36=90（大箱）。
     */
    private boolean isContainerOpen(Minecraft mc) {
        LocalPlayer player = mc.player;
        return player != null
                && player.containerMenu != null
                && player.containerMenu.slots.size() > PLAYER_INVENTORY_MENU_SLOTS;
    }

    /** 尝试右键打开箱子。优先走标准 gameMode，失败再直接发包兜底。 */
    private void tryOpenChest(Minecraft mc, ChestTargetFinder.Target target) {
        InteractionHand hand = pickSafeHand(mc);
        if (hand == null) {
            if (AutoFillConfig.REQUIRE_EMPTY_HAND) {
                Feedback.send(mc, "message.autofillchest.need_empty_hand");
                reset();
                return;
            }
            hand = InteractionHand.MAIN_HAND;
        }
        InteractionResult result = ContainerClickHelper.useItemOn(mc, hand, target.getHit());
        if (result == null || !result.consumesAction()) {
            // 标准路径没反应，直接补一个右键包
            ContainerClickHelper.sendUseItemOn(mc, hand, target.getHit());
        }
    }

    /** 选一只手来右键：优先空手；两只手都有东西时，只要不是方块也能开箱。 */
    private InteractionHand pickSafeHand(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return null;
        }
        if (player.getMainHandItem().isEmpty()) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getOffhandItem().isEmpty()) {
            return InteractionHand.OFF_HAND;
        }
        if (!(player.getMainHandItem().getItem() instanceof BlockItem)) {
            return InteractionHand.MAIN_HAND;
        }
        if (!(player.getOffhandItem().getItem() instanceof BlockItem)) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    /** 箱子的格子是否全部被占满（有物品且达到该物品最大堆叠）。 */
    private boolean isChestFull(Minecraft mc) {
        List<Slot> slots = mc.player.containerMenu.slots;
        int chestSlots = slots.size() - PLAYER_SLOT_COUNT;
        for (int i = 0; i < chestSlots; i++) {
            ItemStack stack = slots.get(i).getItem();
            if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    /** 统计箱子已占用格数，用于提示。返回 {已占用, 总格数}。 */
    private int[] chestUsage(Minecraft mc) {
        List<Slot> slots = mc.player.containerMenu.slots;
        int chestSlots = slots.size() - PLAYER_SLOT_COUNT;
        int used = 0;
        for (int i = 0; i < chestSlots; i++) {
            if (!slots.get(i).getItem().isEmpty()) {
                used++;
            }
        }
        return new int[] { used, chestSlots };
    }

    private void finish(Minecraft mc, FinishReason reason) {
        int[] usage = chestUsage(mc);
        int used = usage[0];
        int total = usage[1];

        switch (reason) {
            case CHEST_FULL:
                Feedback.send(mc, "message.autofillchest.chest_full", used, total);
                break;
            case INVENTORY_EMPTY:
                Feedback.send(mc, "message.autofillchest.inventory_empty", used, total);
                break;
            default:
                Feedback.send(mc, "message.autofillchest.done", used, total);
                break;
        }

        if (AutoFillConfig.AUTO_CLOSE_AFTER_FILL) {
            ContainerClickHelper.closeScreen(mc);
        }

        AutoFillChest.LOGGER.info("[AutoFillChest] 填充结束 {}：箱子 {}/{} 格，共点击 {} 次",
                reason, used, total, movedCount);
        reset();
    }

    private void reset() {
        state = State.IDLE;
        targetPos = null;
        ticks = 0;
        cursor = 0;
        idleTicks = 0;
        openRetries = 0;
        movedCount = 0;
    }

    /** 当前目标坐标（调试用）。 */
    public BlockPos getTargetPos() {
        return targetPos;
    }

    /** 目标方块是否为箱子（例如大箱子的任意一半）。 */
    public boolean isTargetChest(Minecraft mc) {
        return targetPos != null
                && mc.level != null
                && mc.level.getBlockState(targetPos).getBlock() instanceof ChestBlock;
    }
}

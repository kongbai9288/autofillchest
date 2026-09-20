/**
 * 填充任务状态机（核心逻辑）。
 *
 * 流程：
 *   IDLE
 *    └─ request()：准星找箱子
 *         ├─ 找不到        -> 提示"没有对准箱子"
 *         ├─ 界面已打开    -> 校验一致后 FILLING
 *         └─ 界面没打开    -> 模拟右键打开 -> WAITING_OPEN
 *   WAITING_OPEN -> 等界面出现 -> FILLING（超时提示失败）
 *   FILLING      -> 每 tick 对若干背包槽发 QUICK_MOVE，直到箱子满 / 背包空 / 轮数用尽
 *
 * 为什么用 QUICK_MOVE 而不是自己搬 ItemStack：
 *   客户端直接改方块实体 Inventory 只在单人下有效，联机会立刻 desync 并被服务端覆盖。
 *   QUICK_MOVE 就是 vanilla 的"shift + 左键"包，服务端权威执行，
 *   内置顺序恰好是"先补满同种未满堆叠 -> 再占用空格子"，与需求等价。
 *
 * 关于箱子容量：单箱 27 格，两个相连的大箱子合成 54 格。
 * 界面打开后按 slots 数量计算，不写死 27。
 */
package com.kongbai.autofillchest.client;

import com.kongbai.autofillchest.AutoFillChest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.List;

public final class ChestAutoFillTask {

    public static final ChestAutoFillTask INSTANCE = new ChestAutoFillTask();

    /** 背包在任何容器界面里都固定占最后 36 格（27 主背包 + 9 快捷栏）。 */
    private static final int PLAYER_SLOT_COUNT = 36;

    /** 玩家自己背包界面（InventoryMenu）的槽位数，用来判断"有没有打开容器"。 */
    private static final int PLAYER_INVENTORY_MENU_SLOTS = 46;

    private enum State {
        IDLE,
        WAITING_OPEN,
        FILLING
    }

    /** 结束原因，用于给出不同提示。 */
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
    private int rounds;
    private boolean movedThisRound;

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
        if (target == null) {
            Feedback.send(mc, "message.autofillchest.no_target");
            return;
        }

        targetPos = target.getPos();

        // 已经开着容器界面：先核对"打开的容器"是不是"准星指的箱子"，再开填
        if (isContainerScreenOpen(mc)) {
            int expected = expectedChestSlotCount(mc, targetPos);
            int actual = mc.player.containerMenu.slots.size() - PLAYER_SLOT_COUNT;
            if (actual != expected) {
                Feedback.send(mc, "message.autofillchest.mismatch");
                reset();
                return;
            }
            startFilling(mc);
            return;
        }

        // 没开界面：先模拟右键把箱子打开
        InteractionHand hand = pickSafeHand(mc);
        if (hand == null && AutoFillConfig.REQUIRE_EMPTY_HAND) {
            Feedback.send(mc, "message.autofillchest.need_empty_hand");
            reset();
            return;
        }
        if (hand == null) {
            hand = InteractionHand.MAIN_HAND;
        }

        ContainerClickHelper.useItemOn(mc, hand, target.getHit());
        state = State.WAITING_OPEN;
        ticks = 0;
    }

    /** 每 tick 推进状态机。 */
    public void tick(Minecraft mc) {
        if (state == State.IDLE) {
            return;
        }
        ticks++;

        if (state == State.WAITING_OPEN) {
            if (isContainerScreenOpen(mc)) {
                startFilling(mc);
                return;
            }
            if (ticks > AutoFillConfig.OPEN_TIMEOUT_TICKS) {
                Feedback.send(mc, "message.autofillchest.open_failed");
                reset();
            }
            return;
        }

        // ---- FILLING ----
        LocalPlayer player = mc.player;
        if (player == null || !isContainerScreenOpen(mc)) {
            // 界面被关掉了（例如玩家自己按了 Esc）
            reset();
            return;
        }

        List<Slot> slots = player.containerMenu.slots;
        int playerSlotStart = slots.size() - PLAYER_SLOT_COUNT;

        int clicks = 0;
        while (clicks < AutoFillConfig.CLICKS_PER_TICK && cursor < slots.size()) {
            int index = cursor;
            cursor++;

            if (index < playerSlotStart) {
                continue; // 这是箱子的格子，不点
            }
            Slot slot = slots.get(index);
            if (!slot.hasItem()) {
                continue; // 空格子，跳过
            }

            ContainerClickHelper.quickMove(mc, index);
            clicks++;
            movedThisRound = true;
        }

        // 一轮扫完：要么再来一轮，要么收尾
        if (cursor >= slots.size()) {
            cursor = 0;
            rounds++;
            if (!movedThisRound) {
                finish(mc, FinishReason.NO_MORE_MOVES);
                return;
            }
            movedThisRound = false;
            if (rounds >= AutoFillConfig.MAX_ROUNDS) {
                finish(mc, FinishReason.TIMEOUT);
                return;
            }
        }

        if (isChestFull(mc)) {
            finish(mc, FinishReason.CHEST_FULL);
            return;
        }
        if (isPlayerInventoryEmpty(mc)) {
            finish(mc, FinishReason.INVENTORY_EMPTY);
            return;
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
        rounds = 0;
        movedThisRound = false;
        Feedback.send(mc, "message.autofillchest.started");
    }

    /**
     * 当前是否打开着一个"容器界面"。
     *
     * 26.2 的 Minecraft 没有 public 的 screen 字段（也没有 getScreen()），
     * 所以改用容器槽位数判断：玩家自己的背包界面固定 46 格，
     * 打开容器后变成 27+36=63（单箱 / 木桶 / 潜影盒）或 54+36=90（大箱）。
     * 不依赖具体 Screen / Menu 类名，兼容性更好。
     */
    private boolean isContainerScreenOpen(Minecraft mc) {
        LocalPlayer player = mc.player;
        return player != null && player.containerMenu != null
                && player.containerMenu.slots.size() > PLAYER_INVENTORY_MENU_SLOTS;
    }

    /**
     * 准星指的箱子"右键打开后应该有多少格"：单箱 27，大箱（LEFT / RIGHT）54。
     * TODO: 确认 26.2 API 名称：若 BlockStateProperties.CHEST_TYPE / ChestType 改名，
     *       把本方法改成 return 27; 即可（校验退化为不生效，功能不受影响）。
     */
    private int expectedChestSlotCount(Minecraft mc, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (!state.hasProperty(BlockStateProperties.CHEST_TYPE)) {
            return 27;
        }
        ChestType type = state.getValue(BlockStateProperties.CHEST_TYPE);
        return type == ChestType.SINGLE ? 27 : 54;
    }

    /** 选一只空手来右键，避免把手里拿的方块放到箱子旁边。 */
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

    /** 背包里是否还有能塞的东西。 */
    private boolean isPlayerInventoryEmpty(Minecraft mc) {
        List<Slot> slots = mc.player.containerMenu.slots;
        int playerSlotStart = slots.size() - PLAYER_SLOT_COUNT;
        for (int i = playerSlotStart; i < slots.size(); i++) {
            if (slots.get(i).hasItem()) {
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
            case NO_MORE_MOVES:
                // 既没满也搬不动：剩下的格子塞不下背包里剩余的东西
                Feedback.send(mc, "message.autofillchest.done", used, total);
                break;
            default:
                Feedback.send(mc, "message.autofillchest.done", used, total);
                break;
        }

        if (AutoFillConfig.AUTO_CLOSE_AFTER_FILL) {
            ContainerClickHelper.closeScreen(mc);
        }

        AutoFillChest.LOGGER.debug("[AutoFillChest] 填充结束 {}：箱子 {}/{} 格", reason, used, total);
        reset();
    }

    private void reset() {
        state = State.IDLE;
        targetPos = null;
        ticks = 0;
        cursor = 0;
        rounds = 0;
        movedThisRound = false;
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

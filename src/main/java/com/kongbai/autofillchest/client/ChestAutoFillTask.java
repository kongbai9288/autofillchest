/**
 * Auto Fill Chest —— 按 H 键把背包物品一键塞满准星指向的箱子。
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * 参考项目：ImmersiveMC —— https://github.com/hammy275/immersive-mc
 * （作者 hammy275，LGPL-3.0 许可证）
 * 本模组在开发过程中参考了 ImmersiveMC，其容器交互与物品移动的实现思路对本项目
 * 有参考价值；依据 LGPL-3.0 的要求保留署名，并沿用同一许可证（LGPL-3.0-only）。
 * 完整归属声明见仓库根目录 CREDITS.txt。
 */

/**
 * 填充任务状态机（核心逻辑）。
 *
 * 【填充语义】这里做的是"占格子"，不是"搬物品"：
 *   背包 -> 箱子，每次只往箱子的一个空格里放 1 个物品。
 *   放完把背包里剩余的物品放回去，再拿下一堆放 1 个到下一个空格。
 *   如此循环，直到箱子没有空格（27 / 54 格全部被占用）或背包物品用完。
 *
 *   这样箱子的每一格都会被占上（每格 1 个），而不是把整堆物品塞进少数几格。
 *
 * 【为什么一 tick 只发一个包】
 *   ServerboundContainerClickPacket 带 stateId，同一 tick 内连发多个包时 stateId 不变，
 *   服务端只接受第一个、丢弃其余。所以严格一 tick 一包，保证每个包都被执行。
 *
 * 【三步循环】每占一格需要 3 个包（3 tick）：
 *   TAKE  : 左键点背包槽，把整堆拿到手上
 *   PLACE : 右键点箱子空格，放下 1 个（占一格）
 *   RETURN: 左键点背包原槽，把剩余的放回去
 *
 * 状态流转：
 *   IDLE ─┬─ 容器界面已打开 -> FILLING（主路径，最可靠）
 *         ├─ 准星找到箱子   -> 自动开箱 -> WAITING_OPEN
 *         └─ 都没有         -> 提示"没有对准箱子"
 */
package com.kongbai.autofillchest.client;

import com.kongbai.autofillchest.AutoFillChest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;

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

    /** 占格循环的三个步骤。 */
    private enum Step {
        TAKE,
        PLACE,
        RETURN
    }

    private enum FinishReason {
        CHEST_FULL,
        INVENTORY_EMPTY,
        TIMEOUT
    }

    private State state = State.IDLE;
    private Step step = Step.TAKE;
    private FinishReason pendingFinish;

    private BlockPos targetPos;
    private int ticks;
    private int invCursor;
    private int currentSlot = -1;
    private int openRetries;
    private int placedCount;

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
            if (ticks == 40 && openRetries < 1) {
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

        // ---- FILLING：一 tick 一个包 ----
        LocalPlayer player = mc.player;
        if (player == null || !isContainerOpen(mc)) {
            reset(); // 界面被关掉了
            return;
        }

        List<Slot> slots = player.containerMenu.slots;
        int playerSlotStart = slots.size() - PLAYER_SLOT_COUNT;
        if (playerSlotStart < 0) {
            reset();
            return;
        }
        int chestSlots = playerSlotStart;

        switch (step) {
            case TAKE: {
                // 上一轮标记了结束原因（比如箱子没空格了），先把物品归位再收尾
                if (pendingFinish != null) {
                    finish(mc, pendingFinish);
                    return;
                }
                int idx = findNonEmptyInvSlot(slots, playerSlotStart, invCursor);
                if (idx < 0) {
                    finish(mc, FinishReason.INVENTORY_EMPTY);
                    return;
                }
                ContainerClickHelper.click(mc, idx, 0); // 左键：拿起整堆
                currentSlot = idx;
                invCursor = (idx - playerSlotStart + 1) % PLAYER_SLOT_COUNT;
                step = Step.PLACE;
                break;
            }
            case PLACE: {
                int empty = findEmptyChestSlot(slots, chestSlots);
                if (empty < 0) {
                    // 箱子没有空格了：把手上的东西放回去，下一 tick 收尾
                    pendingFinish = FinishReason.CHEST_FULL;
                    if (currentSlot >= 0) {
                        ContainerClickHelper.click(mc, currentSlot, 0);
                    }
                    step = Step.TAKE;
                    break;
                }
                ContainerClickHelper.click(mc, empty, 1); // 右键：放 1 个，占一格
                placedCount++;
                step = Step.RETURN;
                break;
            }
            default: { // RETURN
                if (currentSlot >= 0) {
                    ContainerClickHelper.click(mc, currentSlot, 0); // 左键：剩余放回去
                }
                currentSlot = -1;
                step = Step.TAKE;
                break;
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
        step = Step.TAKE;
        pendingFinish = null;
        ticks = 0;
        invCursor = 0;
        currentSlot = -1;
        placedCount = 0;
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

    /**
     * 右键开箱。
     * 注意：InteractionResult.Pass 是正常结果（动作交给方块 / 服务端处理，箱子随后被打开），
     * 不要对它做兜底重发——重复发右键包会把刚打开的界面又关掉。
     */
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
        ContainerClickHelper.useItemOn(mc, hand, target.getHit());
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

    /** 从 start 开始轮转找下一个非空的背包槽，找不到返回 -1。 */
    private int findNonEmptyInvSlot(List<Slot> slots, int playerSlotStart, int start) {
        for (int i = 0; i < PLAYER_SLOT_COUNT; i++) {
            int offset = (start + i) % PLAYER_SLOT_COUNT;
            int index = playerSlotStart + offset;
            Slot slot = slots.get(index);
            if (slot.hasItem()) {
                return index;
            }
        }
        return -1;
    }

    /** 找箱子的第一个空格，找不到返回 -1。 */
    private int findEmptyChestSlot(List<Slot> slots, int chestSlots) {
        for (int i = 0; i < chestSlots; i++) {
            ItemStack stack = slots.get(i).getItem();
            if (stack.isEmpty()) {
                return i;
            }
        }
        return -1;
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

        AutoFillChest.LOGGER.info("[AutoFillChest] 填充结束 {}：箱子 {}/{} 格，共占 {} 格",
                reason, used, total, placedCount);
        reset();
    }

    private void reset() {
        state = State.IDLE;
        step = Step.TAKE;
        pendingFinish = null;
        targetPos = null;
        ticks = 0;
        invCursor = 0;
        currentSlot = -1;
        openRetries = 0;
        placedCount = 0;
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

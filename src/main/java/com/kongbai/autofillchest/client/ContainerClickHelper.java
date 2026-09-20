/**
 * 容器操作封装：所有界面操作都通过 MultiPlayerGameMode 发 vanilla 包完成，
 * 因此服务端不需要安装本模组，也不会出现客户端 / 服务端状态不同步（desync）。
 *
 * 26.x 相对 1.21.1 的关键变化：
 *  - 1.21.1：gameMode.handleInventoryMouseClick(containerId, slotId, button, ClickType, player)
 *  - 26.1+ ：gameMode.handleContainerInput(containerId, slotNum, buttonNum, ContainerInput, player)
 *  - ClickType 已被 net.minecraft.world.inventory.ContainerInput 取代
 *
 * TODO: 确认 26.2 API 名称：若 26.2 上编译报 handleContainerInput 不存在，
 *       退回 1.21.1 写法：mc.gameMode.handleInventoryMouseClick(containerId, slotIndex, 0, ClickType.QUICK_MOVE, player)
 */
package com.kongbai.autofillchest.client;

import com.kongbai.autofillchest.AutoFillChest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.BlockHitResult;

public final class ContainerClickHelper {

    private ContainerClickHelper() {
    }

    /**
     * 对当前界面某个槽位执行一次"shift + 左键"（快速移动）。
     *
     * 服务端对 QUICK_MOVE 的处理顺序正是需求要的规则：
     *   1) 先补满目标容器里"同种物品且未满堆叠"的格子
     *   2) 没有同种物品才占用第一个空格子
     *   3) 放不下的部分留在背包
     * 所以不需要自己写搬物品逻辑，也不会出现 desync。
     *
     * @param slotIndex 槽位在 menu.slots 中的下标（点击包用的就是下标）
     */
    public static boolean quickMove(Minecraft mc, int slotIndex) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            return false;
        }
        if (slotIndex < 0 || slotIndex >= player.containerMenu.slots.size()) {
            return false;
        }

        int containerId = player.containerMenu.containerId;
        // 26.1+：handleContainerInput(int containerId, int slotNum, int buttonNum, ContainerInput, Player)
        mc.gameMode.handleContainerInput(containerId, slotIndex, 0, ContainerInput.QUICK_MOVE, player);
        return true;
    }

    /**
     * 模拟玩家右键方块（用来把箱子界面打开）。
     * TODO: 确认 26.2 API 名称：useItemOn(LocalPlayer, InteractionHand, BlockHitResult)
     */
    public static InteractionResult useItemOn(Minecraft mc, InteractionHand hand, BlockHitResult blockHit) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            return InteractionResult.FAIL;
        }
        InteractionResult result = mc.gameMode.useItemOn(player, hand, blockHit);
        // 打不开时把结果写进日志，方便定位
        AutoFillChest.LOGGER.info("[AutoFillChest] 右键开箱 hand={} pos={} result={}",
                hand, blockHit.getBlockPos(), result);
        return result;
    }

    /** 关闭当前界面（会发关闭容器包，服务端同步）。 */
    public static void closeScreen(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player != null) {
            player.closeContainer();
        }
    }
}

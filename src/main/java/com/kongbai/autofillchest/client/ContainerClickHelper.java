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
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
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
     *
     * 注意：调用方必须保证一 tick 只调用一次，否则同帧多个包的 stateId 相同，
     * 服务端只接受第一个、丢弃其余。
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

    /** 标准右键开箱（走 prediction，最贴近真人操作）。 */
    public static InteractionResult useItemOn(Minecraft mc, InteractionHand hand, BlockHitResult blockHit) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            return InteractionResult.FAIL;
        }
        InteractionResult result = mc.gameMode.useItemOn(player, hand, blockHit);
        AutoFillChest.LOGGER.info("[AutoFillChest] 右键开箱 hand={} pos={} result={}",
                hand, blockHit.getBlockPos(), result);
        return result;
    }

    /**
     * 直接发右键方块包（不走 prediction），作为标准路径失败时的兜底。
     * TODO: 确认 26.2 API 名称：若 ClientPacketListener 没有可访问的 send(Packet)，
     *       删掉本方法以及 ChestAutoFillTask 里对它的调用即可（功能退化为只用标准路径）。
     */
    public static void sendUseItemOn(Minecraft mc, InteractionHand hand, BlockHitResult blockHit) {
        LocalPlayer player = mc.player;
        if (player == null || player.connection == null) {
            return;
        }
        try {
            player.connection.send(new ServerboundUseItemOnPacket(hand, blockHit, 0));
            AutoFillChest.LOGGER.info("[AutoFillChest] 兜底直发右键包 hand={} pos={}", hand, blockHit.getBlockPos());
        } catch (Exception e) {
            AutoFillChest.LOGGER.warn("[AutoFillChest] 兜底发包失败：{}", e.toString());
        }
    }

    /** 关闭当前界面（会发关闭容器包，服务端同步）。 */
    public static void closeScreen(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player != null) {
            player.closeContainer();
        }
    }
}

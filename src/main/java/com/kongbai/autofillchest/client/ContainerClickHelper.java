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
 * 容器操作封装：所有界面操作都通过 MultiPlayerGameMode 发 vanilla 包完成，
 * 因此服务端不需要安装本模组，也不会出现客户端 / 服务端状态不同步（desync）。
 *
 * 26.x 相对 1.21.1 的关键变化：
 *  - 1.21.1：gameMode.handleInventoryMouseClick(containerId, slotId, button, ClickType, player)
 *  - 26.1+ ：gameMode.handleContainerInput(containerId, slotNum, buttonNum, ContainerInput, player)
 *  - ClickType 已被 net.minecraft.world.inventory.ContainerInput 取代
 *
 * TODO: 确认 26.2 API 名称：若 26.2 上编译报 handleContainerInput 不存在，
 *       退回 1.21.1 写法：mc.gameMode.handleInventoryMouseClick(containerId, slotIndex, button, ClickType.PICKUP, player)
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
     * 对当前界面某个槽位做一次点击。
     *   button = 0 左键：拿起整堆 / 放下整堆
     *   button = 1 右键：放下 1 个 / 拿起一半
     *
     * 注意：调用方必须保证一 tick 只调用一次，否则同帧多个包的 stateId 相同，
     * 服务端只接受第一个、丢弃其余。
     */
    public static boolean click(Minecraft mc, int slotIndex, int button) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            return false;
        }
        if (slotIndex < 0 || slotIndex >= player.containerMenu.slots.size()) {
            return false;
        }
        int containerId = player.containerMenu.containerId;
        mc.gameMode.handleContainerInput(containerId, slotIndex, button, ContainerInput.PICKUP, player);
        return true;
    }

    /**
     * shift + 左键快速移动（备用模式，当前占格流程不用它）。
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
        mc.gameMode.handleContainerInput(containerId, slotIndex, 0, ContainerInput.QUICK_MOVE, player);
        return true;
    }

    /**
     * 标准右键开箱（走 prediction，最贴近真人操作）。
     *
     * 注意：返回 Pass 是正常结果（动作交给方块 / 服务端处理，箱子随后被打开），
     * 千万不要对 Pass 再补发一次右键包——重复右键会把刚打开的界面又关掉。
     */
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

    /** 关闭当前界面（会发关闭容器包，服务端同步）。 */
    public static void closeScreen(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player != null) {
            player.closeContainer();
        }
    }
}

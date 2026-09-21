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
 * 准星目标查找：找出玩家正在看的那个箱子。
 *
 * 客户端每 tick 已经把准星射线结果缓存在 Minecraft.hitResult 里，
 * 所以直接复用它，只在上面补一层距离校验（≤ 4.5 格）。
 */
package com.kongbai.autofillchest.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class ChestTargetFinder {

    private ChestTargetFinder() {
    }

    /** 找到的目标箱子；找不到返回 null。 */
    public static final class Target {
        private final BlockPos pos;
        private final BlockHitResult hit;
        private final ChestBlockEntity blockEntity;

        Target(BlockPos pos, BlockHitResult hit, ChestBlockEntity blockEntity) {
            this.pos = pos;
            this.hit = hit;
            this.blockEntity = blockEntity;
        }

        public BlockPos getPos() {
            return pos;
        }

        public BlockHitResult getHit() {
            return hit;
        }

        public ChestBlockEntity getBlockEntity() {
            return blockEntity;
        }
    }

    public static Target find(Minecraft mc) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return null;
        }

        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockHitResult blockHit = (BlockHitResult) hitResult;

        // 距离校验：命中点到眼睛的距离不能超过 4.5 格
        double distance = blockHit.getLocation().distanceTo(player.getEyePosition());
        if (distance > AutoFillConfig.MAX_REACH) {
            return null;
        }

        BlockPos pos = blockHit.getBlockPos();

        // 必须是箱子：TrappedChestBlock 继承 ChestBlock，所以陷阱箱也算；末影箱不算
        if (!(level.getBlockState(pos).getBlock() instanceof ChestBlock)) {
            return null;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ChestBlockEntity)) {
            return null;
        }

        // 大箱子：两半各有一个 ChestBlockEntity，右键任一半打开的容器是同一个。
        // 容器真实格数（单箱 27 / 大箱 54）在界面打开后按 slots 数量读取，这里不猜。
        return new Target(pos, blockHit, (ChestBlockEntity) blockEntity);
    }
}

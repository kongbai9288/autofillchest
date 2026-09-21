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
 * Auto Fill Chest —— 公共常量（无入口点，本模组是纯客户端模组）。
 *
 * 26.2 / Mojang 官方映射下的命名注意：
 *  - 标识符类是 net.minecraft.resources.Identifier，不是 Yarn 的 net.minecraft.util.Identifier
 *  - 方块实体常量是 BlockEntityTypes.CHEST，不是旧的 BlockEntityType.CHEST
 */
package com.kongbai.autofillchest;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AutoFillChest {

    public static final String MOD_ID = "autofillchest";
    public static final String MOD_NAME = "Auto Fill Chest";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private AutoFillChest() {
    }

    /** 构造本模组的命名空间标识符。 */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}

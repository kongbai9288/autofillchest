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

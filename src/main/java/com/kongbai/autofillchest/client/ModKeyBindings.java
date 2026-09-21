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
 * 按键注册：默认 H 键，类别 "Auto Fill Chest"，出现在 选项 → 控制 → 按键绑定。
 *
 * 26.2 注意点（和 1.21.1 不一样）：
 *  - 用 Fabric API 的 KeyMappingHelper（net.fabricmc.fabric.api.client.keymapping.v1），
 *    不是 1.21.1 老教程里的 KeyBindingHelper
 *  - 类别是 KeyMapping.Category 对象，用 KeyMapping.Category.register(Identifier) 注册，
 *    不再是直接传一个字符串
 */
package com.kongbai.autofillchest.client;

import com.kongbai.autofillchest.AutoFillChest;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ModKeyBindings {

    /** 类别标识符 autofillchest:category -> 翻译键 key.category.autofillchest.category */
    public static final Identifier CATEGORY_ID = AutoFillChest.id("category");

    /** 按键翻译键 key.autofillchest.fill */
    public static final String FILL_TRANSLATION_KEY = "key." + AutoFillChest.MOD_ID + ".fill";

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(CATEGORY_ID);

    public static KeyMapping fillKey;

    private ModKeyBindings() {
    }

    public static void register() {
        fillKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                FILL_TRANSLATION_KEY,       // 翻译键
                InputConstants.Type.KEYSYM, // 键盘按键（鼠标用 InputConstants.Type.MOUSE）
                GLFW.GLFW_KEY_H,            // 默认按键：H
                CATEGORY                    // 所属类别
        ));
    }
}

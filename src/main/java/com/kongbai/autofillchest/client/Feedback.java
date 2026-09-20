/**
 * 玩家提示：统一走 actionbar / 聊天栏，并支持本地化。
 */
package com.kongbai.autofillchest.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public final class Feedback {

    private Feedback() {
    }

    public static void send(Minecraft mc, String translationKey) {
        send(mc, Component.translatable(translationKey));
    }

    /** 带参数的本地化提示，例如 "已占用 %1$d / %2$d 格"。 */
    public static void send(Minecraft mc, String translationKey, Object... args) {
        send(mc, Component.translatable(translationKey, args));
    }

    public static void send(Minecraft mc, Component message) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        // displayClientMessage(Component, boolean actionBar)
        player.displayClientMessage(message, AutoFillConfig.FEEDBACK_ACTION_BAR);
    }
}

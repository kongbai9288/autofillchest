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
        // 26.2 没有 displayClientMessage：聊天栏用 sendSystemMessage，
        // 快捷栏上方的 overlay 用 sendOverlayMessage。
        if (AutoFillConfig.FEEDBACK_ACTION_BAR) {
            player.sendOverlayMessage(message);
        } else {
            player.sendSystemMessage(message);
        }
    }
}

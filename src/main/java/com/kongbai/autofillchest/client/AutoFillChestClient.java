/**
 * 客户端入口。纯客户端模组：没有 main 入口，也不注册任何自定义网络包。
 *
 * 26.2 注意点：
 *  - ClientModInitializer 位于 net.fabricmc.api.ClientModInitializer
 *  - 客户端 tick 事件用 net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
 */
package com.kongbai.autofillchest.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public class AutoFillChestClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 1) 注册 H 键（选项 → 控制 → 按键绑定 → Auto Fill Chest）
        ModKeyBindings.register();

        // 2) 每 tick 处理按键与填充任务
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }

        // consumeClick() 要用 while 消费，避免同帧多次按键被吞
        while (ModKeyBindings.fillKey.consumeClick()) {
            ChestAutoFillTask.INSTANCE.request(client);
        }

        ChestAutoFillTask.INSTANCE.tick(client);
    }
}

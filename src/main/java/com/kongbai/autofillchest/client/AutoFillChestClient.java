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

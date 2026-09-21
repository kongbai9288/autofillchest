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
 * 可调参数集中放这里，方便按需求改。
 */
package com.kongbai.autofillchest.client;

public final class AutoFillConfig {

    /** 准星有效距离（格）。超过这个距离，即使指着箱子也不处理。 */
    public static final double MAX_REACH = 4.5D;

    /**
     * 填充阶段的安全上限（tick），超时自动结束，防止状态机卡死。
     * 占格模式下一格需要 3 tick，54 格约 162 tick，这里留足余量。
     */
    public static final int MAX_FILL_TICKS = 1200;

    /** 发出"打开箱子"后，等待界面出现的上限（tick）。移动端 TPS 偏低，给足 5 秒。 */
    public static final int OPEN_TIMEOUT_TICKS = 100;

    /** 填充完成后是否自动关闭箱子界面。 */
    public static final boolean AUTO_CLOSE_AFTER_FILL = false;

    /**
     * 自动打开箱子时是否要求至少一只手是空的。
     * 手里拿着可放置的方块时，右键箱子会变成"放方块"而不是"开箱子"，所以默认要求空手。
     */
    public static final boolean REQUIRE_EMPTY_HAND = true;

    /** 提示显示在快捷栏上方（true）还是聊天栏（false）。 */
    public static final boolean FEEDBACK_ACTION_BAR = true;

    private AutoFillConfig() {
    }
}

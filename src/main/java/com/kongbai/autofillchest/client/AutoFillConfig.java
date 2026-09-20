/**
 * 可调参数集中放这里，方便按需求改。
 */
package com.kongbai.autofillchest.client;

public final class AutoFillConfig {

    /** 准星有效距离（格）。超过这个距离，即使指着箱子也不处理。 */
    public static final double MAX_REACH = 4.5D;

    /**
     * 每 tick 最多发多少个 QUICK_MOVE 点击包。
     * 目的是模拟真人操作速度，避免一帧内狂发几十个包。
     */
    public static final int CLICKS_PER_TICK = 3;

    /** 填充阶段的安全上限（tick），超时自动结束，防止状态机卡死。 */
    public static final int MAX_FILL_TICKS = 200;

    /** 发出"打开箱子"后，等待界面出现的上限（tick）。 */
    public static final int OPEN_TIMEOUT_TICKS = 20;

    /** 背包最多扫几轮。一轮 = 把 36 个背包槽各点一次。 */
    public static final int MAX_ROUNDS = 3;

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

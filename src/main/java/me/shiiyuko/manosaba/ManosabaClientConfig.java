package me.shiiyuko.manosaba;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 魔女审判选项界面中的自定义设置项（原游戏文本相关选项的对应实现）。
 * 项目目前还没有 ADV 文本系统，这些值先做持久化存储，供后续功能读取。
 */
public final class ManosabaClientConfig {

    public static final ModConfigSpec SPEC;

    /** 文本显示速度（1-10） */
    public static final ModConfigSpec.IntValue TEXT_DISPLAY_SPEED;
    /** 自动播放间隔时间（1-10） */
    public static final ModConfigSpec.IntValue AUTO_PLAY_INTERVAL;
    /** 文本跳过模式：true=全部文本，false=仅已读文本 */
    public static final ModConfigSpec.BooleanValue SKIP_UNREAD_TEXT;
    /** 在重要分支选项处显示提示 */
    public static final ModConfigSpec.BooleanValue SHOW_BRANCH_HINT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("message");
        TEXT_DISPLAY_SPEED = builder
                .comment("文本显示速度（1-10）")
                .defineInRange("textDisplaySpeed", 8, 1, 10);
        AUTO_PLAY_INTERVAL = builder
                .comment("自动播放间隔时间（1-10）")
                .defineInRange("autoPlayInterval", 5, 1, 10);
        SKIP_UNREAD_TEXT = builder
                .comment("文本跳过模式：true=全部文本，false=仅已读文本")
                .define("skipUnreadText", false);
        SHOW_BRANCH_HINT = builder
                .comment("在重要分支选项处显示提示")
                .define("showBranchHint", true);
        builder.pop();
        SPEC = builder.build();
    }

    private ManosabaClientConfig() {
    }
}

package me.shiiyuko.manosaba.bridge;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;

/**
 * 「对局域网开放」屏幕（ShareToLanScreen）的宿主能力接口：由 ShareToLanScreenMixin
 * 实现并混入原版屏幕，供内嵌的 Compose 界面（ManosabaShareToLanUi）读写
 * 游戏模式 / 允许命令 / 端口，并执行创建与取消。
 *
 * <p>端口输入：界面自管文本状态，每次按键实时写回原版 EditBox（responder 链同步
 * port 字段与按钮状态）；错误提示由 {@link #manosaba$portError()} 复刻原版
 * tryParsePort 校验（无副作用），不重复驱动 port 字段。
 *
 * <p>位于 bridge 包而非 mixin 包：mixin 包（me.shiiyuko.manosaba.mixin）归
 * manosaba.mixins.json 所有，包内非 @Mixin 类被外部类直接引用/加载会触发
 * IllegalClassLoadError（cannot be referenced directly）。
 */
public interface ShareToLanHost {

    /** 当前游戏模式的显示名（复刻原版 CycleButton：GameType.getShortDisplayName）。 */
    Component manosaba$gameModeName();

    /** 循环切换游戏模式（复刻原版 withValues 顺序 SURVIVAL→SPECTATOR→CREATIVE→ADVENTURE）。 */
    void manosaba$cycleGameMode();

    /** 是否允许命令（复刻原版 CycleButton.onOffBuilder 当前值）。 */
    boolean manosaba$allowCommands();

    /** 切换「允许命令」开关。 */
    void manosaba$toggleCommands();

    /** 当前端口值（responder 维护；端口输入为空时为随机可用端口）。 */
    int manosaba$port();

    /** 端口输入框当前文本（供界面初始化时读取）。 */
    String manosaba$portText();

    /** 写回端口输入框（触发原版 responder：更新 port 字段 / 校验状态 / 创建按钮可用性）。 */
    void manosaba$setPortInput(String text);

    /** 复刻原版 tryParsePort 校验：返回错误提示；null = 合法或留空（留空自动选用可用端口）。 */
    @Nullable
    Component manosaba$portError();

    /** 复刻原版「创建局域网世界」按钮：关闭屏幕 + publishServer + 聊天栏消息 + 更新窗口标题。 */
    void manosaba$create();

    /** 复刻原版「取消」按钮：onClose → setScreen(lastScreen)。 */
    void manosaba$cancel();
}

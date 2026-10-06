package me.shiiyuko.manosaba.bridge;

import net.minecraft.network.chat.Component;

/**
 * 连接失败屏幕（DisconnectedScreen）的宿主能力接口：由 DisconnectedScreenMixin
 * 实现并混入原版屏幕，供内嵌的 Compose 界面（ManosabaDisconnectedUi）读取
 * 断开原因与执行原版按钮行为。
 *
 * <p>位于 bridge 包而非 mixin 包：mixin 包（me.shiiyuko.manosaba.mixin）归
 * manosaba.mixins.json 所有，包内非 @Mixin 类被外部类直接引用/加载会触发
 * IllegalClassLoadError（cannot be referenced directly）。
 */
public interface DisconnectedHost {

    /** 屏幕标题（原版构造器参数，如「连接失败」）。 */
    Component manosaba$title();

    /** 断开原因（DisconnectionDetails.reason()）。 */
    Component manosaba$reason();

    /** 主按钮文本（原版为「返回服务器列表」/「回到主菜单」）。 */
    Component manosaba$buttonText();

    /** 是否存在「向服务器报告」链接。 */
    boolean manosaba$hasReportLink();

    /** 是否存在「打开报告目录」路径。 */
    boolean manosaba$hasReportDir();

    /** 主按钮行为：可多人游戏 → 返回 parent；否则回标题屏（复刻原版）。 */
    void manosaba$close();

    /** 打开报告链接确认屏（复刻原版 ConfirmLinkScreen.confirmLinkNow）。 */
    void manosaba$openReportLink();

    /** 打开报告所在目录（复刻原版 Util.getPlatform().openPath）。 */
    void manosaba$openReportDir();
}

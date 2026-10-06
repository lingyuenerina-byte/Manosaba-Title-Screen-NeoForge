package me.shiiyuko.manosaba.bridge;

/**
 * 直接连接屏幕（DirectJoinServerScreen）的宿主能力接口：由 DirectJoinServerScreenMixin
 * 实现并混入原版屏幕，供内嵌的 Compose 界面（ManosabaDirectJoinUi）读取 / 同步
 * 地址输入并执行原版「连接」「取消」行为。
 *
 * <p>输入同步：界面自管文本状态，每次按键实时写回原版 EditBox（ipEdit）；
 * 原版 removed() 读 ipEdit 保存 lastMpIp，实时同步保证该保存拿到最新值。
 *
 * <p>位于 bridge 包而非 mixin 包：mixin 包（me.shiiyuko.manosaba.mixin）归
 * manosaba.mixins.json 所有，包内非 @Mixin 类被外部类直接引用/加载会触发
 * IllegalClassLoadError（cannot be referenced directly）。
 */
public interface DirectJoinHost {

    /** 初始/当前服务器地址（EditBox 未就绪时回退 options.lastMpIp）。 */
    String manosaba$ip();

    /** 将界面输入的文本同步回原版地址输入框（ipEdit）。 */
    void manosaba$setIp(String value);

    /** 「连接」：走原版 onSelect（写入 serverData.ip 并回调）。 */
    void manosaba$submit();

    /** 「取消」：走原版取消回调（callback.accept(false)）。 */
    void manosaba$cancel();
}

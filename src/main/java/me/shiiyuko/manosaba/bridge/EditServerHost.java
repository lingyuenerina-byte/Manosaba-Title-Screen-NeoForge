package me.shiiyuko.manosaba.bridge;

import net.minecraft.client.multiplayer.ServerData;

/**
 * 编辑服务器屏幕（EditServerScreen，「添加服务器」与「编辑」共用）的宿主能力接口：
 * 由 EditServerScreenMixin 实现并混入原版屏幕，供内嵌的 Compose 界面
 * （ManosabaEditServerUi）读取 / 同步名称与地址输入、循环资源包策略并执行
 * 原版「完成」「取消」行为。
 *
 * <p>输入同步：界面自管文本状态，每次按键实时写回原版 EditBox（responder 链
 * 同步 updateAddButtonStatus）；「完成」经 onAdd 读取的总是最新文本。
 *
 * <p>位于 bridge 包而非 mixin 包：mixin 包（me.shiiyuko.manosaba.mixin）归
 * manosaba.mixins.json 所有，包内非 @Mixin 类被外部类直接引用/加载会触发
 * IllegalClassLoadError（cannot be referenced directly）。
 */
public interface EditServerHost {

    /** 初始/当前服务器名称（EditBox 未就绪时回退 serverData.name）。 */
    String manosaba$name();

    /** 初始/当前服务器地址（EditBox 未就绪时回退 serverData.ip）。 */
    String manosaba$ip();

    /** 将界面输入的文本同步回原版名称输入框（触发 responder → 按钮状态刷新）。 */
    void manosaba$setName(String value);

    /** 将界面输入的文本同步回原版地址输入框（触发 responder → 按钮状态刷新）。 */
    void manosaba$setIp(String value);

    /** 当前资源包策略（ENABLED / DISABLED / PROMPT）。 */
    ServerData.ServerPackStatus manosaba$packStatus();

    /** 点击资源包按钮：按枚举顺序循环到下一项（复刻原版 CycleButton 行为）。 */
    void manosaba$cyclePackStatus();

    /** 「完成」：释放 Compose 资源后走原版 onAdd（写入 serverData 并回调）。 */
    void manosaba$submit();

    /** 「取消」：释放 Compose 资源后走原版取消回调（callback.accept(false)）。 */
    void manosaba$cancel();
}

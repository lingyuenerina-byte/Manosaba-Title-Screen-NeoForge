package me.shiiyuko.manosaba.bridge;

import net.minecraft.stats.StatsCounter;

/**
 * 「统计信息」屏幕（StatsScreen）的宿主能力接口：由 StatsScreenMixin 实现并混入
 * 原版屏幕，供内嵌的 Compose 界面（ManosabaStatsUi）读取统计计数与加载状态。
 *
 * <p>数据说明：界面自行遍历 Stats.CUSTOM / 物品 / 生物统计并复刻原版三列表文本
 * （通用 / 物品 / 生物页签），宿主仅提供 StatsCounter 与 isLoading 状态；
 * onStatsUpdated（服务端数据回包）时由混入刷新桥接渲染顺序并通知界面重建数据。
 *
 * <p>位于 bridge 包而非 mixin 包：mixin 包（me.shiiyuko.manosaba.mixin）归
 * manosaba.mixins.json 所有，包内非 @Mixin 类被外部类直接引用/加载会触发
 * IllegalClassLoadError（cannot be referenced directly）。
 */
public interface StatsHost {

    /** 玩家统计计数器（StatsScreen.stats 字段：mc.player.getStats()）。 */
    StatsCounter manosaba$stats();

    /** 是否仍在等待服务端统计回包（true = 显示「正在下载统计信息」）。 */
    boolean manosaba$isLoading();

    /**
     * 每帧拉回桥接控件焦点：目标类无 render override（RenderTarget 模式），
     * Tab 键在 Screen 层切换焦点后没有注入点每帧复位，改由界面在 render 时调用。
     */
    void manosaba$refocus();

    /** 复刻原版「完成」按钮：onClose → setScreen(lastScreen)。 */
    void manosaba$done();
}

package me.shiiyuko.manosaba.bridge;

import javax.annotation.Nullable;
import me.shiiyuko.manosaba.ui.ManosabaTitleScreen;

/**
 * 创建世界屏幕的宿主能力接口：由 CreateWorldScreenMixin 实现并混入原版屏幕，
 * 供内嵌的 Compose 界面（ManosabaCreateWorldUi）调用原版私有/受保护逻辑。
 *
 * <p>位于 bridge 包而非 mixin 包：mixin 包（me.shiiyuko.manosaba.mixin）归
 * manosaba.mixins.json 所有，包内非 @Mixin 类被外部类直接引用/加载会触发
 * IllegalClassLoadError（cannot be referenced directly）。
 */
public interface CreateWorldHost {

    /** 触发原版「创建新世界」流程（含实验性警告确认等分支）。 */
    void manosaba$createWorld();

    /** 打开原版「实验性内容」子屏幕。 */
    void manosaba$openExperiments();

    /** 打开原版「数据包」子屏幕。 */
    void manosaba$openDataPacks();

    /**
     * 主界面「倒映」幽灵层来源：上一屏为 ManosabaTitleScreen 时返回该实例，否则 null。
     * 供创建世界屏打开的子屏（实验性内容 / 数据包）经 parent 链取回背景来源。
     */
    @Nullable
    ManosabaTitleScreen manosaba$ghostSource();
}

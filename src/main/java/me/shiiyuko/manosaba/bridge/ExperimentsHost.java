package me.shiiyuko.manosaba.bridge;

import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import net.minecraft.server.packs.repository.Pack;

/**
 * 实验性内容屏幕的宿主能力接口：由 ExperimentsScreenMixin 实现并混入原版屏幕，
 * 供内嵌的 Compose 界面（ManosabaExperimentsUi）读写 FEATURE 数据包开关
 * 并触发原版「完成」写回逻辑。
 *
 * <p>位于 bridge 包而非 mixin 包：mixin 包（me.shiiyuko.manosaba.mixin）归
 * manosaba.mixins.json 所有，包内非 @Mixin 类被外部类直接引用/加载会触发
 * IllegalClassLoadError（cannot be referenced directly）。
 */
public interface ExperimentsHost {

    /** FEATURE 源数据包 → 启用状态（原版 packs 字段，Linked 顺序稳定）。 */
    Object2BooleanMap<Pack> manosaba$packs();

    /** 触发原版「完成」：按开关写回 packRepository 并回调 output。 */
    void manosaba$onDone();
}

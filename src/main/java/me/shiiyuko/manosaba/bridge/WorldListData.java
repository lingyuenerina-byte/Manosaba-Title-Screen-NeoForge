package me.shiiyuko.manosaba.bridge;

import javax.annotation.Nullable;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;

/**
 * 世界选择列表（WorldSelectionList）的宿主能力接口：由 WorldSelectionListMixin 实现。
 *
 * <p>原版列表的数据填充发生在 renderWidget（异步扫描结果轮询），mod 接管渲染后
 * renderWidget 不再被调用，需由外部（SelectWorldScreenMixin 的 render 注入）
 * 每帧调用 {@link #manosaba$refreshData()} 复刻该驱动。
 *
 * <p>位于 bridge 包而非 mixin 包：mixin 包（me.shiiyuko.manosaba.mixin）归
 * manosaba.mixins.json 所有，包内非 @Mixin 类被外部类直接引用/加载会触发
 * IllegalClassLoadError（cannot be referenced directly）。
 */
public interface WorldListData {

    /** 复刻原版 renderWidget 的轮询填充；返回 true 表示条目已变化（界面应刷新）。 */
    boolean manosaba$refreshData();

    /** 条目的世界摘要（加载占位行等非世界条目返回 null）。 */
    @Nullable
    LevelSummary manosaba$summaryOf(WorldSelectionList.Entry entry);

    /** 触发异步重载（等同原版删除/编辑后的 reloadWorldList）。 */
    void manosaba$reloadWorldList();
}

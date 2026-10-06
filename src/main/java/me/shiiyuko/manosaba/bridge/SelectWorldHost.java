package me.shiiyuko.manosaba.bridge;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;

/**
 * 选择世界屏幕的宿主能力接口：由 SelectWorldScreenMixin 实现并混入原版屏幕，
 * 供内嵌的 Compose 界面（ManosabaSelectWorldUi）读取世界列表与调用原版逻辑。
 *
 * <p>位于 bridge 包而非 mixin 包：mixin 包（me.shiiyuko.manosaba.mixin）归
 * manosaba.mixins.json 所有，包内非 @Mixin 类被外部类直接引用/加载会触发
 * IllegalClassLoadError（cannot be referenced directly）。
 */
public interface SelectWorldHost {

    /** 世界列表当前全部条目（含加载占位行）。 */
    List<WorldSelectionList.Entry> manosaba$entries();

    /** 当前选中条目（可能为 null）。 */
    @Nullable
    WorldSelectionList.Entry manosaba$selectedEntry();

    /** 条目的世界摘要（加载占位行返回 null）。 */
    @Nullable
    LevelSummary manosaba$summaryOf(WorldSelectionList.Entry entry);

    /** 选中条目（同步原版按钮状态）。 */
    void manosaba$select(@Nullable WorldSelectionList.Entry entry);

    /** 进入当前选中的世界（复刻「进入选中的世界」按钮）。 */
    void manosaba$joinSelected();

    /** 编辑当前选中的世界（打开编辑世界屏幕）。 */
    void manosaba$editSelected();

    /** 删除当前选中的世界（确认弹层已由 Compose 界面完成）。 */
    void manosaba$deleteSelected();

    /** 重建当前选中的世界（打开创建世界屏幕并带入数据）。 */
    void manosaba$recreateSelected();

    /** 创建新的世界。 */
    void manosaba$createWorld();

    /** 触发世界列表异步重载（等同原版「刷新」）。 */
    void manosaba$reloadList();

    /** 更新搜索过滤词（写入原版搜索框，经 responder 同步过滤列表）。 */
    void manosaba$updateFilter(String filter);
}

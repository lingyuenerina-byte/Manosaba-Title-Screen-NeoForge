package me.shiiyuko.manosaba.mixin;

import java.util.List;
import javax.annotation.Nullable;
import me.shiiyuko.manosaba.bridge.WorldListData;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 世界选择列表的数据驱动（TEST37）：原版异步扫描结果只在 renderWidget 中轮询填充
 * （pollLevelsIgnoreErrors / handleNewLevels，均为私有），mod 接管渲染后该路径不再执行，
 * 由 SelectWorldScreenMixin 的 render 注入每帧调用 {@link #manosaba$refreshData()} 复刻。
 */
@Mixin(WorldSelectionList.class)
public abstract class WorldSelectionListMixin implements WorldListData {

    @Invoker("pollLevelsIgnoreErrors")
    protected abstract List<LevelSummary> manosaba$invokePollLevelsIgnoreErrors();

    @Invoker("handleNewLevels")
    protected abstract void manosaba$invokeHandleNewLevels(@Nullable List<LevelSummary> levels);

    @Invoker("reloadWorldList")
    protected abstract void manosaba$invokeReloadWorldList();

    @Accessor("currentlyDisplayedLevels")
    protected abstract List<LevelSummary> manosaba$getCurrentlyDisplayedLevels();

    @Override
    public boolean manosaba$refreshData() {
        // 复刻 renderWidget：扫描完成（引用变化）时重建条目；
        // 引用比较保证同一次扫描结果只填充一次
        List<LevelSummary> displayed = this.manosaba$getCurrentlyDisplayedLevels();
        List<LevelSummary> levels = this.manosaba$invokePollLevelsIgnoreErrors();
        if (levels != displayed) {
            this.manosaba$invokeHandleNewLevels(levels);
            return true;
        }
        return false;
    }

    @Override
    @Nullable
    public LevelSummary manosaba$summaryOf(WorldSelectionList.Entry entry) {
        return entry instanceof WorldSelectionList.WorldListEntry worldEntry
            ? ((WorldListEntryAccessor) (Object) worldEntry).manosaba$getSummary()
            : null;
    }

    @Override
    public void manosaba$reloadWorldList() {
        this.manosaba$invokeReloadWorldList();
    }
}

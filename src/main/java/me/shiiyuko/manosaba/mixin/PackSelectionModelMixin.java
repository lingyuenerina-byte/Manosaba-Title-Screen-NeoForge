package me.shiiyuko.manosaba.mixin;

import java.util.List;
import me.shiiyuko.manosaba.bridge.PackSelectionModelHost;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 数据包选择模型的数据访问（TEST34 魔女审判化配套）：
 * PackSelectionModel.Entry 不暴露 Pack 本体（无法读取图标与完整信息），
 * 故经 @Accessor 暴露内部 selected / unselected 两份 Pack 列表，
 * 供内嵌 Compose 界面（ManosabaPackSelectionUi）直接遍历展示。
 *
 * <p>选择 / 取消选择仍走原版 Entry 语义（canSelect / canUnselect / select / unselect），
 * 本接口只提供只读视图；返回的是模型内部列表引用，调用方按需拷贝（.toList()）。
 */
@Mixin(PackSelectionModel.class)
public abstract class PackSelectionModelMixin implements PackSelectionModelHost {

    @Accessor("selected")
    protected abstract List<Pack> manosaba$getSelected();

    @Accessor("unselected")
    protected abstract List<Pack> manosaba$getUnselected();

    @Override
    public List<Pack> manosaba$selectedPacks() {
        return this.manosaba$getSelected();
    }

    @Override
    public List<Pack> manosaba$unselectedPacks() {
        return this.manosaba$getUnselected();
    }
}

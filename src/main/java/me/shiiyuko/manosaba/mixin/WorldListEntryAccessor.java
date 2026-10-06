package me.shiiyuko.manosaba.mixin;

import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * WorldListEntry 的 summary 字段访问器：该字段为包级（package-private），
 * mixin 包无法在编译期直接访问，经接口 accessor 暴露给 WorldSelectionListMixin。
 */
@Mixin(WorldSelectionList.WorldListEntry.class)
public interface WorldListEntryAccessor {

    @Accessor("summary")
    LevelSummary manosaba$getSummary();
}

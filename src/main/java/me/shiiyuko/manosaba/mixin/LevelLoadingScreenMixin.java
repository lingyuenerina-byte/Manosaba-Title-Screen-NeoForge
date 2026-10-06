package me.shiiyuko.manosaba.mixin;

import java.util.List;
import me.shiiyuko.manosaba.bridge.LoadingScreenPainter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 世界加载屏幕（进世界 / 下界 / 末地等维度切换）魔女审判化：
 * 整屏改绘「纯黑背景 + 右下角白色进度条」统一样式（LoadingScreenPainter，与启动 /
 * 传送 / 世界生成等所有加载画面一致），条体上方显示加载进度。
 *
 * <p>TAIL 注入而非 HEAD cancel：原版 render 完整执行（无障碍 narration 每 2 秒
 * 播报、完成状态等原版逻辑全部保留）。TAIL 里同批次先以全屏黑底覆盖原版绘制
 * （黑底网格 / 中央文字），进度条与文字再叠加其上。
 */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin {

    @Accessor("progressListener")
    protected abstract StoringChunkProgressListener manosaba$getProgressListener();

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"))
    private void manosaba$renderLoading(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        int progress = Mth.clamp(this.manosaba$getProgressListener().getProgress(), 0, 100);
        String line = Component.translatable("manosaba.loading.progress", progress).getString();
        // drawBackground = true：同批次内以整屏黑底覆盖原版网格背景
        LoadingScreenPainter.render(guiGraphics, progress / 100.0F, List.of(line), 1.0F, true);
    }
}

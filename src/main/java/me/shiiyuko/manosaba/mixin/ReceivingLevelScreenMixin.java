package me.shiiyuko.manosaba.mixin;

import java.util.List;
import me.shiiyuko.manosaba.bridge.LoadingScreenPainter;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「加载地形中」等待屏魔女审判化（统一样式）：原版 ReceivingLevelScreen
 * （模糊全景背景 + 白色文字提示）不再渲染，整帧改绘「纯黑背景 + 右下角白色进度条」
 * （LoadingScreenPainter，与启动 / 进世界 / 世界生成等所有加载画面一致）。
 *
 * <p>本屏没有真实进度信号（等待服务端 / 区块就绪），进度按时间渐近估算
 * （指数逼近 99%）；等待完成或 30 秒超时关屏后由后续画面接管。
 *
 * <p>直接替换原版 render（HEAD cancel），杜绝「先闪过一秒原版再切到我们的画面」；
 * tick / onClose 等原版逻辑不动。
 */
@Mixin(ReceivingLevelScreen.class)
public abstract class ReceivingLevelScreenMixin {

    /** 本屏首次渲染时间（毫秒），用于估算进度。 */
    @Unique
    private long manosaba$startMillis = -1L;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void manosaba$render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ci.cancel();
        if (this.manosaba$startMillis < 0L) {
            this.manosaba$startMillis = Util.getMillis();
        }
        float elapsed = (Util.getMillis() - this.manosaba$startMillis) / 1000.0F;
        float progress = Math.min(1.0F - (float) Math.exp(-elapsed / 3.0F), 0.99F);
        String line = Component.translatable("manosaba.loading.progress", Math.round(progress * 100.0F)).getString();
        LoadingScreenPainter.render(guiGraphics, progress, List.of(line), 1.0F);
    }
}

package me.shiiyuko.manosaba.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import me.shiiyuko.manosaba.bridge.LoadingScreenPainter;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.fml.loading.progress.StartupNotificationManager.AgeMessage;
import net.neoforged.neoforge.client.loading.NeoForgeLoadingOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge 启动加载动画（游戏接管渲染后的 NeoForge LoadingOverlay，TEST36 目标）：
 * 整体替换为纯黑背景 + 右下角白色进度条 + 条体上方加载日志（LoadingScreenPainter 绘制）。
 *
 * <p>时序说明：此处（B 阶段）早显 DisplayWindow 的渲染线程已无活跃调用方
 * （renderThreadFunc / periodicTick 在游戏接管后不再被调用），因此可以安全地跳过
 * displayWindow.render(...)，仅保留 displayWindow.close() 完成资源收尾。
 * 启动最早期（A 阶段，mixin 尚不可用）的纯黑背景由 options.txt 的
 * darkMojangStudiosBackground 选项保证。
 *
 * <p>淡出：fadeOutStart 起 1s 内保持不透明，1s~2s 线性淡出露出下层（标题屏），
 * 2s 时完成收尾并卸载 overlay——与原版节奏一致。
 */
@Mixin(NeoForgeLoadingOverlay.class)
public abstract class NeoForgeLoadingOverlayMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private ReloadInstance reload;

    @Shadow
    @Final
    private Consumer<Optional<Throwable>> onFinish;

    @Shadow
    @Final
    private DisplayWindow displayWindow;

    @Shadow
    @Final
    private ProgressMeter progressMeter;

    @Shadow
    private float currentProgress;

    @Shadow
    private long fadeOutStart;

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true)
    private void manosaba$renderLoadingScreen(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        long millis = Util.getMillis();
        float fadeouttimer = this.fadeOutStart > -1L ? (float) (millis - this.fadeOutStart) / 1000.0F : -1.0F;
        this.currentProgress = Mth.clamp(this.currentProgress * 0.95F + this.reload.getActualProgress() * 0.05F, 0.0F, 1.0F);
        this.progressMeter.setAbsolute(Mth.ceil(this.currentProgress * 1000.0F));

        // 淡出阶段先把下一屏（标题屏等）渲染到下层，再叠加本画面的淡出
        if (fadeouttimer >= 1.0F && this.minecraft.screen != null) {
            this.minecraft.screen.render(graphics, 0, 0, partialTick);
        }

        float alpha = fadeouttimer >= 1.0F ? Mth.clamp(2.0F - fadeouttimer, 0.0F, 1.0F) : 1.0F;
        if (alpha > 0.0F) {
            LoadingScreenPainter.render(graphics, this.currentProgress, this.manosaba$collectLines(), alpha);
        }

        // 收尾：与原版一致（卸载 overlay、关闭早显窗口）
        if (fadeouttimer >= 2.0F) {
            this.progressMeter.complete();
            this.minecraft.setOverlay(null);
            this.displayWindow.close();
        }

        if (this.fadeOutStart == -1L && this.reload.isDone()) {
            this.fadeOutStart = Util.getMillis();
            try {
                this.reload.checkExceptions();
                this.onFinish.accept(Optional.empty());
            } catch (Throwable t) {
                this.onFinish.accept(Optional.of(t));
            }
            if (this.minecraft.screen != null) {
                this.minecraft.screen.init(this.minecraft, this.minecraft.getWindow().getGuiScaledWidth(), this.minecraft.getWindow().getGuiScaledHeight());
            }
        }
        ci.cancel();
    }

    /** 条体上方文字：第一行为主进度，其余为最近的启动日志（最多 7 条，最新在上）。 */
    @Unique
    private List<String> manosaba$collectLines() {
        List<String> lines = new ArrayList<>();
        lines.add("正在加载… " + Math.round(this.currentProgress * 100.0F) + "%");
        try {
            List<AgeMessage> messages = StartupNotificationManager.getMessages();
            for (int i = messages.size() - 1; i >= 0 && lines.size() <= 7; i--) {
                String text = messages.get(i).message().getText();
                if (text != null && !text.isEmpty()) {
                    lines.add(text);
                }
            }
        } catch (Throwable ignored) {
            // 日志仅用于展示，任何异常都不应影响加载流程
        }
        return lines;
    }
}

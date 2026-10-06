package me.shiiyuko.manosaba.mixin;

import java.util.List;
import me.shiiyuko.manosaba.bridge.LoadingScreenPainter;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 进度屏（原版加载画面最后缺口）魔女审判化：
 * 原版 ProgressScreen 出现在「进世界时等待内置服务器启动」（doWorldLoad 开头的
 * 无参 disconnect）、「保存退出多人世界」、「删除世界」等场景，原版渲染会露出
 * 全景 / 菜单背景（水平面 level == null 时），即用户报告的「从主界面进入世界
 * 闪出原版加载画面」。
 *
 * <p>整帧改绘「纯黑背景 + 右下角白色进度条」统一样式（LoadingScreenPainter，
 * 与启动 / 进世界 / 世界生成等所有加载画面一致）；条体上方显示屏幕标题 +
 * 估算进度（本屏无真实进度信号，按时间指数渐近逼近 99%）。
 *
 * <p>stop 态交还原版：ProgressScreen.render 在 stop 时负责
 * clearScreenAfterStop 路径的自我关闭（setScreen(null)），HEAD 注入需保留该逻辑，
 * 仅在非 stop 态取消原版渲染。
 */
@Mixin(ProgressScreen.class)
public abstract class ProgressScreenMixin {

    @Shadow
    private boolean stop;

    /** 本屏首次渲染时间（毫秒），用于估算进度。 */
    @Unique
    private long manosaba$startMillis = -1L;

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true)
    private void manosaba$render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.stop) {
            // 完成态：交还原版（clearScreenAfterStop 时由其自我关闭屏幕）
            return;
        }
        ci.cancel();
        if (this.manosaba$startMillis < 0L) {
            this.manosaba$startMillis = Util.getMillis();
        }
        float elapsed = (Util.getMillis() - this.manosaba$startMillis) / 1000.0F;
        float progress = Math.min(1.0F - (float) Math.exp(-elapsed / 3.0F), 0.99F);
        Component title = ((Screen) (Object) this).getTitle();
        String line = title == null ? "" : title.getString() + " " + Math.round(progress * 100.0F) + "%";
        LoadingScreenPainter.render(guiGraphics, progress, List.of(line), 1.0F);
    }
}

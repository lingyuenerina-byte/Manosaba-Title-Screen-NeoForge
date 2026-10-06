package me.shiiyuko.manosaba.mixin;

import java.util.List;
import me.shiiyuko.manosaba.bridge.LoadingScreenPainter;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 通用信息屏（TEST35「正在准备生成世界…」）加载化：
 * 取消原版全景 / 模糊 / 菜单背景与居中文字，改为纯黑背景 + 右下角白色进度条，
 * 条体上方显示屏幕标题 + 估算进度（LoadingScreenPainter，与 TEST36 样式一致）。
 *
 * <p>世界生成没有真实进度信号（GenericMessageScreen 仅承载一句话），
 * 因此进度按时间渐近估算（指数逼近 99%），生成完成时整屏被下一画面替换。
 * 同一 mixin 同时覆盖所有同类加载场景（读档数据加载 / 资源重载等）。
 */
@Mixin(GenericMessageScreen.class)
public abstract class GenericMessageScreenMixin {

    @Shadow
    private FocusableTextWidget textWidget;

    /** 本屏首次渲染时间（毫秒），用于估算进度。 */
    @Unique
    private long manosaba$startMillis = -1L;

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true)
    private void manosaba$renderLoading(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.manosaba$startMillis < 0L) {
            this.manosaba$startMillis = Util.getMillis();
        }
        // 隐藏原版居中文字控件（每帧确认，覆盖 resize 后 init 重建的实例）
        if (this.textWidget != null) {
            this.textWidget.visible = false;
        }

        float elapsed = (Util.getMillis() - this.manosaba$startMillis) / 1000.0F;
        float progress = Math.min(1.0F - (float) Math.exp(-elapsed / 3.0F), 0.99F);
        String line = ((Screen) (Object) this).getTitle().getString() + " " + Math.round(progress * 100.0F) + "%";
        LoadingScreenPainter.render(graphics, progress, List.of(line), 1.0F);
        ci.cancel();
    }
}

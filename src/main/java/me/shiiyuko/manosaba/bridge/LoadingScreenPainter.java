package me.shiiyuko.manosaba.bridge;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 加载界面统一样式绘制器（TEST36 基准）：
 * 纯黑背景 + 右下角白色进度条（两端带竖直端帽），条体上方右对齐文字（加载过程 / 日志）。
 *
 * <p>供两处 mixin 复用：NeoForge 启动动画（NeoForgeLoadingOverlayMixin，TEST36 目标）
 * 与世界生成加载屏（GenericMessageScreenMixin，TEST35 目标）。
 *
 * <p>几何取自 TEST36.png（2560x1080）逐像素扫描结果，全部按窗口比例换算：
 * 条体 x1951..2192（宽 241）、y1029..1044（高 16）、端帽高 33 居中、填充纯白、
 * 未填充轨 25% 白、条右缘距屏右 368、条底缘距屏底 36、背景纯黑。
 *
 * <p>位于 bridge 包而非 mixin 包：mixin 包内非 @Mixin 类被外部类直接引用会触发
 * IllegalClassLoadError（cannot be referenced directly）。
 */
public final class LoadingScreenPainter {

    // ---------------- TEST36 几何比例 ----------------
    private static final float BAR_WIDTH_RATIO = 0.09414F;         // 241 / 2560
    private static final float BAR_HEIGHT_RATIO = 0.01481F;        // 16 / 1080
    private static final float BAR_RIGHT_MARGIN_RATIO = 0.14375F;  // 368 / 2560
    private static final float BAR_BOTTOM_MARGIN_RATIO = 0.03333F; // 36 / 1080
    private static final float CAP_HEIGHT_RATIO = 0.03056F;        // 33 / 1080
    private static final float CAP_WIDTH_RATIO = 0.00080F;         // 2 / 2560

    // ---------------- 颜色（TEST36 采样） ----------------
    /** 全屏背景：纯黑 */
    public static final int COLOR_BACKGROUND = 0xFF000000;
    /** 进度填充 / 端帽：纯白 */
    public static final int COLOR_FILL = 0xFFFFFFFF;
    /** 未填充轨道：25% 白（黑底上呈 RGB 63 灰） */
    public static final int COLOR_TRACK = 0x40FFFFFF;
    /** 主文字（第一行）：纯白 */
    public static final int COLOR_TEXT = 0xFFFFFFFF;
    /** 日志行（其余行）：半透明白 */
    public static final int COLOR_LOG_TEXT = 0xA8FFFFFF;

    private static final int FONT_LINE_HEIGHT = 10;

    private LoadingScreenPainter() {
    }

    /**
     * 绘制整屏加载画面（含全屏黑底）。
     *
     * @param graphics GuiGraphics（GUI 缩放空间）
     * @param progress 0..1 进度（自动裁剪）
     * @param lines    条体上方右对齐的文字：index 0 为主信息（亮白），其余为日志（暗白、向上堆叠）；可为 null
     * @param alpha    0..1 整体透明度（淡出用）
     */
    public static void render(GuiGraphics graphics, float progress, List<String> lines, float alpha) {
        render(graphics, progress, lines, alpha, true);
    }

    /**
     * 绘制加载画面，可选择是否铺全屏黑底。
     *
     * @param drawBackground 是否绘制全屏黑底；背景由整屏主界面图承担时传 false，
     *                       此时仅叠加进度条与文字
     */
    public static void render(GuiGraphics graphics, float progress, List<String> lines, float alpha, boolean drawBackground) {
        int a = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
        if (a <= 3) {
            return;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        if (drawBackground) {
            // 全屏黑底
            graphics.fill(0, 0, width, height, mulAlpha(COLOR_BACKGROUND, a));
        }

        // 进度条几何（TEST36 比例）
        int barWidth = Math.max(1, Math.round(width * BAR_WIDTH_RATIO));
        int barHeight = Math.max(2, Math.round(height * BAR_HEIGHT_RATIO));
        int x1 = width - Math.round(width * BAR_RIGHT_MARGIN_RATIO);
        int x0 = x1 - barWidth;
        int y1 = height - Math.round(height * BAR_BOTTOM_MARGIN_RATIO);
        int y0 = y1 - barHeight;

        // 轨道 + 填充
        graphics.fill(x0, y0, x1, y1, mulAlpha(COLOR_TRACK, a));
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        int filled = x0 + Math.round(barWidth * clamped);
        if (filled > x0) {
            graphics.fill(x0, y0, filled, y1, mulAlpha(COLOR_FILL, a));
        }

        // 两端端帽：高约条高 ×2，居中于条体两端
        int capWidth = Math.max(1, Math.round(width * CAP_WIDTH_RATIO));
        int capHeight = Math.max(barHeight + 2, Math.round(height * CAP_HEIGHT_RATIO));
        int capY0 = y0 - (capHeight - barHeight) / 2;
        int capY1 = capY0 + capHeight;
        int leftCapX = x0 - capWidth / 2;
        int rightCapX = x1 - capWidth / 2;
        graphics.fill(leftCapX, capY0, leftCapX + capWidth, capY1, mulAlpha(COLOR_FILL, a));
        graphics.fill(rightCapX, capY0, rightCapX + capWidth, capY1, mulAlpha(COLOR_FILL, a));

        // 条体上方文字（右对齐，主行在下、日志向上堆叠）
        if (lines != null && !lines.isEmpty()) {
            Font font = Minecraft.getInstance().font;
            int gap = Math.max(2, Math.round(height * 0.01F));
            int baseY = y0 - gap - FONT_LINE_HEIGHT;
            int maxTextWidth = Math.round(width * 0.55F);
            int row = 0;
            for (String line : lines) {
                if (line == null || line.isEmpty()) {
                    row++;
                    continue;
                }
                int textY = baseY - row * FONT_LINE_HEIGHT;
                if (textY < 0) {
                    break;
                }
                String fitted = fit(font, line, maxTextWidth);
                int color = mulAlpha(row == 0 ? COLOR_TEXT : COLOR_LOG_TEXT, a);
                graphics.drawString(font, fitted, x1 - font.width(fitted), textY, color, true);
                row++;
            }
        }
    }

    /** 颜色叠加整体透明度：c 的基础 alpha × a / 255 */
    private static int mulAlpha(int color, int a) {
        int base = (color >>> 24) & 0xFF;
        int out = base * a / 255;
        return (out << 24) | (color & 0x00FFFFFF);
    }

    /** 超长文本截断（尾部省略号），避免日志行越过屏幕左缘 */
    private static String fit(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int budget = maxWidth - font.width(ellipsis);
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int w = font.width(String.valueOf(c));
            if (used + w > budget) {
                break;
            }
            sb.append(c);
            used += w;
        }
        return sb + ellipsis;
    }
}

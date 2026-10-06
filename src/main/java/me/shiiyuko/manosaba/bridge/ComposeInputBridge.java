package me.shiiyuko.manosaba.bridge;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 通用输入桥接控件：作为宿主屏幕 children 列表中的首个元素（init 时最先加入），
 * 在原版控件之前优先接收全部鼠标/键盘事件并转发给内嵌的 Compose 界面，
 * 实现「原版屏幕做逻辑壳、Compose 全接管交互」的嵌入模式。
 *
 * <p>渲染：当宿主屏幕无 render override（如 ExperimentsScreen / PackSelectionScreen）时，
 * 本控件以 addRenderableWidget 加入 renderables 末尾（最后渲染、覆盖原版控件），
 * 由 {@link RenderTarget} 在 renderWidget 中转发整屏 Compose 绘制；
 * 宿主有 render override 时则由宿主 mixin 的 render 注入统一转发，本控件不绘制任何内容。
 *
 * <p>位于 bridge 包而非 mixin 包：mixin 包（me.shiiyuko.manosaba.mixin）归
 * manosaba.mixins.json 所有，包内非 @Mixin 类被外部类直接引用/加载会触发
 * IllegalClassLoadError（cannot be referenced directly）。
 */
public class ComposeInputBridge extends AbstractWidget {

    /** 事件转发目标：由嵌入的 ComposeScreen 子类实现（其 override 方法签名天然匹配）。 */
    public interface Target {
        boolean mouseClicked(double mouseX, double mouseY, int button);

        boolean mouseReleased(double mouseX, double mouseY, int button);

        void mouseMoved(double mouseX, double mouseY);

        boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY);

        boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount);

        boolean keyPressed(int keyCode, int scanCode, int modifiers);

        boolean keyReleased(int keyCode, int scanCode, int modifiers);

        boolean charTyped(char chr, int modifiers);
    }

    /**
     * 渲染转发扩展：目标屏没有可注入的 render 时，内嵌 UI 实现本接口，
     * 由本控件的 renderWidget（随宿主 Screen.render 渲染 renderables）转发整屏绘制。
     */
    public interface RenderTarget extends Target {
        void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick);
    }

    private final Screen screen;
    private final Target target;

    public ComposeInputBridge(Screen screen, Target target) {
        super(0, 0, screen.width, screen.height, Component.empty());
        this.screen = screen;
        this.target = target;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.target instanceof RenderTarget renderTarget) {
            renderTarget.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 取得焦点：Screen 的 mouseDragged / mouseReleased / mouseScrolled / charTyped
        // 只派发给 focused 子项，没有焦点就收不到后续事件
        this.screen.setFocused(this);
        this.target.mouseClicked(mouseX, mouseY, button);
        // 无条件吞掉：原版控件不可交互（事件遍历正序，本控件位于首位最先收到）
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.target.mouseReleased(mouseX, mouseY, button);
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        this.target.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        this.target.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.target.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.target.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        this.target.keyReleased(keyCode, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        this.target.charTyped(chr, modifiers);
        return true;
    }
}

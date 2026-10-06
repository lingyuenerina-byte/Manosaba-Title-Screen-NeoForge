package me.shiiyuko.manosaba.mixin;

import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;
import me.shiiyuko.manosaba.bridge.ComposeInputBridge;
import me.shiiyuko.manosaba.bridge.GhostSourceHolder;
import me.shiiyuko.manosaba.bridge.PackSelectionHost;
import me.shiiyuko.manosaba.ui.ManosabaPackSelectionUi;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 数据包选择屏幕（TEST34 魔女审判化）：原版 PackSelectionScreen 保留为逻辑壳
 * （数据包模型 / 目录监听 / 完成时提交并切屏），渲染与输入全部转发给内嵌的
 * ManosabaPackSelectionUi（魔女审判风格 Compose 界面，视觉与设置页一致）。
 *
 * <p>目标类未 override render / removed / keyPressed 等继承方法（mixin 无法注入），故：
 * <ul>
 *   <li>渲染：桥接控件置于 renderables 末尾（最后渲染、覆盖原版控件），经 RenderTarget 转发整屏绘制；</li>
 *   <li>输入：桥接控件位于 children 首位并持有焦点（ESC 由宿主 Screen 层直接 onClose）；</li>
 *   <li>释放：onClose（完成 / ESC → 提交数据包并切屏）时释放 Compose 资源。</li>
 * </ul>
 *
 * <p>「倒映」幽灵层来源经 GhostSourceHolder 传递（本屏构造器直接收仓库 / 回调 / 目录，
 * 不持有 parent 引用），由 CreateWorldScreenMixin 打开本屏前写入。
 */
@Mixin(PackSelectionScreen.class)
public abstract class PackSelectionScreenMixin implements PackSelectionHost {

    @Unique
    @Nullable
    private ManosabaPackSelectionUi manosaba$ui;

    @Unique
    @Nullable
    private ComposeInputBridge manosaba$bridge;

    @Accessor("model")
    protected abstract PackSelectionModel manosaba$getModel();

    @Accessor("packDir")
    protected abstract Path manosaba$getPackDir();

    @Override
    public PackSelectionModel manosaba$model() {
        return this.manosaba$getModel();
    }

    @Override
    public Path manosaba$packDir() {
        return this.manosaba$getPackDir();
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void manosaba$onInit(CallbackInfo ci) {
        if (this.manosaba$ui == null) {
            this.manosaba$ui = new ManosabaPackSelectionUi(
                (PackSelectionScreen) (Object) this, GhostSourceHolder.get()
            );
        }
        // 桥接控件每次 init 重建（覆盖 resize 后的新尺寸）
        this.manosaba$bridge = new ComposeInputBridge((PackSelectionScreen) (Object) this, this.manosaba$ui);
        PackSelectionScreen self = (PackSelectionScreen) (Object) this;
        // 渲染：renderables 是 Screen 的公开字段，置于末尾（原版控件之后渲染，整屏覆盖）
        self.renderables.add(this.manosaba$bridge);
        // 输入：children 移到首位（事件遍历正序，桥最先收到并吞掉）
        @SuppressWarnings("unchecked")
        List<GuiEventListener> screenChildren = (List<GuiEventListener>) self.children();
        screenChildren.remove(this.manosaba$bridge);
        screenChildren.add(0, this.manosaba$bridge);
        // 焦点：键盘与拖拽/滚轮事件依赖 focused 派发
        self.setFocused(this.manosaba$bridge);
    }

    @Inject(method = "onClose()V", at = @At("TAIL"))
    private void manosaba$onClose(CallbackInfo ci) {
        if (this.manosaba$ui != null) {
            this.manosaba$ui.disposeCompose();
        }
    }
}

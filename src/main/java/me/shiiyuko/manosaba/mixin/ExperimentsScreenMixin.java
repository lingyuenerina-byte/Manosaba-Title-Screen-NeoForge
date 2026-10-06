package me.shiiyuko.manosaba.mixin;

import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import java.util.List;
import javax.annotation.Nullable;
import me.shiiyuko.manosaba.bridge.ComposeInputBridge;
import me.shiiyuko.manosaba.bridge.ExperimentsHost;
import me.shiiyuko.manosaba.bridge.GhostSourceHolder;
import me.shiiyuko.manosaba.ui.ManosabaExperimentsUi;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.worldselection.ExperimentsScreen;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 实验性内容屏幕（TEST33 魔女审判化）：原版 ExperimentsScreen 保留为逻辑壳
 * （FEATURE 数据包开关数据 / 完成写回回调），渲染与输入全部转发给内嵌的
 * ManosabaExperimentsUi（魔女审判风格 Compose 界面，视觉与设置页一致）。
 *
 * <p>目标类未 override render / removed / keyPressed 等继承方法（mixin 无法注入），故：
 * <ul>
 *   <li>渲染：桥接控件置于 renderables 末尾（最后渲染、覆盖原版控件），经 RenderTarget 转发整屏绘制；</li>
 *   <li>输入：桥接控件位于 children 首位并持有焦点（ESC 由宿主 Screen 层直接 onClose）；</li>
 *   <li>释放：onClose（取消/ESC → setScreen(parent)）时释放 Compose 资源；
 *       「完成」路径由界面自身在触发写回前释放（该路径不经过 onClose）。</li>
 * </ul>
 */
@Mixin(ExperimentsScreen.class)
public abstract class ExperimentsScreenMixin implements ExperimentsHost {

    @Unique
    @Nullable
    private ManosabaExperimentsUi manosaba$ui;

    @Unique
    @Nullable
    private ComposeInputBridge manosaba$bridge;

    @Accessor("packs")
    protected abstract Object2BooleanMap<Pack> manosaba$getPacks();

    @Invoker("onDone")
    protected abstract void manosaba$invokeOnDone();

    @Override
    public Object2BooleanMap<Pack> manosaba$packs() {
        return this.manosaba$getPacks();
    }

    @Override
    public void manosaba$onDone() {
        this.manosaba$invokeOnDone();
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void manosaba$onInit(CallbackInfo ci) {
        if (this.manosaba$ui == null) {
            this.manosaba$ui = new ManosabaExperimentsUi(
                (ExperimentsScreen) (Object) this, GhostSourceHolder.get()
            );
        }
        // 桥接控件每次 init 重建（覆盖 resize 后的新尺寸）
        this.manosaba$bridge = new ComposeInputBridge((ExperimentsScreen) (Object) this, this.manosaba$ui);
        ExperimentsScreen self = (ExperimentsScreen) (Object) this;
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

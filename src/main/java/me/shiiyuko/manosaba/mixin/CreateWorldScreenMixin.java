package me.shiiyuko.manosaba.mixin;

import java.util.List;
import javax.annotation.Nullable;
import me.shiiyuko.manosaba.bridge.ComposeInputBridge;
import me.shiiyuko.manosaba.bridge.CreateWorldHost;
import me.shiiyuko.manosaba.bridge.GhostSourceHolder;
import me.shiiyuko.manosaba.ui.ManosabaCreateWorldUi;
import me.shiiyuko.manosaba.ui.ManosabaTitleScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.world.level.WorldDataConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 创建新世界屏幕（TEST23-25 魔女审判化）：原版 CreateWorldScreen 保留为逻辑壳
 * （世界生成数据 / 创建世界流程 / 子屏跳转），渲染与输入全部转发给内嵌的
 * ManosabaCreateWorldUi（魔女审判风格 Compose 界面，视觉与设置页一致）。
 *
 * 说明：目标类未 override 的继承方法（mouseXxx / charTyped / keyReleased 等）无法注入，
 * 因此使用 ComposeInputBridge 控件实现输入转发（见 ComposeInputBridge 顶部注释）。
 * 生命周期：init 时创建并挂接桥接控件；onClose / onCreate 时释放 Compose 资源。
 */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin implements CreateWorldHost {

    @Unique
    @Nullable
    private ManosabaCreateWorldUi manosaba$ui;

    @Unique
    @Nullable
    private ComposeInputBridge manosaba$bridge;

    /** 上一屏幕：主界面打开创建世界时为 ManosabaTitleScreen，用于「倒映」背景。 */
    @Accessor("lastScreen")
    @Nullable
    protected abstract Screen manosaba$getLastScreen();

    @Invoker("onCreate")
    protected abstract void manosaba$invokeOnCreate();

    @Invoker("openExperimentsScreen")
    protected abstract void manosaba$invokeOpenExperiments(WorldDataConfiguration configuration);

    @Invoker("openDataPackSelectionScreen")
    protected abstract void manosaba$invokeOpenDataPacks(WorldDataConfiguration configuration);

    @Inject(method = "init()V", at = @At("HEAD"))
    private void manosaba$onInitHead(CallbackInfo ci) {
        if (this.manosaba$ui == null) {
            this.manosaba$ui = new ManosabaCreateWorldUi((CreateWorldScreen) (Object) this, this.manosaba$ghostSource());
        }
        // 桥接控件每次 init 重建（覆盖 resize 后的新尺寸），位于 children 首位
        this.manosaba$bridge = new ComposeInputBridge((CreateWorldScreen) (Object) this, this.manosaba$ui);
        // Screen.children() 是公开方法（父类字段无法被 mixin 静态定位，故不用 @Shadow 字段）
        @SuppressWarnings("unchecked")
        List<GuiEventListener> screenChildren = (List<GuiEventListener>) ((Screen) (Object) this).children();
        screenChildren.add(0, this.manosaba$bridge);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void manosaba$onInitTail(CallbackInfo ci) {
        if (this.manosaba$bridge != null) {
            // 让桥接控件取得焦点：键盘与拖拽/滚轮事件依赖 focused 派发
            ((CreateWorldScreen) (Object) this).setFocused(this.manosaba$bridge);
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true)
    private void manosaba$onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.manosaba$ui != null) {
            this.manosaba$ui.render(guiGraphics, mouseX, mouseY, partialTick);
            ci.cancel();
        }
    }

    @Inject(method = "keyPressed(III)Z", at = @At("HEAD"), cancellable = true)
    private void manosaba$onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (this.manosaba$ui != null && this.manosaba$ui.keyPressed(keyCode, scanCode, modifiers)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "onClose()V", at = @At("TAIL"))
    private void manosaba$onClose(CallbackInfo ci) {
        this.manosaba$disposeUi();
    }

    @Inject(method = "onCreate()V", at = @At("HEAD"))
    private void manosaba$onCreateHead(CallbackInfo ci) {
        this.manosaba$disposeUi();
    }

    @Unique
    private void manosaba$disposeUi() {
        if (this.manosaba$ui != null) {
            this.manosaba$ui.disposeCompose();
        }
    }

    // ---------------- CreateWorldHost ----------------

    @Override
    public void manosaba$createWorld() {
        this.manosaba$invokeOnCreate();
    }

    @Override
    public void manosaba$openExperiments() {
        // 幽灵层来源经静态 holder 传递给子屏（实验屏 / 数据包屏均在 init 时读取）
        GhostSourceHolder.set(this.manosaba$ghostSource());
        this.manosaba$invokeOpenExperiments(
            ((CreateWorldScreen) (Object) this).getUiState().getSettings().dataConfiguration()
        );
    }

    @Override
    public void manosaba$openDataPacks() {
        // 数据包屏不持有 parent 引用，幽灵层来源只能经 holder 传递
        GhostSourceHolder.set(this.manosaba$ghostSource());
        this.manosaba$invokeOpenDataPacks(
            ((CreateWorldScreen) (Object) this).getUiState().getSettings().dataConfiguration()
        );
    }

    @Override
    @Nullable
    public ManosabaTitleScreen manosaba$ghostSource() {
        Screen last = this.manosaba$getLastScreen();
        if (last instanceof ManosabaTitleScreen title) {
            return title;
        }
        if (last instanceof SelectWorldScreen) {
            // 选择世界页打开的创建世界（新建 / 重建）：幽灵层来源已由
            // SelectWorldScreenMixin（createWorld / recreateSelected）写入 holder
            return GhostSourceHolder.get();
        }
        return null;
    }
}

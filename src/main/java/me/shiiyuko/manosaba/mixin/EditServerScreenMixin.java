package me.shiiyuko.manosaba.mixin;

import java.util.List;
import javax.annotation.Nullable;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import me.shiiyuko.manosaba.bridge.ComposeInputBridge;
import me.shiiyuko.manosaba.bridge.EditServerHost;
import me.shiiyuko.manosaba.bridge.GhostSourceHolder;
import me.shiiyuko.manosaba.ui.ManosabaEditServerUi;
import me.shiiyuko.manosaba.ui.ManosabaTitleScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.EditServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 编辑服务器屏幕（TEST43 魔女审判化）：原版 EditServerScreen 保留为逻辑壳
 * （名称 / 地址 EditBox、资源包策略、完成 / 取消回调），渲染与输入全部转发给
 * 内嵌的 ManosabaEditServerUi（魔女审判风格 Compose 界面，视觉与设置页一致）。
 *
 * <p>输入同步：界面自管文本状态，每次按键实时写回原版 EditBox（responder 链
 * 同步 updateAddButtonStatus）；「完成」经 @Invoker 调 onAdd，读取的总是最新文本。
 *
 * <p>生命周期：EditServerScreen 未声明 removed()，释放点挂在三个出口——
 * onClose（ESC 路径，TAIL 注入）与 submit / cancel（界面按钮路径）。
 *
 * <p>「倒映」背景来源：parent（lastScreen）为 JoinMultiplayerScreen 时读
 * GhostSourceHolder（打开子屏前由 JoinMultiplayerScreenMixin 统一写入）。
 */
@Mixin(EditServerScreen.class)
public abstract class EditServerScreenMixin implements EditServerHost {

    @Unique
    @Nullable
    private ManosabaEditServerUi manosaba$ui;

    @Unique
    @Nullable
    private ComposeInputBridge manosaba$bridge;

    @Shadow
    private EditBox nameEdit;

    @Shadow
    private EditBox ipEdit;

    @Shadow
    private ServerData serverData;

    /** 上一屏幕：从多人游戏屏打开时为 JoinMultiplayerScreen。 */
    @Accessor("lastScreen")
    @Nullable
    protected abstract Screen manosaba$getLastScreen();

    @Accessor("callback")
    protected abstract BooleanConsumer manosaba$getCallback();

    @Invoker("onAdd")
    protected abstract void manosaba$invokeOnAdd();

    @Inject(method = "init()V", at = @At("HEAD"))
    private void manosaba$onInitHead(CallbackInfo ci) {
        if (this.manosaba$ui == null) {
            this.manosaba$ui = new ManosabaEditServerUi((EditServerScreen) (Object) this, this.manosaba$ghost());
        }
        // 桥接控件每次 init 重建（覆盖 resize 后的新尺寸），位于 children 首位
        this.manosaba$bridge = new ComposeInputBridge((EditServerScreen) (Object) this, this.manosaba$ui);
        // Screen.children() 是公开方法（父类字段无法被 mixin 静态定位，故不用 @Shadow 字段）
        @SuppressWarnings("unchecked")
        List<GuiEventListener> screenChildren = (List<GuiEventListener>) ((Screen) (Object) this).children();
        screenChildren.add(0, this.manosaba$bridge);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void manosaba$onInitTail(CallbackInfo ci) {
        if (this.manosaba$bridge != null) {
            // 让桥接控件取得焦点：键盘与拖拽/滚轮事件依赖 focused 派发
            ((EditServerScreen) (Object) this).setFocused(this.manosaba$bridge);
        }
    }

    @Inject(method = "setInitialFocus()V", at = @At("HEAD"), cancellable = true)
    private void manosaba$onSetInitialFocus(CallbackInfo ci) {
        // 原版会把焦点交给名称输入框；嵌入模式下改由桥接控件持有（输入由 Compose 自管）
        if (this.manosaba$bridge != null) {
            ((EditServerScreen) (Object) this).setFocused(this.manosaba$bridge);
            ci.cancel();
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true)
    private void manosaba$onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.manosaba$ui == null) {
            return;
        }
        EditServerScreen self = (EditServerScreen) (Object) this;
        // Tab 键在 Screen.keyPressed 中先行切换焦点（先于 children 派发），每帧拉回桥接控件
        if (this.manosaba$bridge != null && self.getFocused() != this.manosaba$bridge) {
            self.setFocused(this.manosaba$bridge);
        }
        this.manosaba$ui.onHostFrame();
        this.manosaba$ui.render(guiGraphics, mouseX, mouseY, partialTick);
        ci.cancel();
    }

    @Inject(method = "onClose()V", at = @At("TAIL"))
    private void manosaba$onClose(CallbackInfo ci) {
        // ESC 路径（Screen.keyPressed 直达 onClose）：释放 Compose 资源；
        // 完成 / 取消按钮路径由 submit / cancel 释放，两个目标类无 removed 可注入
        if (this.manosaba$ui != null) {
            this.manosaba$ui.disposeUi();
        }
    }

    @Unique
    @Nullable
    private ManosabaTitleScreen manosaba$ghost() {
        Screen last = this.manosaba$getLastScreen();
        if (last instanceof ManosabaTitleScreen title) {
            return title;
        }
        if (last instanceof JoinMultiplayerScreen) {
            // 打开子屏前由 JoinMultiplayerScreenMixin 统一写入
            return GhostSourceHolder.get();
        }
        return null;
    }

    // ---------------- EditServerHost ----------------

    @Override
    public String manosaba$name() {
        // init HEAD 创建界面时 EditBox 尚未建立，回退 serverData 初值
        return this.nameEdit != null ? this.nameEdit.getValue() : this.serverData.name;
    }

    @Override
    public String manosaba$ip() {
        return this.ipEdit != null ? this.ipEdit.getValue() : this.serverData.ip;
    }

    @Override
    public void manosaba$setName(String value) {
        if (this.nameEdit != null) {
            this.nameEdit.setValue(value);
        }
    }

    @Override
    public void manosaba$setIp(String value) {
        if (this.ipEdit != null) {
            this.ipEdit.setValue(value);
        }
    }

    @Override
    public ServerData.ServerPackStatus manosaba$packStatus() {
        return this.serverData.getResourcePackStatus();
    }

    @Override
    public void manosaba$cyclePackStatus() {
        // 复刻原版 CycleButton：按枚举声明顺序循环到下一项
        ServerData.ServerPackStatus[] values = ServerData.ServerPackStatus.values();
        ServerData.ServerPackStatus next = values[(this.manosaba$packStatus().ordinal() + 1) % values.length];
        this.serverData.setResourcePackStatus(next);
    }

    @Override
    public void manosaba$submit() {
        // 先释放 Compose 场景再走原版提交：同屏回调切屏（setScreen(lastScreen)），
        // EditServerScreen 无 removed 注入点，资源须在此显式释放
        if (this.manosaba$ui != null) {
            this.manosaba$ui.disposeUi();
        }
        this.manosaba$invokeOnAdd();
    }

    @Override
    public void manosaba$cancel() {
        if (this.manosaba$ui != null) {
            this.manosaba$ui.disposeUi();
        }
        this.manosaba$getCallback().accept(false);
    }
}

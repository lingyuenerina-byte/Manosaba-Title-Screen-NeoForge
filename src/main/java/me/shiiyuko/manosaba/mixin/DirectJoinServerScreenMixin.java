package me.shiiyuko.manosaba.mixin;

import java.util.List;
import javax.annotation.Nullable;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import me.shiiyuko.manosaba.bridge.ComposeInputBridge;
import me.shiiyuko.manosaba.bridge.DirectJoinHost;
import me.shiiyuko.manosaba.bridge.GhostSourceHolder;
import me.shiiyuko.manosaba.ui.ManosabaDirectJoinUi;
import me.shiiyuko.manosaba.ui.ManosabaTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 直接连接屏幕（TEST44 魔女审判化）：原版 DirectJoinServerScreen 保留为逻辑壳
 * （地址 EditBox、连接 / 取消回调、removed 保存 lastMpIp），渲染与输入全部转发给
 * 内嵌的 ManosabaDirectJoinUi（魔女审判风格 Compose 界面，视觉与设置页一致）。
 *
 * <p>输入同步：界面自管文本状态，每次按键实时写回原版 EditBox；原版 removed()
 * 读 ipEdit 保存 lastMpIp，实时同步保证该保存拿到最新值。
 *
 * <p>生命周期：removed() 在本屏被替换时总被调用（ESC / 连接 / 取消路径统一），
 * TAIL 注入释放 Compose 资源。
 *
 * <p>「倒映」背景来源：parent（lastScreen）为 JoinMultiplayerScreen 时读
 * GhostSourceHolder（打开子屏前由 JoinMultiplayerScreenMixin 统一写入）。
 */
@Mixin(DirectJoinServerScreen.class)
public abstract class DirectJoinServerScreenMixin implements DirectJoinHost {

    @Unique
    @Nullable
    private ManosabaDirectJoinUi manosaba$ui;

    @Unique
    @Nullable
    private ComposeInputBridge manosaba$bridge;

    @Shadow
    private EditBox ipEdit;

    /** 上一屏幕：从多人游戏屏打开时为 JoinMultiplayerScreen。 */
    @Accessor("lastScreen")
    @Nullable
    protected abstract Screen manosaba$getLastScreen();

    @Accessor("callback")
    protected abstract BooleanConsumer manosaba$getCallback();

    @Invoker("onSelect")
    protected abstract void manosaba$invokeOnSelect();

    @Inject(method = "init()V", at = @At("HEAD"))
    private void manosaba$onInitHead(CallbackInfo ci) {
        if (this.manosaba$ui == null) {
            this.manosaba$ui = new ManosabaDirectJoinUi((DirectJoinServerScreen) (Object) this, this.manosaba$ghost());
        }
        // 桥接控件每次 init 重建（覆盖 resize 后的新尺寸），位于 children 首位
        this.manosaba$bridge = new ComposeInputBridge((DirectJoinServerScreen) (Object) this, this.manosaba$ui);
        // Screen.children() 是公开方法（父类字段无法被 mixin 静态定位，故不用 @Shadow 字段）
        @SuppressWarnings("unchecked")
        List<GuiEventListener> screenChildren = (List<GuiEventListener>) ((Screen) (Object) this).children();
        screenChildren.add(0, this.manosaba$bridge);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void manosaba$onInitTail(CallbackInfo ci) {
        if (this.manosaba$bridge != null) {
            // 让桥接控件取得焦点：键盘与拖拽/滚轮事件依赖 focused 派发
            ((DirectJoinServerScreen) (Object) this).setFocused(this.manosaba$bridge);
        }
    }

    @Inject(method = "setInitialFocus()V", at = @At("HEAD"), cancellable = true)
    private void manosaba$onSetInitialFocus(CallbackInfo ci) {
        // 原版会把焦点交给地址输入框；嵌入模式下改由桥接控件持有（输入由 Compose 自管）
        if (this.manosaba$bridge != null) {
            ((DirectJoinServerScreen) (Object) this).setFocused(this.manosaba$bridge);
            ci.cancel();
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true)
    private void manosaba$onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.manosaba$ui == null) {
            return;
        }
        DirectJoinServerScreen self = (DirectJoinServerScreen) (Object) this;
        // Tab 键在 Screen.keyPressed 中先行切换焦点（先于 children 派发），每帧拉回桥接控件
        if (this.manosaba$bridge != null && self.getFocused() != this.manosaba$bridge) {
            self.setFocused(this.manosaba$bridge);
        }
        this.manosaba$ui.onHostFrame();
        this.manosaba$ui.render(guiGraphics, mouseX, mouseY, partialTick);
        ci.cancel();
    }

    @Inject(method = "removed()V", at = @At("TAIL"))
    private void manosaba$onRemoved(CallbackInfo ci) {
        // 原版已保存 lastMpIp（读实时同步的 ipEdit 值）；此处释放 Compose 资源
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

    // ---------------- DirectJoinHost ----------------

    @Override
    public String manosaba$ip() {
        // init HEAD 创建界面时 EditBox 尚未建立，回退原版初值 options.lastMpIp
        return this.ipEdit != null ? this.ipEdit.getValue() : Minecraft.getInstance().options.lastMpIp;
    }

    @Override
    public void manosaba$setIp(String value) {
        if (this.ipEdit != null) {
            this.ipEdit.setValue(value);
        }
    }

    @Override
    public void manosaba$submit() {
        // 原版提交：serverData.ip = ipEdit.getValue(); callback.accept(true)
        this.manosaba$invokeOnSelect();
    }

    @Override
    public void manosaba$cancel() {
        this.manosaba$getCallback().accept(false);
    }
}

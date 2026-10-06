package me.shiiyuko.manosaba.mixin;

import java.util.List;
import javax.annotation.Nullable;
import io.netty.channel.ChannelFuture;
import me.shiiyuko.manosaba.bridge.ActiveUiReclaimer;
import me.shiiyuko.manosaba.bridge.ComposeInputBridge;
import me.shiiyuko.manosaba.bridge.ConnectHost;
import me.shiiyuko.manosaba.bridge.GhostSourceHolder;
import me.shiiyuko.manosaba.ui.ManosabaConnectUi;
import me.shiiyuko.manosaba.ui.ManosabaTitleScreen;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 连接中屏幕（TEST45 魔女审判化）：原版 ConnectScreen 保留为逻辑壳（连接线程 /
 * 状态回调 / 取消语义），渲染与输入全部转发给内嵌的 ManosabaConnectUi
 * （魔女审判风格 Compose 界面，视觉与设置页一致）。
 *
 * <p>目标类未 override removed / setInitialFocus（无法注入）：
 * <ul>
 *   <li>渲染：render override 注入 HEAD cancel，转发整屏 Compose 绘制，
 *       并复刻原版的无障碍等待播报（narrator.joining，每 2 秒）；</li>
 *   <li>取消：TAIL 无注入点，改为界面自身的「取消」按钮复刻原版逻辑
 *       （aborted 置位、断开连接、返回上一屏）后释放；</li>
 *   <li>释放：esc 不可关闭（shouldCloseOnEsc=false 原版语义）；连接失败 /
 *       成功进服等不可注入的替换路径由连接失败屏 / 标题屏初始化兜底释放
 *       （ActiveUiReclaimer），再次连接时 arm 换任释放。</li>
 * </ul>
 *
 * <p>「倒映」背景来源：parent 为 JoinMultiplayerScreen 时读 GhostSourceHolder
 * （连接发起前由 JoinMultiplayerScreenMixin 的 join() 注入统一写入）。
 */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin implements ConnectHost {

    @Unique
    @Nullable
    private ManosabaConnectUi manosaba$ui;

    @Unique
    @Nullable
    private ComposeInputBridge manosaba$bridge;

    /** 上一屏幕：从多人游戏屏连接时为 JoinMultiplayerScreen（final 字段，只读）。 */
    @Accessor("parent")
    @Nullable
    protected abstract Screen manosaba$getParent();

    /** 连接状态文本（连接线程经原版 updateStatus 动态更新）。 */
    @Accessor("status")
    protected abstract Component manosaba$getStatus();

    @Accessor("lastNarration")
    protected abstract long manosaba$getLastNarration();

    @Accessor("lastNarration")
    protected abstract void manosaba$setLastNarration(long value);

    @Accessor("aborted")
    protected abstract void manosaba$setAborted(boolean value);

    @Accessor("channelFuture")
    @Nullable
    protected abstract ChannelFuture manosaba$getChannelFuture();

    @Accessor("channelFuture")
    protected abstract void manosaba$setChannelFuture(@Nullable ChannelFuture value);

    @Accessor("connection")
    @Nullable
    protected abstract Connection manosaba$getConnection();

    @Inject(method = "init()V", at = @At("HEAD"))
    private void manosaba$onInitHead(CallbackInfo ci) {
        if (this.manosaba$ui == null) {
            this.manosaba$ui = new ManosabaConnectUi((ConnectScreen) (Object) this, this.manosaba$ghost());
        }
        // 桥接控件每次 init 重建（覆盖 resize 后的新尺寸），位于 children 首位
        this.manosaba$bridge = new ComposeInputBridge((ConnectScreen) (Object) this, this.manosaba$ui);
        // Screen.children() 是公开方法（父类字段无法被 mixin 静态定位，故不用 @Shadow 字段）
        @SuppressWarnings("unchecked")
        List<GuiEventListener> screenChildren = (List<GuiEventListener>) ((Screen) (Object) this).children();
        screenChildren.add(0, this.manosaba$bridge);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void manosaba$onInitTail(CallbackInfo ci) {
        if (this.manosaba$bridge != null) {
            // 让桥接控件取得焦点：键盘与拖拽/滚轮事件依赖 focused 派发
            ((ConnectScreen) (Object) this).setFocused(this.manosaba$bridge);
        }
        if (this.manosaba$ui != null) {
            // 登记释放器：再次连接（新 ConnectScreen 实例）登记时自动释放上一任
            ActiveUiReclaimer.arm(this, this.manosaba$ui::disposeUi);
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true)
    private void manosaba$onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.manosaba$ui == null) {
            return;
        }
        ConnectScreen self = (ConnectScreen) (Object) this;
        // Tab 键在 Screen.keyPressed 中先行切换焦点（先于 children 派发），每帧拉回桥接控件
        if (this.manosaba$bridge != null && self.getFocused() != this.manosaba$bridge) {
            self.setFocused(this.manosaba$bridge);
        }
        // 复刻原版 render 中的等待播报（每 2 秒一次；无障碍模式依赖）
        long now = Util.getMillis();
        if (now - this.manosaba$getLastNarration() > 2000L) {
            this.manosaba$setLastNarration(now);
            Minecraft.getInstance().getNarrator().sayNow(Component.translatable("narrator.joining"));
        }
        this.manosaba$ui.onHostFrame();
        this.manosaba$ui.render(guiGraphics, mouseX, mouseY, partialTick);
        ci.cancel();
    }

    @Unique
    @Nullable
    private ManosabaTitleScreen manosaba$ghost() {
        Screen parent = this.manosaba$getParent();
        if (parent instanceof ManosabaTitleScreen title) {
            return title;
        }
        if (parent instanceof JoinMultiplayerScreen) {
            // 连接发起前由 JoinMultiplayerScreenMixin 的 join() 注入统一写入
            return GhostSourceHolder.get();
        }
        return null;
    }

    // ---------------- ConnectHost ----------------

    @Override
    public Component manosaba$status() {
        return this.manosaba$getStatus();
    }

    @Override
    public void manosaba$cancel() {
        // 复刻原版取消按钮：置位 aborted、取消未完成的连接尝试并断开既有连接，返回上一屏
        synchronized (this) {
            this.manosaba$setAborted(true);
            ChannelFuture future = this.manosaba$getChannelFuture();
            if (future != null) {
                future.cancel(true);
                this.manosaba$setChannelFuture(null);
            }
            Connection connection = this.manosaba$getConnection();
            if (connection != null) {
                connection.disconnect(ConnectScreen.ABORT_CONNECTION);
            }
        }
        ActiveUiReclaimer.dispose();
        Minecraft.getInstance().setScreen(this.manosaba$getParent());
    }
}

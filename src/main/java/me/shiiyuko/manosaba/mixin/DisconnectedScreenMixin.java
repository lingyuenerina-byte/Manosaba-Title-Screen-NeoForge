package me.shiiyuko.manosaba.mixin;

import java.util.List;
import javax.annotation.Nullable;
import me.shiiyuko.manosaba.bridge.ActiveUiReclaimer;
import me.shiiyuko.manosaba.bridge.ComposeInputBridge;
import me.shiiyuko.manosaba.bridge.DisconnectedHost;
import me.shiiyuko.manosaba.bridge.GhostSourceHolder;
import me.shiiyuko.manosaba.ui.ManosabaDisconnectedUi;
import me.shiiyuko.manosaba.ui.ManosabaTitleScreen;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 连接失败屏幕（TEST39 魔女审判化）：原版 DisconnectedScreen 保留为逻辑壳
 * （断开原因数据 / 返回按钮行为 / 报告链接与目录），渲染与输入全部转发给内嵌的
 * ManosabaDisconnectedUi（魔女审判风格 Compose 界面）。
 *
 * <p>目标类未 override render / removed 等方法（mixin 无法注入），故：
 * <ul>
 *   <li>渲染：桥接控件置于 renderables 末尾（最后渲染、覆盖原版控件），
 *       经 RenderTarget 转发整屏绘制（与 ExperimentsScreenMixin 同模式）；</li>
 *   <li>输入：桥接控件位于 children 首位并持有焦点（ESC 由原版 shouldCloseOnEsc=false
 *       保持不可关闭）；</li>
 *   <li>释放：「返回」按钮由界面自身在切屏前释放 Compose 资源（无 removed 可注入）。</li>
 * </ul>
 *
 * <p>构造器参数缓存：全部公开构造器都汇聚到 4 参构造器（parent / title /
 * DisconnectionDetails / buttonText），在此 @Inject TAIL 捕获供界面读取。
 * 「倒映」背景来源：parent 为 JoinMultiplayerScreen 时读 GhostSourceHolder
 * （连接发起前由 JoinMultiplayerScreenMixin 的 join() 注入统一写入）。
 */
@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin implements DisconnectedHost {

    @Unique
    @Nullable
    private ManosabaDisconnectedUi manosaba$ui;

    @Unique
    @Nullable
    private ComposeInputBridge manosaba$bridge;

    @Unique
    @Nullable
    private Screen manosaba$parent;

    @Unique
    @Nullable
    private Component manosaba$titleText;

    @Unique
    @Nullable
    private DisconnectionDetails manosaba$details;

    @Unique
    @Nullable
    private Component manosaba$buttonLabel;

    @Inject(
        method = "<init>(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/DisconnectionDetails;Lnet/minecraft/network/chat/Component;)V",
        at = @At("TAIL")
    )
    private void manosaba$onConstruct(Screen parent, Component title, DisconnectionDetails details, Component buttonText, CallbackInfo ci) {
        this.manosaba$parent = parent;
        this.manosaba$titleText = title;
        this.manosaba$details = details;
        this.manosaba$buttonLabel = buttonText;
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void manosaba$onInit(CallbackInfo ci) {
        // 兜底释放：连接失败前 ConnectScreen（TEST45）界面可能仍登记（该屏无 removed
        // 可注入，取消路径已自释放，失败路径在此汇聚释放）
        ActiveUiReclaimer.dispose();
        if (this.manosaba$ui == null) {
            this.manosaba$ui = new ManosabaDisconnectedUi((DisconnectedScreen) (Object) this, this.manosaba$ghost());
        }
        // 桥接控件每次 init 重建（覆盖 resize 后的新尺寸）
        this.manosaba$bridge = new ComposeInputBridge((DisconnectedScreen) (Object) this, this.manosaba$ui);
        DisconnectedScreen self = (DisconnectedScreen) (Object) this;
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

    @Unique
    @Nullable
    private ManosabaTitleScreen manosaba$ghost() {
        if (this.manosaba$parent instanceof ManosabaTitleScreen title) {
            return title;
        }
        if (this.manosaba$parent instanceof JoinMultiplayerScreen) {
            // 连接发起前由 JoinMultiplayerScreenMixin 的 join() 注入统一写入
            return GhostSourceHolder.get();
        }
        return null;
    }

    // ---------------- DisconnectedHost ----------------

    @Override
    public Component manosaba$title() {
        return this.manosaba$titleText != null ? this.manosaba$titleText : CommonComponents.CONNECT_FAILED;
    }

    @Override
    public Component manosaba$reason() {
        return this.manosaba$details != null ? this.manosaba$details.reason() : Component.empty();
    }

    @Override
    public Component manosaba$buttonText() {
        return this.manosaba$buttonLabel != null ? this.manosaba$buttonLabel : CommonComponents.GUI_BACK;
    }

    @Override
    public boolean manosaba$hasReportLink() {
        return this.manosaba$details != null && this.manosaba$details.bugReportLink().isPresent();
    }

    @Override
    public boolean manosaba$hasReportDir() {
        return this.manosaba$details != null && this.manosaba$details.report().isPresent();
    }

    @Override
    public void manosaba$close() {
        // 复刻原版按钮行为：可多人游戏 → 返回 parent；否则回标题屏
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.allowsMultiplayer()) {
            minecraft.setScreen(this.manosaba$parent);
        } else {
            minecraft.setScreen(new TitleScreen());
        }
    }

    @Override
    public void manosaba$openReportLink() {
        if (this.manosaba$details == null) {
            return;
        }
        this.manosaba$details.bugReportLink().ifPresent(uri ->
            ConfirmLinkScreen.confirmLinkNow((DisconnectedScreen) (Object) this, uri, false));
    }

    @Override
    public void manosaba$openReportDir() {
        if (this.manosaba$details == null) {
            return;
        }
        this.manosaba$details.report().ifPresent(path ->
            Util.getPlatform().openPath(path.getParent()));
    }
}

package me.shiiyuko.manosaba.mixin;

import java.util.List;
import javax.annotation.Nullable;
import me.shiiyuko.manosaba.bridge.ComposeInputBridge;
import me.shiiyuko.manosaba.bridge.GhostSourceHolder;
import me.shiiyuko.manosaba.bridge.MultiplayerHost;
import me.shiiyuko.manosaba.ui.ManosabaMultiplayerUi;
import me.shiiyuko.manosaba.ui.ManosabaTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.EditServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 多人游戏屏幕（TEST26 魔女审判化）：原版 JoinMultiplayerScreen 保留为逻辑壳
 * （服务器列表 / 局域网检测 / Ping / 子屏跳转），渲染与输入全部转发给内嵌的
 * ManosabaMultiplayerUi（魔女审判风格 Compose 界面，视觉与设置页一致）。
 *
 * 输入转发依赖 ComposeInputBridge（目标类未 override 的鼠标继承方法无法注入）；
 * 生命周期：init 创建 / 挂接；removed 释放 Compose 资源（同一实例往返时 render 自动重建）。
 */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin implements MultiplayerHost {

    @Unique
    @Nullable
    private ManosabaMultiplayerUi manosaba$ui;

    @Unique
    @Nullable
    private ComposeInputBridge manosaba$bridge;

    @Shadow
    protected ServerSelectionList serverSelectionList;

    /** 上一屏幕：主界面打开多人游戏时为 ManosabaTitleScreen，用于「倒映」背景。 */
    @Accessor("lastScreen")
    @Nullable
    protected abstract Screen manosaba$getLastScreen();

    @Accessor("editingServer")
    protected abstract void manosaba$setEditingServer(ServerData serverData);

    @Accessor("editingServer")
    protected abstract ServerData manosaba$getEditingServer();

    @Invoker("refreshServerList")
    protected abstract void manosaba$invokeRefresh();

    @Invoker("directJoinCallback")
    protected abstract void manosaba$invokeDirectJoinCallback(boolean joined);

    @Invoker("addServerCallback")
    protected abstract void manosaba$invokeAddServerCallback(boolean added);

    @Invoker("editServerCallback")
    protected abstract void manosaba$invokeEditServerCallback(boolean edited);

    @Invoker("deleteCallback")
    protected abstract void manosaba$invokeDeleteCallback(boolean deleted);

    @Inject(method = "init()V", at = @At("HEAD"))
    private void manosaba$onInitHead(CallbackInfo ci) {
        if (this.manosaba$ui == null) {
            Screen last = this.manosaba$getLastScreen();
            ManosabaTitleScreen ghost = last instanceof ManosabaTitleScreen title ? title : null;
            this.manosaba$ui = new ManosabaMultiplayerUi((JoinMultiplayerScreen) (Object) this, ghost);
        }
        // 桥接控件每次 init 重建（覆盖 resize 后的新尺寸），位于 children 首位
        this.manosaba$bridge = new ComposeInputBridge((JoinMultiplayerScreen) (Object) this, this.manosaba$ui);
        // Screen.children() 是公开方法（父类字段无法被 mixin 静态定位，故不用 @Shadow 字段）
        @SuppressWarnings("unchecked")
        List<GuiEventListener> screenChildren = (List<GuiEventListener>) ((Screen) (Object) this).children();
        screenChildren.add(0, this.manosaba$bridge);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void manosaba$onInitTail(CallbackInfo ci) {
        if (this.manosaba$bridge != null) {
            // 让桥接控件取得焦点：键盘与拖拽/滚轮事件依赖 focused 派发
            ((JoinMultiplayerScreen) (Object) this).setFocused(this.manosaba$bridge);
        }
        // rebuildWidgets（同屏 setScreen 返回 / resize）后主动通知界面重组：
        // 编辑保存返回路径不重建 Compose 场景（同屏 setScreen 不触发 removed），
        // 只有显式通知才能立即呈现更新后的服务器列表（TEST38 报修）
        if (this.manosaba$ui != null) {
            this.manosaba$ui.onHostDataChanged();
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

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void manosaba$onTick(CallbackInfo ci) {
        if (this.manosaba$ui != null) {
            this.manosaba$ui.onHostTick();
        }
    }

    @Inject(method = "join(Lnet/minecraft/client/multiplayer/ServerData;)V", at = @At("HEAD"))
    private void manosaba$onJoin(ServerData serverData, CallbackInfo ci) {
        // 所有连接入口（「加入服务器」回车 / 直接连接回调 / LAN）都汇聚到 join：
        // 发起连接前统一把幽灵层来源写入 holder，保证连接失败屏（TEST39）总能读到「倒映」背景
        this.manosaba$prepareConnect();
    }

    @Inject(method = "removed()V", at = @At("TAIL"))
    private void manosaba$onRemoved(CallbackInfo ci) {
        if (this.manosaba$ui != null) {
            this.manosaba$ui.disposeUi();
        }
    }

    // ---------------- MultiplayerHost ----------------

    @Override
    @SuppressWarnings("unchecked")
    public List<ServerSelectionList.Entry> manosaba$entries() {
        if (this.serverSelectionList == null) {
            return List.of();
        }
        return (List<ServerSelectionList.Entry>) this.serverSelectionList.children();
    }

    @Override
    @Nullable
    public ServerSelectionList.Entry manosaba$selectedEntry() {
        return this.serverSelectionList == null ? null : this.serverSelectionList.getSelected();
    }

    @Override
    public void manosaba$refresh() {
        this.manosaba$invokeRefresh();
    }

    @Override
    public void manosaba$directJoin() {
        // 复刻原版「直接连接」按钮：新建空服务器数据并打开 DirectJoinServerScreen；
        // 打开子屏前同步幽灵层来源（TEST44 直接连接屏的背景「倒映」）
        this.manosaba$syncGhostHolder();
        this.manosaba$setEditingServer(new ServerData(I18n.get("selectServer.defaultName"), "", ServerData.Type.OTHER));
        Minecraft.getInstance().setScreen(
            new DirectJoinServerScreen(
                (JoinMultiplayerScreen) (Object) this,
                this::manosaba$invokeDirectJoinCallback,
                this.manosaba$getEditingServer()
            )
        );
    }

    @Override
    public void manosaba$addServer() {
        // 复刻原版「添加服务器」按钮；打开子屏前同步幽灵层来源（TEST43 编辑服务器屏的背景「倒映」）
        this.manosaba$syncGhostHolder();
        this.manosaba$setEditingServer(new ServerData(I18n.get("selectServer.defaultName"), "", ServerData.Type.OTHER));
        Minecraft.getInstance().setScreen(
            new EditServerScreen(
                (JoinMultiplayerScreen) (Object) this,
                this::manosaba$invokeAddServerCallback,
                this.manosaba$getEditingServer()
            )
        );
    }

    @Override
    public void manosaba$editServer() {
        // 复刻原版「编辑」按钮：复制选中在线服务器的数据后打开 EditServerScreen
        if (this.serverSelectionList == null) {
            return;
        }
        this.manosaba$syncGhostHolder();
        ServerSelectionList.Entry entry = this.serverSelectionList.getSelected();
        if (entry instanceof ServerSelectionList.OnlineServerEntry online) {
            ServerData serverData = online.getServerData();
            ServerData editing = new ServerData(serverData.name, serverData.ip, ServerData.Type.OTHER);
            editing.copyFrom(serverData);
            this.manosaba$setEditingServer(editing);
            Minecraft.getInstance().setScreen(
                new EditServerScreen(
                    (JoinMultiplayerScreen) (Object) this,
                    this::manosaba$invokeEditServerCallback,
                    editing
                )
            );
        }
    }

    @Override
    public void manosaba$prepareConnect() {
        // 连接发起前把幽灵层来源写入静态 holder：连接失败屏（DisconnectedScreen）
        // 不持有主界面引用，init 时从这里读取「倒映」背景来源（TEST39）
        this.manosaba$syncGhostHolder();
    }

    /**
     * 子屏 / 连接失败屏的「倒映」来源：本屏的 lastScreen 为标题屏时写入静态 holder，
     * 供不持有主界面引用的下游屏幕（EditServerScreen / DirectJoinServerScreen /
     * DisconnectedScreen）在 init 时读取。
     */
    @Unique
    private void manosaba$syncGhostHolder() {
        Screen last = this.manosaba$getLastScreen();
        GhostSourceHolder.set(last instanceof ManosabaTitleScreen title ? title : null);
    }

    @Override
    public void manosaba$deleteSelected() {
        // 删除确认已由 Compose 界面完成，这里直接走原版删除回调
        this.manosaba$invokeDeleteCallback(true);
    }
}

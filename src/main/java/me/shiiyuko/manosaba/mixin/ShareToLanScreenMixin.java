package me.shiiyuko.manosaba.mixin;

import java.util.List;
import javax.annotation.Nullable;
import me.shiiyuko.manosaba.bridge.ComposeInputBridge;
import me.shiiyuko.manosaba.bridge.ShareToLanHost;
import me.shiiyuko.manosaba.ui.ManosabaShareToLanUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.PublishCommand;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「对局域网开放」屏幕（TEST54 魔女审判化）：原版 ShareToLanScreen 保留为逻辑壳
 * （游戏模式 / 允许命令 / 端口 EditBox、创建与取消回调），渲染与输入全部转发给
 * 内嵌的 ManosabaShareToLanUi（魔女审判对话框风格 Compose 界面，GAME_DIM 背景）。
 *
 * <p>输入同步：界面自管端口文本，每次按键实时写回原版 EditBox（responder 链
 * 同步 port 字段）；「创建」按钮动作由宿主复刻原版 lambda（publishServer + 消息），
 * 界面按钮在切屏前先释放 Compose 资源。
 *
 * <p>生命周期：目标类无 removed()，释放点挂在三个出口——onClose（ESC / 取消路径，
 * TAIL 注入）与 create / cancel（界面按钮路径显式释放）。
 */
@Mixin(ShareToLanScreen.class)
public abstract class ShareToLanScreenMixin implements ShareToLanHost {

    @Unique
    @Nullable
    private ManosabaShareToLanUi manosaba$ui;

    @Unique
    @Nullable
    private ComposeInputBridge manosaba$bridge;

    @Accessor("gameMode")
    protected abstract GameType manosaba$getGameMode();

    @Accessor("gameMode")
    protected abstract void manosaba$setGameMode(GameType gameMode);

    @Accessor("commands")
    protected abstract boolean manosaba$getCommands();

    @Accessor("commands")
    protected abstract void manosaba$setCommands(boolean commands);

    @Accessor("port")
    protected abstract int manosaba$getPort();

    @Accessor("portEdit")
    @Nullable
    protected abstract EditBox manosaba$getPortEdit();

    @Inject(method = "init()V", at = @At("HEAD"))
    private void manosaba$onInitHead(CallbackInfo ci) {
        if (this.manosaba$ui == null) {
            this.manosaba$ui = new ManosabaShareToLanUi((ShareToLanScreen) (Object) this);
        }
        // 桥接控件每次 init 重建（覆盖 resize / 从暂停屏往返后的新尺寸），位于 children 首位
        this.manosaba$bridge = new ComposeInputBridge((ShareToLanScreen) (Object) this, this.manosaba$ui);
        @SuppressWarnings("unchecked")
        List<GuiEventListener> screenChildren = (List<GuiEventListener>) ((Screen) (Object) this).children();
        screenChildren.add(0, this.manosaba$bridge);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void manosaba$onInitTail(CallbackInfo ci) {
        if (this.manosaba$bridge != null) {
            // 让桥接控件取得焦点：键盘与拖拽/滚轮事件依赖 focused 派发
            ((ShareToLanScreen) (Object) this).setFocused(this.manosaba$bridge);
        }
        if (this.manosaba$ui != null) {
            // resize / 往返后原版重建 EditBox（文本清空）：界面状态跟随重读
            this.manosaba$ui.onHostInit();
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true)
    private void manosaba$onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.manosaba$ui == null) {
            return;
        }
        ShareToLanScreen self = (ShareToLanScreen) (Object) this;
        // Tab 键在 Screen.keyPressed 中先行切换焦点，每帧拉回桥接控件
        if (this.manosaba$bridge != null && self.getFocused() != this.manosaba$bridge) {
            self.setFocused(this.manosaba$bridge);
        }
        this.manosaba$ui.onHostFrame();
        this.manosaba$ui.render(guiGraphics, mouseX, mouseY, partialTick);
        ci.cancel();
    }

    @Inject(method = "onClose()V", at = @At("TAIL"))
    private void manosaba$onClose(CallbackInfo ci) {
        // ESC 路径（Screen.keyPressed 直达 onClose）与「取消」按钮路径：释放 Compose 资源
        if (this.manosaba$ui != null) {
            this.manosaba$ui.disposeUi();
        }
    }

    // ---------------- ShareToLanHost ----------------

    @Override
    public Component manosaba$gameModeName() {
        return this.manosaba$getGameMode().getShortDisplayName();
    }

    @Override
    public void manosaba$cycleGameMode() {
        // 复刻原版 CycleButton.withValues(SURVIVAL, SPECTATOR, CREATIVE, ADVENTURE) 的循环顺序
        GameType[] cycle = { GameType.SURVIVAL, GameType.SPECTATOR, GameType.CREATIVE, GameType.ADVENTURE };
        GameType current = this.manosaba$getGameMode();
        int index = 0;
        for (int i = 0; i < cycle.length; i++) {
            if (cycle[i] == current) {
                index = i;
                break;
            }
        }
        this.manosaba$setGameMode(cycle[(index + 1) % cycle.length]);
    }

    @Override
    public boolean manosaba$allowCommands() {
        return this.manosaba$getCommands();
    }

    @Override
    public void manosaba$toggleCommands() {
        this.manosaba$setCommands(!this.manosaba$getCommands());
    }

    @Override
    public int manosaba$port() {
        return this.manosaba$getPort();
    }

    @Override
    public String manosaba$portText() {
        EditBox portEdit = this.manosaba$getPortEdit();
        return portEdit != null ? portEdit.getValue() : "";
    }

    @Override
    public void manosaba$setPortInput(String text) {
        EditBox portEdit = this.manosaba$getPortEdit();
        if (portEdit != null) {
            // 触发原版 responder：同步 port 字段 / 校验颜色 / hint / 创建按钮可用性
            portEdit.setValue(text);
        }
    }

    @Override
    @Nullable
    public Component manosaba$portError() {
        EditBox portEdit = this.manosaba$getPortEdit();
        if (portEdit == null) {
            return null;
        }
        String text = portEdit.getValue();
        if (text.isBlank()) {
            // 留空 = 自动选用可用端口（原版 tryParsePort 语义，无副作用复刻）
            return null;
        }
        try {
            int port = Integer.parseInt(text);
            if (port < 1024 || port > 65535) {
                return Component.translatable("lanServer.port.invalid.new", 1024, 65535);
            }
            return !HttpUtil.isPortAvailable(port) ? Component.translatable("lanServer.port.unavailable.new", 1024, 65535) : null;
        } catch (NumberFormatException e) {
            return Component.translatable("lanServer.port.invalid.new", 1024, 65535);
        }
    }

    @Override
    public void manosaba$create() {
        // 复刻原版「创建局域网世界」按钮 lambda：关闭屏幕 + publishServer + 聊天栏消息 + 更新窗口标题
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        mc.setScreen(null);
        Component message;
        if (server != null && server.publishServer(this.manosaba$getGameMode(), this.manosaba$getCommands(), this.manosaba$getPort())) {
            message = PublishCommand.getSuccessMessage(this.manosaba$getPort());
        } else {
            message = Component.translatable("commands.publish.failed");
        }
        mc.gui.getChat().addMessage(message);
        mc.updateTitle();
    }

    @Override
    public void manosaba$cancel() {
        // 复刻原版「取消」按钮：onClose → setScreen(lastScreen)
        ((ShareToLanScreen) (Object) this).onClose();
    }
}

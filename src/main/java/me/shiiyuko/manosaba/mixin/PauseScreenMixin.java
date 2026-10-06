package me.shiiyuko.manosaba.mixin;

import java.net.URI;
import java.util.List;
import javax.annotation.Nullable;
import com.mojang.realmsclient.RealmsMainScreen;
import me.shiiyuko.manosaba.bridge.ActiveUiReclaimer;
import me.shiiyuko.manosaba.bridge.ComposeInputBridge;
import me.shiiyuko.manosaba.bridge.PauseHost;
import me.shiiyuko.manosaba.ui.ManosabaOptionsScreen;
import me.shiiyuko.manosaba.ui.ManosabaPauseUi;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonLinks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 游戏暂停菜单（TEST48 魔女审判化）：原版 PauseScreen 保留为逻辑壳（暂停语义 /
 * F3+ESC 的 showPauseMenu 状态 / 断线与草稿报告流程），渲染与输入全部转发给内嵌的
 * ManosabaPauseUi（魔女审判「手机菜单」Compose 界面，GAME_DIM 暗化游戏画面作背景）。
 *
 * <p>按钮映射（详见 PauseHost 接口文档）：
 * 保存→进度 / 读取→回到游戏 / 历史记录→对局域网开放 / 选项设置→本模组设置界面 /
 * 返回标题画面→保存并退回到标题屏幕；统计信息 / 提供反馈 / 报告漏洞 / 模组
 * （原版与 NeoForge 的其余按钮）位于手机底部的灰色区域。
 *
 * <p>关键注入点：
 * <ul>
 *   <li>init HEAD/TAIL：创建内嵌界面与输入桥（children 首位，先于原版控件收到事件），
 *       登记 ActiveUiReclaimer；子屏往返 / resize 时 PauseScreen.init 重跑，
 *       桥接控件自动重建；</li>
 *   <li>render HEAD cancel：转发整屏 Compose 绘制，并每帧拉回桥接控件焦点
 *       （Tab 键在 Screen 层先行切换焦点，先于 children 派发）；</li>
 *   <li>onClose override：目标类未定义 onClose（Screen 默认实现即 popGuiLayer），
 *       混入后覆盖为「释放界面资源 + popGuiLayer」——与 NeoForge GUI 层栈语义一致
 *       （栈空时即 setScreen(null) 回到游戏，栈非空时恢复上一屏）。</li>
 * </ul>
 *
 * <p>释放链：resume / onClose 显式释放；子屏往返（进度 / 统计 / 选项 / 模组 / LAN）
 * 不释放，返回暂停屏时由 init 重建桥接、界面保留；保存退出等替换路径由标题屏等
 * 屏幕初始化时的 ActiveUiReclaimer 登记兜底。
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin implements PauseHost {

    @Unique
    @Nullable
    private ManosabaPauseUi manosaba$ui;

    @Unique
    @Nullable
    private ComposeInputBridge manosaba$bridge;

    /** 是否显示完整菜单（false = F3+ESC 等场景仅显示「游戏已暂停」）。 */
    @Accessor("showPauseMenu")
    protected abstract boolean manosaba$getShowPauseMenu();

    @Inject(method = "init()V", at = @At("HEAD"))
    private void manosaba$onInitHead(CallbackInfo ci) {
        if (this.manosaba$ui == null) {
            this.manosaba$ui = new ManosabaPauseUi((PauseScreen) (Object) this);
        }
        // 桥接控件每次 init 重建（覆盖 resize / 子屏返回后的新尺寸），位于 children 首位
        this.manosaba$bridge = new ComposeInputBridge((PauseScreen) (Object) this, this.manosaba$ui);
        // Screen.children() 是公开方法（父类字段无法被 mixin 静态定位，故不用 @Shadow 字段）
        @SuppressWarnings("unchecked")
        List<GuiEventListener> screenChildren = (List<GuiEventListener>) ((Screen) (Object) this).children();
        screenChildren.add(0, this.manosaba$bridge);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void manosaba$onInitTail(CallbackInfo ci) {
        if (this.manosaba$bridge != null) {
            // 让桥接控件取得焦点：键盘与拖拽/滚轮事件依赖 focused 派发
            ((PauseScreen) (Object) this).setFocused(this.manosaba$bridge);
        }
        if (this.manosaba$ui != null) {
            // 登记释放器：其它 manosaba 屏幕初始化时 arm 换任自动释放
            ActiveUiReclaimer.arm(this, this.manosaba$ui::disposeUi);
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true)
    private void manosaba$onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.manosaba$ui == null) {
            return;
        }
        PauseScreen self = (PauseScreen) (Object) this;
        // Tab 键在 Screen.keyPressed 中先行切换焦点，每帧拉回桥接控件
        if (this.manosaba$bridge != null && self.getFocused() != this.manosaba$bridge) {
            self.setFocused(this.manosaba$bridge);
        }
        this.manosaba$ui.render(guiGraphics, mouseX, mouseY, partialTick);
        ci.cancel();
    }

    /**
     * 覆盖 Screen.onClose（目标类未定义该方法）：释放内嵌界面资源后按 NeoForge
     * GUI 层语义关闭——暂停屏由 setScreen 打开（未入层栈）时栈为空，
     * popGuiLayer 即 setScreen(null) 回到游戏，与原版 ESC 行为一致。
     */
    public void onClose() {
        ActiveUiReclaimer.dispose();
        Minecraft.getInstance().popGuiLayer();
    }

    // ---------------- PauseHost ----------------

    @Override
    public boolean manosaba$showPauseMenu() {
        return this.manosaba$getShowPauseMenu();
    }

    @Override
    public void manosaba$resume() {
        // 复刻原版「回到游戏」按钮：setScreen(null) + 抓取鼠标
        ActiveUiReclaimer.dispose();
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(null);
        mc.mouseHandler.grabMouse();
    }

    @Override
    public void manosaba$openAdvancements() {
        // 复刻原版「进度」按钮
        PauseScreen self = (PauseScreen) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new AdvancementsScreen(mc.player.connection.getAdvancements(), self));
    }

    @Override
    public void manosaba$openStats() {
        // 复刻原版「统计信息」按钮
        PauseScreen self = (PauseScreen) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new StatsScreen(self, mc.player.getStats()));
    }

    @Override
    public void manosaba$openFeedback() {
        // 复刻原版「提供反馈」链接按钮（稳定版用 RELEASE 链接，快照用 SNAPSHOT）
        URI uri = SharedConstants.getCurrentVersion().isStable()
                ? CommonLinks.RELEASE_FEEDBACK
                : CommonLinks.SNAPSHOT_FEEDBACK;
        ConfirmLinkScreen.confirmLinkNow((PauseScreen) (Object) this, uri);
    }

    @Override
    public void manosaba$reportBugs() {
        // 复刻原版「报告漏洞」链接按钮
        ConfirmLinkScreen.confirmLinkNow((PauseScreen) (Object) this, CommonLinks.SNAPSHOT_BUGS_FEEDBACK);
    }

    @Override
    public boolean manosaba$reportBugsActive() {
        // 原版语义：仅非 side series 版本（快照专属分支）可用
        return !SharedConstants.getCurrentVersion().getDataVersion().isSideSeries();
    }

    @Override
    public void manosaba$openOptions() {
        // 打开本模组设置界面；titleParent 为 null 时使用 GAME_DIM 背景（暂停中的游戏画面）
        PauseScreen self = (PauseScreen) (Object) this;
        Minecraft.getInstance().setScreen(new ManosabaOptionsScreen(self));
    }

    @Override
    public void manosaba$openLanOrSocial() {
        // 复刻原版「对局域网开放 / 举报玩家」同槽位逻辑
        PauseScreen self = (PauseScreen) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer() && !mc.getSingleplayerServer().isPublished()) {
            mc.setScreen(new ShareToLanScreen(self));
        } else {
            mc.setScreen(new SocialInteractionsScreen(self));
        }
    }

    @Override
    public void manosaba$openMods() {
        // 复刻 NeoForge「模组」按钮
        PauseScreen self = (PauseScreen) (Object) this;
        Minecraft.getInstance().setScreen(new net.neoforged.neoforge.client.gui.ModListScreen(self));
    }

    @Override
    public void manosaba$saveAndQuit() {
        PauseScreen self = (PauseScreen) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        // 点击即离开暂停屏：先行释放界面资源（草稿报告屏 / 断线流程不再需要暂停界面）
        ActiveUiReclaimer.dispose();
        mc.getReportingContext().draftReportHandled(mc, self, this::manosaba$disconnectToTitle, true);
    }

    /** 复刻原版 PauseScreen.onDisconnect：本地存档回标题屏；服务器回 Realms 或多人屏。 */
    @Unique
    private void manosaba$disconnectToTitle() {
        Minecraft mc = Minecraft.getInstance();
        boolean local = mc.isLocalServer();
        ServerData serverData = mc.getCurrentServer();
        mc.level.disconnect();
        if (local) {
            mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
        } else {
            mc.disconnect();
        }
        TitleScreen titleScreen = new TitleScreen();
        if (local) {
            mc.setScreen(titleScreen);
        } else if (serverData != null && serverData.isRealm()) {
            mc.setScreen(new RealmsMainScreen(titleScreen));
        } else {
            mc.setScreen(new JoinMultiplayerScreen(titleScreen));
        }
    }
}

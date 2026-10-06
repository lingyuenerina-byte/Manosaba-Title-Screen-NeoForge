package me.shiiyuko.manosaba.mixin;

import java.util.List;
import javax.annotation.Nullable;
import me.shiiyuko.manosaba.bridge.ActiveUiReclaimer;
import me.shiiyuko.manosaba.bridge.ComposeInputBridge;
import me.shiiyuko.manosaba.bridge.GhostSourceHolder;
import me.shiiyuko.manosaba.bridge.SelectWorldHost;
import me.shiiyuko.manosaba.bridge.WorldListData;
import me.shiiyuko.manosaba.ui.ManosabaSelectWorldUi;
import me.shiiyuko.manosaba.ui.ManosabaTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 选择世界屏幕（TEST37 魔女审判化）：原版 SelectWorldScreen 保留为逻辑壳
 * （世界列表 / 搜索过滤 / 子屏跳转），渲染与输入全部转发给内嵌的
 * ManosabaSelectWorldUi（魔女审判风格 Compose 界面，视觉与设置页一致）。
 *
 * <p>与原版列表的数据契约：原版 WorldSelectionList 的条目填充发生在 renderWidget
 * 的异步扫描轮询（mod 接管渲染后不再执行），由 {@link #manosaba$onRender} 每帧
 * 调用 {@link WorldListData#manosaba$refreshData()} 复刻。
 *
 * <p>ESC 特殊处理：SelectWorldScreen 未 override keyPressed，Screen.keyPressed 会在
 * children 派发之前直接消费 ESC 并调用 onClose，因此弹层/搜索态的 ESC 拦截
 * 必须注入 {@link #manosaba$onClose}（HEAD cancellable）。
 *
 * <p>初始焦点：原版 setInitialFocus 会把焦点赋予搜索框（EditBox），
 * 注入 HEAD 取消并改为桥接控件持有焦点（搜索输入由 Compose 侧自管状态）。
 */
@Mixin(SelectWorldScreen.class)
public abstract class SelectWorldScreenMixin implements SelectWorldHost {

    @Unique
    @Nullable
    private ManosabaSelectWorldUi manosaba$ui;

    @Unique
    @Nullable
    private ComposeInputBridge manosaba$bridge;

    @Shadow
    private WorldSelectionList list;

    @Shadow
    protected EditBox searchBox;

    /** 上一屏幕：主界面打开选择世界时为 ManosabaTitleScreen，用于「倒映」背景。 */
    @Accessor("lastScreen")
    @Nullable
    protected abstract Screen manosaba$getLastScreen();

    @Unique
    @Nullable
    private ManosabaTitleScreen manosaba$ghostSource() {
        return this.manosaba$getLastScreen() instanceof ManosabaTitleScreen title ? title : null;
    }

    @Inject(method = "init()V", at = @At("HEAD"))
    private void manosaba$onInitHead(CallbackInfo ci) {
        SelectWorldScreen self = (SelectWorldScreen) (Object) this;
        if (this.manosaba$ui == null) {
            this.manosaba$ui = new ManosabaSelectWorldUi(self, this.manosaba$ghostSource());
        }
        // 桥接控件每次 init 重建（覆盖 resize / 同屏 setScreen 返回后的新尺寸），位于 children 首位
        this.manosaba$bridge = new ComposeInputBridge(self, this.manosaba$ui);
        // Screen.children() 是公开方法（父类字段无法被 mixin 静态定位，故不用 @Shadow 字段）
        @SuppressWarnings("unchecked")
        List<GuiEventListener> screenChildren = (List<GuiEventListener>) self.children();
        screenChildren.add(0, this.manosaba$bridge);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void manosaba$onInitTail(CallbackInfo ci) {
        SelectWorldScreen self = (SelectWorldScreen) (Object) this;
        // 兜底释放：编辑世界屏（TEST46）「优化世界」经备份确认 / 优化屏绕行返回本屏时，
        // 其界面资源在此统一释放（该屏无 removed 可注入；自身退出路径已提前释放则为空操作）
        ActiveUiReclaimer.dispose();
        if (this.manosaba$bridge != null) {
            // 让桥接控件取得焦点：键盘与拖拽/滚轮事件依赖 focused 派发
            self.setFocused(this.manosaba$bridge);
        }
        // rebuildWidgets（同屏 setScreen 返回 / resize）后主动通知界面重组：
        // 编辑世界 / 删除世界返回路径不重建 Compose 场景（同屏 setScreen 不触发 removed），
        // 只有显式通知才能立即呈现更新后的世界列表（TEST38 同类报修的列表页版本）
        if (this.manosaba$ui != null) {
            this.manosaba$ui.onHostDataChanged();
        }
    }

    @Inject(method = "setInitialFocus()V", at = @At("HEAD"), cancellable = true)
    private void manosaba$onSetInitialFocus(CallbackInfo ci) {
        // 原版会把焦点交给搜索框；嵌入模式下改由桥接控件持有（搜索输入由 Compose 自管）
        if (this.manosaba$bridge != null) {
            ((SelectWorldScreen) (Object) this).setFocused(this.manosaba$bridge);
            ci.cancel();
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true)
    private void manosaba$onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.manosaba$ui == null) {
            return;
        }
        SelectWorldScreen self = (SelectWorldScreen) (Object) this;
        // Tab 键在 Screen.keyPressed 中先行切换焦点（先于 children 派发），每帧拉回桥接控件
        if (this.manosaba$bridge != null && self.getFocused() != this.manosaba$bridge) {
            self.setFocused(this.manosaba$bridge);
        }
        // 复刻原版 renderWidget 的异步扫描轮询：条目变化时返回 true，驱动界面重组
        boolean changed = false;
        if (this.list != null) {
            changed = ((WorldListData) (Object) this.list).manosaba$refreshData();
        }
        this.manosaba$ui.onHostFrame(changed);
        this.manosaba$ui.render(guiGraphics, mouseX, mouseY, partialTick);
        ci.cancel();
    }

    @Inject(method = "onClose()V", at = @At("HEAD"), cancellable = true)
    private void manosaba$onClose(CallbackInfo ci) {
        // ESC 由 Screen.keyPressed 直达本方法（先于 children）：删除弹层 / 搜索态优先消费
        if (this.manosaba$ui != null && this.manosaba$ui.handleEsc()) {
            ci.cancel();
        }
    }

    @Inject(method = "removed()V", at = @At("TAIL"))
    private void manosaba$onRemoved(CallbackInfo ci) {
        if (this.manosaba$ui != null) {
            this.manosaba$ui.disposeUi();
        }
    }

    // ---------------- SelectWorldHost ----------------

    @Override
    @SuppressWarnings("unchecked")
    public List<WorldSelectionList.Entry> manosaba$entries() {
        if (this.list == null) {
            return List.of();
        }
        return (List<WorldSelectionList.Entry>) this.list.children();
    }

    @Override
    @Nullable
    public WorldSelectionList.Entry manosaba$selectedEntry() {
        return this.list == null ? null : this.list.getSelected();
    }

    @Override
    @Nullable
    public LevelSummary manosaba$summaryOf(WorldSelectionList.Entry entry) {
        if (this.list == null || entry == null) {
            return null;
        }
        return ((WorldListData) (Object) this.list).manosaba$summaryOf(entry);
    }

    @Override
    public void manosaba$select(@Nullable WorldSelectionList.Entry entry) {
        // setSelected 为原版列表的公开 override：同步条目选中与原版按钮状态
        if (this.list != null) {
            this.list.setSelected(entry);
        }
    }

    @Override
    public void manosaba$joinSelected() {
        // 进世界流程的加载过渡屏（ProgressScreen→「主界面+遮罩」）
        // 读取「倒映」背景来源：写入静态 holder 供其渲染读取
        GhostSourceHolder.set(this.manosaba$ghostSource());
        if (this.list != null) {
            this.list.getSelectedOpt().ifPresent(WorldSelectionList.WorldListEntry::joinWorld);
        }
    }

    @Override
    public void manosaba$editSelected() {
        // 编辑世界屏（TEST46）构造器不持有 parent：「倒映」背景来源写入静态 holder 供其 init 读取
        GhostSourceHolder.set(this.manosaba$ghostSource());
        if (this.list != null) {
            this.list.getSelectedOpt().ifPresent(WorldSelectionList.WorldListEntry::editWorld);
        }
    }

    @Override
    public void manosaba$deleteSelected() {
        // 删除确认已由 Compose 界面完成，这里直接走原版删除（跳过原版 ConfirmScreen）
        if (this.list != null) {
            this.list.getSelectedOpt().ifPresent(WorldSelectionList.WorldListEntry::doDeleteWorld);
        }
    }

    @Override
    public void manosaba$recreateSelected() {
        // 重建世界经 CreateWorldScreen.createFromExisting(screen) 打开创建世界屏：
        // 幽灵层来源写入静态 holder 供其 init 读取（该路径不持有标题屏引用）
        GhostSourceHolder.set(this.manosaba$ghostSource());
        if (this.list != null) {
            this.list.getSelectedOpt().ifPresent(WorldSelectionList.WorldListEntry::recreateWorld);
        }
    }

    @Override
    public void manosaba$createWorld() {
        GhostSourceHolder.set(this.manosaba$ghostSource());
        CreateWorldScreen.openFresh(Minecraft.getInstance(), (SelectWorldScreen) (Object) this);
    }

    @Override
    public void manosaba$reloadList() {
        if (this.list != null) {
            ((WorldListData) (Object) this.list).manosaba$reloadWorldList();
        }
    }

    @Override
    public void manosaba$updateFilter(String filter) {
        // 写入原版搜索框：responder 链（list.updateFilter）同步过滤列表；
        // 过滤生效依赖 refreshData 每帧轮询（fillLevels 重建条目后下一帧被感知）
        if (this.searchBox != null) {
            this.searchBox.setValue(filter);
        }
    }
}

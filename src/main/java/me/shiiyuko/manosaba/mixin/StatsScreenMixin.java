package me.shiiyuko.manosaba.mixin;

import java.util.List;
import javax.annotation.Nullable;
import me.shiiyuko.manosaba.bridge.ComposeInputBridge;
import me.shiiyuko.manosaba.bridge.StatsHost;
import me.shiiyuko.manosaba.ui.ManosabaStatsUi;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.stats.StatsCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「统计信息」屏幕（TEST55 魔女审判化）：原版 StatsScreen 保留为逻辑壳
 * （统计请求 / 数据回包 / 返回行为），渲染与输入全部转发给内嵌的
 * ManosabaStatsUi（魔女审判风格 Compose 界面，GAME_DIM 背景）。
 *
 * <p>目标类未 override render（mixin 无法注入），故桥接控件置于 renderables
 * 末尾经 RenderTarget 转发整屏绘制；背景为 GAME_DIM 半透明暗化，若原版控件
 * 留在 renderables 会透出残影（界面全量接管），故 init / onStatsUpdated 时
 * 清空其余 renderable，仅保留桥接控件。
 *
 * <p>onStatsUpdated（数据回包）时原版 initButtons / setActiveList 会重新注册
 * 控件并抢走焦点：TAIL 注入重新清空 renderables、桥移到末尾并拉回焦点，
 * 同时通知界面重建统计数据。
 *
 * <p>生命周期：目标类无 removed()，释放点挂在两个出口——onClose（ESC /
 * 「完成」路径，TAIL 注入）与界面按钮显式释放。
 */
@Mixin(StatsScreen.class)
public abstract class StatsScreenMixin implements StatsHost {

    @Unique
    @Nullable
    private ManosabaStatsUi manosaba$ui;

    @Unique
    @Nullable
    private ComposeInputBridge manosaba$bridge;

    @Accessor("stats")
    protected abstract StatsCounter manosaba$getStats();

    @Accessor("isLoading")
    protected abstract boolean manosaba$getIsLoading();

    @Inject(method = "init()V", at = @At("TAIL"))
    private void manosaba$onInitTail(CallbackInfo ci) {
        StatsScreen self = (StatsScreen) (Object) this;
        if (this.manosaba$ui == null) {
            this.manosaba$ui = new ManosabaStatsUi(self);
        }
        // 桥接控件每次 init 重建（覆盖 resize 后的新尺寸）
        this.manosaba$bridge = new ComposeInputBridge(self, this.manosaba$ui);
        // 渲染：清空原版控件（LoadingDots / 列表 / 按钮），仅保留桥在 renderables 末尾
        self.renderables.removeIf(renderable -> renderable != this.manosaba$bridge);
        self.renderables.add(this.manosaba$bridge);
        // 输入：children 移到首位（事件遍历正序，桥最先收到并吞掉）
        @SuppressWarnings("unchecked")
        List<GuiEventListener> screenChildren = (List<GuiEventListener>) self.children();
        screenChildren.remove(this.manosaba$bridge);
        screenChildren.add(0, this.manosaba$bridge);
        // 焦点：键盘与拖拽/滚轮事件依赖 focused 派发
        self.setFocused(this.manosaba$bridge);
    }

    @Inject(method = "onStatsUpdated()V", at = @At("TAIL"))
    private void manosaba$onStatsUpdated(CallbackInfo ci) {
        if (this.manosaba$bridge == null) {
            return;
        }
        StatsScreen self = (StatsScreen) (Object) this;
        // 原版 initButtons / setActiveList 重新注册了控件：再次清空并保持桥在末尾
        self.renderables.removeIf(renderable -> renderable != this.manosaba$bridge);
        self.renderables.remove(this.manosaba$bridge);
        self.renderables.add(this.manosaba$bridge);
        // setInitialFocus 把焦点交给了统计列表：拉回桥接控件
        self.setFocused(this.manosaba$bridge);
        if (this.manosaba$ui != null) {
            this.manosaba$ui.onHostDataChanged();
        }
    }

    @Inject(method = "onClose()V", at = @At("TAIL"))
    private void manosaba$onClose(CallbackInfo ci) {
        // ESC 路径（Screen.keyPressed 直达 onClose）与「完成」按钮路径：释放 Compose 资源
        if (this.manosaba$ui != null) {
            this.manosaba$ui.disposeUi();
        }
    }

    // ---------------- StatsHost ----------------

    @Override
    public StatsCounter manosaba$stats() {
        return this.manosaba$getStats();
    }

    @Override
    public boolean manosaba$isLoading() {
        return this.manosaba$getIsLoading();
    }

    @Override
    public void manosaba$refocus() {
        if (this.manosaba$bridge != null) {
            StatsScreen self = (StatsScreen) (Object) this;
            if (self.getFocused() != this.manosaba$bridge) {
                self.setFocused(this.manosaba$bridge);
            }
        }
    }

    @Override
    public void manosaba$done() {
        // 复刻原版「完成」按钮：onClose → setScreen(lastScreen)
        ((StatsScreen) (Object) this).onClose();
    }
}

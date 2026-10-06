package me.shiiyuko.manosaba.mixin;

import java.util.List;
import javax.annotation.Nullable;
import me.shiiyuko.manosaba.bridge.ComposeInputBridge;
import me.shiiyuko.manosaba.bridge.ModListHost;
import me.shiiyuko.manosaba.ui.ManosabaModListUi;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.ModListScreen;
import net.neoforged.neoforge.client.gui.widget.ModListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge「模组列表」屏幕（TEST56 魔女审判化）：原版 ModListScreen 保留为
 * 逻辑壳（模组数据 / 选中 / 配置入口 / 返回行为），渲染与输入全部转发给内嵌的
 * ManosabaModListUi（魔女审判风格 Compose 界面，GAME_DIM 背景）。
 *
 * <p>列表显示由界面自行复刻（过滤 / 排序 / 选中），选中经 modId 映射回原版
 * ModListWidget.ModEntry（setSelected + updateCache），保证「配置」按钮
 * （displayModConfig）读到正确的 selected；打开配置屏时界面资源保留
 * （返回本屏 init 重跑，桥接重建，Compose 场景不变）。
 *
 * <p>生命周期：目标类无 removed()，释放点挂在两个出口——onClose（ESC /
 * 「完成」路径，TAIL 注入）与界面按钮显式释放。
 */
@Mixin(ModListScreen.class)
public abstract class ModListScreenMixin implements ModListHost {

    @Unique
    @Nullable
    private ManosabaModListUi manosaba$ui;

    @Unique
    @Nullable
    private ComposeInputBridge manosaba$bridge;

    /** 模组列表控件：选中映射需按 modId 查找原版 ModEntry。 */
    @Accessor("modList")
    @Nullable
    protected abstract ModListWidget manosaba$getModList();

    /** 复刻原版「配置」按钮：private displayModConfig（selected 为空时原版内部自检）。 */
    @Invoker("displayModConfig")
    protected abstract void manosaba$invokeDisplayModConfig();

    @Inject(method = "init()V", at = @At("HEAD"))
    private void manosaba$onInitHead(CallbackInfo ci) {
        if (this.manosaba$ui == null) {
            this.manosaba$ui = new ManosabaModListUi((ModListScreen) (Object) this);
        }
        // 桥接控件每次 init 重建（覆盖 resize / 从配置屏返回后的新尺寸），位于 children 首位
        this.manosaba$bridge = new ComposeInputBridge((ModListScreen) (Object) this, this.manosaba$ui);
        @SuppressWarnings("unchecked")
        List<GuiEventListener> screenChildren = (List<GuiEventListener>) ((Screen) (Object) this).children();
        screenChildren.add(0, this.manosaba$bridge);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void manosaba$onInitTail(CallbackInfo ci) {
        if (this.manosaba$bridge != null) {
            // 让桥接控件取得焦点：键盘与拖拽/滚轮事件依赖 focused 派发
            ((ModListScreen) (Object) this).setFocused(this.manosaba$bridge);
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true)
    private void manosaba$onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.manosaba$ui == null) {
            return;
        }
        ModListScreen self = (ModListScreen) (Object) this;
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
        // ESC 路径（Screen.keyPressed 直达 onClose）与「完成」按钮路径：释放 Compose 资源
        if (this.manosaba$ui != null) {
            this.manosaba$ui.disposeUi();
        }
    }

    // ---------------- ModListHost ----------------

    @Override
    public void manosaba$selectMod(String modId) {
        ModListWidget list = this.manosaba$getModList();
        if (list == null) {
            return;
        }
        for (GuiEventListener child : list.children()) {
            if (child instanceof ModListWidget.ModEntry entry && entry.getInfo().getModId().equals(modId)) {
                // setSelected（public）：写入 selected 并 updateCache（配置按钮可用性）
                ((ModListScreen) (Object) this).setSelected(entry);
                return;
            }
        }
    }

    @Override
    public void manosaba$openConfig() {
        this.manosaba$invokeDisplayModConfig();
    }

    @Override
    public void manosaba$openFolder() {
        // 复刻原版「打开模组文件夹」按钮
        Util.getPlatform().openFile(FMLPaths.MODSDIR.get().toFile());
    }

    @Override
    public void manosaba$done() {
        // 复刻原版「完成」按钮：onClose → setScreen(parentScreen)
        ((ModListScreen) (Object) this).onClose();
    }
}

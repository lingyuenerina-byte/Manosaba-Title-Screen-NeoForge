package me.shiiyuko.manosaba.mixin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import me.shiiyuko.manosaba.bridge.ActiveUiReclaimer;
import me.shiiyuko.manosaba.bridge.ComposeInputBridge;
import me.shiiyuko.manosaba.bridge.EditWorldHost;
import me.shiiyuko.manosaba.bridge.GhostSourceHolder;
import me.shiiyuko.manosaba.ui.ManosabaEditWorldUi;
import net.minecraft.FileUtil;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.client.gui.screens.worldselection.OptimizeWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.apache.commons.io.FileUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 编辑世界屏幕（TEST46 魔女审判化）：原版 EditWorldScreen 保留为逻辑壳
 * （名称 EditBox、存档访问、备份 / 优化 / 重命名回调），渲染与输入全部转发给
 * 内嵌的 ManosabaEditWorldUi（魔女审判风格 Compose 界面，视觉与设置页一致）。
 *
 * <p>名称输入由界面自管状态，每次按键实时写回原版 EditBox（responder 链同步
 * 原版保存按钮状态），保证 onRename 保存拿到最新值。
 *
 * <p>释放：目标类未 override removed，采用「自释放 + 兜底」：
 * <ul>
 *   <li>取消（onClose TAIL）与保存（onRename TAIL）路径由注入直接释放；</li>
 *   <li>「进行备份」在 host 方法内释放（原版无论成败都切屏返回）；</li>
 *   <li>「优化世界」经 BackupConfirmScreen / OptimizeWorldScreen 绕行时不释放
 *       （可能返回本屏），返回选择世界屏后由其 init 兜底释放（ActiveUiReclaimer）。</li>
 * </ul>
 *
 * <p>「倒映」背景来源：构造器不持有 parent，打开前由 SelectWorldScreenMixin 的
 * editSelected 写入 GhostSourceHolder。
 */
@Mixin(EditWorldScreen.class)
public abstract class EditWorldScreenMixin implements EditWorldHost {

    @Unique
    @Nullable
    private ManosabaEditWorldUi manosaba$ui;

    @Unique
    @Nullable
    private ComposeInputBridge manosaba$bridge;

    /** 世界名称输入框（构造器创建，final 字段，只读引用）。 */
    @Accessor("nameEdit")
    @Nullable
    protected abstract EditBox manosaba$getNameEdit();

    /** 存档访问句柄（图标文件 / 目录路径 / 备份等操作的数据源）。 */
    @Accessor("levelAccess")
    protected abstract LevelStorageSource.LevelStorageAccess manosaba$getLevelAccess();

    /** 退出回调（重命名 / 备份 / 取消后关闭存档并切回选择世界屏）。 */
    @Accessor("callback")
    protected abstract BooleanConsumer manosaba$getCallback();

    /** 原版重命名 + 回调（保存按钮行为）。 */
    @Invoker("onRename")
    protected abstract void manosaba$invokeOnRename(String name);

    @Inject(method = "init()V", at = @At("HEAD"))
    private void manosaba$onInitHead(CallbackInfo ci) {
        if (this.manosaba$ui == null) {
            this.manosaba$ui = new ManosabaEditWorldUi((EditWorldScreen) (Object) this, GhostSourceHolder.get());
        }
        // 桥接控件每次 init 重建（覆盖 rebuildWidgets 后的新尺寸），位于 children 首位
        this.manosaba$bridge = new ComposeInputBridge((EditWorldScreen) (Object) this, this.manosaba$ui);
        // Screen.children() 是公开方法（父类字段无法被 mixin 静态定位，故不用 @Shadow 字段）
        @SuppressWarnings("unchecked")
        List<GuiEventListener> screenChildren = (List<GuiEventListener>) ((Screen) (Object) this).children();
        screenChildren.add(0, this.manosaba$bridge);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void manosaba$onInitTail(CallbackInfo ci) {
        if (this.manosaba$bridge != null) {
            // 让桥接控件取得焦点：键盘与拖拽/滚轮事件依赖 focused 派发
            ((EditWorldScreen) (Object) this).setFocused(this.manosaba$bridge);
        }
        if (this.manosaba$ui != null) {
            ActiveUiReclaimer.arm(this, this.manosaba$ui::disposeUi);
        }
    }

    @Inject(method = "setInitialFocus()V", at = @At("HEAD"), cancellable = true)
    private void manosaba$onSetInitialFocus(CallbackInfo ci) {
        // 原版会把焦点交给名称输入框；嵌入模式下改由桥接控件持有（输入由 Compose 自管）
        if (this.manosaba$bridge != null) {
            ((EditWorldScreen) (Object) this).setFocused(this.manosaba$bridge);
            ci.cancel();
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true)
    private void manosaba$onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.manosaba$ui == null) {
            return;
        }
        EditWorldScreen self = (EditWorldScreen) (Object) this;
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
        // 取消 / ESC 路径：原版已回调切屏（返回选择世界屏），释放界面资源
        ActiveUiReclaimer.dispose();
    }

    @Inject(method = "onRename(Ljava/lang/String;)V", at = @At("TAIL"))
    private void manosaba$onRename(String name, CallbackInfo ci) {
        // 保存路径：原版已回调切屏，释放界面资源
        ActiveUiReclaimer.dispose();
    }

    // ---------------- EditWorldHost ----------------

    @Override
    public String manosaba$name() {
        EditBox edit = this.manosaba$getNameEdit();
        return edit != null ? edit.getValue() : "";
    }

    @Override
    public void manosaba$setName(String value) {
        EditBox edit = this.manosaba$getNameEdit();
        if (edit != null) {
            edit.setValue(value);
        }
    }

    @Override
    public boolean manosaba$canResetIcon() {
        // 原版「重置图标」按钮 active 条件：图标文件存在（重置删除后自动转不可用）
        return this.manosaba$getLevelAccess().getIconFile().filter(Files::isRegularFile).isPresent();
    }

    @Override
    public void manosaba$resetIcon() {
        this.manosaba$getLevelAccess().getIconFile().ifPresent(path -> FileUtils.deleteQuietly(path.toFile()));
    }

    @Override
    public void manosaba$openFolder() {
        Util.getPlatform().openPath(this.manosaba$getLevelAccess().getLevelPath(LevelResource.ROOT));
    }

    @Override
    public void manosaba$backup() {
        // 复刻原版备份按钮：执行备份 → 回调（changed=!created → 刷新 + 切屏）；无论成败都切屏返回
        boolean created = EditWorldScreen.makeBackupAndShowToast(this.manosaba$getLevelAccess());
        ActiveUiReclaimer.dispose();
        this.manosaba$getCallback().accept(!created);
    }

    @Override
    public void manosaba$openBackupFolder() {
        LevelStorageSource source = Minecraft.getInstance().getLevelSource();
        Path path = source.getBackupPath();
        try {
            FileUtil.createDirectoriesSafe(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Util.getPlatform().openPath(path);
    }

    @Override
    public void manosaba$optimize() {
        // 复刻原版优化按钮：BackupConfirmScreen（取消返回本屏；确认可选备份后进入优化屏）。
        // 本路径不释放：从确认屏 / 优化屏存在返回本屏的可能
        Minecraft minecraft = Minecraft.getInstance();
        EditWorldScreen self = (EditWorldScreen) (Object) this;
        minecraft.setScreen(
            new BackupConfirmScreen(
                () -> minecraft.setScreen(self),
                (backup, eraseCache) -> {
                    if (backup) {
                        EditWorldScreen.makeBackupAndShowToast(this.manosaba$getLevelAccess());
                    }
                    minecraft.setScreen(
                        OptimizeWorldScreen.create(
                            minecraft,
                            this.manosaba$getCallback(),
                            minecraft.getFixerUpper(),
                            this.manosaba$getLevelAccess(),
                            eraseCache
                        )
                    );
                },
                Component.translatable("optimizeWorld.confirm.title"),
                Component.translatable("optimizeWorld.confirm.description"),
                true
            )
        );
    }

    @Override
    public void manosaba$save() {
        EditBox edit = this.manosaba$getNameEdit();
        this.manosaba$invokeOnRename(edit != null ? edit.getValue() : "");
    }

    @Override
    public void manosaba$cancel() {
        ((EditWorldScreen) (Object) this).onClose();
    }
}

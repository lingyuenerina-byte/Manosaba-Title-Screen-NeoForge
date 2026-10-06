package me.shiiyuko.manosaba.bridge;

import java.nio.file.Path;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;

/**
 * 数据包选择屏幕的宿主能力接口：由 PackSelectionScreenMixin 实现并混入原版屏幕，
 * 供内嵌的 Compose 界面（ManosabaPackSelectionUi）读取数据包模型与包目录。
 *
 * <p>位于 bridge 包而非 mixin 包：mixin 包（me.shiiyuko.manosaba.mixin）归
 * manosaba.mixins.json 所有，包内非 @Mixin 类被外部类直接引用/加载会触发
 * IllegalClassLoadError（cannot be referenced directly）。
 */
public interface PackSelectionHost {

    /** 原版数据包选择模型（提交 / 选中列表读写均经其公开 API）。 */
    PackSelectionModel manosaba$model();

    /** 数据包目录（「打开包文件夹」按钮使用）。 */
    Path manosaba$packDir();
}

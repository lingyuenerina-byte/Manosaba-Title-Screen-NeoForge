package me.shiiyuko.manosaba.bridge;

/**
 * 编辑世界屏幕（EditWorldScreen）的宿主能力接口：由 EditWorldScreenMixin 实现并
 * 混入原版屏幕，供内嵌的 Compose 界面（ManosabaEditWorldUi）读取世界名称、执行
 * 各项世界操作（图标 / 文件夹 / 备份 / 优化）与保存 / 取消。
 *
 * <p>名称输入由界面自管状态，每次按键实时写回原版 EditBox（nameEdit），
 * 保证 onRename 保存拿到最新值；「进行备份」「保存」「取消」复刻原版行为
 * （回调切屏 + 释放界面资源）。
 *
 * <p>位于 bridge 包而非 mixin 包：mixin 包（me.shiiyuko.manosaba.mixin）归
 * manosaba.mixins.json 所有，包内非 @Mixin 类被外部类直接引用/加载会触发
 * IllegalClassLoadError（cannot be referenced directly）。
 */
public interface EditWorldHost {

    /** 世界名称初值（原版 nameEdit 当前值）。 */
    String manosaba$name();

    /** 将界面输入的名称同步回原版 EditBox（responder 链同步原版保存按钮状态）。 */
    void manosaba$setName(String value);

    /** 图标文件是否存在（原版「重置图标」按钮的可用条件，重置后自动转不可用）。 */
    boolean manosaba$canResetIcon();

    /** 「重置图标」：删除图标文件（原版 FileUtils.deleteQuietly）。 */
    void manosaba$resetIcon();

    /** 「打开世界文件夹」：打开存档根目录。 */
    void manosaba$openFolder();

    /** 「进行备份」：执行备份并走原版回调（无论成败都切屏返回选择世界屏）。 */
    void manosaba$backup();

    /** 「打开备份文件夹」：创建并打开备份目录。 */
    void manosaba$openBackupFolder();

    /** 「优化世界」：打开原版 BackupConfirmScreen，确认后进入 OptimizeWorldScreen。 */
    void manosaba$optimize();

    /** 「保存」：原版 onRename（重命名 + 回调切屏 + 释放界面资源）。 */
    void manosaba$save();

    /** 「取消」：原版 onClose（回调切屏 + 释放界面资源）。 */
    void manosaba$cancel();
}

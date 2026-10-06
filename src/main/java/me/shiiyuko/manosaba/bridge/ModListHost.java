package me.shiiyuko.manosaba.bridge;

/**
 * NeoForge「模组列表」屏幕（ModListScreen）的宿主能力接口：由 ModListScreenMixin
 * 实现并混入原版屏幕，供内嵌的 Compose 界面（ManosabaModListUi）执行选中、
 * 打开配置 / 模组文件夹与完成。
 *
 * <p>列表数据由界面直接读取 ModList.get().getSortedMods() 并自行复刻
 * 过滤（displayName 包含匹配）与排序（默认 / A-Z / Z-A）；选中经
 * {@link #manosaba$selectMod(String)} 按 modId 映射到原版 ModListWidget.ModEntry，
 * 保证「配置」按钮（displayModConfig）读到正确的 selected。
 *
 * <p>位于 bridge 包而非 mixin 包：mixin 包（me.shiiyuko.manosaba.mixin）归
 * manosaba.mixins.json 所有，包内非 @Mixin 类被外部类直接引用/加载会触发
 * IllegalClassLoadError（cannot be referenced directly）。
 */
public interface ModListHost {

    /** 按 modId 选中模组（写入原版 selected 并刷新 updateCache，供配置按钮使用）。 */
    void manosaba$selectMod(String modId);

    /** 复刻原版「配置」按钮：displayModConfig（IConfigScreenFactory 打开模组配置屏）。 */
    void manosaba$openConfig();

    /** 复刻原版「打开模组文件夹」按钮：Util.getPlatform().openFile(FMLPaths.MODSDIR)。 */
    void manosaba$openFolder();

    /** 复刻原版「完成」按钮：onClose → setScreen(parentScreen)。 */
    void manosaba$done();
}

package me.shiiyuko.manosaba.bridge;

/**
 * 游戏暂停菜单（PauseScreen）的宿主能力接口：由 PauseScreenMixin 实现并混入
 * 原版屏幕，供内嵌的 Compose 界面（ManosabaPauseUi）执行各按钮动作。
 *
 * <p>按钮映射（魔女审判「手机菜单」素材 → 原版功能）：
 * <ul>
 *   <li>保存 → 进度（AdvancementsScreen）；读取 → 回到游戏；</li>
 *   <li>历史记录 → 对局域网开放（不可用时回退原版同槽位的举报玩家/社交界面）；</li>
 *   <li>选项设置 → 本项目设置界面（ManosabaOptionsScreen，父屏为本暂停屏）；</li>
 *   <li>返回标题画面 → 保存并退回到标题屏幕（复刻原版断开流程）；</li>
 *   <li>灰色区域 → 统计信息 / 提供反馈 / 报告漏洞 / 模组（原版与 NeoForge 其余按钮）。</li>
 * </ul>
 *
 * <p>位于 bridge 包而非 mixin 包：mixin 包（me.shiiyuko.manosaba.mixin）归
 * manosaba.mixins.json 所有，包内非 @Mixin 类被外部类直接引用/加载会触发
 * IllegalClassLoadError（cannot be referenced directly）。
 */
public interface PauseHost {

    /** 是否显示完整菜单（false = 仅显示「游戏已暂停」标题；F3+ESC 等场景）。 */
    boolean manosaba$showPauseMenu();

    /** 「读取」/关闭图标：回到游戏（复刻原版 setScreen(null) + 抓取鼠标）。 */
    void manosaba$resume();

    /** 「保存」：打开进度界面（原版「进度」按钮 → AdvancementsScreen）。 */
    void manosaba$openAdvancements();

    /** 灰色区域：统计信息（原版「统计信息」按钮 → StatsScreen）。 */
    void manosaba$openStats();

    /** 灰色区域：提供反馈（复刻原版链接按钮；RELEASE/SNAPSHOT 链接按版本选择）。 */
    void manosaba$openFeedback();

    /** 灰色区域：报告漏洞（复刻原版链接按钮）。 */
    void manosaba$reportBugs();

    /** 灰色区域：「报告漏洞」是否可用（原版：非 isSideSeries 版本才可用）。 */
    boolean manosaba$reportBugsActive();

    /** 「选项设置」：打开本项目设置界面（父屏为本暂停屏，关闭后回到暂停菜单）。 */
    void manosaba$openOptions();

    /** 「历史记录」：对局域网开放；不可用时回退到原版同槽位按钮（举报玩家）。 */
    void manosaba$openLanOrSocial();

    /** 灰色区域：模组列表（NeoForge「模组」按钮 → ModListScreen）。 */
    void manosaba$openMods();

    /** 「返回标题画面」：保存并退回到标题屏幕（复刻原版断开流程，含举报草稿处理）。 */
    void manosaba$saveAndQuit();
}

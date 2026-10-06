package me.shiiyuko.manosaba.bridge;

import java.util.List;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;

/**
 * 多人游戏屏幕的宿主能力接口：由 JoinMultiplayerScreenMixin 实现并混入原版屏幕，
 * 供内嵌的 Compose 界面（ManosabaMultiplayerUi）读取服务器列表与调用原版逻辑。
 *
 * <p>位于 bridge 包而非 mixin 包：mixin 包（me.shiiyuko.manosaba.mixin）归
 * manosaba.mixins.json 所有，包内非 @Mixin 类被外部类直接引用/加载会触发
 * IllegalClassLoadError（cannot be referenced directly）。
 */
public interface MultiplayerHost {

    /** 服务器列表当前全部条目（含在线服务器、局域网提示头、局域网服务器）。 */
    List<ServerSelectionList.Entry> manosaba$entries();

    /** 当前选中条目（可能为 null）。 */
    ServerSelectionList.Entry manosaba$selectedEntry();

    /** 重建屏幕以刷新服务器列表（等同原版「刷新」按钮）。 */
    void manosaba$refresh();

    /** 打开原版「直接连接」子屏幕。 */
    void manosaba$directJoin();

    /** 打开原版「添加服务器」子屏幕。 */
    void manosaba$addServer();

    /** 打开原版「编辑服务器」子屏幕（仅对在线服务器条目有效）。 */
    void manosaba$editServer();

    /** 删除当前选中的服务器（确认弹层已由 Compose 界面完成）。 */
    void manosaba$deleteSelected();

    /**
     * 连接发起前的准备：把主界面（幽灵层来源）写入 GhostSourceHolder，
     * 供连接失败屏（DisconnectedScreen）读取「倒映」背景（TEST39）。
     */
    void manosaba$prepareConnect();
}

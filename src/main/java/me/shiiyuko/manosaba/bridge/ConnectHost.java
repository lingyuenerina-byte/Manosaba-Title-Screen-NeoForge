package me.shiiyuko.manosaba.bridge;

import net.minecraft.network.chat.Component;

/**
 * 连接中屏幕（ConnectScreen）的宿主能力接口：由 ConnectScreenMixin 实现并混入
 * 原版屏幕，供内嵌的 Compose 界面（ManosabaConnectUi）读取连接状态并执行取消。
 *
 * <p>状态文本由连接线程经原版 updateStatus 动态更新（正在连接 / 登录中……），
 * 界面每次重组时读取；「取消」复刻原版取消按钮（aborted 置位、断开连接、
 * 返回上一屏），并顺带释放界面资源（该屏无 removed 可注入）。
 *
 * <p>位于 bridge 包而非 mixin 包：mixin 包（me.shiiyuko.manosaba.mixin）归
 * manosaba.mixins.json 所有，包内非 @Mixin 类被外部类直接引用/加载会触发
 * IllegalClassLoadError（cannot be referenced directly）。
 */
public interface ConnectHost {

    /** 当前连接状态文本（由连接线程动态更新）。 */
    Component manosaba$status();

    /** 「取消」：复刻原版取消按钮（aborted 置位、断开连接、返回上一屏）。 */
    void manosaba$cancel();
}

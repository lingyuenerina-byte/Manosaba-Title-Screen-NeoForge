package me.shiiyuko.manosaba.bridge;

import javax.annotation.Nullable;

/**
 * 内嵌 Compose 界面资源的「登记 - 释放」工具：适用于目标屏幕没有 removed() 可注入、
 * 或被替换路径不经过任何可注入方法的场景（连接中屏 / 编辑世界屏），由屏幕在 init 时
 * 登记自身界面的释放器（Runnable），在以下时机统一释放：
 * <ul>
 *   <li>界面自身可控的退出路径（取消 / 保存等）显式调用 {@link #dispose()}；</li>
 *   <li>无法注入的替换路径出现时，由其它 manosaba 屏幕的初始化兜底调用 {@link #dispose()}
 *       （连接失败屏 / 选择世界屏 / 标题屏均为汇聚点）；</li>
 *   <li>新一任登记（{@link #arm} 收到不同 owner）出现时自动释放上一任，
 *       覆盖「再次进入同类屏幕」前的历史遗留。</li>
 * </ul>
 *
 * <p>同一时刻最多有一个待释放界面（屏幕互斥），故使用单槽位。
 * 全部调用都发生在客户端渲染主线程，无需加锁。
 *
 * <p>位于 bridge 包而非 mixin 包：mixin 包（me.shiiyuko.manosaba.mixin）归
 * manosaba.mixins.json 所有，包内非 @Mixin 类被外部类直接引用/加载会触发
 * IllegalClassLoadError（cannot be referenced directly）。
 */
public final class ActiveUiReclaimer {

    @Nullable
    private static Object owner;

    @Nullable
    private static Runnable disposer;

    private ActiveUiReclaimer() {
    }

    /**
     * 登记当前活动界面的释放器；owner 变化（屏幕换代）时先释放上一任。
     * 同一 owner 重复登记（resize / 子屏往返后的 init）只更新释放器，不释放。
     */
    public static void arm(Object newOwner, Runnable newDisposer) {
        if (owner == newOwner) {
            disposer = newDisposer;
            return;
        }
        dispose();
        owner = newOwner;
        disposer = newDisposer;
    }

    /** 释放当前登记的界面资源并清空登记（幂等；未登记时为空操作）。 */
    public static void dispose() {
        Runnable d = disposer;
        owner = null;
        disposer = null;
        if (d != null) {
            d.run();
        }
    }
}

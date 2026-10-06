package me.shiiyuko.manosaba.bridge;

import javax.annotation.Nullable;
import me.shiiyuko.manosaba.ui.ManosabaTitleScreen;

/**
 * 「倒映」幽灵层来源的运行时传递：创建世界界面打开子屏（实验性内容 / 数据包）时写入，
 * 子屏 mixin 初始化时读取。
 *
 * <p>实验性内容屏可经 parent 字段回查，而数据包屏幕（PackSelectionScreen）
 * 不持有 parent 引用，故统一由此静态传递（均在客户端渲染线程访问）。
 */
public final class GhostSourceHolder {

    @Nullable
    private static ManosabaTitleScreen source;

    private GhostSourceHolder() {
    }

    public static void set(@Nullable ManosabaTitleScreen titleScreen) {
        source = titleScreen;
    }

    @Nullable
    public static ManosabaTitleScreen get() {
        return source;
    }
}

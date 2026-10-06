package me.shiiyuko.manosaba.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.mojang.blaze3d.systems.RenderSystem
import me.shiiyuko.manosaba.ManosabaMod
import me.shiiyuko.manosaba.utils.AWTUtils
import me.shiiyuko.manosaba.utils.GlStateUtils
import me.shiiyuko.manosaba.utils.glfwToAwtKeyCode
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.FramebufferFormat
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import org.jetbrains.skiko.Library
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL33C
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import kotlin.math.min

@OptIn(InternalComposeUiApi::class)
abstract class ComposeScreen(title: Component) : Screen(title) {

    /**
     * 背景处理模式：
     * - [OPAQUE] 纯黑铺底（默认；设计画布之外呈现黑边，与超宽屏下原游戏行为一致）；
     * - [GAME_DIM] 保留宿主已渲染画面（如暂停中的游戏世界），在其上叠加半透明黑
     *   （暂停菜单系列；Skia drawRect 默认 SrcOver 混合，不清屏）。
     */
    enum class BackgroundMode { OPAQUE, GAME_DIM }

    companion object {
        const val DESIGN_WIDTH = 1920f
        const val DESIGN_HEIGHT = 1080f

        /**
         * 滚轮逐格滚动距离（设计 px/格）：Compose 1.7 桌面版对滚轮事件按
         * 「像素 + 惯性」模型消费——增量本身即时滚动，随后按 min(增量/100, 1dp)×1000 (px/s)
         * 触发 splineBasedDecay 惯性滑动（fling 位移约为 velocity 的 4 倍），
         * 单格总位移约为本值的 40 倍，故基数需显著缩小。
         * 实测：50f → >1800px（整页到底）；7f → ~283px；据此取 2f，单格约 80px，接近逐行手感。
         */
        const val WHEEL_SCROLL_STEP = 2f
    }

    private val mc = Minecraft.getInstance()
    private var skiaContext: DirectContext? = null
    private var surface: Surface? = null
    private var renderTarget: BackendRenderTarget? = null
    private var composeScene: ComposeScene? = null

    private val window get() = mc.window
    private val currentTime get() = System.currentTimeMillis()
    private val awtMods get() = AWTUtils.getAwtMods(window.window)

    private var renderScale = 1f
    private var renderOffsetX = 0f
    private var renderOffsetY = 0f

    private var edgeFillSpriteCache: ImageBitmap? = null
    private var edgeFillImageCache: SkiaImage? = null

    private var lastHoverX = Float.NaN
    private var lastHoverY = Float.NaN

    @Composable
    abstract fun Content()

    /** 背景模式（子类可覆盖；暂停菜单覆盖为 GAME_DIM 使游戏画面透出） */
    open val backgroundMode: BackgroundMode get() = BackgroundMode.OPAQUE

    /** GAME_DIM 模式的整屏暗化强度（0..1） */
    open val backgroundDim: Float get() = 0.55f

    /**
     * 边缘延伸素材：窗口比例宽于 / 高于 16:9 时，用素材最外侧一列 / 一行像素拉伸
     * 铺满画布外的空白区（左右条带只覆盖画布高度、上下条带覆盖全宽，互不叠加）。
     * 默认 null = 不延伸（画布外保持原有黑边 / 暗化游戏画面）。
     */
    open val edgeFillSprite: ImageBitmap? get() = null

    /** 边缘延伸的透明度（应与素材在画布内的渲染透明度一致，默认 1） */
    open val edgeFillAlpha: Float get() = 1f

    private fun closeSkiaResources() {
        listOf(surface, renderTarget, skiaContext).forEach { it?.close() }
        skiaContext = null
        renderTarget = null
        surface = null
        edgeFillImageCache?.close()
        edgeFillImageCache = null
        edgeFillSpriteCache = null
    }

    private fun initCompose() {
        // 离屏渲染路径不经过 SkiaLayer 的初始化流程，必须手动加载 skiko 本地库，
        // 否则首个 native 调用（如 Paint_nMake）会抛 UnsatisfiedLinkError。
        // 注意：skiko 的 loaded 标志位在加载前抢先置位且失败后永不复位，
        // 因此任何这里抛出的异常都不能吞掉，否则后续所有 load() 都会静默失效。
        try {
            Library.load()
        } catch (t: Throwable) {
            ManosabaMod.LOGGER.error("[Manosaba] Failed to load skiko native library", t)
            throw t
        }
        composeScene = (composeScene ?: CanvasLayersComposeScene(
            density = Density(1f),
            invalidate = {}
        ).apply { setContent { Content() } }).also {
            it.density = Density(1f)
            it.size = IntSize(DESIGN_WIDTH.toInt(), DESIGN_HEIGHT.toInt())
        }
    }

    private fun buildSkiaSurface() {
        val (frameWidth, frameHeight) = window.width to window.height

        surface?.takeIf { it.width == frameWidth && it.height == frameHeight }?.let { return }

        closeSkiaResources()

        skiaContext = DirectContext.makeGL()
        renderTarget = BackendRenderTarget.makeGL(
            frameWidth, frameHeight, 0, 8,
            mc.mainRenderTarget.frameBufferId, FramebufferFormat.GR_GL_RGBA8
        )
        surface = Surface.makeFromBackendRenderTarget(
            skiaContext!!, renderTarget!!, SurfaceOrigin.BOTTOM_LEFT,
            SurfaceColorFormat.BGRA_8888, ColorSpace.sRGB
        )
    }

    private fun calculateScaleParams() {
        val fbWidth = window.width.toFloat()
        val fbHeight = window.height.toFloat()

        val scaleX = fbWidth / DESIGN_WIDTH
        val scaleY = fbHeight / DESIGN_HEIGHT

        // 保持 16:9 设计画布完整可见（contain），窗口比例不符时由 render 填充黑边
        renderScale = min(scaleX, scaleY)
        renderOffsetX = (fbWidth - DESIGN_WIDTH * renderScale) / 2f
        renderOffsetY = (fbHeight - DESIGN_HEIGHT * renderScale) / 2f
    }

    private fun syncHoverPointer(windowX: Double, windowY: Double) {
        val scene = composeScene ?: return
        val handle = window.window
        val inside = GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_HOVERED) == GLFW.GLFW_TRUE &&
            GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE

        val designX: Float
        val designY: Float
        if (inside) {
            val coord = toDesignCoord(windowX, windowY)
            designX = coord.x
            designY = coord.y
        } else {
            // 指针不在窗口内或窗口未聚焦：移到场景外，使所有悬停状态退出
            designX = -10000f
            designY = -10000f
        }

        if (designX == lastHoverX && designY == lastHoverY) return
        lastHoverX = designX
        lastHoverY = designY

        val event = AWTUtils.createMouseEvent(
            designX.toInt(), designY.toInt(), awtMods, MouseEvent.NOBUTTON, MouseEvent.MOUSE_MOVED
        )
        scene.sendPointerEvent(
            eventType = PointerEventType.Move,
            position = Offset(designX, designY),
            type = PointerType.Mouse,
            nativeEvent = event
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (composeScene == null) {
            initCompose()
        }

        buildSkiaSurface()
        calculateScaleParams()
        // 每帧兜底同步指针位置：快速甩动丢失事件、指针移出窗口、
        // 切换窗口等场景下，悬停状态也能在下一帧内自愈，而不是失效或卡住
        syncHoverPointer(mouseX.toDouble(), mouseY.toDouble())

        GlStateUtils.save()
        resetPixelStore()

        // 每帧释放 Skia GPU 资源缓存：在本项目「Skia Surface 包裹 MC 主帧缓冲」的结构下，
        // 这是稳定性的必要条件——若只在初始化时调用或完全不调用，长期运行后会出现
        // GL_INVALID_VALUE 异常，最终引发 NVIDIA 驱动崩溃（nvoglv64.dll，0xC0000005）。
        // 实测每帧调用的性能开销可忽略（满帧 60fps），请勿移除或改为仅初始化时调用一次。
        skiaContext?.resetAll()

        RenderSystem.enableBlend()
        surface?.let { s ->
            val canvas = s.canvas
            when (backgroundMode) {
                BackgroundMode.OPAQUE ->
                    // 纯黑铺底，使设计画布之外的区域呈现黑边（与超宽屏下的原游戏行为一致）
                    canvas.clear(0xFF000000.toInt())
                BackgroundMode.GAME_DIM -> {
                    // 保留宿主已渲染画面（暂停中的游戏世界）并整屏暗化：
                    // Skia drawRect 默认 SrcOver 混合，与帧缓冲现有像素混合实现变暗
                    val alpha = (backgroundDim.coerceIn(0f, 1f) * 255f).toInt()
                    val paint = Paint().apply { color = alpha shl 24 }
                    try {
                        canvas.drawRect(Rect.makeWH(s.width.toFloat(), s.height.toFloat()), paint)
                    } finally {
                        paint.close()
                    }
                }
            }
            canvas.save()
            canvas.translate(renderOffsetX, renderOffsetY)
            canvas.scale(renderScale, renderScale)
            composeScene?.render(canvas.asComposeCanvas(), System.nanoTime())
            canvas.restore()
            drawEdgeExtension(canvas, s.width.toFloat(), s.height.toFloat())
            s.flush()
        }
        GlStateUtils.restore()
        RenderSystem.disableBlend()
    }

    /**
     * 画布外区域的边缘延伸：把 [edgeFillSprite] 最外侧一列 / 一行像素分别拉伸到
     * 画布左右 / 上下的空白区，使超宽（>16:9）窗口下背景图向屏幕边缘自然延展，
     * 与画布内边缘像素连续（TEST51 报修：两侧露出亮于界面背景的暗化游戏画面）。
     */
    private fun drawEdgeExtension(canvas: Canvas, screenW: Float, screenH: Float) {
        if (renderOffsetX <= 0.5f && renderOffsetY <= 0.5f) return
        val sprite = edgeFillSprite ?: return
        if (edgeFillSpriteCache !== sprite) {
            edgeFillImageCache?.close()
            edgeFillImageCache = SkiaImage.makeFromBitmap(sprite.asSkiaBitmap())
            edgeFillSpriteCache = sprite
        }
        val image = edgeFillImageCache ?: return
        val paint = Paint().apply {
            color = (edgeFillAlpha.coerceIn(0f, 1f) * 255f).toInt() shl 24
        }
        try {
            val iw = image.width.toFloat()
            val ih = image.height.toFloat()
            val contentTop = renderOffsetY
            val contentBottom = renderOffsetY + DESIGN_HEIGHT * renderScale
            val contentRight = renderOffsetX + DESIGN_WIDTH * renderScale
            if (renderOffsetX > 0.5f) {
                // 左 / 右：拉伸最外侧列（只覆盖画布高度区段，四角留给上下条带）
                canvas.drawImageRect(
                    image, Rect.makeLTRB(0f, 0f, 1f, ih),
                    Rect.makeLTRB(0f, contentTop, renderOffsetX, contentBottom), paint
                )
                canvas.drawImageRect(
                    image, Rect.makeLTRB(iw - 1f, 0f, iw, ih),
                    Rect.makeLTRB(contentRight, contentTop, screenW, contentBottom), paint
                )
            }
            if (renderOffsetY > 0.5f) {
                // 上 / 下：拉伸最外侧行（覆盖全宽，含四个角）
                canvas.drawImageRect(
                    image, Rect.makeLTRB(0f, 0f, iw, 1f),
                    Rect.makeLTRB(0f, 0f, screenW, renderOffsetY), paint
                )
                canvas.drawImageRect(
                    image, Rect.makeLTRB(0f, ih - 1f, iw, ih),
                    Rect.makeLTRB(0f, contentBottom, screenW, screenH), paint
                )
            }
        } finally {
            paint.close()
        }
    }

    private fun resetPixelStore() {
        GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, 0)
        GL33C.glPixelStorei(GL33C.GL_UNPACK_SWAP_BYTES, GL33C.GL_FALSE)
        GL33C.glPixelStorei(GL33C.GL_UNPACK_LSB_FIRST, GL33C.GL_FALSE)
        GL33C.glPixelStorei(GL33C.GL_UNPACK_ROW_LENGTH, 0)
        GL33C.glPixelStorei(GL33C.GL_UNPACK_SKIP_ROWS, 0)
        GL33C.glPixelStorei(GL33C.GL_UNPACK_SKIP_PIXELS, 0)
        GL33C.glPixelStorei(GL33C.GL_UNPACK_ALIGNMENT, 4)
    }

    private fun toDesignCoord(windowX: Double, windowY: Double): Offset {
        val scaleFactor = window.guiScale
        val fbX = windowX * scaleFactor
        val fbY = windowY * scaleFactor

        val designX = ((fbX - renderOffsetX) / renderScale).toFloat()
        val designY = ((fbY - renderOffsetY) / renderScale).toFloat()

        return Offset(designX, designY)
    }

    private fun sendMouseEvent(
        mouseX: Double,
        mouseY: Double,
        button: Int = 0,
        eventType: Int,
        pointerEventType: PointerEventType,
        scrollDelta: Offset? = null
    ) {
        val designCoord = toDesignCoord(mouseX, mouseY)
        val event = AWTUtils.createMouseEvent(
            designCoord.x.toInt(), designCoord.y.toInt(), awtMods, button, eventType
        )
        composeScene?.sendPointerEvent(
            eventType = pointerEventType,
            position = designCoord,
            type = PointerType.Mouse,
            scrollDelta = scrollDelta ?: Offset.Zero,
            nativeEvent = event
        )
    }

    private fun sendKeyEvent(eventId: Int, keyCode: Int, char: Char, location: Int) {
        composeScene?.sendKeyEvent(
            AWTUtils.createKeyEvent(eventId, currentTime, awtMods, keyCode, char, location)
        )
    }

    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        surface?.close()
        renderTarget?.close()
        surface = null
        renderTarget = null
        super.resize(minecraft, width, height)
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        syncHoverPointer(mouseX, mouseY)
        super.mouseMoved(mouseX, mouseY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        sendMouseEvent(mouseX, mouseY, button, MouseEvent.MOUSE_PRESSED, PointerEventType.Press)
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        syncHoverPointer(mouseX, mouseY)
        return true
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        sendMouseEvent(mouseX, mouseY, button, MouseEvent.MOUSE_RELEASED, PointerEventType.Release)
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double
    ): Boolean {
        val designCoord = toDesignCoord(mouseX, mouseY)
        val event = AWTUtils.createMouseWheelEvent(
            designCoord.x.toInt(), designCoord.y.toInt(), mouseY, awtMods, MouseEvent.MOUSE_WHEEL
        )
        composeScene?.sendPointerEvent(
            position = designCoord,
            eventType = PointerEventType.Scroll,
            scrollDelta = Offset(
                horizontalAmount.toFloat() * WHEEL_SCROLL_STEP,
                (-verticalAmount * WHEEL_SCROLL_STEP).toFloat()
            ),
            nativeEvent = event
        )
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        sendKeyEvent(
            KeyEvent.KEY_TYPED,
            Key.Unknown.keyCode.toInt(),
            chr,
            KeyEvent.KEY_LOCATION_UNKNOWN
        )
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // 嵌入模式（本实例不是当前屏幕，由宿主屏幕持有并渲染）：ESC 交还宿主处理，
            // 不能在这里走 onClose——否则会误触发 setScreen(null) 并销毁宿主界面
            if (mc.screen !== this) return false
            return onClose().let { true }
        }
        // F3 不拦截，保留原版调试信息（帧率等）功能
        if (keyCode == GLFW.GLFW_KEY_F3) return false
        sendKeyEvent(
            KeyEvent.KEY_PRESSED,
            glfwToAwtKeyCode(keyCode),
            KeyEvent.CHAR_UNDEFINED,
            KeyEvent.KEY_LOCATION_STANDARD
        )
        return true
    }

    override fun keyReleased(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        sendKeyEvent(
            KeyEvent.KEY_RELEASED,
            glfwToAwtKeyCode(keyCode),
            0.toChar(),
            KeyEvent.KEY_LOCATION_STANDARD
        )
        return super.keyReleased(keyCode, scanCode, modifiers)
    }

    override fun shouldCloseOnEsc() = false

    override fun onClose() {
        closeSkiaResources()
        composeScene?.close()
        super.onClose()
    }

    /**
     * 释放 Compose/Skia 资源而不触发屏幕切换：
     * 嵌入模式（宿主屏幕 removed() 时）由宿主调用，避免资源随屏幕切换泄漏。
     * 释放后再次 render() 会自动重建 ComposeScene。
     */
    fun disposeCompose() {
        closeSkiaResources()
        composeScene?.close()
        composeScene = null
        lastHoverX = Float.NaN
        lastHoverY = Float.NaN
    }

    /**
     * 打开第三方（其他模组）界面：先释放 Compose/Skia 资源再切换屏幕（同屏 setScreen
     * 不触发 removed，若不释放会残留场景）；本实例保留为返回目标，返回后 render()
     * 自动重建 ComposeScene。
     */
    fun openExternalScreen(target: Screen) {
        disposeCompose()
        mc.setScreen(target)
    }
}

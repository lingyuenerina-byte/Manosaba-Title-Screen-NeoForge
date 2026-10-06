package me.shiiyuko.manosaba.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import me.shiiyuko.manosaba.ManosabaMod
import me.shiiyuko.manosaba.bridge.ComposeInputBridge
import me.shiiyuko.manosaba.bridge.PauseHost
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component

// —— 布局常量：由 TEST48（2560x1080 截图；设计画布 1920x1080 按 1:1 居中渲染、
//    两侧黑边各 320px，故设计坐标 = 截图坐标 − 320）逐像素测量，并以素材内部
//    结构（MenuFrame 屏幕区 / 灰区）互校 ——

/** MenuFrame 素材（882x1398，手机框整体：外壳 + 米色屏幕 + 底部灰色区域） */
private const val FRAME_SCALE = 0.72f
private const val FRAME_X = 1208f
private const val FRAME_Y = 43f
private const val FRAME_W = 882f * FRAME_SCALE
private const val FRAME_H = 1398f * FRAME_SCALE

/** MenuButton 素材（293x276，五种主按钮的 Normal / Highlighted 底板） */
private const val BUTTON_W = 293f * FRAME_SCALE
private const val BUTTON_H = 276f * FRAME_SCALE

// 主按钮中心（左列 1445 / 右列 1660；首行 289 / 次行 473 / 底部行 650）
private const val BTN_COL_L = 1445f
private const val BTN_COL_R = 1660f
private const val BTN_ROW_1 = 289f
private const val BTN_ROW_2 = 473f
private const val BTN_ROW_3 = 650f
// 第五枚（返回标题画面）与左列对齐（TEST50 报修：原 1480 偏右 35px，视觉错位）
private const val BTN_TITLE_CX = BTN_COL_L

// 右上角 ✕ 关闭（素材 67x65）
private const val CLOSE_CX = 1723f
private const val CLOSE_CY = 120f
private const val CLOSE_W = 64f
private const val CLOSE_H = 62f

// 底部灰区圆角板（MenuFrame 内浅色圆角板：素材 x123..770 / y990..1208 →
// 设计 1296.6..1762.4 / 755.8..912.8）内的 2x2 按钮（统计信息 / 提供反馈 /
// 报告漏洞 / 模组）；按钮块在板内水平居中（块宽 394 = 2×185 + 24 列间距）
private const val AREA_W = 185f
private const val AREA_H = 70f
private const val AREA_COL_L = 1425f
private const val AREA_COL_R = 1634f
private const val AREA_ROW_1 = 787f
private const val AREA_ROW_2 = 876f

/**
 * 游戏暂停菜单（TEST48 魔女审判「手机菜单」）：原版 PauseScreen 作为逻辑壳
 * （暂停语义 / F3+ESC 的 showPauseMenu 状态 / 断线流程），本类为内嵌 Compose 界面
 * （不是当前屏幕），由 PauseScreenMixin 创建并转发渲染与输入。
 *
 * <p>背景为 GAME_DIM（暂停中的游戏画面整屏暗化），手机框居中偏右悬浮其上：
 * 五枚主按钮（保存 / 读取 / 历史记录 / 选项设置 / 返回标题画面）、右上角 ✕、
 * 底部灰色区域的其余原版按钮（统计信息 / 提供反馈 / 报告漏洞 / 模组）。
 */
class ManosabaPauseUi(
    private val screen: PauseScreen
) : ComposeScreen(Component.literal("Manosaba Pause")), ComposeInputBridge.Target {

    private val mc = Minecraft.getInstance()
    private val host: PauseHost get() = screen as PauseHost

    /** 暂停菜单需要游戏画面透出：保留宿主已渲染的世界画面并整屏暗化 */
    override val backgroundMode: BackgroundMode get() = BackgroundMode.GAME_DIM

    init {
        PauseSprites.preload()
    }

    /** 确认类操作音效（进入子界面 / 打开链接）：Sfx_System_Submit_001 */
    private fun playClick() {
        mc.soundManager.play(SimpleSoundInstance.forUI(ManosabaMod.UI_SUBMIT.get(), 1.0f))
    }

    /** 取消类操作音效（回到游戏 / 保存退出 / 关闭）：Sfx_System_Cancel_001 */
    private fun playCancel() {
        mc.soundManager.play(SimpleSoundInstance.forUI(ManosabaMod.UI_CANCEL.get(), 1.0f))
    }

    /** 释放 Compose/Skia 资源（resume / onClose / 兜底释放路径调用） */
    fun disposeUi() {
        disposeCompose()
    }

    // ---------------- 页面 ----------------

    @Composable
    override fun Content() {
        if (host.`manosaba$showPauseMenu`()) {
            Box(modifier = Modifier.fillMaxSize()) {
                PhoneMenu()
            }
        } else {
            // F3+ESC 等场景：仅显示「游戏已暂停」（原版 PauseScreen(false) 语义）
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BasicText(Component.translatable("menu.paused").string, style = rowTextStyle(40f))
            }
        }
    }

    @Composable
    private fun PhoneMenu() {
        // 手机框底板
        PauseSprites["MenuFrame"]?.let { frame ->
            Image(
                frame, null,
                modifier = Modifier
                    .offset(x = FRAME_X.dp, y = FRAME_Y.dp)
                    .size(FRAME_W.dp, FRAME_H.dp),
                contentScale = ContentScale.FillBounds
            )
        }

        // 五枚主按钮：保存→进度；读取→回到游戏；历史记录→对局域网开放；
        // 选项设置→本模组设置界面；返回标题画面→保存并退回到标题屏幕
        PauseMenuButton("Save", BTN_COL_L, BTN_ROW_1) {
            playClick()
            host.`manosaba$openAdvancements`()
        }
        PauseMenuButton("Load", BTN_COL_R, BTN_ROW_1) {
            playCancel()
            host.`manosaba$resume`()
        }
        PauseMenuButton("Log", BTN_COL_L, BTN_ROW_2) {
            playClick()
            host.`manosaba$openLanOrSocial`()
        }
        PauseMenuButton("Options", BTN_COL_R, BTN_ROW_2) {
            playClick()
            host.`manosaba$openOptions`()
        }
        PauseMenuButton("Title", BTN_TITLE_CX, BTN_ROW_3) {
            playCancel()
            host.`manosaba$saveAndQuit`()
        }

        // 右上角 ✕：等同「回到游戏」
        CloseButton {
            playCancel()
            host.`manosaba$resume`()
        }

        // 底部灰色区域：其余原版按钮（统计信息 / 提供反馈 / 报告漏洞 / 模组）
        AreaButton(Component.translatable("gui.stats").string, AREA_COL_L, AREA_ROW_1) {
            playClick()
            host.`manosaba$openStats`()
        }
        AreaButton(Component.translatable("menu.sendFeedback").string, AREA_COL_R, AREA_ROW_1) {
            playClick()
            host.`manosaba$openFeedback`()
        }
        AreaButton(
            Component.translatable("menu.reportBugs").string, AREA_COL_L, AREA_ROW_2,
            enabled = host.`manosaba$reportBugsActive`()
        ) {
            playClick()
            host.`manosaba$reportBugs`()
        }
        AreaButton(Component.translatable("fml.menu.mods").string, AREA_COL_R, AREA_ROW_2) {
            playClick()
            host.`manosaba$openMods`()
        }
    }

    // ---------------- 组件 ----------------

    /** 主按钮：三态底板素材（Normal / Highlighted），中心定位 */
    @Composable
    private fun PauseMenuButton(iconKey: String, cx: Float, cy: Float, onClick: () -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val sprite = PauseSprites[
            if (hovered) "MenuButton_${iconKey}_Highlighted@ZhHans"
            else "MenuButton_${iconKey}_Normal@ZhHans"
        ]

        Box(
            modifier = Modifier
                .offset(x = (cx - BUTTON_W / 2f).dp, y = (cy - BUTTON_H / 2f).dp)
                .size(BUTTON_W.dp, BUTTON_H.dp)
                .clickable(interactionSource = interaction, indication = null) { onClick() }
        ) {
            sprite?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
        }
    }

    /** 右上角 ✕ 关闭：静置略透明，悬停全亮 */
    @Composable
    private fun CloseButton(onClick: () -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        PauseSprites["MenuCloseIcon"]?.let { icon ->
            Image(
                icon, null,
                modifier = Modifier
                    .offset(x = (CLOSE_CX - CLOSE_W / 2f).dp, y = (CLOSE_CY - CLOSE_H / 2f).dp)
                    .size(CLOSE_W.dp, CLOSE_H.dp)
                    .graphicsLayer { alpha = if (hovered) 1f else 0.72f }
                    .clickable(interactionSource = interaction, indication = null) { onClick() }
            )
        }
    }

    /**
     * 灰区小按钮：魔女审判对话框同款撕裂纸素材（ButtonBase_Default / _Highlighted，
     * 与「恢复初始设置」、退出对话框按钮同源），悬停换高亮红版；不可用时整体压暗置灰
     */
    @Composable
    private fun AreaButton(
        text: String,
        cx: Float,
        cy: Float,
        enabled: Boolean = true,
        onClick: () -> Unit
    ) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val sprite = PauseSprites[
            if (hovered && enabled) "AreaButton_Highlighted" else "AreaButton_Normal"
        ]
        Box(
            modifier = Modifier
                .offset(x = (cx - AREA_W / 2f).dp, y = (cy - AREA_H / 2f).dp)
                .size(AREA_W.dp, AREA_H.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.55f }
                .clickable(interactionSource = interaction, indication = null, enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            sprite?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            BasicText(text, style = rowTextStyle(26f, if (enabled) COLOR_TEXT else COLOR_DISABLED))
        }
    }
}

/** 暂停菜单全部素材（全局单例，首次访问时从 jar 加载） */
internal object PauseSprites {
    private val names = listOf(
        "MenuFrame",
        "MenuButton_Save_Normal@ZhHans", "MenuButton_Save_Highlighted@ZhHans",
        "MenuButton_Load_Normal@ZhHans", "MenuButton_Load_Highlighted@ZhHans",
        "MenuButton_Log_Normal@ZhHans", "MenuButton_Log_Highlighted@ZhHans",
        "MenuButton_Options_Normal@ZhHans", "MenuButton_Options_Highlighted@ZhHans",
        "MenuButton_Title_Normal@ZhHans", "MenuButton_Title_Highlighted@ZhHans",
        "MenuCloseIcon",
        "AreaButton_Normal", "AreaButton_Highlighted"
    )

    private val sprites: Map<String, ImageBitmap> by lazy {
        buildMap {
            names.forEach { name ->
                val path = "/assets/pause/$name.png"
                runCatching {
                    // 注意：本 lambda 的隐含 receiver 是 buildMap 的 MutableMap，裸写类引用
                    // 会被解析为 LinkedHashMap 的类（bootstrap 类加载器找不到 mod 资源，静默返回 null）；
                    // 必须显式使用 PauseSprites 类字面量，从 mod 类加载器读取
                    val stream = PauseSprites::class.java.getResourceAsStream(path)
                        ?: error("pause sprite not found on classpath: $path")
                    val bytes = stream.use { it.readBytes() }
                    put(name, org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap())
                }.onFailure {
                    ManosabaMod.LOGGER.error("[Manosaba] Failed to load pause sprite: $path", it)
                }
            }
        }.also {
            ManosabaMod.LOGGER.info("[Manosaba] Pause sprites loaded: ${it.size}/${names.size}")
        }
    }

    operator fun get(name: String): ImageBitmap? = sprites[name]

    /** 在界面初始化时调用，提前完成解码 */
    fun preload() {
        sprites.size
    }
}

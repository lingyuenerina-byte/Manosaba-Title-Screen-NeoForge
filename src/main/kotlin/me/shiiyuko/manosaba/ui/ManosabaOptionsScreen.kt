package me.shiiyuko.manosaba.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mojang.blaze3d.platform.InputConstants
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import me.shiiyuko.manosaba.ManosabaClientConfig
import me.shiiyuko.manosaba.ManosabaMod
import net.minecraft.client.AttackIndicatorStatus
import net.minecraft.client.CloudStatus
import net.minecraft.client.GraphicsStatus
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.NarratorStatus
import net.minecraft.client.Options
import net.minecraft.client.ParticleStatus
import net.minecraft.client.PrioritizeChunkUpdates
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.packs.PackSelectionModel
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.player.ChatVisiblity
import net.minecraft.world.entity.player.PlayerModelPart
import net.neoforged.neoforge.client.settings.KeyModifier
import org.jetbrains.skia.Image
import org.lwjgl.glfw.GLFW

/** 设置界面页签：前三个沿用原游戏素材标签，其后为原版选项重新规划路径后的新页面 */
internal enum class OptionsTab(val label: String) {
    TEXT("文字"),
    GRAPHICS("图形"),
    SOUND("声音"),
    CONTROLS("按键控制"),
    PACKS("资源包"),
    ACCESSIBILITY("辅助功能"),
    SKIN("皮肤"),
    ONLINE("联机"),
    TELEMETRY("遥测"),
    CREDITS("鸣谢")
}

/**
 * 魔女审判风格设置界面：主界面「倒映」为背景，
 * 右侧竖排页签（文字 / 图形 / 声音 + 全部原版选项重新归类的 7 个页面）。
 *
 * 父屏泛化为 [Screen]：从标题屏打开时 titleParent 非空（保持「倒映」背景与
 * 周目联动）；从暂停菜单打开时 titleParent 为 null（背景改为暗化游戏画面）。
 */
class ManosabaOptionsScreen @JvmOverloads constructor(
    internal val parent: Screen,
    internal val titleParent: ManosabaTitleScreen? = parent as? ManosabaTitleScreen
) : ComposeScreen(Component.literal("Manosaba Options")) {

    internal val minecraft: Minecraft get() = Minecraft.getInstance()
    internal val options: Options get() = minecraft.options

    /** 从标题屏打开时保持「倒映」背景；从暂停菜单打开时改为暗化游戏画面 */
    override val backgroundMode: BackgroundMode get() =
        if (titleParent != null) BackgroundMode.OPAQUE else BackgroundMode.GAME_DIM

    /**
     * 超宽 / 超高窗口的边缘延伸：用磨砂底板（OptionsUnderlay）最外侧列 / 行向外拉伸，
     * 使背景铺满画布外区域、与画布内边缘连续（TEST51 报修：两侧露出偏亮的暗化游戏画面）；
     * 透明度与画布内一致（UNDERLAY_ALPHA）。
     */
    override val edgeFillSprite: ImageBitmap? get() = OptionsSprites["OptionsUnderlay"]
    override val edgeFillAlpha: Float get() = UNDERLAY_ALPHA

    // —— 界面状态 ——
    internal var tick by mutableStateOf(0)
        private set
    internal var currentTab by mutableStateOf(OptionsTab.TEXT)
        private set
    internal var popup by mutableStateOf<PopupSpec?>(null)
        private set
    /** 按键控制页的子视图：按键绑定编辑 */
    internal var showKeyBinds by mutableStateOf(false)
        private set
    /** 正在等待按键输入的按键映射（null = 未在捕获） */
    internal var capturing by mutableStateOf<KeyMapping?>(null)
        private set
    internal var packModel by mutableStateOf<PackSelectionModel?>(null)
        private set
    private var packsDirty = false

    private val scrollStates: Map<OptionsTab, ScrollState> = OptionsTab.values().associateWith { ScrollState(0) }
    private val keyBindsScroll = ScrollState(0)

    // 按键捕获（对齐原版 KeyBindsScreen 的修饰键处理）
    private var lastPressedKey: InputConstants.Key = InputConstants.UNKNOWN
    private var lastPressedModifier: InputConstants.Key = InputConstants.UNKNOWN
    private var isLastKeyHeldDown = false
    private var isLastModifierHeldDown = false

    init {
        OptionsSprites.preload()
        OptionsUi.playClick = { playClick() }
    }

    internal fun tabScroll(tab: OptionsTab): ScrollState =
        if (tab == OptionsTab.CONTROLS && showKeyBinds) keyBindsScroll else scrollStates.getValue(tab)

    internal fun bump() {
        tick += 1
    }

    /** 确认类操作音效（按钮点击、按键绑定完成等）：Sfx_System_Submit_001 */
    internal fun playClick() {
        minecraft.soundManager.play(SimpleSoundInstance.forUI(ManosabaMod.UI_SUBMIT.get(), 1.0f))
    }

    /** 取消类操作音效（关闭、中断、返回等）：Sfx_System_Cancel_001 */
    internal fun playCancel() {
        minecraft.soundManager.play(SimpleSoundInstance.forUI(ManosabaMod.UI_CANCEL.get(), 1.0f))
    }

    internal fun showPopup(spec: PopupSpec) {
        spec.anchorY = OptionsUi.anchorY
        OptionsUi.popupOpen = true
        popup = spec
    }

    internal fun closePopup() {
        OptionsUi.popupOpen = false
        popup = null
    }

    internal fun selectTab(tab: OptionsTab) {
        if (tab == currentTab) return
        if (currentTab == OptionsTab.PACKS) commitPacksIfDirty()
        if (tab == OptionsTab.PACKS) ensurePacks()
        currentTab = tab
        showKeyBinds = false
        playClick()
    }

    internal fun openKeyBinds() {
        showKeyBinds = true
        playClick()
    }

    internal fun closeKeyBinds() {
        showKeyBinds = false
        playCancel()
    }

    // ---------------- 资源包 ----------------

    internal fun ensurePacks() {
        if (packModel == null) {
            val repo = minecraft.resourcePackRepository
            packModel = PackSelectionModel(
                { packsDirty = true; bump() },
                { ResourceLocation.withDefaultNamespace("textures/misc/unknown_pack.png") },
                repo,
                { r -> options.updateResourcePacks(r) }
            )
        }
    }

    internal fun commitPacksIfDirty() {
        if (packsDirty) {
            packModel?.commit()
            packsDirty = false
        }
    }

    internal fun rescanPacks() {
        packModel?.findNewPacks()
        packModel?.let { packsDirty = true }
        bump()
    }

    /** 资源包缩略图缓存：id → 图标（null = 已尝试且不可用，避免重复 IO）（TEST21） */
    private val packIcons = mutableMapOf<String, ImageBitmap?>()
    private var unknownPackIcon: ImageBitmap? = null
    private var unknownPackIconResolved = false

    /** 读取资源包 pack.png 作为列表缩略图；缺失/损坏时回退原版 unknown_pack 图标 */
    internal fun packIcon(entry: PackSelectionModel.Entry): ImageBitmap? {
        val id = entry.id
        if (packIcons.containsKey(id)) return packIcons[id]
        val icon: ImageBitmap? = runCatching {
            val repo = minecraft.resourcePackRepository
            val pack = repo.availablePacks.firstOrNull { it.id == id }
                ?: repo.selectedPacks.firstOrNull { it.id == id }
            pack?.open().use { resources ->
                resources?.getRootResource("pack.png")?.get()?.use { stream ->
                    Image.makeFromEncoded(stream.readBytes()).toComposeImageBitmap()
                }
            }
        }.getOrNull() ?: fallbackPackIcon()
        packIcons[id] = icon
        return icon
    }

    private fun fallbackPackIcon(): ImageBitmap? {
        if (!unknownPackIconResolved) {
            unknownPackIconResolved = true
            unknownPackIcon = runCatching {
                minecraft.resourceManager
                    .getResource(ResourceLocation.withDefaultNamespace("textures/misc/unknown_pack.png"))
                    .orElse(null)
                    ?.open()?.use { stream: java.io.InputStream ->
                        Image.makeFromEncoded(stream.readBytes()).toComposeImageBitmap()
                    }
            }.getOrNull()
        }
        return unknownPackIcon
    }

    // ---------------- 语言 / 分辨率 ----------------

    internal fun applyLanguage(code: String) {
        if (code != options.languageCode) {
            minecraft.languageManager.setSelected(code)
            options.languageCode = code
            options.save()
            minecraft.reloadResourcePacks()
        }
        bump()
    }

    internal fun currentLanguageName(): String =
        minecraft.languageManager.getLanguage(options.languageCode)?.let { info ->
            if (info.region.isEmpty()) info.name else "${info.name} (${info.region})"
        } ?: options.languageCode

    internal fun applyResolution(width: Int, height: Int) {
        // Window.setWindowed 在窗口模式下会套用「进入全屏时保存」的陈旧位置导致窗口跳动，
        // 这里先记录当前位置，改完尺寸后恢复，让窗口原地调整大小
        val handle = minecraft.window.window
        val posX = IntArray(1)
        val posY = IntArray(1)
        GLFW.glfwGetWindowPos(handle, posX, posY)
        minecraft.window.setWindowed(width, height)
        GLFW.glfwSetWindowPos(handle, posX[0], posY[0])
        bump()
    }

    internal fun resolutionOptions(): List<Pair<String, Pair<Int, Int>>> {
        val standard = listOf(
            1280 to 720, 1366 to 768, 1600 to 900, 1920 to 1080,
            2560 to 1440, 3200 to 1800, 3840 to 2160
        )
        val current = minecraft.window.width to minecraft.window.height
        return (standard + current)
            .distinct()
            .sortedBy { it.first.toLong() * it.second }
            .map { (w, h) -> "${w} x ${h}" to (w to h) }
    }

    internal fun frameRateLabel(value: Int) = if (value >= 260) "无限制" else "$value fps"

    // ---------------- 按键捕获 ----------------

    internal fun startCapture(mapping: KeyMapping) {
        capturing = mapping
        lastPressedKey = InputConstants.UNKNOWN
        lastPressedModifier = InputConstants.UNKNOWN
        isLastKeyHeldDown = false
        isLastModifierHeldDown = false
        bump()
    }

    private fun finishCapture(confirmed: Boolean = true) {
        capturing = null
        lastPressedKey = InputConstants.UNKNOWN
        lastPressedModifier = InputConstants.UNKNOWN
        isLastKeyHeldDown = false
        isLastModifierHeldDown = false
        options.save()
        KeyMapping.resetMapping()
        if (confirmed) playClick() else playCancel()
        bump()
    }

    internal fun resetAllKeyBinds() {
        options.keyMappings.forEach { it.setToDefault() }
        KeyMapping.resetMapping()
        options.save()
        bump()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        // 弹层打开时 Esc 只关闭弹层，不退出整个界面
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && popup != null) {
            playCancel()
            closePopup()
            return true
        }
        if (capturing != null) {
            val key = InputConstants.getKey(keyCode, scanCode)
            if (lastPressedModifier == InputConstants.UNKNOWN && KeyModifier.isKeyCodeModifier(key)) {
                lastPressedModifier = key
                isLastModifierHeldDown = true
            } else {
                lastPressedKey = key
                isLastKeyHeldDown = true
            }
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun keyReleased(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val mapping = capturing
        // 忽略 Mac 上 Fn 键的伪释放事件（scan code 63）
        if (mapping != null && (!Minecraft.ON_OSX || scanCode != 63)) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                mapping.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN)
                options.setKey(mapping, InputConstants.UNKNOWN)
                finishCapture(confirmed = false)
            } else {
                val key = InputConstants.getKey(keyCode, scanCode)
                if (lastPressedKey == key) {
                    isLastKeyHeldDown = false
                } else if (lastPressedModifier == key) {
                    isLastModifierHeldDown = false
                }
                if (!isLastKeyHeldDown && !isLastModifierHeldDown) {
                    if (lastPressedKey != InputConstants.UNKNOWN) {
                        mapping.setKeyModifierAndCode(KeyModifier.getKeyModifier(lastPressedModifier), lastPressedKey)
                        options.setKey(mapping, lastPressedKey)
                    } else {
                        mapping.setKeyModifierAndCode(KeyModifier.NONE, lastPressedModifier)
                        options.setKey(mapping, lastPressedModifier)
                    }
                    finishCapture()
                } else {
                    return true
                }
            }
            return true
        }
        return super.keyReleased(keyCode, scanCode, modifiers)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mapping = capturing
        if (mapping != null) {
            val key = InputConstants.Type.MOUSE.getOrCreate(button)
            mapping.setKeyModifierAndCode(KeyModifier.NONE, key)
            options.setKey(mapping, key)
            finishCapture()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun onClose() {
        playCancel()
        commitPacksIfDirty()
        options.save()
        OptionsUi.playClick = null
        // 释放本屏 Compose/Skia 资源。不调用 super.onClose()：其末段为 Screen.onClose
        // = NeoForge popGuiLayer——本屏由 setScreen 打开、未入 GUI 层栈，会落到
        // setScreen(null)：主界面场景（level==null）将创建全新标题屏实例（此前音乐重播
        // 的来源，且重复解码图集、重播入场动画）；游戏内场景会先误 resume 被暂停的声音。
        disposeCompose()
        // 返回方式与「读取进度」（原版 SelectWorldScreen.onClose → setScreen(lastScreen)）
        // 对齐：直接切回父屏实例——同实例复用、Compose 场景延续，音乐与入场动画不重置
        if (minecraft.screen !== parent) {
            minecraft.setScreen(parent)
        }
    }

    // ---------------- 恢复初始设置 ----------------

    /**
     * 恢复初始设置：仅重置当前页面的选项（与官方每页独立的重置入口一致）。
     * 资源包 / 鸣谢页没有可重置的设置项，按钮在这些页面置灰。
     */
    private fun resetCurrentPage() {
        val o = options
        when (currentTab) {
            OptionsTab.TEXT -> {
                // 魔女审判自定义文本设置
                ManosabaClientConfig.TEXT_DISPLAY_SPEED.set(8)
                ManosabaClientConfig.AUTO_PLAY_INTERVAL.set(5)
                ManosabaClientConfig.SKIP_UNREAD_TEXT.set(false)
                ManosabaClientConfig.SHOW_BRANCH_HINT.set(true)
                saveManosabaConfig()
                // 聊天 / 字体（语言选择不随重置改变，与官方行为一致）
                o.chatVisibility().set(ChatVisiblity.FULL)
                o.chatColors().set(true)
                o.chatLinks().set(true)
                o.chatLinksPrompt().set(true)
                o.chatOpacity().set(1.0)
                o.textBackgroundOpacity().set(0.5)
                o.backgroundForChatOnly().set(true)
                o.chatScale().set(1.0)
                o.chatLineSpacing().set(0.0)
                o.chatDelay().set(0.0)
                o.chatWidth().set(1.0)
                o.chatHeightFocused().set(1.0)
                o.chatHeightUnfocused().set(ChatComponent.defaultUnfocusedPct())
                o.autoSuggestions().set(true)
                o.hideMatchedNames().set(true)
                o.onlyShowSecureChat().set(false)
                o.forceUnicodeFont().set(false)
                o.japaneseGlyphVariants().set(false)
            }
            OptionsTab.GRAPHICS -> {
                // 显示 / 画质 / 视角（分辨率与全屏分辨率属窗口状态，不在此重置）
                o.fullscreen().set(false)
                o.guiScale().set(0)
                o.framerateLimit().set(120)
                o.enableVsync().set(true)
                o.showAutosaveIndicator().set(true)
                o.graphicsMode().set(GraphicsStatus.FANCY)
                o.renderDistance().set(12)
                o.simulationDistance().set(12)
                o.entityDistanceScaling().set(1.0)
                o.biomeBlendRadius().set(2)
                o.ambientOcclusion().set(true)
                o.entityShadows().set(true)
                o.gamma().set(0.5)
                o.mipmapLevels().set(4)
                o.cloudStatus().set(CloudStatus.FANCY)
                o.particles().set(ParticleStatus.ALL)
                o.attackIndicator().set(AttackIndicatorStatus.CROSSHAIR)
                o.prioritizeChunkUpdates().set(PrioritizeChunkUpdates.NONE)
                o.menuBackgroundBlurriness().set(5)
                o.fov().set(70)
                o.bobView().set(true)
                o.fovEffectScale().set(1.0)
                o.screenEffectScale().set(1.0)
                o.glintSpeed().set(0.5)
                o.glintStrength().set(0.75)
            }
            OptionsTab.SOUND -> {
                SoundSource.values().forEach { o.getSoundSourceOptionInstance(it).set(1.0) }
                o.soundDevice().set("")
                o.showSubtitles().set(false)
                o.directionalAudio().set(false)
            }
            OptionsTab.CONTROLS -> {
                // 按键绑定编辑子视图中重置全部按键；鼠标 / 操作页重置操作选项
                if (showKeyBinds) {
                    resetAllKeyBinds()
                    return
                }
                o.sensitivity().set(0.5)
                o.invertYMouse().set(false)
                o.mouseWheelSensitivity().set(1.0)
                o.discreteMouseScroll().set(false)
                o.touchscreen().set(false)
                o.rawMouseInput().set(true)
                o.autoJump().set(false)
                o.operatorItemsTab().set(false)
                o.toggleCrouch().set(false)
                o.toggleSprint().set(false)
            }
            OptionsTab.ACCESSIBILITY -> {
                o.narrator().set(NarratorStatus.OFF)
                o.narratorHotkey().set(true)
                o.highContrast().set(false)
                o.notificationDisplayTime().set(1.0)
                o.hideLightningFlash().set(false)
                o.darknessEffectScale().set(1.0)
                o.damageTiltStrength().set(1.0)
                o.reducedDebugInfo().set(false)
                o.panoramaSpeed().set(1.0)
                o.hideSplashTexts().set(false)
                o.darkMojangStudiosBackground().set(false)
                // 标题背景周目（临时机制）回到一周目
                titleParent?.weekIndex = 0
            }
            OptionsTab.SKIN -> {
                o.mainHand().set(HumanoidArm.RIGHT)
                PlayerModelPart.values().forEach { part ->
                    if (!o.isModelPartEnabled(part)) o.toggleModelPart(part, true)
                }
            }
            OptionsTab.ONLINE -> {
                o.realmsNotifications().set(true)
                o.allowServerListing().set(true)
            }
            OptionsTab.TELEMETRY -> {
                o.telemetryOptInExtra().set(false)
            }
            OptionsTab.PACKS, OptionsTab.CREDITS -> return
        }
        o.save()
        bump()
    }

    // ============================== UI ==============================

    @Composable
    override fun Content() {
        LaunchedEffect(Unit) {
            while (true) {
                delay(500)
                bump()
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // 背景「倒映」：主界面同构图的幽灵层（同方向、暗化），上层为磨砂底板；
            // 从暂停菜单打开时无标题屏来源，仅保留磨砂底板（下层为暗化游戏画面）
            titleParent?.GhostLayer(GHOST_ALPHA)
            OptionsSprites["OptionsUnderlay"]?.let { bitmap ->
                Image(
                    bitmap, null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = UNDERLAY_ALPHA },
                    contentScale = ContentScale.FillBounds
                )
            }

            OptionsSprites["OptionsTitle"]?.let { bitmap ->
                Image(
                    bitmap, null,
                    modifier = Modifier
                        .offset(x = 5.dp, y = 16.dp)
                        .size((bitmap.width * TITLE_SCALE).dp, (bitmap.height * TITLE_SCALE).dp)
                )
            }

            ResetButton()
            CloseButton()
            TabColumn()
            PageHost()

            popup?.let { PopupLayer(it) }
        }
    }

    @Composable
    private fun TabColumn() {
        OptionsTab.values().forEachIndexed { index, tab ->
            TabButton(tab, TAB_FIRST_CENTER + TAB_STEP * index)
        }
    }

    @Composable
    private fun TabButton(tab: OptionsTab, centerY: Float) {
        val active = currentTab == tab
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()

        // 底板宽度：hover 或选中时向左滑出 56px（170ms ease-out，逐帧实测），离开时滑回
        val width by animateFloatAsState(
            targetValue = if (active || hovered) TAB_WIDTH_ACTIVE else TAB_WIDTH_INACTIVE,
            animationSpec = tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing)
        )
        // 选中红板交叉淡入：短促切换（实测原版 1-2 帧内完成）
        val red by animateFloatAsState(
            targetValue = if (active) 1f else 0f,
            animationSpec = tween(TAB_SWITCH_MS)
        )
        // hover 提亮强度：与滑出动画同步渐入渐出
        val lit by animateFloatAsState(
            targetValue = if (hovered && !active) 1f else 0f,
            animationSpec = tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing)
        )
        Box(
            modifier = Modifier
                .offset(x = (1920f - width).dp, y = (centerY - TAB_HEIGHT / 2f).dp)
                .size(width.dp, TAB_HEIGHT.dp)
                .clickable(interactionSource = interaction, indication = null) { selectTab(tab) }
        ) {
            // 底板交叉淡入：未选中用 Default、选中用 Highlighted，均采用「净版」素材
            // （擦除虚线灰边，静止/悬停均不再出现灰线框）；静止态乘性压暗到 0.7
            // （TEST27 报修：原版静止板「只露出一点」隐没、无淡灰判定框），
            // 悬停/选中时渐亮回素材原亮度
            (OptionsSprites["TabBase_Default_Clean"] ?: OptionsSprites["TabBase_Default"])?.let {
                val dim = 1f - TAB_DIM_MASK * (1f - maxOf(red, lit))
                Image(
                    it, null,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - red },
                    contentScale = ContentScale.FillBounds,
                    colorFilter = ColorFilter.colorMatrix(
                        ColorMatrix().apply { setToScale(dim, dim, dim, 1f) }
                    )
                )
            }
            (OptionsSprites["TabBase_Highlighted_Clean"] ?: OptionsSprites["TabBase_Highlighted"])?.let {
                Image(
                    it, null,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = red },
                    contentScale = ContentScale.FillBounds
                )
            }
            // 标签左缘锚定底板左缘 +TAB_LABEL_ANCHOR，随底板滑出同步位移；
            // 文字/图形/声音沿用官方双色素材交叉淡入，其余页签以同色系自绘两段文字
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = TAB_LABEL_ANCHOR.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                when (tab) {
                    OptionsTab.TEXT -> TabSpriteLabel("TabLabel_Message", red, lit)
                    OptionsTab.GRAPHICS -> TabSpriteLabel("TabLabel_Graphics", red, lit)
                    OptionsTab.SOUND -> TabSpriteLabel("TabLabel_Audio", red, lit)
                    else -> TabTextLabel(tab.label, red, lit)
                }
            }
        }
    }

    /**
     * 官方双色页签标签（首字粉 + 其余字）。
     * 选中：整图交叉淡入 Active；未选中悬停：仅首字区域（TAB_HEAD_WIDTHS 分界列之前）
     * 叠加 Active——首字渐显为粉、次字维持 Inactive 原样（TEST27 报修：悬停只应首字更粉）。
     */
    @Composable
    private fun TabSpriteLabel(baseName: String, red: Float, lit: Float) {
        val inactive = OptionsSprites["${baseName}_Inactive"] ?: return
        val active = OptionsSprites["${baseName}_Active"] ?: return
        val headWidth = TAB_HEAD_WIDTHS[baseName] ?: active.width
        Canvas(
            Modifier.size((active.width * TAB_LABEL_SCALE).dp, (active.height * TAB_LABEL_SCALE).dp)
        ) {
            val full = IntSize(size.width.roundToInt(), size.height.roundToInt())
            drawImage(inactive, dstSize = full, alpha = 1f - red)
            drawImage(active, dstSize = full, alpha = red)
            val head = lit * (1f - red)
            if (head > 0.001f) {
                drawImage(
                    image = active,
                    srcSize = IntSize(headWidth, active.height),
                    dstSize = IntSize((size.width * headWidth / active.width).roundToInt(), full.height),
                    alpha = head
                )
            }
        }
    }

    /** 自绘页签标签：首字大号强调色 + 其余小号基础色（对齐官方双字号标签观感，基线自动对齐） */
    @Composable
    private fun TabTextLabel(label: String, red: Float, lit: Float) {
        // 首字静止时与其余字符同为灰粉（TEST27 报修：静止不粉），仅悬停/选中渐显为全粉；
        // 其余字符仅随选中过渡到高亮文本色，悬停时保持不变
        val accent = lerp(COLOR_TAB_LABEL_INACTIVE, COLOR_TAB_LABEL_ACTIVE, maxOf(red, lit))
        val base = lerp(COLOR_TAB_LABEL_INACTIVE, COLOR_TEXT, red)
        // 官方 TabLabel_*@ZhHans 实测：首字约为其余字符的 1.5~1.6 倍（居中下沉、底部基线对齐）；
        // 在板高 98 放大的基础上整体回缩约 5%（用户反馈放大后文字略大，仍与官方标签墨高观感一致）
        val (bigSize, smallSize) = when {
            label.length <= 2 -> 54f to 35f
            label.length == 3 -> 48f to 30f
            else -> 44f to 28f
        }
        BasicText(
            text = buildAnnotatedString {
                if (label.isNotEmpty()) {
                    withStyle(SpanStyle(color = accent, fontSize = bigSize.sp)) { append(label.substring(0, 1)) }
                    withStyle(SpanStyle(color = base, fontSize = smallSize.sp)) { append(label.substring(1)) }
                }
            },
            style = rowTextStyle(smallSize),
            maxLines = 1,
            softWrap = false
        )
    }

    @Composable
    private fun PageHost() {
        when (currentTab) {
            OptionsTab.TEXT -> PageScroll(tabScroll(OptionsTab.TEXT)) { TextPage() }
            OptionsTab.GRAPHICS -> PageScroll(tabScroll(OptionsTab.GRAPHICS)) { GraphicsPage() }
            OptionsTab.SOUND -> PageScroll(tabScroll(OptionsTab.SOUND)) { SoundPage() }
            OptionsTab.CONTROLS -> PageScroll(tabScroll(OptionsTab.CONTROLS)) {
                if (showKeyBinds) KeyBindsPage() else ControlsPage()
            }
            OptionsTab.PACKS -> PageScroll(tabScroll(OptionsTab.PACKS)) { PacksPage() }
            OptionsTab.ACCESSIBILITY -> PageScroll(tabScroll(OptionsTab.ACCESSIBILITY)) { AccessibilityPage() }
            OptionsTab.SKIN -> PageScroll(tabScroll(OptionsTab.SKIN)) { SkinPage() }
            OptionsTab.ONLINE -> PageScroll(tabScroll(OptionsTab.ONLINE)) { OnlinePage() }
            OptionsTab.TELEMETRY -> PageScroll(tabScroll(OptionsTab.TELEMETRY)) { TelemetryPage() }
            OptionsTab.CREDITS -> PageScroll(tabScroll(OptionsTab.CREDITS)) { CreditsPage() }
        }
    }

    // ---------------- 关闭 / 重置 ----------------

    @Composable
    private fun CloseButton() {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        var pressed by remember { mutableStateOf(false) }
        val sprite = when {
            pressed -> OptionsSprites["CloseButton_Pressed"]
            hovered -> OptionsSprites["CloseButton_Highlighted"]
            else -> OptionsSprites["CloseButton_Default"]
        } ?: return

        Box(
            modifier = Modifier
                // x=1620：素材墨迹触自身右缘（300px 宽中至 x≈299），1644 会使内容越出
                // 设计右边界 1920（TEST22 报修），左移 24px 后右缘恰对齐 1920
                .offset(x = 1620.dp, y = (-20).dp)
                .size((300 * CLOSE_SCALE).dp, (238 * CLOSE_SCALE).dp)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            when (event.type) {
                                PointerEventType.Press -> pressed = true
                                PointerEventType.Release -> pressed = false
                                else -> {}
                            }
                        }
                    }
                }
                .clickable(interactionSource = interaction, indication = null) {
                    playClick()
                    onClose()
                }
        ) {
            Image(sprite, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
    }

    @Composable
    private fun ResetButton() {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val plate = OptionsSprites[if (hovered) "ButtonBase_Highlighted" else "ButtonBase_Default"]
        // 资源包 / 鸣谢页没有可重置的设置项，置灰禁用
        val resettable = currentTab != OptionsTab.PACKS && currentTab != OptionsTab.CREDITS

        Box(
            modifier = Modifier
                .offset(x = 50.dp, y = 945.dp)
                .size(RESET_WIDTH.dp, RESET_HEIGHT.dp)
                .clickable(interactionSource = interaction, indication = null, enabled = resettable) {
                    playClick()
                    resetCurrentPage()
                },
            contentAlignment = Alignment.Center
        ) {
            plate?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            BasicText(
                text = buildAnnotatedString {
                    val accent = when {
                        !resettable -> COLOR_DISABLED
                        hovered -> COLOR_PINK_BRIGHT
                        else -> COLOR_PINK
                    }
                    withStyle(SpanStyle(color = accent)) { append("恢复") }
                    withStyle(SpanStyle(color = if (resettable) COLOR_TEXT else COLOR_DISABLED)) { append("初始设置") }
                },
                style = rowTextStyle(38f)
            )
        }
    }

    // ---------------- 通用弹层 ----------------

    @Composable
    private fun PopupLayer(spec: PopupSpec) {
        // 紧凑选择弹层（原版逐帧实测样式）：挂在锚定行右侧（行板右缘 + POPUP_GAP），
        // 垂直方向与锚定行中心对齐；选项板宽 300、每项高 50 竖直紧贴。
        // 选项板使用 UI_Options 图集的 DropDown 系列素材（选中红板自带粉色勾选、
        // 未选中暗板、悬停红板），整块弹层在最上层覆盖九宫格白框（包边）。
        val itemH = POPUP_ITEM_HEIGHT
        val left = CTRL_LEFT + CTRL_WIDTH + POPUP_GAP
        val contentH = spec.rows.size * itemH
        val height = contentH.coerceAtMost(1040f)
        val top = (spec.anchorY - height / 2f).coerceIn(8f, 1080f - height - 8f)

        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val appear by animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = tween(90)
        )

        // 全屏点击层：点击弹层以外任意处关闭（取消类操作）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    playCancel()
                    closePopup()
                }
        ) {
            Box(
                modifier = Modifier
                    .offset(x = left.dp, y = top.dp)
                    .size(POPUP_ITEM_WIDTH.dp, height.dp)
                    .graphicsLayer { alpha = appear }
                    .drawWithContent {
                        drawContent()
                        OptionsSprites["DropDownOutline"]?.let { outline ->
                            drawNinePatch(outline, srcBorder = 3f, dstBorder = 3f)
                        }
                    }
            ) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(spec.rows.size) { index ->
                        PopupRow(spec.rows[index].first, spec.rows[index].second, itemH) {
                            playClick()
                            spec.onPick(index)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PopupRow(text: String, selected: Boolean, height: Float, onClick: () -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            // 选项板（UI_Options 图集 DropDown 素材）：选中=红板（自带勾选）、悬停=红板、未选中=暗板
            val plate = when {
                selected -> OptionsSprites["DropDownItemBase_Enable"]
                hovered -> OptionsSprites["DropDownItemBase_Highlighted"]
                else -> OptionsSprites["DropDownItemBase_Disable"]
            }
            plate?.let {
                Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            }
            BasicText(
                text,
                style = rowTextStyle(32f, if (selected || hovered) COLOR_POPUP_TEXT_SELECTED else COLOR_TEXT)
            )
        }
    }

    internal companion object {
        val FRAMERATE_OPTIONS = listOf(30, 60, 90, 120, 144, 165, 240, 260)
    }
}

/**
 * 九宫格贴图绘制：四角按原尺寸裁切，四边拉伸到目标矩形（中区透明，不绘制）。
 * srcBorder = 素材四边边框宽（px），dstBorder = 目标边框宽（设计 px）。
 */
internal fun DrawScope.drawNinePatch(image: ImageBitmap, srcBorder: Float, dstBorder: Float) {
    val iw = image.width
    val ih = image.height
    val sb = srcBorder.toInt().coerceIn(1, iw / 2 - 1)
    val db = dstBorder.toInt()
    val w = size.width.toInt()
    val h = size.height.toInt()
    if (db <= 0 || w <= db * 2 || h <= db * 2) {
        drawImage(image, dstSize = IntSize(w, h))
        return
    }
    // 四角
    drawImage(image, srcOffset = IntOffset(0, 0), srcSize = IntSize(sb, sb), dstOffset = IntOffset(0, 0), dstSize = IntSize(db, db))
    drawImage(image, srcOffset = IntOffset(iw - sb, 0), srcSize = IntSize(sb, sb), dstOffset = IntOffset(w - db, 0), dstSize = IntSize(db, db))
    drawImage(image, srcOffset = IntOffset(0, ih - sb), srcSize = IntSize(sb, sb), dstOffset = IntOffset(0, h - db), dstSize = IntSize(db, db))
    drawImage(image, srcOffset = IntOffset(iw - sb, ih - sb), srcSize = IntSize(sb, sb), dstOffset = IntOffset(w - db, h - db), dstSize = IntSize(db, db))
    // 四边（拉伸）
    drawImage(image, srcOffset = IntOffset(sb, 0), srcSize = IntSize(iw - 2 * sb, sb), dstOffset = IntOffset(db, 0), dstSize = IntSize(w - 2 * db, db))
    drawImage(image, srcOffset = IntOffset(sb, ih - sb), srcSize = IntSize(iw - 2 * sb, sb), dstOffset = IntOffset(db, h - db), dstSize = IntSize(w - 2 * db, db))
    drawImage(image, srcOffset = IntOffset(0, sb), srcSize = IntSize(sb, ih - 2 * sb), dstOffset = IntOffset(0, db), dstSize = IntSize(db, h - 2 * db))
    drawImage(image, srcOffset = IntOffset(iw - sb, sb), srcSize = IntSize(sb, ih - 2 * sb), dstOffset = IntOffset(w - db, db), dstSize = IntSize(db, h - 2 * db))
}

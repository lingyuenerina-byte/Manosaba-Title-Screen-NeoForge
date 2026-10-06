package me.shiiyuko.manosaba.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random
import kotlinx.coroutines.delay
import me.shiiyuko.manosaba.ManosabaMod
import me.shiiyuko.manosaba.utils.SpriteAtlas
import me.shiiyuko.manosaba.utils.UnitySpriteParser
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundSource
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.ImageInfo

private const val BUTTON_SCALE = 0.75f
private const val LOGO_SCALE = 0.75f
private const val LABEL_SCALE = 0.72f

// hover 命中掩码：对按钮贴图做 2x 降采样，alpha 超过阈值才算命中（排除透明边/间隙）
private const val HIT_MASK_DOWNSAMPLE = 2
private const val HIT_ALPHA_THRESHOLD = 0.15f

// 按钮贴图里散布着 alpha 3%~18% 的"底雾"像素（图集导出的半透明噪点），
// 直接渲染会在较暗背景上显出整个贴图矩形轮廓（即用户看到的"按钮范围"）。
// 加载时把低于该阈值的近透明像素直接清零，保留 alpha 更高的正常内容（文字/阴影/光晕）。
private const val FOG_ALPHA_THRESHOLD = 0.2f

val manosabaFont: FontFamily = FontFamily(Font(resource = "assets/TsukushiMincho.otf"))

class ManosabaTitleScreen : ComposeScreen(Component.literal("Manosaba Title Screen")) {

    private var atlasImage: Image? = null
    private var atlasData: SpriteAtlas? = null
    private var buttonSprites: Map<String, ImageBitmap>? = null
    private var titleLogo: ImageBitmap? = null
    private var titleOverlay: ImageBitmap? = null
    private var hoverLabels: Map<String, ImageBitmap>? = null

    /**
     * 周目索引：0 = 一周目（background_ema.png），1 = 二周目（background_next.png）。
     * 每次进入主界面（实例创建）随机选择，使两张周目背景随机播放；
     * 提升为界面字段，使设置界面的「倒映」背景可读取当前周目（并支持手动切换覆盖）。
     */
    internal var weekIndex by mutableStateOf(Random.nextInt(2))

    private val weekOneBackground: ImageBitmap? by lazy { loadBackgroundImage("/assets/background_ema.png") }
    private val weekTwoBackground: ImageBitmap? by lazy { loadBackgroundImage("/assets/background_next.png") }

    private var showExitDialog by mutableStateOf(false)
    private var exitDialog: ExitDialog? = null

    private val buttonNames = listOf(
        "Button_NewGame", "Button_LoadGame", "Button_Options",
        "Button_Exit", "Button_Gallery", "Button_WitchBook"
    )

    init {
        loadAtlasResources()
        exitDialog = ExitDialog(
            onCancel = { showExitDialog = false },
            onConfirm = { Minecraft.getInstance().stop() }
        )
    }

    override fun onClose() {
        super.onClose()
    }

    /**
     * 界面每次显示（setScreen 切回 / 窗口变化重建）都会触发 init：
     * 从世界返回（含多人断线、退出存档）时 clearLevel 已把所有声音停掉，
     * 在此确保标题背景音乐恢复播放。
     */
    override fun init() {
        super.init()
        ensureMusicPlaying()
    }

    /**
     * 音乐仍在播则不动；实例为空 / 已被停掉（含被暂停后清理）则重新拉起。
     * 实例为全局共享（见 companion）：从设置界面返回主界面时（返回路径可能
     * 短暂新建主界面实例），只要音乐未真正停止就延续播放，不再从头重播。
     */
    private fun ensureMusicPlaying() {
        val client = Minecraft.getInstance()
        val current = sharedMusicInstance
        if (current == null || !client.soundManager.isActive(current)) {
            playMusic()
        }
    }

    private fun playMusic() {
        val client = Minecraft.getInstance()

        client.soundManager.stop()
        client.musicManager.stopPlaying()

        // forMusic 建出的实例 looping=false，一曲播完即静音；标题音乐需循环，
        // 故用 12 参构造器手动开启 looping。
        val sound = SimpleSoundInstance(
            ManosabaMod.TITLE_MUSIC.get().location,
            SoundSource.MUSIC,
            1.0F,
            1.0F,
            SoundInstance.createUnseededRandom(),
            true,
            0,
            SoundInstance.Attenuation.NONE,
            0.0,
            0.0,
            0.0,
            true
        )
        sharedMusicInstance = sound
        client.soundManager.play(sound)
    }

    private fun loadAtlasResources() = runCatching {
        val image = UnitySpriteParser.loadAtlasImageFromResources("/assets/UI_Title.png") ?: return@runCatching
        val data = UnitySpriteParser.loadAtlasDataFromResources("/assets/UI_Title.json") ?: return@runCatching

        atlasImage = image
        atlasData = data

        buttonSprites = buttonNames.flatMap { name ->
            listOf("${name}_Normal", "${name}_Highlighted")
        }.mapNotNull { spriteName ->
            data.sprites[spriteName]?.let { spriteData ->
                spriteName to cleanFaintPixels(
                    UnitySpriteParser.cropSprite(image, spriteData).toComposeImageBitmap()
                )
            }
        }.toMap()

        titleLogo = data.sprites["TitleLogo@ZhHans"]?.let { spriteData ->
            UnitySpriteParser.cropSprite(image, spriteData).toComposeImageBitmap()
        }

        titleOverlay = data.sprites["TitleOverlay"]?.let { spriteData ->
            UnitySpriteParser.cropSprite(image, spriteData).toComposeImageBitmap()
        }

        hoverLabels = buttonNames.mapNotNull { fullName ->
            val shortName = fullName.removePrefix("Button_")
            data.sprites["Label_${shortName}@ZhHans"]?.let { spriteData ->
                shortName to UnitySpriteParser.cropSprite(image, spriteData).toComposeImageBitmap()
            }
        }.toMap()
    }.onFailure {
        it.printStackTrace()
        ManosabaMod.LOGGER.error("[Manosaba] Failed to load title screen atlas resources", it)
    }

    /** 当前周目对应的标题背景图 */
    internal fun currentBackground(): ImageBitmap? = if (weekIndex == 1) weekTwoBackground else weekOneBackground

    /**
     * 主界面「倒映」幽灵层：以与标题界面相同构图静态绘制
     * 背景图 / 覆盖层 / Logo / 五个按钮的 Normal 贴图，供设置界面作半透明背景。
     * 仅绘制，不注册任何交互。
     */
    @Composable
    internal fun GhostLayer(alpha: Float) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha }
        ) {
            currentBackground()?.let { bitmap ->
                Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }

            titleOverlay?.let { overlayBitmap ->
                Image(overlayBitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            }

            titleLogo?.let { logoBitmap ->
                Image(
                    logoBitmap, null,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 1120.dp, y = 20.dp)
                        .size((logoBitmap.width * LOGO_SCALE).dp, (logoBitmap.height * LOGO_SCALE).dp)
                )
            }

            buttonSprites?.let { spriteMap ->
                val ghostButtons = listOf(
                    "LoadGame" to (0f to 836.5f),
                    "NewGame" to (249.7f to 816f),
                    "Gallery" to (486.1f to 889.4f),
                    "Options" to (679.4f to 863f),
                    "Exit" to (864.9f to 919.1f)
                )
                ghostButtons.forEach { (name, pos) ->
                    spriteMap["Button_${name}_Normal"]?.let { normal ->
                        Image(
                            normal, null,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = pos.first.dp, y = pos.second.dp)
                                .size((normal.width * BUTTON_SCALE).dp, (normal.height * BUTTON_SCALE).dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    override fun Content() {
        val client = Minecraft.getInstance()
        val backgroundImage = currentBackground()
        val sprites = remember { buttonSprites }
        val logo = remember { titleLogo }
        val overlay = remember { titleOverlay }
        val labels = remember { hoverLabels }
        val versionText = remember { "Ver. ${SharedConstants.getCurrentVersion().name}" }

        val backgroundAnimProgress = remember { Animatable(0f) }
        val uiAlpha = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            ensureMusicPlaying()
            backgroundAnimProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 2500, easing = FastOutSlowInEasing)
            )
            delay(250L)
            uiAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
        }

        val scale = 1.1f - (0.1f * backgroundAnimProgress.value)
        val blurAmount = 20f * (1f - backgroundAnimProgress.value)

        Box(modifier = Modifier.fillMaxSize()) {
            backgroundImage?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = "Background",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            if (blurAmount > 0.1f) {
                                renderEffect = ImageFilter.makeBlur(
                                    blurAmount, blurAmount, FilterTileMode.CLAMP
                                ).asComposeRenderEffect()
                            }
                        },
                    contentScale = ContentScale.Crop
                )
            }

            overlay?.let { overlayBitmap ->
                Image(
                    bitmap = overlayBitmap,
                    contentDescription = "Title Overlay",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = uiAlpha.value },
                    contentScale = ContentScale.FillBounds
                )
            }

            logo?.let { logoBitmap ->
                Image(
                    bitmap = logoBitmap,
                    contentDescription = "Title Logo",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 1120.dp, y = 20.dp)
                        .size(
                            width = (logoBitmap.width * LOGO_SCALE).dp,
                            height = (logoBitmap.height * LOGO_SCALE).dp
                        )
                        .graphicsLayer { alpha = uiAlpha.value }
                )
            }

            sprites?.let { spriteMap ->
                val buttons = listOf(
                    // labelOffsetX/Y = 标签中心相对按钮贴图左上角的位置（按原游戏截图逐按钮校准）
                    ButtonConfig("LoadGame", 0f, 836.5f, 217.5f, 190.5f) { client.setScreen(SelectWorldScreen(this@ManosabaTitleScreen)) },
                    ButtonConfig("NewGame", 249.7f, 816f, 183.5f, 172.5f) { CreateWorldScreen.openFresh(client, this@ManosabaTitleScreen) },
                    ButtonConfig("Gallery", 486.1f, 889.4f, 148f, 123.5f) { client.setScreen(JoinMultiplayerScreen(this@ManosabaTitleScreen)) },
                    ButtonConfig("Options", 679.4f, 863f, 139f, 123.5f) { client.setScreen(ManosabaOptionsScreen(this@ManosabaTitleScreen)) },
                    ButtonConfig("Exit", 864.9f, 919.1f, 110f, 105.5f) { showExitDialog = true }
                )

                // 命中掩码：按贴图可见像素判定，只建一次
                val hitMasks = remember(spriteMap) {
                    buttons.associate { cfg ->
                        cfg.name to buildHitMask(spriteMap["Button_${cfg.name}_Normal"])
                    }
                }
                var hoveredName by remember { mutableStateOf<String?>(null) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = uiAlpha.value }
                        .pointerInput(Unit) {
                            // 顶层统一判定：每帧帧同步的指针事件都会到达此处，指针不在任何按钮上时立即清空
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    if (event.type == PointerEventType.Exit) {
                                        hoveredName = null
                                        continue
                                    }
                                    val pos = event.changes.firstOrNull()?.position ?: continue
                                    // 从最上层按钮开始检测（后声明者绘制在上层），命中可见像素才算
                                    hoveredName = buttons.asReversed().firstOrNull { cfg ->
                                        hitMasks[cfg.name]?.contains(pos.x - cfg.x, pos.y - cfg.y) == true
                                    }?.name
                                }
                            }
                        }
                ) {
                    buttons.forEach { config ->
                        SpriteButton(
                            normalSprite = spriteMap["Button_${config.name}_Normal"],
                            highlightedSprite = spriteMap["Button_${config.name}_Highlighted"],
                            hoverLabelSprite = labels?.get(config.name),
                            labelOffsetX = config.labelOffsetX,
                            labelOffsetY = config.labelOffsetY,
                            isHovered = hoveredName == config.name,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = config.x.dp, y = config.y.dp),
                            onClick = config.onClick
                        )
                    }
                }
            }

            BasicText(
                text = versionText,
                style = TextStyle(
                    fontFamily = manosabaFont,
                    color = Color.White,
                    fontSize = 27.sp,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.7f),
                        blurRadius = 4f
                    )
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 52.dp, bottom = 44.dp)
                    .graphicsLayer { alpha = uiAlpha.value }
            )

            if (showExitDialog) {
                exitDialog?.Content()
            }
        }
    }

    private fun loadBackgroundImage(path: String): ImageBitmap? = runCatching {
        javaClass.getResourceAsStream(path)?.use {
            Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap()
        }
    }.onFailure {
        it.printStackTrace()
        ManosabaMod.LOGGER.error("[Manosaba] Failed to load title background image: $path", it)
    }.getOrNull()

    private companion object {
        /**
         * 全局共享的标题音乐实例（跨主界面实例复用）：TitleScreenMixin 每次回主界面
         * 都会新建 ManosabaTitleScreen，若音乐实例随界面实例存亡，新实例会因
         * 「本地实例为空」而重新拉起 playMusic（其中会 stop 掉仍在播的音乐）——
         * 用户报修：设置返回主界面时音乐被从头重播。共享实例使
         * 「仍在播则延续、真停了才重新播」跨实例成立（含被暂停/停止后的恢复）。
         */
        var sharedMusicInstance: SoundInstance? = null
    }
}

private data class ButtonConfig(
    val name: String,
    val x: Float,
    val y: Float,
    val labelOffsetX: Float,
    val labelOffsetY: Float,
    val onClick: () -> Unit
)

// 按钮命中掩码：按贴图可见像素（alpha > 阈值）判定 hover，剔除透明边与倾斜卡片的空角
private class HitMask(
    private val maskWidth: Int,
    private val maskHeight: Int,
    private val bits: BooleanArray
) {
    fun contains(localX: Float, localY: Float): Boolean {
        val sx = (localX / (BUTTON_SCALE * HIT_MASK_DOWNSAMPLE)).toInt()
        val sy = (localY / (BUTTON_SCALE * HIT_MASK_DOWNSAMPLE)).toInt()
        if (sx < 0 || sy < 0 || sx >= maskWidth || sy >= maskHeight) return false
        return bits[sy * maskWidth + sx]
    }
}

private fun buildHitMask(sprite: ImageBitmap?): HitMask? {
    sprite ?: return null
    val pm = sprite.toPixelMap()
    val w = (sprite.width + HIT_MASK_DOWNSAMPLE - 1) / HIT_MASK_DOWNSAMPLE
    val h = (sprite.height + HIT_MASK_DOWNSAMPLE - 1) / HIT_MASK_DOWNSAMPLE
    val bits = BooleanArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            val px = minOf(x * HIT_MASK_DOWNSAMPLE + 1, sprite.width - 1)
            val py = minOf(y * HIT_MASK_DOWNSAMPLE + 1, sprite.height - 1)
            bits[y * w + x] = pm[px, py].alpha > HIT_ALPHA_THRESHOLD
        }
    }
    return HitMask(w, h, bits)
}

// 清零贴图中 alpha 低于阈值的"底雾"像素，其余像素原样保留（预乘 BGRA 写回，与贴图原有格式一致）
private fun cleanFaintPixels(source: ImageBitmap): ImageBitmap {
    val pm = source.toPixelMap()
    val w = source.width
    val h = source.height
    val bytes = ByteArray(w * h * 4)
    var i = 0
    for (y in 0 until h) {
        for (x in 0 until w) {
            val color = pm[x, y]
            val alpha = color.alpha
            if (alpha >= FOG_ALPHA_THRESHOLD) {
                // skia BGRA_8888 在小端内存中的字节序：B, G, R, A（预乘）
                bytes[i] = (color.blue * alpha * 255f + 0.5f).toInt().toByte()
                bytes[i + 1] = (color.green * alpha * 255f + 0.5f).toInt().toByte()
                bytes[i + 2] = (color.red * alpha * 255f + 0.5f).toInt().toByte()
                bytes[i + 3] = (alpha * 255f + 0.5f).toInt().toByte()
            }
            i += 4
        }
    }
    return Image.makeRaster(
        ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL), bytes, w * 4
    ).toComposeImageBitmap()
}

@Composable
private fun SpriteButton(
    normalSprite: ImageBitmap?,
    highlightedSprite: ImageBitmap?,
    hoverLabelSprite: ImageBitmap? = null,
    labelOffsetX: Float = 0f,
    labelOffsetY: Float = 0f,
    isHovered: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    normalSprite ?: return

    val currentSprite = highlightedSprite?.takeIf { isHovered } ?: normalSprite

    Box(
        modifier = modifier
            .size(
                width = (normalSprite.width * BUTTON_SCALE).dp,
                height = (normalSprite.height * BUTTON_SCALE).dp
            )
            // indication = null：项目无 Material 主题，LocalIndication 默认回退到
            // DefaultDebugIndication 调试层，会在 hover 时绘制半透明黑矩形覆盖按钮范围
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    // 五个主按钮点击音效（TEST20 报修）：ui_cancel.ogg（Sfx_System_Cancel_001）
                    Minecraft.getInstance().soundManager.play(
                        SimpleSoundInstance.forUI(ManosabaMod.UI_CANCEL.get(), 1.0f)
                    )
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // 按当前贴图自身尺寸渲染、顶左对齐：Highlighted 比 Normal 高（光晕），溢出部分向下绘制不缩放
        Image(
            bitmap = currentSprite,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(
                    width = (currentSprite.width * BUTTON_SCALE).dp,
                    height = (currentSprite.height * BUTTON_SCALE).dp
                )
        )

        // 悬停时显示中文标签：精灵原样渲染（本身即白色），中心点按原游戏截图逐按钮校准
        // filterQuality=None 保持最近邻采样，避免双线性滤波把笔画糊化（与原版锐利度一致）
        hoverLabelSprite?.let { label ->
            val labelWidth = label.width * LABEL_SCALE
            val labelHeight = label.height * LABEL_SCALE
            Image(
                bitmap = label,
                contentDescription = null,
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = (labelOffsetX - labelWidth / 2f).dp,
                        y = (labelOffsetY - labelHeight / 2f).dp
                    )
                    .size(width = labelWidth.dp, height = labelHeight.dp)
                    .graphicsLayer { alpha = if (isHovered) 1f else 0f }
            )
        }
    }
}

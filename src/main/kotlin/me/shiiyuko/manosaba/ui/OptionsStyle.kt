package me.shiiyuko.manosaba.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.shiiyuko.manosaba.ManosabaClientConfig
import me.shiiyuko.manosaba.ManosabaMod
import org.jetbrains.skia.Image
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// —— 布局常量：由 TEST8/9/10 像素扫描测得（设计空间 1920x1080，与原游戏画布 1:1）——

// 行布局：每行固定 ROW_STEP 高，标签垂直居中于左侧，控件区从 CTRL_LEFT 开始
internal const val LABEL_LEFT = 258f        // 左侧文字标签左对齐位置
internal const val LABEL_FONT_SIZE = 30f
internal const val CTRL_LEFT = 985f         // Toggle / 选择条左端
internal const val CTRL_WIDTH = 590f        // Toggle / 选择条宽度（中缝在 295 处）
internal const val TOGGLE_HEIGHT = 52f
internal const val SELECT_HEIGHT = 52f
internal const val TRACK_LEFT = 985f        // 滑条轨道左端
internal const val TRACK_WIDTH = 590f       // 滑条轨道宽度
internal const val TRACK_HEIGHT = 26f       // 滑条轨道高度
// 滑条命中区左右外扩量：轨道两侧空白与数值附近也可起拖 / 续拖（判定范围加大防断触）
internal const val SLIDER_HIT_PAD = 45f
internal const val ROW_STEP = 97f           // 行距
internal const val VALUE_RIGHT = 960f       // 滑条数值右对齐位置
internal const val VALUE_FONT_SIZE = 34f

// 行悬停红横幅（原版 hover 实测：笔尖约 x222、主体延至 x≈1585、垂直居中于行）。
// 素材为 UI_Options 图集的长红笔刷 Item_Highlighted（原生 2006x116），
// 高度按原生宽高比换算：宽 1385 × 116/2006 ≈ 80，避免拉伸变形
internal const val BANNER_LEFT = 200f
internal const val BANNER_RIGHT = 1585f
internal const val BANNER_HEIGHT = 80f

// 滚动视口：标题之下、底部按钮之上
internal const val VIEW_TOP = 138f          // 视口顶部 y
internal const val VIEW_BOTTOM_MARGIN = 140f
internal const val PAGE_RIGHT = 1650f       // 内容/滚动条右边界（页签区左侧）
internal const val PAGE_BOTTOM_PAD = 120f   // 内容底部留白（保证末行可滚到视口上方）

internal const val TITLE_SCALE = 0.72f      // "选项设置"标题素材缩放
// 页签文字素材缩放：官方 TabLabel_* 素材（100x65）墨迹 64px，0.95 缩放后墨高≈61，贴合
// 实测原版墨高≈60（用户反馈 1.0 时文字略大，整体回缩 5%）
internal const val TAB_LABEL_SCALE = 0.95f
internal const val CLOSE_SCALE = 1.0f       // 关闭按钮素材（原游戏为 1:1）
internal const val RESET_WIDTH = 305f       // "恢复初始设置"底板宽高
internal const val RESET_HEIGHT = 66f

// 页签竖排。逐帧实测原版三页签：中心 248/353/458（步进 105、板高 80、右缘贴 1920），
// 选中板固定在自己槽位、不重排不平移，仅向左滑出；本 mod 扩到 10 页签后步进压缩为
// 最大可行值 86（首中心 248 与原版对齐、末页签中心 1022），间距保持均匀。
// 未选中底板设计宽 ≈200（右缘贴 1920），hover/选中时整块底板向左滑出 56px
// （170ms ease-out）至宽 ≈256，标签随底板同步滑动；标签左缘恒为底板左缘 +52。
// 未选中静止态乘性压暗到 0.7（TEST27 报修：原版静止「只露出一点」隐没感），hover/选中
// 时渐亮回素材全亮；选中再交叉淡入红板 Highlighted。
// 框高 98：TabBase 素材 357x144，实心内容仅 y9..126（约 82%），框高 80 时实际渲染
// 板高仅 ≈66，呈细长条观感（TEST29/TEST30 对比报修：按钮比原版短一截）。
// 按原版板高 80 反推框高 = 80x144/118 ≈ 98，与原版等比。
internal const val TAB_HEIGHT = 98f
internal const val TAB_STEP = 86f
internal const val TAB_FIRST_CENTER = 248f
internal const val TAB_WIDTH_ACTIVE = 256f
internal const val TAB_WIDTH_INACTIVE = 200f
internal const val TAB_LABEL_ANCHOR = 52f     // 标签左缘距底板左缘（设计 px）
internal const val TAB_SLIDE_MS = 170         // hover/选中时的滑出动画时长
internal const val TAB_SWITCH_MS = 50         // 选中红板交叉淡入时长（实测切换 1-2 帧骤变）
// 未选中静止态底板乘性压暗：渲染亮度 = 素材×(1−TAB_DIM_MASK) ≈ 0.7
// （TEST27 逐像素实测：原版静止板亮度 ≈ mod 全亮的 0.7，几乎隐没）
internal const val TAB_DIM_MASK = 0.30f

// 官方 TabLabel_*_Active 素材首字与次字的分界列（逐像素实测；两字笔画在分界附近
// 有轻微交叠，取残差最小列）：未选中悬停时仅将该列之前的首字区域叠加 Active 素材，
// 使首字渐显为粉、次字保持 Inactive 原样（TEST27 报修：悬停只应首字更粉）
internal val TAB_HEAD_WIDTHS = mapOf(
    "TabLabel_Message" to 61,
    "TabLabel_Graphics" to 51,
    "TabLabel_Audio" to 61
)

// 右侧拉动条（TEST19 报修）：按下命中区为最右 24px 列；按住期间条身隐藏、
// 可拖动横带扩大为 140px（向左延伸 116px）；指针左右超出该带立即断开，
// 松开鼠标后恢复原状
internal const val SCROLLBAR_HIT_WIDTH = 24f
internal const val SCROLLBAR_BAND_WIDTH = 140f

// 紧凑选择弹层（原版实测）：挂在锚定行右侧、垂直方向与行中心对齐
internal const val POPUP_ITEM_WIDTH = 300f
internal const val POPUP_ITEM_HEIGHT = 50f
internal const val POPUP_GAP = 15f            // 行控件板右缘到弹层左缘间距

// 背景「倒映」：主界面幽灵层（同方向、暗化）+ 磨砂底板
// 校准依据：TEST8/TEST9 与主界面原图 menu.png 逐像素线性回归（各 n≈73k，结果一致）：
//   主界面图透出斜率 ≈ 0.129(luma)、底板抬升 c ≈ (30.9, 28.5, 25.8)
//   UNDERLAY_ALPHA 0.85 ≈ 30.9 ÷ (素材 A*U 加权均值 36.5×0.925)
//   GHOST_ALPHA 0.60 → 0.60×(1−0.85×0.925) ≈ 0.128 ✓
internal const val GHOST_ALPHA = 0.60f
internal const val UNDERLAY_ALPHA = 0.85f

internal val COLOR_TEXT = Color(0xFFEDE6E2)
internal val COLOR_TEXT_DIM = Color(0xFFD9CFC9)
internal val COLOR_PINK = Color(0xFFE56B85)
internal val COLOR_PINK_BRIGHT = Color(0xFFFF9AAC)
internal val COLOR_SHADOW = Color(0xB0000000)
internal val COLOR_DISABLED = Color(0xFF9A8F8A)

// 页签自绘标签配色（取自官方 TabLabel_*@ZhHans 素材核心色）
internal val COLOR_TAB_LABEL_ACTIVE = Color(0xFFFAB2C4)
internal val COLOR_TAB_LABEL_INACTIVE = Color(0xFFD4BDBC)

// 紧凑弹层选中项文字（红板上的粉白字）
internal val COLOR_POPUP_TEXT_SELECTED = Color(0xFFF6DDE0)

// 纸面弹层文字配色（DialogBase 和纸底板之上使用深色）
internal val COLOR_DIALOG_TITLE = Color(0xFF382E2C)
internal val COLOR_DIALOG_TEXT = Color(0xFF453A38)
internal val COLOR_DIALOG_ACCENT = Color(0xFFC4485F)

internal val optionsFont: FontFamily = FontFamily(Font(resource = "assets/SourceHanSerifSC.otf"))

internal fun rowTextStyle(size: Float, color: Color = COLOR_TEXT) = TextStyle(
    fontFamily = optionsFont,
    color = color,
    fontSize = size.sp,
    shadow = Shadow(color = COLOR_SHADOW, blurRadius = 6f)
)

/** 纸面弹层文字样式：无阴影深色墨字（用于 DialogBase 和纸底） */
internal fun dialogTextStyle(size: Float, color: Color) = TextStyle(
    fontFamily = optionsFont,
    color = color,
    fontSize = size.sp
)

/** 界面统一的点击音效桥：由当前 ComposeScreen 在 init 时注入 */
internal object OptionsUi {
    var playClick: (() -> Unit)? = null
    /** 最近一次交互行的垂直中心（设计 px），供弹层锚定行位置 */
    var anchorY: Float = 0f
    /** 弹层是否打开（打开时全屏点击层会拦截悬停，行横幅据此统一隐藏） */
    var popupOpen by mutableStateOf(false)
    fun click() {
        playClick?.invoke()
    }
}

/** 设置界面全部素材（全局单例，首次访问时从 jar 加载） */
internal object OptionsSprites {
    private val names = listOf(
        "OptionsUnderlay", "OptionsTitle",
        "TabBase_Default", "TabBase_Highlighted",
        "TabLabel_Message_Active", "TabLabel_Message_Inactive",
        "TabLabel_Graphics_Active", "TabLabel_Graphics_Inactive",
        "TabLabel_Audio_Active", "TabLabel_Audio_Inactive",
        "SliderFrame", "SliderBase", "SliderFiller_Default", "SliderFiller_Highlighted",
        "Handle_Default", "Handle_Highlighted",
        "ToggleBody_Enabled", "ToggleBody_Disabled", "ToggleBody_Highlighted", "ToggleOutline",
        "CloseButton_Default", "CloseButton_Highlighted", "CloseButton_Pressed",
        "Icon_Arrow", "ButtonBase_Default", "ButtonBase_Highlighted",
        "Scrollbar_Base", "Scrollbar_Fill", "DialogBase", "CheckMark", "MenuCloseIcon",
        "Item_Highlighted",
        "DropDownItemBase_Enable", "DropDownItemBase_Disable", "DropDownItemBase_Highlighted",
        "DropDownOutline"
    )

    private val sprites: Map<String, ImageBitmap> by lazy {
        buildMap {
            names.forEach { name ->
                val path = "/assets/options/$name.png"
                runCatching {
                    // 注意：本 lambda 的隐含 receiver 是 buildMap 的 MutableMap，裸写 javaClass
                    // 会被解析为 LinkedHashMap 的类（bootstrap 类加载器找不到 mod 资源，静默返回 null）；
                    // 必须显式使用 OptionsSprites 类字面量，从 mod 类加载器读取
                    val stream = OptionsSprites::class.java.getResourceAsStream(path)
                        ?: error("options sprite not found on classpath: $path")
                    val bytes = stream.use { it.readBytes() }
                    put(name, Image.makeFromEncoded(bytes).toComposeImageBitmap())
                    // 页签底板另存「净版」（擦除上下虚线灰边）：静止态用净版渲染，
                    // 未选中悬停时在净版上叠加原图，使灰边仅在未选中悬停时出现
                    if (name == "TabBase_Default" || name == "TabBase_Highlighted") {
                        eraseTabEdgeLines(bytes)?.let { cleaned ->
                            put("${name}_Clean", Image.makeFromEncoded(cleaned).toComposeImageBitmap())
                        }
                    }
                }.onFailure {
                    ManosabaMod.LOGGER.error("[Manosaba] Failed to load options sprite: $path", it)
                }
            }
        }.also {
            ManosabaMod.LOGGER.info("[Manosaba] Options sprites loaded: ${it.size}/${names.size}")
        }
    }

    operator fun get(name: String): ImageBitmap? = sprites[name]

    /** 在界面初始化时调用，提前完成解码 */
    fun preload() {
        sprites.size
    }
}

/**
 * 擦除页签底板（TabBase_Default / TabBase_Highlighted）素材上下的虚线灰边（TEST19 报修）。
 * 素材 357x144 逐像素实测出三个亮线区间；区间内亮度超阈值的像素按比例压暗到 cap 亮度
 * （保留色相，避免出现色斑），缩放渲染后与原底纹理融为一体。
 * 悬停时叠加原始素材即可让灰边复现。
 */
private fun eraseTabEdgeLines(png: ByteArray): ByteArray? = runCatching {
    val img = ImageIO.read(ByteArrayInputStream(png)) ?: return@runCatching null
    fun capZone(y0: Int, y1: Int, x0: Int, x1: Int, threshold: Int, cap: Int) {
        for (y in y0..minOf(y1, img.height - 1)) {
            for (x in x0..minOf(x1, img.width - 1)) {
                val c = img.getRGB(x, y)
                val a = (c ushr 24) and 0xFF
                if (a <= 100) continue
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                val lum = (r + g + b) / 3
                if (lum > threshold) {
                    val f = cap.toDouble() / lum
                    img.setRGB(
                        x, y,
                        (a shl 24) or ((r * f).toInt() shl 16) or ((g * f).toInt() shl 8) or (b * f).toInt()
                    )
                }
            }
        }
    }
    capZone(14, 22, 75, 296, 55, 30)    // 顶部虚线左/中段
    capZone(17, 20, 296, 356, 80, 30)   // 顶部虚线右段（叠在大亮块边缘，单独放宽阈值）
    capZone(108, 123, 10, 356, 45, 34)  // 底部虚线
    ByteArrayOutputStream().use { out ->
        ImageIO.write(img, "png", out)
        out.toByteArray()
    }
}.getOrNull()

internal fun saveManosabaConfig() {
    runCatching { ManosabaClientConfig.SPEC.save() }
}

internal fun percentLabel(value: Double): String = "${(value * 100.0).roundToInt()}%"

/** 滚轮灵敏度滑条位置：logMouse 值 0.01..10 → -200..100 */
internal fun wheelSliderPos(value: Double): Float =
    (Math.log10(value.coerceAtLeast(0.01)) * 100.0).toFloat()

/** 滚轮灵敏度滑条 → logMouse 值（10^(x/100)） */
internal fun wheelValueAt(pos: Float): Double = Math.pow(10.0, pos / 100.0)

// ============================== 通用行组件 ==============================

/** 行容器：固定行高，标签垂直居中于左侧，内容自铺（坐标按设计空间绝对定位） */
@Composable
internal fun RowBox(label: String, labelColor: Color = COLOR_TEXT, content: @Composable BoxScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(Modifier.fillMaxWidth().height(ROW_STEP.dp).hoverable(interaction)) {
        // 行悬停红横幅：原版长红笔刷（UI_Options 图集 Item_Highlighted），原版 hover 反馈；
        // 弹层打开时隐藏（全屏点击层已拦截悬停，避免残留）
        if (hovered && !OptionsUi.popupOpen) {
            OptionsSprites["Item_Highlighted"]?.let {
                Image(
                    it, null,
                    modifier = Modifier
                        .offset(x = BANNER_LEFT.dp, y = ((ROW_STEP - BANNER_HEIGHT) / 2f).dp)
                        .size((BANNER_RIGHT - BANNER_LEFT).dp, BANNER_HEIGHT.dp),
                    contentScale = ContentScale.FillBounds
                )
            }
        }
        Box(
            modifier = Modifier.offset(x = LABEL_LEFT.dp).fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicText(label, style = rowTextStyle(LABEL_FONT_SIZE, labelColor))
        }
        content()
    }
}

@Composable
internal fun SectionHeader(text: String) {
    Box(Modifier.fillMaxWidth().height(78.dp)) {
        Box(
            modifier = Modifier.offset(x = LABEL_LEFT.dp).fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicText(text, style = rowTextStyle(33f, COLOR_PINK))
        }
        Box(
            modifier = Modifier
                .offset(x = LABEL_LEFT.dp, y = 66.dp)
                .width((PAGE_RIGHT - LABEL_LEFT - 40f).dp)
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.13f))
        )
    }
}

/** 静态说明文本块（自动换行） */
@Composable
internal fun TextBlock(text: String, size: Float = 30f, width: Float = 1220f, color: Color = COLOR_TEXT_DIM) {
    Box(Modifier.fillMaxWidth().padding(start = LABEL_LEFT.dp, top = 10.dp, bottom = 10.dp)) {
        BasicText(
            text,
            modifier = Modifier.width(width.dp),
            style = TextStyle(
                fontFamily = optionsFont,
                color = color,
                fontSize = size.sp,
                lineHeight = (size * 1.6f).sp,
                shadow = Shadow(color = COLOR_SHADOW, blurRadius = 6f)
            )
        )
    }
}

/** 只读行：标签 + 暗色数值文本 */
@Composable
internal fun StaticRow(label: String, text: String) {
    RowBox(label) {
        Box(
            modifier = Modifier.offset(x = CTRL_LEFT.dp).fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicText(text, style = rowTextStyle(34f, COLOR_TEXT_DIM))
        }
    }
}

/** 双段开关：左侧/右侧各半，选中段使用粉色底（ToggleBody_Enabled），未选中为暗色 */
@Composable
internal fun ToggleRow(
    label: String,
    leftText: String,
    rightText: String,
    rightSelected: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    RowBox(label, if (enabled) COLOR_TEXT else COLOR_DISABLED) {
        Box(
            modifier = Modifier.offset(x = CTRL_LEFT.dp).fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(Modifier.size(CTRL_WIDTH.dp, TOGGLE_HEIGHT.dp)) {
                ToggleSegment(0f, leftText, !rightSelected, enabled) { onChange(false) }
                ToggleSegment(CTRL_WIDTH / 2f, rightText, rightSelected, enabled) { onChange(true) }
                OptionsSprites["ToggleOutline"]?.let {
                    Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                }
            }
        }
    }
}

@Composable
private fun ToggleSegment(x: Float, text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val body = when {
        enabled && hovered -> OptionsSprites["ToggleBody_Highlighted"]
        selected -> OptionsSprites["ToggleBody_Enabled"]
        else -> OptionsSprites["ToggleBody_Disabled"]
    }

    Box(
        modifier = Modifier
            .offset(x = x.dp)
            .size((CTRL_WIDTH / 2f).dp, TOGGLE_HEIGHT.dp)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                OptionsUi.click()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        body?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
        BasicText(text, style = rowTextStyle(34f, if (enabled) COLOR_TEXT else COLOR_DISABLED))
    }
}

/** 选择条：整条粉色底板 + 文字 + 右端 ▶ 箭头，点击打开选项弹层 */
@Composable
internal fun SelectRow(label: String, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    RowBox(label, if (enabled) COLOR_TEXT else COLOR_DISABLED) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        var rowCenterY by remember { mutableStateOf(0f) }
        val bg = when {
            enabled && hovered -> OptionsSprites["ToggleBody_Highlighted"]
            enabled -> OptionsSprites["ToggleBody_Enabled"]
            else -> OptionsSprites["ToggleBody_Disabled"]
        }

        Box(
            modifier = Modifier
                .offset(x = CTRL_LEFT.dp)
                .fillMaxHeight()
                .onGloballyPositioned { coords: LayoutCoordinates ->
                    // 记录本行中心（设计 px，密度 1:1），供打开弹层时锚定垂直位置
                    rowCenterY = coords.positionInRoot().y + ROW_STEP / 2f
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(CTRL_WIDTH.dp, SELECT_HEIGHT.dp)
                    .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                        OptionsUi.click()
                        OptionsUi.anchorY = rowCenterY
                        onClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                bg?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                BasicText(text, style = rowTextStyle(34f, if (enabled) COLOR_TEXT else COLOR_DISABLED))
                OptionsSprites["Icon_Arrow"]?.let {
                    Image(
                        it, null,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 14.dp)
                            .size(16.dp, 26.dp)
                    )
                }
            }
        }
    }
}

/** 滑条：轨道 = 白色底框(SliderFrame) + 右侧暗段(SliderBase) + 左侧亮段(SliderFiller) + 白色圆环滑块 */
@Composable
internal fun SliderRow(
    label: String,
    value: Float,
    minValue: Float,
    maxValue: Float,
    display: String,
    step: Float = 0f,
    onCommit: () -> Unit = {},
    onChange: (Float) -> Unit
) {
    RowBox(label) {
        val ratio = if (maxValue > minValue) ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f) else 0f
        val fillWidth = TRACK_WIDTH * ratio

        // 数值：右对齐显示在轨道左侧（与 TEST9/10 一致）
        Box(
            modifier = Modifier.offset(x = (VALUE_RIGHT - 160f).dp).fillMaxHeight().width(160.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            BasicText(display, style = rowTextStyle(VALUE_FONT_SIZE))
        }

        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        var scrubbing by remember { mutableStateOf(false) }
        // 拖动中要读「当前值」：pointerInput(Unit) 的闭包不随重组重建，直接捕获
        // value 参数会拿到旧值（外扩区相对拖动起点会错），经 State 桥接保持最新
        val currentValue by rememberUpdatedState(value)

        // 命中区：整行高度 × 视觉轨道左右各外扩 SLIDER_HIT_PAD —— 轨道周围空白与数值
        // 附近区域也可起拖 / 续拖，指针滑出细轨道不再断触（Exit 中断问题见下方手势处理）。
        // 右缘 985+590+45=1620，与滚动条命中列（右起 24px，即 1626 起）留 6px 间隔。
        Box(
            modifier = Modifier
                .offset(x = (TRACK_LEFT - SLIDER_HIT_PAD).dp)
                .fillMaxHeight()
                .width((TRACK_WIDTH + SLIDER_HIT_PAD * 2f).dp)
                .hoverable(interaction)
                .pointerInput(Unit) {
                    // 视觉轨道在命中区内的像素范围（左右各外扩 SLIDER_HIT_PAD）
                    fun padPx(): Float = SLIDER_HIT_PAD * size.width / (TRACK_WIDTH + SLIDER_HIT_PAD * 2f)
                    fun trackPx(): Float = size.width - padPx() * 2f
                    fun snap(v: Float): Float =
                        if (step > 0f) {
                            (minValue + ((v - minValue) / step).roundToInt() * step).coerceIn(minValue, maxValue)
                        } else {
                            v.coerceIn(minValue, maxValue)
                        }
                    fun updateFromX(x: Float) {
                        // 轨道内绝对映射：点哪到哪（外扩区由相对拖动分支处理）
                        val r = ((x - padPx()) / trackPx()).coerceIn(0f, 1f)
                        onChange(snap(minValue + r * (maxValue - minValue)))
                    }
                    awaitPointerEventScope {
                        // relative：在轨道外（外扩空白区）按下时不跳值，从当前值出发按
                        // 指针位移调整——避免「点空白 → 值瞬间跳到 0% / 100%」的误触；
                        // 轨道内按下仍为「点哪到哪」+ 绝对跟随（标准滑条手感）
                        var relative = false
                        var pressX = 0f
                        var pressValue = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            when (event.type) {
                                PointerEventType.Press -> {
                                    scrubbing = true
                                    // 消费按下 / 拖动事件：声明滑条为主手势，避免页面纵向
                                    // 滚动把手势抢走（拖动中页面跟着滚或拖动中断）
                                    event.changes.forEach { it.consume() }
                                    val x = event.changes.first().position.x
                                    val trackX = x - padPx()
                                    if (trackX < 0f || trackX > trackPx()) {
                                        relative = true
                                        pressX = x
                                        pressValue = currentValue
                                    } else {
                                        relative = false
                                        updateFromX(x)
                                    }
                                }
                                PointerEventType.Move -> {
                                    if (scrubbing) {
                                        event.changes.forEach { it.consume() }
                                        val x = event.changes.first().position.x
                                        if (relative) {
                                            val delta = (x - pressX) / trackPx() * (maxValue - minValue)
                                            onChange(snap(pressValue + delta))
                                        } else {
                                            updateFromX(x)
                                        }
                                    }
                                }
                                PointerEventType.Release -> {
                                    if (scrubbing) {
                                        scrubbing = false
                                        onCommit()
                                        OptionsUi.click()
                                    }
                                }
                                // 不再响应 Exit：按下后拖动连接持续到松开（指针滑出轨道 /
                                // 行范围也不会断开）——此前 Exit 即中断 scrubbing，
                                // 是“容易断触”的主因
                                else -> {}
                            }
                        }
                    }
                }
                .drawWithContent {
                    drawContent()
                    // 滑块：使用原游戏 Handle 素材（悬停/拖动时切换高亮版）
                    val knob = if (hovered || scrubbing) {
                        OptionsSprites["Handle_Highlighted"]
                    } else {
                        OptionsSprites["Handle_Default"]
                    }
                    if (knob != null) {
                        drawImage(
                            image = knob,
                            dstOffset = IntOffset(
                                (SLIDER_HIT_PAD + fillWidth - knob.width / 2f).roundToInt(),
                                ((size.height - knob.height) / 2f).roundToInt()
                            ),
                            dstSize = IntSize(knob.width, knob.height)
                        )
                    }
                }
        ) {
            val trackTop = (ROW_STEP - TRACK_HEIGHT) / 2f
            OptionsSprites["SliderFrame"]?.let {
                Image(
                    it, null,
                    modifier = Modifier.offset(x = SLIDER_HIT_PAD.dp, y = trackTop.dp).size(TRACK_WIDTH.dp, TRACK_HEIGHT.dp),
                    contentScale = ContentScale.FillBounds
                )
            }
            if (fillWidth < TRACK_WIDTH - 2f) {
                OptionsSprites["SliderBase"]?.let {
                    Image(
                        it, null,
                        modifier = Modifier
                            .offset(x = (SLIDER_HIT_PAD + fillWidth).dp, y = (trackTop + 2f).dp)
                            .size((TRACK_WIDTH - fillWidth).dp, (TRACK_HEIGHT - 4f).dp),
                        contentScale = ContentScale.FillBounds
                    )
                }
            }
            if (fillWidth > 1f) {
                val filler = if (hovered || scrubbing) {
                    OptionsSprites["SliderFiller_Highlighted"]
                } else {
                    OptionsSprites["SliderFiller_Default"]
                }
                filler?.let {
                    Image(
                        it, null,
                        modifier = Modifier
                            .offset(x = SLIDER_HIT_PAD.dp, y = (trackTop + 2f).dp)
                            .size(fillWidth.dp, (TRACK_HEIGHT - 4f).dp),
                        contentScale = ContentScale.FillBounds
                    )
                }
            }
        }
    }
}

/** 小按钮：复用 Toggle 底板素材（启用/停用/上下移/重置/绑定等） */
@Composable
internal fun SmallButton(
    text: String,
    width: Float,
    enabled: Boolean = true,
    textColor: Color = COLOR_TEXT,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg = when {
        !enabled -> OptionsSprites["ToggleBody_Disabled"]
        hovered -> OptionsSprites["ToggleBody_Highlighted"]
        else -> OptionsSprites["ToggleBody_Enabled"]
    }

    Box(
        modifier = Modifier
            .size(width.dp, TOGGLE_HEIGHT.dp)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                OptionsUi.click()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        bg?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
        BasicText(text, style = rowTextStyle(29f, if (enabled) textColor else COLOR_DISABLED))
    }
}

/** 动作行：标签 + 小按钮 */
@Composable
internal fun ActionRow(label: String, buttonText: String, width: Float = 420f, enabled: Boolean = true, onClick: () -> Unit) {
    RowBox(label) {
        Box(
            modifier = Modifier.offset(x = CTRL_LEFT.dp).fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            SmallButton(buttonText, width = width, enabled = enabled, onClick = onClick)
        }
    }
}

// ============================== 滚动视口 ==============================

/** 页面滚动容器：固定视口，内容顺序流式排列，右侧为细滚动条 */
@Composable
internal fun PageScroll(scrollState: ScrollState, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .offset(y = VIEW_TOP.dp)
            .fillMaxWidth()
            .height((1080f - VIEW_TOP - VIEW_BOTTOM_MARGIN).dp)
            .padding(end = (1920f - PAGE_RIGHT).dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Spacer(Modifier.height(4.dp))
            content()
            Spacer(Modifier.height(PAGE_BOTTOM_PAD.dp))
        }

        val maxV = scrollState.maxValue
        if (maxV > 0) {
            val viewport = scrollState.viewportSize.toFloat()
            val contentH = viewport + maxV
            val barH = (viewport * viewport / contentH).coerceAtLeast(90f)
            val frac = scrollState.value.toFloat() / maxV
            val scope = rememberCoroutineScope()
            // pressed：按住滚动条期间（条身隐藏、拖动带扩大）；dragging：仍在带内跟随拖动
            var pressed by remember { mutableStateOf(false) }
            var dragging by remember { mutableStateOf(false) }

            // 右侧拉动条：暗色轨道 + 亮色滑柄（原游戏素材，加宽至 12px 保证可见）。
            // 交互（TEST19 报修）：按下（命中区 24px）即隐藏条身并把可拖动横带扩大为 140px；
            // 拖动中指针左右超出该带立即断开；松开鼠标后恢复原状、重新显示条身。
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(SCROLLBAR_HIT_WIDTH.dp)
                    .fillMaxHeight()
                    .pointerInput(maxV) {
                        fun seekTo(y: Float) {
                            val travel = (viewport - barH).coerceAtLeast(1f)
                            val target = ((y - barH / 2f) / travel).coerceIn(0f, 1f) * maxV
                            scope.launch { scrollState.scrollTo(target.roundToInt()) }
                        }
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val x = event.changes.first().position.x
                                val y = event.changes.first().position.y
                                when (event.type) {
                                    PointerEventType.Press -> {
                                        pressed = true
                                        dragging = true
                                        seekTo(y)
                                    }
                                    PointerEventType.Move -> {
                                        if (dragging) {
                                            // 局部坐标下拖动带为 [命中宽-带宽, 命中宽]；左右越界即断开
                                            if (x < SCROLLBAR_HIT_WIDTH - SCROLLBAR_BAND_WIDTH || x > SCROLLBAR_HIT_WIDTH) {
                                                dragging = false
                                            } else {
                                                seekTo(y)
                                            }
                                        }
                                    }
                                    PointerEventType.Release, PointerEventType.Exit -> {
                                        pressed = false
                                        dragging = false
                                    }
                                    else -> {}
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.CenterEnd
            ) {
                // 按住期间条身整体隐藏（松开后恢复）；扩展的拖动带不绘制任何视觉
                if (!pressed) {
                    Box(Modifier.width(12.dp).fillMaxHeight()) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                        OptionsSprites["Scrollbar_Base"]?.let {
                            Image(
                                it, null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds
                            )
                        }
                        OptionsSprites["Scrollbar_Fill"]?.let {
                            Image(
                                it, null,
                                modifier = Modifier
                                    .offset(y = ((viewport - barH) * frac).dp)
                                    .width(12.dp)
                                    .height(barH.dp),
                                contentScale = ContentScale.FillBounds
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================== 迷你行组件（列表式编辑界面） ==============================

// 用于「编辑游戏规则」「实验性内容」等条目密集的列表式界面：行高压缩、
// 控件尺寸减半（开关/输入框宽 250、高 44），标签与横幅位置与设置页对齐。

internal const val MINI_ROW_HEIGHT = 64f
internal const val MINI_CTRL_WIDTH = 250f
internal const val MINI_CTRL_HEIGHT = 44f

/** 无效输入的错误红（原版 EditGameRulesScreen 的 -65536） */
internal val COLOR_ERROR = Color(0xFFFF5A5A)

/** 迷你行：固定行高 + 悬停红横幅 + 左侧标签，右侧控件由内容自铺 */
@Composable
internal fun MiniLine(
    label: String,
    labelSize: Float = 30f,
    labelColor: Color = COLOR_TEXT,
    height: Float = MINI_ROW_HEIGHT,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(Modifier.fillMaxWidth().height(height.dp).hoverable(interaction)) {
        if (hovered && !OptionsUi.popupOpen) {
            OptionsSprites["Item_Highlighted"]?.let {
                Image(
                    it, null,
                    modifier = Modifier
                        .offset(x = BANNER_LEFT.dp, y = ((height - BANNER_HEIGHT) / 2f).dp)
                        .size((BANNER_RIGHT - BANNER_LEFT).dp, BANNER_HEIGHT.dp),
                    contentScale = ContentScale.FillBounds
                )
            }
        }
        Box(
            modifier = Modifier.offset(x = LABEL_LEFT.dp).fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicText(label, style = rowTextStyle(labelSize, labelColor))
        }
        content()
    }
}

/** 迷你双段开关：右侧控件区（宽 250、高 44），左「关闭」右「开启」 */
@Composable
internal fun MiniToggle(rightSelected: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier.offset(x = CTRL_LEFT.dp).fillMaxHeight(),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.size(MINI_CTRL_WIDTH.dp, MINI_CTRL_HEIGHT.dp)) {
            MiniSegment(0f, "关闭", !rightSelected, enabled) { onChange(false) }
            MiniSegment(MINI_CTRL_WIDTH / 2f, "开启", rightSelected, enabled) { onChange(true) }
            OptionsSprites["ToggleOutline"]?.let {
                Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            }
        }
    }
}

@Composable
private fun MiniSegment(x: Float, text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val body = when {
        enabled && hovered -> OptionsSprites["ToggleBody_Highlighted"]
        selected -> OptionsSprites["ToggleBody_Enabled"]
        else -> OptionsSprites["ToggleBody_Disabled"]
    }

    Box(
        modifier = Modifier
            .offset(x = x.dp)
            .size((MINI_CTRL_WIDTH / 2f).dp, MINI_CTRL_HEIGHT.dp)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                OptionsUi.click()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        body?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
        BasicText(text, style = rowTextStyle(24f, if (enabled) COLOR_TEXT else COLOR_DISABLED))
    }
}

/** 迷你文本输入框（右侧控件区），invalid 时文字转错误红 */
@Composable
internal fun MiniInput(
    value: String,
    invalid: Boolean,
    enabled: Boolean = true,
    maxLength: Int = 12,
    onChange: (String) -> Unit
) {
    Box(
        modifier = Modifier.offset(x = CTRL_LEFT.dp).fillMaxHeight(),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier.size(MINI_CTRL_WIDTH.dp, MINI_CTRL_HEIGHT.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            OptionsSprites["ToggleBody_Disabled"]?.let {
                Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            }
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= maxLength) onChange(it) },
                enabled = enabled,
                singleLine = true,
                textStyle = rowTextStyle(24f, if (invalid) COLOR_ERROR else COLOR_TEXT),
                cursorBrush = SolidColor(COLOR_PINK_BRIGHT),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            )
        }
    }
}

// ============================== 底栏按钮 ==============================

/** 列表式界面的底栏操作按钮（完成/取消）：ButtonBase 底板 + 单色文本，disabled 时灰字且不可点 */
@Composable
internal fun FooterButton(x: Float, text: String, accentColor: Color, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val plate = OptionsSprites[if (enabled && hovered) "ButtonBase_Highlighted" else "ButtonBase_Default"]

    Box(
        modifier = Modifier
            .offset(x = x.dp, y = 945.dp)
            .size(RESET_WIDTH.dp, RESET_HEIGHT.dp)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        plate?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
        BasicText(
            text,
            style = rowTextStyle(38f, when {
                !enabled -> COLOR_DISABLED
                hovered -> COLOR_PINK_BRIGHT
                else -> accentColor
            })
        )
    }
}

// ============================== 弹层规格 ==============================

/** 通用选项弹层：标题 + 行列表（文本, 是否选中）+ 选择回调 */
internal class PopupSpec(
    val title: String,
    val rows: List<Pair<String, Boolean>>,
    val onPick: (Int) -> Unit
) {
    /** 弹层锚定行的垂直中心（设计 px），由 showPopup 打开时写入 */
    var anchorY: Float = 0f
}

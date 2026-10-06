package me.shiiyuko.manosaba.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.shiiyuko.manosaba.ManosabaMod
import me.shiiyuko.manosaba.bridge.ComposeInputBridge
import me.shiiyuko.manosaba.bridge.PackSelectionHost
import me.shiiyuko.manosaba.bridge.PackSelectionModelHost
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.packs.PackSelectionModel
import net.minecraft.client.gui.screens.packs.PackSelectionScreen
import net.minecraft.client.resources.language.I18n
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.server.packs.repository.Pack
import org.jetbrains.skia.Image as SkiaImage

/**
 * 数据包选择界面（TEST34）：原版 PackSelectionScreen 作为逻辑壳（数据包模型 /
 * 目录监听 / 完成提交），本类为内嵌 Compose 界面（不是当前屏幕），
 * 由 PackSelectionScreenMixin 创建并转发渲染与输入。
 *
 * 视觉与设置界面一致：标题屏「倒映」背景 + 磨砂底板 + 双栏列表；
 * 点击行 = 选择 / 取消选择（走原版 Entry 语义，required 包不可取消），
 * 包图标直接读取各包内 pack.png（原版走纹理注册，此处独立解码并缓存）。
 */
class ManosabaPackSelectionUi(
    private val screen: PackSelectionScreen,
    private val ghostSource: ManosabaTitleScreen?
) : ComposeScreen(Component.literal("Manosaba Pack Selection")), ComposeInputBridge.RenderTarget {

    private val mc = Minecraft.getInstance()
    private val host: PackSelectionHost get() = screen as PackSelectionHost
    private val model: PackSelectionModel get() = host.`manosaba$model`()

    private var tick by mutableStateOf(0)
    private var available by mutableStateOf<List<Pack>>(emptyList())
    private var selected by mutableStateOf<List<Pack>>(emptyList())
    private val availableScroll = ScrollState(0)
    private val selectedScroll = ScrollState(0)

    /** 包图标缓存（pack.png 原始字节解码）；iconAttempted 记录已尝试项，失败不反复 IO */
    private val iconCache = HashMap<String, ImageBitmap?>()
    private val iconAttempted = HashSet<String>()

    init {
        OptionsSprites.preload()
        OptionsUi.playClick = { playClick() }
        // 兜底重置弹层拦截标记（本屏无弹层，避免上一屏残留导致行悬停横幅不显示）
        OptionsUi.popupOpen = false
        refresh()
    }

    // ---------------- 基础设施 ----------------

    /** 从原版模型拷贝两份列表（内部为 Pack 引用，结构相等比较避免无变化重组） */
    private fun refresh() {
        val modelHost = model as PackSelectionModelHost
        available = modelHost.`manosaba$unselectedPacks`().toList()
        selected = modelHost.`manosaba$selectedPacks`().toList()
    }

    private fun bump() {
        tick += 1
    }

    /** 确认类操作音效：Sfx_System_Submit_001 */
    private fun playClick() {
        mc.soundManager.play(SimpleSoundInstance.forUI(ManosabaMod.UI_SUBMIT.get(), 1.0f))
    }

    // ---------------- 选择逻辑 ----------------

    /** 整行点击：左栏=选择（unselected→selected），右栏=取消选择（required 包不可取消） */
    private fun onPackClick(pack: Pack, selecting: Boolean) {
        val entry = (if (selecting) model.getUnselected() else model.getSelected())
            .filter { it.id == pack.id }
            .findFirst()
            .orElse(null) ?: return
        if (selecting && !entry.canSelect()) return
        if (!selecting && !entry.canUnselect()) return
        OptionsUi.click()
        if (selecting) entry.select() else entry.unselect()
        bump()
        refresh()
    }

    /** 读取包内 pack.png 并解码（一次性）；读取失败显示占位暗块 */
    private fun icon(pack: Pack): ImageBitmap? {
        val id = pack.id
        if (iconAttempted.add(id)) {
            iconCache[id] = decodeIcon(pack)
        }
        return iconCache[id]
    }

    private fun decodeIcon(pack: Pack): ImageBitmap? = try {
        pack.open().use { resources ->
            val supplier = resources.getRootResource("pack.png")
            if (supplier == null) {
                null
            } else {
                supplier.get().use { input ->
                    val bytes = input.readBytes()
                    if (bytes.isEmpty()) null
                    else SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                }
            }
        }
    } catch (t: Throwable) {
        ManosabaMod.LOGGER.warn("[Manosaba] Failed to load pack icon: ${pack.id}", t)
        null
    }

    // ---------------- 页面 ----------------

    @Composable
    override fun Content() {
        // 读取 tick（state）建立订阅：点击操作后由 bump() 驱动整页刷新
        @Suppress("UNUSED_VARIABLE") val version = tick

        // 目录变化（原版 watcher 触发的 reload）不经过本界面，靠轮询同步列表
        LaunchedEffect(Unit) {
            while (true) {
                delay(500)
                refresh()
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // 背景「倒映」：主界面同构图的幽灵层 + 磨砂底板（与创建世界界面一致）
            ghostSource?.GhostLayer(GHOST_ALPHA)
            OptionsSprites["OptionsUnderlay"]?.let { bitmap ->
                Image(
                    bitmap, null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = UNDERLAY_ALPHA },
                    contentScale = ContentScale.FillBounds
                )
            }

            BasicText(
                I18n.get("dataPack.title"),
                modifier = Modifier.offset(x = 56.dp, y = 24.dp),
                style = rowTextStyle(62f, COLOR_TAB_LABEL_ACTIVE)
            )
            BasicText(
                I18n.get("pack.dropInfo"),
                modifier = Modifier.offset(x = 56.dp, y = 114.dp),
                style = rowTextStyle(23f, COLOR_DISABLED)
            )

            PackColumn(160f, I18n.get("pack.available.title"), available, selectable = true, scroll = availableScroll)
            PackColumn(990f, I18n.get("pack.selected.title"), selected, selectable = false, scroll = selectedScroll)

            FooterButton(50f, "完成", COLOR_PINK) {
                playClick()
                // 原版完成语义：commit 提交选择并触发数据包验证（可能切到加载屏）
                screen.onClose()
            }
            FooterButton(375f, "打开包文件夹", COLOR_TEXT) {
                playClick()
                runCatching { Util.getPlatform().openPath(host.`manosaba$packDir`()) }
                    .onFailure { ManosabaMod.LOGGER.warn("[Manosaba] Failed to open pack folder", it) }
            }
        }
    }

    /** 单栏：栏标题 + 分隔线 + 可滚动行列表（宽度与栏间距按 1920 设计宽对称排布） */
    @Composable
    private fun PackColumn(x: Float, title: String, packs: List<Pack>, selectable: Boolean, scroll: ScrollState) {
        Box(
            modifier = Modifier
                .offset(x = x.dp, y = COLUMN_TOP.dp)
                .size(COLUMN_WIDTH.dp, COLUMN_HEIGHT.dp)
        ) {
            BasicText(title, style = rowTextStyle(33f, COLOR_PINK))
            Box(
                modifier = Modifier
                    .offset(y = 46.dp)
                    .width(COLUMN_WIDTH.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.13f))
            )
            Column(
                modifier = Modifier
                    .offset(y = 58.dp)
                    .fillMaxWidth()
                    .height((COLUMN_HEIGHT - 58f).dp)
                    .verticalScroll(scroll)
            ) {
                packs.forEach { pack ->
                    key(pack) { PackRow(pack, selectable) }
                }
            }
        }
    }

    /** 包行（高 64）：图标 + 标题 + 描述；悬停淡粉高亮，整行点击选择 / 取消 */
    @Composable
    private fun PackRow(pack: Pack, selectable: Boolean) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val icon = icon(pack)
        val compatible = pack.compatibility.isCompatible()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT.dp)
                .clickable(interactionSource = interaction, indication = null) { onPackClick(pack, selectable) }
        ) {
            if (hovered) {
                Box(Modifier.fillMaxSize().background(COLOR_PINK.copy(alpha = 0.16f)))
            }
            // 包图标（pack.png；缺失 / 解码失败显示占位暗块）
            Box(
                modifier = Modifier.offset(x = 6.dp, y = 10.dp).size(44.dp, 44.dp),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Image(icon, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    Box(Modifier.size(36.dp).background(Color.White.copy(alpha = 0.10f)))
                }
            }
            BasicText(
                pack.title.string,
                modifier = Modifier.offset(x = 58.dp, y = 4.dp).width(700.dp),
                style = rowTextStyle(26f),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
            BasicText(
                // 与原件一致：不兼容包描述行改用兼容性说明（红字）
                if (compatible) pack.description.string else pack.compatibility.description.string,
                modifier = Modifier.offset(x = 58.dp, y = 34.dp).width(700.dp),
                style = rowTextStyle(20f, if (compatible) COLOR_DISABLED else COLOR_ERROR),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        }
    }

    private companion object {
        private const val COLUMN_TOP = 150f
        private const val COLUMN_WIDTH = 770f
        private const val COLUMN_HEIGHT = 760f
        private const val ROW_HEIGHT = 64f
    }
}

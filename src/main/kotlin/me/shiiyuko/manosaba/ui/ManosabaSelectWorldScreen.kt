package me.shiiyuko.manosaba.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.shiiyuko.manosaba.ManosabaMod
import me.shiiyuko.manosaba.bridge.ComposeInputBridge
import me.shiiyuko.manosaba.bridge.SelectWorldHost
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.LoadingDotsText
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.world.level.storage.LevelSummary
import org.lwjgl.glfw.GLFW
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 选择世界界面（TEST37）：原版 SelectWorldScreen 作为逻辑壳（世界列表 / 搜索过滤 /
 * 子屏跳转），本类为内嵌 Compose 界面（不是当前屏幕），
 * 由 SelectWorldScreenMixin 创建并转发渲染与输入。
 *
 * 视觉与设置界面一致：标题屏「倒映」背景 + 磨砂底板 + 列表行组件 + 底部按钮；
 * 列表数据由宿主 render 注入每帧驱动（refreshData → onHostFrame），
 * 搜索输入由本界面自管状态（不依赖原版 EditBox 的焦点与按键门控）。
 *
 * 行点击交互复刻原版 WorldListEntry.mouseClicked：单击 = 选中；250ms 内再次点击
 * 或点击行左缘 32px 图标区 = 进入世界（可进入时）；编辑 / 删除 / 重建使用下方独立按钮。
 */
class ManosabaSelectWorldUi(
    private val screen: SelectWorldScreen,
    private val ghostSource: ManosabaTitleScreen?
) : ComposeScreen(Component.literal("Manosaba Select World")), ComposeInputBridge.Target {

    private val mc = Minecraft.getInstance()
    private val host: SelectWorldHost get() = screen as SelectWorldHost

    private var tick by mutableStateOf(0)
    private var confirmDelete by mutableStateOf<LevelSummary?>(null)
    private var searchText by mutableStateOf("")
    private var searchActive by mutableStateOf(false)
    private val scrollState = ScrollState(0)
    private var lastFrameSweep = 0L

    /** 双击连接检测（复刻原版 lastClickTime 语义） */
    private var lastClickEntry: WorldSelectionList.Entry? = null
    private var lastClickTime = 0L

    init {
        OptionsSprites.preload()
        OptionsUi.playClick = { playClick() }
    }

    // ---------------- 基础设施 ----------------

    private fun bump() {
        tick += 1
    }

    /** 确认类操作音效：Sfx_System_Submit_001 */
    private fun playClick() {
        mc.soundManager.play(SimpleSoundInstance.forUI(ManosabaMod.UI_SUBMIT.get(), 1.0f))
    }

    /** 取消类操作音效：Sfx_System_Cancel_001 */
    private fun playCancel() {
        mc.soundManager.play(SimpleSoundInstance.forUI(ManosabaMod.UI_CANCEL.get(), 1.0f))
    }

    /** 宿主 render 每帧驱动：数据变化立即刷新；否则 250ms 节流（加载点 / 光标动画） */
    fun onHostFrame(dataChanged: Boolean) {
        val now = Util.getMillis()
        if (dataChanged || now - lastFrameSweep >= 250L) {
            lastFrameSweep = now
            bump()
        }
    }

    /**
     * 数据变化后的主动刷新（宿主 mixin 在 init 重建后调用）：
     * 同屏 setScreen 不触发 removed、不重建 Compose 场景，编辑世界返回 / 删除世界等
     * 路径必须由宿主显式通知，重组后才能立即呈现新的世界列表。
     * 同时重绑全局点击音效（OptionsUi 为多界面共享单例）。
     */
    fun onHostDataChanged() {
        OptionsUi.playClick = { playClick() }
        bump()
    }

    /** 释放 Compose/Skia 资源并复位交互状态（宿主 removed / 删除确认时调用） */
    fun disposeUi() {
        confirmDelete = null
        searchActive = false
        disposeCompose()
    }

    /**
     * ESC 优先消费（宿主 onClose HEAD 注入转发；SelectWorldScreen 无 keyPressed
     * override，ESC 在 Screen 层直达 onClose，桥接控件收不到）：
     * 删除弹层 → 关闭弹层；搜索态 → 退出搜索。返回 true 表示已消费（宿主不关闭屏幕）。
     */
    fun handleEsc(): Boolean {
        if (confirmDelete != null) {
            playCancel()
            confirmDelete = null
            return true
        }
        if (searchActive) {
            playCancel()
            endSearch()
            return true
        }
        return false
    }

    /** 退出搜索编辑态（保留过滤词与原版搜索框内容同步） */
    private fun endSearch() {
        if (searchActive) {
            searchActive = false
            bump()
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        // 删除弹层打开时吞掉其它按键（ESC 由宿主 handleEsc 处理）
        if (confirmDelete != null) {
            return true
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && searchActive) {
            if (searchText.isNotEmpty()) {
                searchText = searchText.dropLast(1)
                host.`manosaba$updateFilter`(searchText)
            }
            bump()
            return true
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            // 原版键盘语义：回车进入选中的世界
            val summary = host.`manosaba$selectedEntry`()?.let { host.`manosaba$summaryOf`(it) }
            if (summary != null && summary.primaryActionActive()) {
                playClick()
                host.`manosaba$joinSelected`()
            }
            return true
        }
        if (keyCode == GLFW.GLFW_KEY_F5) {
            // 原版键盘语义：F5 重载世界列表
            playClick()
            host.`manosaba$reloadList`()
            bump()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (confirmDelete != null) {
            return true
        }
        // 搜索输入自管：写入原版搜索框（responder 链过滤列表），刷新由 refreshData 驱动；
        // 字符过滤等效原版 SharedConstants.isAllowedChatCharacter（控制字符与 § 除外）
        if (searchActive && !chr.isISOControl() && chr != '\u00a7' && searchText.length < SEARCH_MAX_LENGTH) {
            searchText += chr
            host.`manosaba$updateFilter`(searchText)
            bump()
        }
        return true
    }

    // ---------------- 页面 ----------------

    @Composable
    override fun Content() {
        val version = tick
        val deleting = confirmDelete

        Box(modifier = Modifier.fillMaxSize()) {
            // 背景「倒映」：主界面同构图的幽灵层 + 磨砂底板（与设置页一致）
            ghostSource?.GhostLayer(GHOST_ALPHA)
            OptionsSprites["OptionsUnderlay"]?.let { bitmap ->
                Image(
                    bitmap, null,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = UNDERLAY_ALPHA },
                    contentScale = ContentScale.FillBounds
                )
            }

            BasicText(
                "选择世界",
                modifier = Modifier.offset(x = 56.dp, y = 30.dp),
                style = rowTextStyle(62f, COLOR_TAB_LABEL_ACTIVE)
            )

            SearchBar(version)
            WorldList(version)
            BottomButtons(version)

            deleting?.let { DeleteConfirmLayer(it) }
        }
    }

    // ---------------- 搜索框 ----------------

    @Composable
    private fun SearchBar(version: Int) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val plate = OptionsSprites[
            if (searchActive || hovered) "ToggleBody_Highlighted" else "ToggleBody_Enabled"
        ]

        Box(
            modifier = Modifier
                .offset(x = SEARCH_LEFT.dp, y = SEARCH_TOP.dp)
                .size(SEARCH_WIDTH.dp, TOGGLE_HEIGHT.dp)
                .clickable(interactionSource = interaction, indication = null) {
                    if (!searchActive) {
                        playClick()
                        searchActive = true
                    }
                    bump()
                },
            contentAlignment = Alignment.CenterStart
        ) {
            plate?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            val cursor = if (searchActive && version % 2 == 0) "▎" else ""
            if (searchText.isEmpty() && !searchActive) {
                BasicText(
                    "搜索世界……",
                    modifier = Modifier.padding(start = 18.dp),
                    style = rowTextStyle(26f, COLOR_TEXT_DIM)
                )
            } else {
                BasicText(
                    searchText + cursor,
                    modifier = Modifier.padding(start = 18.dp),
                    style = rowTextStyle(26f, COLOR_TEXT),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }

    // ---------------- 世界列表 ----------------

    @Composable
    private fun WorldList(version: Int) {
        val entries = host.`manosaba$entries`()
        val selected = host.`manosaba$selectedEntry`()

        PageScroll(scrollState) {
            entries.forEach { entry ->
                key(entry) {
                    WorldRow(entry, entry === selected, version) { e, x -> onRowClick(e, x) }
                }
            }
        }
    }

    /**
     * 行点击状态机（复刻原版）：单击 = 仅选中（选择框语义，编辑/删除据此可用）；
     * 250ms 内再次点击同一行、或点击行左缘 32px 图标区 = 进入世界（可进入时）。
     */
    private fun onRowClick(entry: WorldSelectionList.Entry, x: Float) {
        if (entry is WorldSelectionList.LoadingHeader) return
        endSearch()
        host.`manosaba$select`(entry)
        val summary = host.`manosaba$summaryOf`(entry) ?: return
        if (!summary.primaryActionActive()) {
            bump()
            return
        }
        val now = Util.getMillis()
        if (x <= ICON_CLICK_WIDTH || (entry === lastClickEntry && now - lastClickTime < DOUBLE_CLICK_MS)) {
            lastClickEntry = null
            playClick()
            host.`manosaba$joinSelected`()
        } else {
            lastClickEntry = entry
            lastClickTime = now
            bump()
        }
    }

    @Composable
    private fun WorldRow(
        entry: WorldSelectionList.Entry,
        selected: Boolean,
        version: Int,
        onClick: (WorldSelectionList.Entry, Float) -> Unit
    ) {
        @Suppress("UNUSED_VARIABLE") val v = version
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT.dp)
                .hoverable(interaction)
                .pointerInput(entry) {
                    detectTapGestures { offset -> onClick(entry, offset.x) }
                }
        ) {
            // 选中定格红横幅（与设置页行悬停同款长红笔刷素材）；未选中悬停时轻微提亮
            if (selected) {
                OptionsSprites["Item_Highlighted"]?.let {
                    Image(
                        it, null,
                        modifier = Modifier
                            .offset(x = BANNER_LEFT.dp, y = ((ROW_HEIGHT - BANNER_HEIGHT) / 2f).dp)
                            .size((BANNER_RIGHT - BANNER_LEFT).dp, BANNER_HEIGHT.dp),
                        contentScale = ContentScale.FillBounds
                    )
                }
            } else if (hovered) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.06f)))
            }

            when (entry) {
                is WorldSelectionList.LoadingHeader -> LoadingHeaderContent(version)
                is WorldSelectionList.WorldListEntry -> WorldEntryContent(entry)
                else -> {}
            }
        }
    }

    /** 世界列表异步扫描完成前的加载占位行（原版 LoadingHeader 的 UI 版） */
    @Composable
    private fun LoadingHeaderContent(version: Int) {
        @Suppress("UNUSED_VARIABLE") val v = version
        BasicText(
            Component.translatable("selectWorld.loading_list").string + LoadingDotsText.get(Util.getMillis()),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            style = rowTextStyle(30f, COLOR_TEXT_DIM).copy(textAlign = TextAlign.Center)
        )
    }

    @Composable
    private fun WorldEntryContent(entry: WorldSelectionList.WorldListEntry) {
        val summary = host.`manosaba$summaryOf`(entry)

        // 世界图标原版存于 GL 纹理（FaviconTexture），Compose 侧不可解码：以暗色占位块呈现
        Box(Modifier.offset(x = 20.dp, y = 12.dp).size(44.dp)) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
        }

        BasicText(
            summary?.levelName ?: "",
            modifier = Modifier.offset(x = 76.dp, y = 6.dp).width(940.dp),
            style = rowTextStyle(30f),
            maxLines = 1,
            softWrap = false
        )

        val lastPlayed = summary?.lastPlayed ?: -1L
        val subtitle = buildString {
            append(summary?.levelId ?: "")
            if (lastPlayed != -1L) {
                append("（")
                append(DATE_FORMAT.format(Instant.ofEpochMilli(lastPlayed).atZone(ZoneId.systemDefault())))
                append("）")
            }
        }
        BasicText(
            subtitle,
            modifier = Modifier.offset(x = 76.dp, y = 40.dp).width(940.dp),
            style = rowTextStyle(24f, COLOR_TEXT_DIM),
            maxLines = 1,
            softWrap = false
        )

        BasicText(
            summary?.info?.string ?: "",
            modifier = Modifier.offset(x = 1040.dp, y = 20.dp).width(570.dp),
            style = rowTextStyle(27f, COLOR_TEXT_DIM).copy(textAlign = TextAlign.Right),
            maxLines = 1,
            softWrap = false
        )
    }

    // ---------------- 底部按钮 ----------------

    @Composable
    private fun BottomButtons(version: Int) {
        @Suppress("UNUSED_VARIABLE") val v = version
        val selected = host.`manosaba$selectedEntry`()
        val summary = selected?.let { host.`manosaba$summaryOf`(it) }
        val canJoin = summary != null && summary.primaryActionActive()
        val canEdit = summary != null && summary.canEdit()
        val canDelete = summary != null && summary.canDelete()
        val canRecreate = summary != null && summary.canRecreate()

        // 第一行：进入选中的世界 / 创建新的世界（2 列等宽等距：w=471、gap=24，外缘 477→1443）
        RowButton(
            477f, 945f, 471f,
            summary?.primaryActionMessage()?.string ?: "进入选中的世界",
            enabled = canJoin, accent = true
        ) {
            endSearch()
            host.`manosaba$joinSelected`()
        }
        RowButton(972f, 945f, 471f, "创建新的世界") {
            endSearch()
            host.`manosaba$createWorld`()
        }

        // 第二行：编辑 / 删除 / 重建 / 返回（4 列等宽等距：w=228、gap=18，外缘与第一行完全对齐）
        RowButton(477f, 1010f, 228f, "编辑", enabled = canEdit) {
            endSearch()
            host.`manosaba$editSelected`()
        }
        RowButton(723f, 1010f, 228f, "删除", enabled = canDelete, accent = true) {
            endSearch()
            confirmDelete = summary
        }
        RowButton(969f, 1010f, 228f, "重建", enabled = canRecreate) {
            endSearch()
            host.`manosaba$recreateSelected`()
        }
        RowButton(1215f, 1010f, 228f, "返回", cancelSound = true) {
            endSearch()
            screen.onClose()
        }
    }

    /** 底部行按钮：三态底板素材（与设置页 SmallButton 同款），支持取消音效与强调配色 */
    @Composable
    private fun RowButton(
        x: Float,
        y: Float,
        width: Float,
        text: String,
        enabled: Boolean = true,
        accent: Boolean = false,
        cancelSound: Boolean = false,
        onClick: () -> Unit
    ) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val plate = when {
            !enabled -> OptionsSprites["ToggleBody_Disabled"]
            hovered -> OptionsSprites["ToggleBody_Highlighted"]
            else -> OptionsSprites["ToggleBody_Enabled"]
        }

        Box(
            modifier = Modifier
                .offset(x = x.dp, y = y.dp)
                .size(width.dp, TOGGLE_HEIGHT.dp)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                    if (cancelSound) playCancel() else playClick()
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            plate?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            val color = when {
                !enabled -> COLOR_DISABLED
                accent && hovered -> COLOR_PINK_BRIGHT
                accent -> COLOR_PINK
                else -> COLOR_TEXT
            }
            BasicText(text, style = rowTextStyle(29f, color))
        }
    }

    // ---------------- 删除确认弹层 ----------------

    @Composable
    private fun DeleteConfirmLayer(summary: LevelSummary) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
            // 全屏点击层：点击弹层以外任意处取消
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        playCancel()
                        confirmDelete = null
                    }
            )
            Box(Modifier.align(Alignment.Center).size(880.dp, 360.dp)) {
                OptionsSprites["DialogBase"]?.let {
                    Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                }
                BasicText(
                    "确定要删除世界\n「${summary.levelName}」吗？",
                    modifier = Modifier.align(Alignment.Center).offset(y = (-40).dp),
                    style = dialogTextStyle(38f, COLOR_DIALOG_TEXT).copy(textAlign = TextAlign.Center)
                )
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-42).dp),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DialogActionButton("取消", cancelSound = true) {
                        confirmDelete = null
                    }
                    DialogActionButton("删除", danger = true) {
                        confirmDelete = null
                        // 先释放 Compose 场景再走原版删除（doDeleteWorld 触发异步重载）：
                        // 释放后回屏下一帧自动重建并读取新列表，保证删除结果立即呈现
                        disposeUi()
                        host.`manosaba$deleteSelected`()
                    }
                }
            }
        }
    }

    @Composable
    private fun DialogActionButton(
        text: String,
        danger: Boolean = false,
        cancelSound: Boolean = false,
        onClick: () -> Unit
    ) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val plate = OptionsSprites[if (hovered) "ToggleBody_Highlighted" else "ToggleBody_Enabled"]

        Box(
            modifier = Modifier
                .size(230.dp, TOGGLE_HEIGHT.dp)
                .clickable(interactionSource = interaction, indication = null) {
                    if (cancelSound) playCancel() else playClick()
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            plate?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            val color = when {
                danger && hovered -> COLOR_PINK_BRIGHT
                danger -> COLOR_PINK
                else -> COLOR_TEXT
            }
            BasicText(text, style = rowTextStyle(30f, color))
        }
    }

    private companion object {
        const val ROW_HEIGHT = 68f
        const val SEARCH_LEFT = 1250f
        const val SEARCH_TOP = 48f
        const val SEARCH_WIDTH = 370f
        const val SEARCH_MAX_LENGTH = 32
        const val DOUBLE_CLICK_MS = 250L
        const val ICON_CLICK_WIDTH = 32f

        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}

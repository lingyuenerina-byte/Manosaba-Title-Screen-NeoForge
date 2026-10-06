package me.shiiyuko.manosaba.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.shiiyuko.manosaba.ManosabaMod
import me.shiiyuko.manosaba.bridge.ComposeInputBridge
import me.shiiyuko.manosaba.bridge.ModListHost
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.util.StringUtil
import net.neoforged.fml.ModList
import net.neoforged.fml.i18n.MavenVersionTranslator
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import net.neoforged.neoforge.client.gui.ModListScreen
import org.lwjgl.glfw.GLFW

/**
 * 「模组列表」界面（TEST56）：NeoForge ModListScreen 作为逻辑壳（模组数据 /
 * 选中 / 配置入口 / 返回行为），本类为内嵌 Compose 界面（不是当前屏幕），
 * 由 ModListScreenMixin 创建并转发渲染与输入。
 *
 * 视觉与统计信息界面同源：标题 + 顶部行（搜索标签 / 输入框 / 排序三枚）+
 * 滚动列表（名称左、版本右，悬停与选中红横幅）+ 底栏三枚按钮
 * （配置 / 打开模组文件夹 / 完成）。
 *
 * 列表数据直接读取 ModList.get().getSortedMods() 并复刻原版过滤（名称小写
 * 包含匹配）与排序（默认 / A→Z / Z→A）；选中经 modId 映射回原版
 * ModListWidget.ModEntry（host.selectMod），保证「配置」按钮读到正确选中。
 * 打开配置屏属于子屏往返：界面资源保留（返回本屏 init 重跑仅重建桥接）。
 */
class ManosabaModListUi(
    private val screen: ModListScreen
) : ComposeScreen(Component.literal("Manosaba Mod List")), ComposeInputBridge.Target {

    private val mc = Minecraft.getInstance()
    private val host: ModListHost get() = screen as ModListHost

    /** 游戏内子屏：保留暂停中的游戏画面并整屏暗化（与暂停菜单一致） */
    override val backgroundMode: BackgroundMode get() = BackgroundMode.GAME_DIM

    private var tick by mutableStateOf(0)
    private var query by mutableStateOf("")
    private var sortType by mutableStateOf(SORT_NORMAL)
    private var selectedId by mutableStateOf<String?>(null)
    private var lastFrameSweep = 0L

    private var allCache: List<ModRow>? = null

    private val scrollState = ScrollState(0)

    init {
        OptionsSprites.preload()
        OptionsUi.playClick = { playClick() }
    }

    private fun bump() {
        tick += 1
    }

    /** 确认类操作音效：Sfx_System_Submit_001 */
    private fun playClick() {
        mc.soundManager.play(SimpleSoundInstance.forUI(ManosabaMod.UI_SUBMIT.get(), 1.0f))
    }

    /** 宿主 render 每帧驱动：250ms 节流刷新（输入光标闪烁） */
    fun onHostFrame() {
        val now = Util.getMillis()
        if (now - lastFrameSweep >= 250L) {
            lastFrameSweep = now
            bump()
        }
    }

    /** 释放 Compose/Skia 资源（宿主 onClose / 完成路径调用） */
    fun disposeUi() {
        disposeCompose()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (query.isNotEmpty()) {
                query = query.dropLast(1)
            }
            bump()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        // 搜索关键词：过滤控制字符与格式码符号，长度上限 64
        if (!chr.isISOControl() && chr != '§' && query.length < QUERY_MAX_LENGTH) {
            query += chr
            bump()
        }
        return true
    }

    // ---------------- 页面 ----------------

    @Composable
    override fun Content() {
        val version = tick
        Box(modifier = Modifier.fillMaxSize()) {
            BasicText(
                Component.translatable("fml.menu.mods.title").string,
                modifier = Modifier.offset(x = 56.dp, y = 30.dp),
                style = rowTextStyle(62f, COLOR_TAB_LABEL_ACTIVE)
            )

            // 搜索：标签 + 输入框（顶部行，与右侧排序按钮同排）
            BasicText(
                Component.translatable("fml.menu.mods.search").string,
                modifier = Modifier.offset(x = 620.dp, y = 56.dp),
                style = rowTextStyle(30f)
            )
            SearchInput(version)

            // 排序三枚（默认 / A→Z / Z→A），选中态粉字常亮；与复位按钮同款底板
            SortButton("fml.menu.mods.normal", SORT_NORMAL, 1130f)
            SortButton("fml.menu.mods.a_to_z", SORT_AZ, 1305f)
            SortButton("fml.menu.mods.z_to_a", SORT_ZA, 1480f)

            ModListBody()

            // 底栏：配置（需选中且模组提供配置屏）/ 打开模组文件夹 / 完成
            FooterButton(
                350f,
                Component.translatable("fml.menu.mods.config").string,
                COLOR_TEXT,
                enabled = selectedConfigurable()
            ) {
                playClick()
                host.`manosaba$openConfig`()
            }
            FooterButton(807f, Component.translatable("fml.menu.mods.openmodsfolder").string, COLOR_TEXT) {
                playClick()
                host.`manosaba$openFolder`()
            }
            FooterButton(1264f, Component.translatable("gui.done").string, COLOR_PINK) {
                playClick()
                disposeCompose()
                host.`manosaba$done`()
            }
        }
    }

    /** 模组滚动列表：过滤 / 排序结果直接组合（query 与 sortType 的读取自动建立重组订阅） */
    @Composable
    private fun ModListBody() {
        val rows = filteredRows()
        PageScroll(scrollState) {
            rows.forEach { row -> ModRowLine(row) }
        }
    }

    // ---------------- 顶部控件 ----------------

    /** 搜索框：自管关键词 + ▎光标（空文本时仅光标闪烁，标签显示在框左侧） */
    @Composable
    private fun SearchInput(version: Int) {
        val interaction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .offset(x = 700.dp, y = 44.dp)
                .size(400.dp, TOGGLE_HEIGHT.dp)
                .clickable(interactionSource = interaction, indication = null) { bump() },
            contentAlignment = Alignment.CenterStart
        ) {
            OptionsSprites["ToggleBody_Enabled"]?.let {
                Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            }
            val cursor = if (version % 2 == 0) "▎" else ""
            BasicText(
                query + cursor,
                modifier = Modifier.padding(start = 22.dp),
                style = rowTextStyle(28f),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    /** 排序按钮：选中粉字常亮（Enabled 底板），悬停高亮；切换后列表滚回顶部 */
    @Composable
    private fun SortButton(textKey: String, id: Int, x: Float) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val selected = sortType == id
        val plate = OptionsSprites[when {
            hovered -> "ToggleBody_Highlighted"
            selected -> "ToggleBody_Enabled"
            else -> "ToggleBody_Disabled"
        }]
        val scope = rememberCoroutineScope()

        Box(
            modifier = Modifier
                .offset(x = x.dp, y = 44.dp)
                .size(165.dp, TOGGLE_HEIGHT.dp)
                .clickable(interactionSource = interaction, indication = null) {
                    if (sortType != id) {
                        playClick()
                        sortType = id
                        scope.launch { scrollState.scrollTo(0) }
                        bump()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            plate?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            BasicText(
                Component.translatable(textKey).string,
                style = rowTextStyle(28f, if (selected) COLOR_PINK else COLOR_TEXT)
            )
        }
    }

    // ---------------- 列表行 ----------------

    /** 模组行：名称（左）+ 版本（右），悬停 / 选中红横幅；点击选中（联动原版 selected） */
    @Composable
    private fun ModRowLine(row: ModRow) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val selected = selectedId == row.modId

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT.dp)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null) {
                    playClick()
                    selectedId = row.modId
                    host.`manosaba$selectMod`(row.modId)
                    bump()
                }
        ) {
            if ((hovered || selected) && !OptionsUi.popupOpen) {
                OptionsSprites["Item_Highlighted"]?.let {
                    Image(
                        it, null,
                        modifier = Modifier
                            .offset(x = BANNER_LEFT.dp, y = ((ROW_HEIGHT - BANNER_HEIGHT) / 2f).dp)
                            .size((BANNER_RIGHT - BANNER_LEFT).dp, BANNER_HEIGHT.dp),
                        contentScale = ContentScale.FillBounds
                    )
                }
            }
            Box(
                modifier = Modifier.offset(x = LABEL_LEFT.dp).width(820.dp).fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicText(
                    row.name,
                    style = rowTextStyle(28f, if (selected) COLOR_PINK else COLOR_TEXT),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier.offset(x = 1020.dp).width(620.dp).fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd
            ) {
                BasicText(
                    row.version,
                    style = rowTextStyle(26f, COLOR_TEXT_DIM).copy(textAlign = TextAlign.End),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }

    // ---------------- 数据构建（复刻原版过滤 / 排序 / 条目文本） ----------------

    /** 全部模组（名称 + artifact 版本 + 配置可用性），一次缓存 */
    private fun allRows(): List<ModRow> {
        allCache?.let { return it }
        // getSortedMods() 返回 ModContainer 流（原版构造器的 mods 字段同源），条目信息经 getModInfo()
        val rows = ModList.get().sortedMods.map { container ->
            val info = container.modInfo
            ModRow(
                modId = info.modId,
                name = StringUtil.stripColor(info.displayName),
                version = StringUtil.stripColor(MavenVersionTranslator.artifactVersionToString(info.version)),
                configurable = IConfigScreenFactory.getForMod(info).isPresent
            )
        }
        return rows.also { allCache = it }
    }

    /** 过滤（名称小写包含）+ 排序（复刻原版 SortType：A→Z / Z→A 按小写名称比较） */
    private fun filteredRows(): List<ModRow> {
        val q = query.lowercase()
        val base = if (q.isEmpty()) allRows() else allRows().filter { it.name.lowercase().contains(q) }
        return when (sortType) {
            SORT_AZ -> base.sortedBy { it.name.lowercase() }
            SORT_ZA -> base.sortedByDescending { it.name.lowercase() }
            else -> base
        }
    }

    /** 当前选中的模组是否提供配置屏（决定「配置」按钮可用性） */
    private fun selectedConfigurable(): Boolean {
        val id = selectedId ?: return false
        return allRows().any { it.modId == id && it.configurable }
    }

    private data class ModRow(val modId: String, val name: String, val version: String, val configurable: Boolean)

    private companion object {
        const val SORT_NORMAL = 0
        const val SORT_AZ = 1
        const val SORT_ZA = 2

        /** 列表行高（单行：名称 + 版本） */
        const val ROW_HEIGHT = 56f

        /** 搜索关键词长度上限 */
        const val QUERY_MAX_LENGTH = 64
    }
}

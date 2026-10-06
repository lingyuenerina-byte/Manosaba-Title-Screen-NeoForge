package me.shiiyuko.manosaba.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.shiiyuko.manosaba.ManosabaMod
import me.shiiyuko.manosaba.bridge.ComposeInputBridge
import me.shiiyuko.manosaba.bridge.StatsHost
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.achievement.StatsScreen
import net.minecraft.client.resources.language.I18n
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.stats.StatType
import net.minecraft.stats.Stats
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block

/**
 * 「统计信息」界面（TEST55）：原版 StatsScreen 作为逻辑壳（统计请求 / 数据回包 /
 * 返回行为），本类为内嵌 Compose 界面（不是当前屏幕），由 StatsScreenMixin 创建
 * 并经 RenderTarget 转发渲染与输入（目标类无 render override）。
 *
 * 视觉与设置界面一致：标题 + 三枚页签（通用 / 物品 / 生物）+ 滚动列表 + 完成；
 * 数据自行遍历 Stats.CUSTOM / 物品 / 生物统计，复刻原版三列表文本
 * （物品页不渲染 3D 物品图标，行首为物品名 + 六列数值）。
 *
 * 下载中（isLoading）显示等待提示；数据回包（onStatsUpdated）时由宿主调用
 * onHostDataChanged 作废缓存并刷新（物品 / 生物页无记录时页签禁用）。
 */
class ManosabaStatsUi(
    private val screen: StatsScreen
) : ComposeScreen(Component.literal("Manosaba Stats")), ComposeInputBridge.RenderTarget {

    private val mc = Minecraft.getInstance()
    private val host: StatsHost get() = screen as StatsHost

    /** 游戏内子屏：保留暂停中的游戏画面并整屏暗化（与暂停菜单一致） */
    override val backgroundMode: BackgroundMode get() = BackgroundMode.GAME_DIM

    private var tick by mutableStateOf(0)
    private var tab by mutableStateOf(TAB_GENERAL)
    private var lastFrameSweep = 0L

    private var generalCache: List<Pair<String, String>>? = null
    private var itemCache: List<ItemRowData>? = null
    private var mobCache: List<MobRowData>? = null

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

    /** 宿主 render 每帧驱动：250ms 节流刷新（加载状态 / 数据就绪后的界面切换） */
    fun onHostFrame() {
        val now = Util.getMillis()
        if (now - lastFrameSweep >= 250L) {
            lastFrameSweep = now
            bump()
        }
    }

    /** 数据回包（onStatsUpdated）时由宿主调用：作废旧数据并刷新 */
    fun onHostDataChanged() {
        generalCache = null
        itemCache = null
        mobCache = null
        bump()
    }

    /** 释放 Compose/Skia 资源（宿主 onClose / 完成路径调用） */
    fun disposeUi() {
        disposeCompose()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // RenderTarget 模式无 render 注入点：每帧拉回桥接控件焦点（Tab 键会切走）
        host.`manosaba$refocus`()
        onHostFrame()
        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    // ---------------- 页面 ----------------

    @Composable
    override fun Content() {
        val version = tick
        val loading = host.`manosaba$isLoading`()

        Box(modifier = Modifier.fillMaxSize()) {
            BasicText(
                Component.translatable("gui.stats").string,
                modifier = Modifier.offset(x = 56.dp, y = 30.dp),
                style = rowTextStyle(62f, COLOR_TAB_LABEL_ACTIVE)
            )

            // 页签：通用 / 物品 / 生物（下载中禁用；物品与生物无记录时禁用）
            TabButton("stat.generalButton", TAB_GENERAL, 830f, !loading)
            TabButton("stat.itemsButton", TAB_ITEMS, 1110f, !loading && itemRows().isNotEmpty())
            TabButton("stat.mobsButton", TAB_MOBS, 1390f, !loading && mobRows().isNotEmpty())

            if (loading) {
                BasicText(
                    Component.translatable("multiplayer.downloadingStats").string,
                    modifier = Modifier.align(Alignment.Center),
                    style = rowTextStyle(40f, COLOR_TEXT_DIM)
                )
            } else {
                StatsList(version)
            }

            FooterButton(807f, Component.translatable("gui.done").string, COLOR_PINK) {
                playClick()
                disposeCompose()
                host.`manosaba$done`()
            }
        }
    }

    @Composable
    private fun StatsList(version: Int) {
        @Suppress("UNUSED_VARIABLE") val v = version
        PageScroll(scrollState) {
            when (tab) {
                TAB_ITEMS -> {
                    ItemHeaderRow()
                    itemRows().forEach { row -> ItemRowLine(row) }
                }
                TAB_MOBS -> {
                    mobRows().forEach { row -> MobRowLine(row) }
                }
                else -> {
                    generalRows().forEach { (label, value) -> GeneralRowLine(label, value) }
                }
            }
        }
    }

    @Composable
    private fun TabButton(textKey: String, id: Int, x: Float, enabled: Boolean) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val active = tab == id
        val plate = OptionsSprites[when {
            !enabled -> "ToggleBody_Disabled"
            hovered -> "ToggleBody_Highlighted"
            active -> "ToggleBody_Enabled"
            else -> "ToggleBody_Disabled"
        }]
        val scope = rememberCoroutineScope()

        Box(
            modifier = Modifier
                .offset(x = x.dp, y = 82.dp)
                .size(260.dp, TOGGLE_HEIGHT.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.55f }
                .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                    playClick()
                    tab = id
                    scope.launch { scrollState.scrollTo(0) }
                    bump()
                },
            contentAlignment = Alignment.Center
        ) {
            plate?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            val color = when {
                !enabled -> COLOR_DISABLED
                active -> COLOR_PINK
                else -> COLOR_TEXT
            }
            BasicText(Component.translatable(textKey).string, style = rowTextStyle(29f, color))
        }
    }

    // ---------------- 列表行 ----------------

    @Composable
    private fun GeneralRowLine(label: String, value: String) {
        RowHoverBox { hovered ->
            if (hovered) {
                RowBanner(56f)
            }
            Box(
                modifier = Modifier.offset(x = LABEL_LEFT.dp).width(760.dp).fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicText(label, style = rowTextStyle(28f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
            Box(
                modifier = Modifier.offset(x = 1020.dp).width(620.dp).fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd
            ) {
                BasicText(value, style = rowTextStyle(28f, COLOR_TEXT_DIM).copy(textAlign = TextAlign.End))
            }
        }
    }

    @Composable
    private fun ItemHeaderRow() {
        Box(Modifier.fillMaxWidth().height(52.dp)) {
            Box(
                modifier = Modifier.offset(x = LABEL_LEFT.dp).fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicText(Component.translatable("stat.itemsButton").string, style = rowTextStyle(26f, COLOR_PINK))
            }
            COLUMN_NAMES.forEachIndexed { index, name ->
                val colRight = columnRight(index)
                Box(
                    modifier = Modifier.offset(x = (colRight - 120f).dp).width(120.dp).fillMaxHeight(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    BasicText(
                        name,
                        style = rowTextStyle(20f, COLOR_TEXT_DIM).copy(textAlign = TextAlign.End),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    @Composable
    private fun ItemRowLine(row: ItemRowData) {
        RowHoverBox { hovered ->
            if (hovered) {
                RowBanner(56f)
            }
            Box(
                modifier = Modifier.offset(x = LABEL_LEFT.dp).width(600.dp).fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicText(row.name, style = rowTextStyle(28f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
            row.values.forEachIndexed { index, value ->
                val colRight = columnRight(index)
                Box(
                    modifier = Modifier.offset(x = (colRight - 110f).dp).width(110.dp).fillMaxHeight(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    BasicText(value, style = rowTextStyle(26f, COLOR_TEXT_DIM).copy(textAlign = TextAlign.End), maxLines = 1, softWrap = false)
                }
            }
        }
    }

    @Composable
    private fun MobRowLine(row: MobRowData) {
        RowHoverBox(height = 96f) { hovered ->
            if (hovered) {
                RowBanner(96f)
            }
            Box(
                modifier = Modifier.offset(x = LABEL_LEFT.dp).width(500.dp).fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicText(row.name, style = rowTextStyle(30f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
            Column(
                modifier = Modifier.offset(x = 620.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                BasicText(
                    row.kills,
                    style = rowTextStyle(25f, if (row.hasKills) COLOR_TEXT_DIM else COLOR_DISABLED),
                    maxLines = 1,
                    softWrap = false
                )
                BasicText(
                    row.killedBy,
                    style = rowTextStyle(25f, if (row.wasKilledBy) COLOR_TEXT_DIM else COLOR_DISABLED),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }

    /** 行容器：固定行高 + 悬停检测（红横幅由各行的 hovered 分支渲染） */
    @Composable
    private fun RowHoverBox(height: Float = 56f, content: @Composable (Boolean) -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        Box(Modifier.fillMaxWidth().height(height.dp).hoverable(interaction)) {
            content(hovered)
        }
    }

    /** 行悬停红横幅（复用设置页 Item_Highlighted 素材，与其它列表行同款） */
    @Composable
    private fun RowBanner(height: Float) {
        if (OptionsUi.popupOpen) {
            return
        }
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

    // ---------------- 数据构建（复刻原版三列表） ----------------

    /** 通用统计：Stats.CUSTOM 按翻译名排序，标签 + 值文本（复刻 GeneralStatisticsList.Entry） */
    private fun generalRows(): List<Pair<String, String>> {
        generalCache?.let { return it }
        val counter = host.`manosaba$stats`()
        val rows = ArrayList<Pair<String, String>>()
        for (stat in Stats.CUSTOM) {
            val key = "stat." + stat.value.toString().replace(':', '.')
            rows += I18n.get(key) to stat.format(counter.getValue(stat))
        }
        return rows.sortedBy { it.first }.also { generalCache = it }
    }

    /** 物品统计：复刻 ItemStatisticsList（六列：开采 / 破坏 / 合成 / 使用 / 拾取 / 丢弃） */
    private fun itemRows(): List<ItemRowData> {
        itemCache?.let { return it }
        val counter = host.`manosaba$stats`()
        val noValue = Component.translatable("stats.none").string
        val items = LinkedHashSet<Item>()
        for (item in BuiltInRegistries.ITEM) {
            if (ITEM_COLUMNS.any { it.contains(item) && counter.getValue(it.get(item)) > 0 }) {
                items += item
            }
        }
        for (block in BuiltInRegistries.BLOCK) {
            if (BLOCK_COLUMNS.any { it.contains(block) && counter.getValue(it.get(block)) > 0 }) {
                items += block.asItem()
            }
        }
        items.remove(Items.AIR)
        val rows = items.map { item ->
            val values = ArrayList<String>(6)
            val blockItem = item as? BlockItem
            for (column in BLOCK_COLUMNS) {
                val stat = blockItem?.let { column.get(it.block) }
                values += if (stat == null) noValue else stat.format(counter.getValue(stat))
            }
            for (column in ITEM_COLUMNS) {
                val stat = column.get(item)
                values += stat.format(counter.getValue(stat))
            }
            ItemRowData(item.description.string, values)
        }
        return rows.also { itemCache = it }
    }

    /** 生物统计：复刻 MobsStatisticsList（击杀 / 被击杀文本行） */
    private fun mobRows(): List<MobRowData> {
        mobCache?.let { return it }
        val counter = host.`manosaba$stats`()
        val rows = ArrayList<MobRowData>()
        for (type in BuiltInRegistries.ENTITY_TYPE) {
            val kills = counter.getValue(Stats.ENTITY_KILLED.get(type))
            val killedBy = counter.getValue(Stats.ENTITY_KILLED_BY.get(type))
            if (kills == 0 && killedBy == 0) {
                continue
            }
            val name = type.description.string
            rows += MobRowData(
                name = name,
                kills = if (kills == 0) {
                    Component.translatable("stat_type.minecraft.killed.none", name).string
                } else {
                    Component.translatable("stat_type.minecraft.killed", kills, name).string
                },
                hasKills = kills > 0,
                killedBy = if (killedBy == 0) {
                    Component.translatable("stat_type.minecraft.killed_by.none", name).string
                } else {
                    Component.translatable("stat_type.minecraft.killed_by", name, killedBy).string
                },
                wasKilledBy = killedBy > 0
            )
        }
        return rows.also { mobCache = it }
    }

    private data class ItemRowData(val name: String, val values: List<String>)

    private data class MobRowData(
        val name: String,
        val kills: String,
        val hasKills: Boolean,
        val killedBy: String,
        val wasKilledBy: Boolean
    )

    private companion object {
        const val TAB_GENERAL = 0
        const val TAB_ITEMS = 1
        const val TAB_MOBS = 2

        val BLOCK_COLUMNS: List<StatType<Block>> = listOf(Stats.BLOCK_MINED)
        val ITEM_COLUMNS: List<StatType<Item>> = listOf(
            Stats.ITEM_BROKEN, Stats.ITEM_CRAFTED, Stats.ITEM_USED, Stats.ITEM_PICKED_UP, Stats.ITEM_DROPPED
        )

        /** 六列列头（复刻原版 iconSprites 顺序的列名） */
        val COLUMN_NAMES: List<String> by lazy { (BLOCK_COLUMNS + ITEM_COLUMNS).map { it.displayName.string } }

        /** 第 index 列数值的右对齐基准 x（六列均布于 900..1650 内容区） */
        fun columnRight(index: Int): Float = 900f + 125f * (index + 1)
    }
}

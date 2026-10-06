package me.shiiyuko.manosaba.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.shiiyuko.manosaba.ManosabaMod
import me.shiiyuko.manosaba.bridge.ComposeInputBridge
import me.shiiyuko.manosaba.bridge.CreateWorldHost
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.world.Difficulty
import org.lwjgl.glfw.GLFW

/**
 * 创建新世界界面（TEST23-25）：原版 CreateWorldScreen 作为逻辑壳（持有世界生成数据、
 * 提供打开子屏/创建世界等逻辑），本类为内嵌 Compose 界面（不是当前屏幕），
 * 由 CreateWorldScreenMixin 在 init 时创建并转发渲染与输入。
 *
 * 视觉与设置界面完全一致：标题屏「倒映」背景 + 磨砂底板 + 右侧竖排页签 +
 * 同款行组件（RowBox / ToggleRow / SelectRow / SmallButton）与选择弹层。
 */
class ManosabaCreateWorldUi(
    private val screen: CreateWorldScreen,
    private val ghostSource: ManosabaTitleScreen?
) : ComposeScreen(Component.literal("Manosaba Create World")), ComposeInputBridge.Target {

    private val mc = Minecraft.getInstance()
    private val uiState: WorldCreationUiState get() = screen.uiState
    private val host: CreateWorldHost get() = screen as CreateWorldHost

    private var tick by mutableStateOf(0)
    private var currentTab by mutableStateOf(0)
    private var popup by mutableStateOf<SelectPopup?>(null)

    private val scrollStates = List(TAB_LABELS.size) { ScrollState(0) }

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

    private fun showPopup(spec: SelectPopup) {
        spec.anchorY = OptionsUi.anchorY
        OptionsUi.popupOpen = true
        popup = spec
    }

    private fun closePopup() {
        OptionsUi.popupOpen = false
        popup = null
    }

    private fun selectTab(index: Int) {
        if (index == currentTab) return
        closePopup()
        currentTab = index
        playClick()
        bump()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        // 弹层打开时 Esc 只关闭弹层；其余情况 ESC 交还宿主（原版 onClose → 返回上一屏）
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && popup != null) {
            playCancel()
            closePopup()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    // ---------------- 页面调度 ----------------

    @Composable
    override fun Content() {
        @Suppress("UNUSED_VARIABLE") val version = tick

        LaunchedEffect(Unit) {
            while (true) {
                delay(500)
                bump()
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // 背景「倒映」：主界面同构图的幽灵层（同方向、暗化），上层为磨砂底板
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
                "创建新世界",
                modifier = Modifier.offset(x = 56.dp, y = 30.dp),
                style = rowTextStyle(62f, COLOR_TAB_LABEL_ACTIVE)
            )

            CreateTabColumn()
            CreatePageHost()
            BottomButtons()

            popup?.let { SelectPopupLayer(it) }
        }
    }

    @Composable
    private fun CreatePageHost() {
        when (currentTab) {
            0 -> PageScroll(scrollStates[0]) { GamePage() }
            1 -> PageScroll(scrollStates[1]) { WorldPage() }
            else -> PageScroll(scrollStates[2]) { MorePage() }
        }
    }

    // ---------------- 页签 ----------------

    @Composable
    private fun CreateTabColumn() {
        TAB_LABELS.forEachIndexed { index, label ->
            SideTab(label, TAB_FIRST_CENTER + TAB_STEP * index, currentTab == index) {
                selectTab(index)
            }
        }
    }

    @Composable
    private fun SideTab(label: String, centerY: Float, active: Boolean, onClick: () -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()

        // 与设置页页签同款：hover/选中向左滑出、红板交叉淡入、静止压暗、悬停/选中恢复全亮
        val width by animateFloatAsState(
            targetValue = if (active || hovered) TAB_WIDTH_ACTIVE else TAB_WIDTH_INACTIVE,
            animationSpec = tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing)
        )
        val red by animateFloatAsState(
            targetValue = if (active) 1f else 0f,
            animationSpec = tween(TAB_SWITCH_MS)
        )
        val lit by animateFloatAsState(
            targetValue = if (hovered && !active) 1f else 0f,
            animationSpec = tween(TAB_SLIDE_MS, easing = FastOutSlowInEasing)
        )
        Box(
            modifier = Modifier
                .offset(x = (1920f - width).dp, y = (centerY - TAB_HEIGHT / 2f).dp)
                .size(width.dp, TAB_HEIGHT.dp)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        ) {
            (OptionsSprites["TabBase_Default_Clean"] ?: OptionsSprites["TabBase_Default"])?.let {
                // 静止态乘性压暗（TEST27 报修：原版「只露出一点」），悬停/选中渐亮回原亮度
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
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = TAB_LABEL_ANCHOR.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                CreateTabLabel(label, red, lit)
            }
        }
    }

    /** 自绘页签标签：首字大号强调色 + 其余小号基础色（与设置页未挂素材的页签一致） */
    @Composable
    private fun CreateTabLabel(label: String, red: Float, lit: Float) {
        // 静止首字与其余字符同色，仅悬停/选中渐显为粉，其余字符仅随选中过渡（与设置页一致）
        val accent = lerp(COLOR_TAB_LABEL_INACTIVE, COLOR_TAB_LABEL_ACTIVE, maxOf(red, lit))
        val base = lerp(COLOR_TAB_LABEL_INACTIVE, COLOR_TEXT, red)
        // 字号与设置页页签同步（板高 98 基础上回缩 5%）
        val (bigSize, smallSize) = if (label.length <= 2) 54f to 35f else 48f to 30f
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

    // ---------------- 游戏页 ----------------

    @Composable
    private fun GamePage() {
        // 读取 tick（state）建立订阅：uiState 数据变化时页面随 bump() 刷新
        @Suppress("UNUSED_VARIABLE") val version = tick
        SectionHeader("基础设置")

        InputRow("世界名称", uiState.name, "输入世界名称", maxLength = 64) { value ->
            uiState.setName(value)
            bump()
        }

        val currentMode = uiState.gameMode
        SelectRow("游戏模式", currentMode.displayName.string) {
            showPopup(
                SelectPopup(
                    rows = GAME_MODES.map { it.displayName.string to (it == currentMode) },
                    onPick = { index ->
                        uiState.setGameMode(GAME_MODES[index])
                        closePopup()
                        bump()
                    }
                )
            )
        }

        val difficulties = Difficulty.values()
        val currentDifficulty = uiState.difficulty
        SelectRow("难度", currentDifficulty.displayName.string, enabled = !uiState.isHardcore) {
            showPopup(
                SelectPopup(
                    rows = difficulties.map { it.displayName.string to (it == currentDifficulty) },
                    onPick = { index ->
                        uiState.setDifficulty(difficulties[index])
                        closePopup()
                        bump()
                    }
                )
            )
        }

        ToggleRow(
            "允许命令", "关闭", "开启",
            rightSelected = uiState.isAllowCommands,
            enabled = !uiState.isDebug && !uiState.isHardcore
        ) { right ->
            uiState.setAllowCommands(right)
            bump()
        }
    }

    // ---------------- 世界页 ----------------

    @Composable
    private fun WorldPage() {
        // 读取 tick（state）建立订阅：uiState 数据变化时页面随 bump() 刷新
        @Suppress("UNUSED_VARIABLE") val version = tick
        SectionHeader("世界生成")

        val worldTypes = uiState.normalPresetList
        val currentTypeName = uiState.worldType.describePreset().string
        SelectRow("世界类型", currentTypeName, enabled = uiState.worldType.preset() != null) {
            showPopup(
                SelectPopup(
                    rows = worldTypes.map { entry ->
                        entry.describePreset().string to (entry.describePreset().string == currentTypeName)
                    },
                    onPick = { index ->
                        uiState.setWorldType(worldTypes[index])
                        closePopup()
                        bump()
                    }
                )
            )
        }

        RowBox("世界类型设置") {
            Box(
                modifier = Modifier.offset(x = CTRL_LEFT.dp).fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                SmallButton(
                    "自定义",
                    width = 320f,
                    enabled = !uiState.isDebug && uiState.presetEditor != null
                ) {
                    openPresetEditor()
                }
            }
        }

        InputRow("种子", uiState.seed, "留空以使用随机种子", maxLength = 64) { value ->
            uiState.setSeed(value)
            bump()
        }

        ToggleRow(
            "生成结构", "关闭", "开启",
            rightSelected = uiState.isGenerateStructures,
            enabled = !uiState.isDebug
        ) { right ->
            uiState.setGenerateStructures(right)
            bump()
        }

        ToggleRow(
            "奖励箱", "关闭", "开启",
            rightSelected = uiState.isBonusChest,
            enabled = !uiState.isHardcore && !uiState.isDebug
        ) { right ->
            uiState.setBonusChest(right)
            bump()
        }
    }

    private fun openPresetEditor() {
        val editor = uiState.presetEditor ?: return
        mc.setScreen(editor.createEditScreen(screen, uiState.settings))
    }

    // ---------------- 更多页 ----------------

    @Composable
    private fun MorePage() {
        // 读取 tick（state）建立订阅：uiState 数据变化时页面随 bump() 刷新
        @Suppress("UNUSED_VARIABLE") val version = tick
        SectionHeader("高级选项")

        ActionRow("游戏规则", "编辑游戏规则", width = 460f) { openGameRules() }
        ActionRow("实验性内容", "配置实验性内容", width = 460f) { host.`manosaba$openExperiments`() }
        ActionRow("数据包", "选择数据包", width = 460f) { host.`manosaba$openDataPacks`() }
    }

    private fun openGameRules() {
        mc.setScreen(
            ManosabaGameRulesScreen(uiState.gameRules.copy(), ghostSource) { result ->
                mc.setScreen(screen)
                result.ifPresent { uiState.setGameRules(it) }
            }
        )
    }

    // ---------------- 底部按钮 ----------------

    @Composable
    private fun BottomButtons() {
        BottomButton(50f, "创建", "新世界", COLOR_PINK) {
            playClick()
            host.`manosaba$createWorld`()
        }
        BottomButton(375f, "取消", "", COLOR_TEXT) {
            playCancel()
            screen.popScreen()
        }
    }

    @Composable
    private fun BottomButton(x: Float, accentPart: String, normalPart: String, accentColor: Color, onClick: () -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val plate = OptionsSprites[if (hovered) "ButtonBase_Highlighted" else "ButtonBase_Default"]

        Box(
            modifier = Modifier
                .offset(x = x.dp, y = 945.dp)
                .size(RESET_WIDTH.dp, RESET_HEIGHT.dp)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            plate?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            BasicText(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = if (hovered) COLOR_PINK_BRIGHT else accentColor)) { append(accentPart) }
                    if (normalPart.isNotEmpty()) {
                        withStyle(SpanStyle(color = COLOR_TEXT)) { append(normalPart) }
                    }
                },
                style = rowTextStyle(38f)
            )
        }
    }

    // ---------------- 输入行（世界名称 / 种子） ----------------

    @Composable
    private fun InputRow(
        label: String,
        value: String,
        hint: String,
        enabled: Boolean = true,
        maxLength: Int = 64,
        onChange: (String) -> Unit
    ) {
        RowBox(label, if (enabled) COLOR_TEXT else COLOR_DISABLED) {
            Box(
                modifier = Modifier.offset(x = CTRL_LEFT.dp).fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier.size(CTRL_WIDTH.dp, SELECT_HEIGHT.dp),
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
                        textStyle = rowTextStyle(32f),
                        cursorBrush = SolidColor(COLOR_PINK_BRIGHT),
                        modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (value.isEmpty() && hint.isNotEmpty()) {
                                    BasicText(hint, style = rowTextStyle(30f, COLOR_DISABLED))
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }
        }
    }

    // ---------------- 选择弹层（与设置页同款） ----------------

    internal class SelectPopup(val rows: List<Pair<String, Boolean>>, val onPick: (Int) -> Unit) {
        var anchorY: Float = 0f
    }

    @Composable
    private fun SelectPopupLayer(spec: SelectPopup) {
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
                        PopupOptionRow(spec.rows[index].first, spec.rows[index].second, itemH) {
                            playClick()
                            spec.onPick(index)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PopupOptionRow(text: String, selected: Boolean, height: Float, onClick: () -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
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

    private companion object {
        private val TAB_LABELS = listOf("游戏", "世界", "更多")
        private val GAME_MODES = WorldCreationUiState.SelectedGameMode.values()
            .filter { it != WorldCreationUiState.SelectedGameMode.DEBUG }
    }
}

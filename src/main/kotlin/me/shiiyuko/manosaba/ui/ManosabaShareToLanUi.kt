package me.shiiyuko.manosaba.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.shiiyuko.manosaba.ManosabaMod
import me.shiiyuko.manosaba.bridge.ComposeInputBridge
import me.shiiyuko.manosaba.bridge.ShareToLanHost
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ShareToLanScreen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * 「对局域网开放」界面（TEST54）：原版 ShareToLanScreen 作为逻辑壳
 * （游戏模式 / 允许命令 / 端口 EditBox、创建与取消回调），本类为内嵌 Compose
 * 界面（不是当前屏幕），由 ShareToLanScreenMixin 创建并转发渲染与输入。
 *
 * 视觉与连接失败弹层同源：DialogBase 和纸底板居中的对话框（GAME_DIM 背景
 * 暗化游戏画面）+ 三行控件（游戏模式循环 / 允许命令双段 / 端口输入）+
 * 底部「创建局域网世界 / 取消」按钮。
 *
 * 端口输入由本界面自管状态（不依赖原版 EditBox 的焦点与按键门控），
 * 每次按键实时写回原版 EditBox，保证创建时 port 字段与校验状态同步；
 * 错误提示复刻原版 tryParsePort（无副作用）。
 */
class ManosabaShareToLanUi(
    private val screen: ShareToLanScreen
) : ComposeScreen(Component.literal("Manosaba Share To LAN")), ComposeInputBridge.Target {

    private val mc = Minecraft.getInstance()
    private val host: ShareToLanHost get() = screen as ShareToLanHost

    /** 游戏内子屏：保留暂停中的游戏画面并整屏暗化（与暂停菜单一致） */
    override val backgroundMode: BackgroundMode get() = BackgroundMode.GAME_DIM

    private var tick by mutableStateOf(0)
    private var portText by mutableStateOf(host.`manosaba$portText`())
    private var lastFrameSweep = 0L

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

    /** 取消类操作音效：Sfx_System_Cancel_001 */
    private fun playCancel() {
        mc.soundManager.play(SimpleSoundInstance.forUI(ManosabaMod.UI_CANCEL.get(), 1.0f))
    }

    /** 宿主 render 每帧驱动：250ms 节流刷新（光标闪烁 / 状态同步） */
    fun onHostFrame() {
        val now = Util.getMillis()
        if (now - lastFrameSweep >= 250L) {
            lastFrameSweep = now
            bump()
        }
    }

    /** 宿主 init 完成时同步：resize 后原版会重建 EditBox（文本清空），界面状态需跟随 */
    fun onHostInit() {
        portText = host.`manosaba$portText`()
        bump()
    }

    /** 释放 Compose/Skia 资源（宿主 onClose / 创建 / 取消路径调用） */
    fun disposeUi() {
        disposeCompose()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (portText.isNotEmpty()) {
                portText = portText.dropLast(1)
                host.`manosaba$setPortInput`(portText)
            }
            bump()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        // 端口为纯数字输入：过滤非数字字符，长度上限 5（65535）
        if (chr.isDigit() && portText.length < PORT_MAX_LENGTH) {
            portText += chr
            host.`manosaba$setPortInput`(portText)
            bump()
        }
        return true
    }

    // ---------------- 页面 ----------------

    @Composable
    override fun Content() {
        val version = tick
        val error = host.`manosaba$portError`()

        Box(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.align(Alignment.Center).size(880.dp, 480.dp)) {
                OptionsSprites["DialogBase"]?.let {
                    Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                }

                BasicText(
                    Component.translatable("lanServer.title").string,
                    modifier = Modifier.align(Alignment.Center).offset(y = (-155).dp).width(740.dp),
                    style = dialogTextStyle(44f, COLOR_DIALOG_TITLE).copy(textAlign = TextAlign.Center),
                    maxLines = 1,
                    softWrap = false
                )

                BasicText(
                    Component.translatable("lanServer.otherPlayers").string,
                    modifier = Modifier.align(Alignment.Center).offset(y = (-108).dp).width(740.dp),
                    style = dialogTextStyle(24f, COLOR_DIALOG_TEXT).copy(textAlign = TextAlign.Center),
                    maxLines = 1,
                    softWrap = false
                )

                // 游戏模式：点击循环（复刻原版 CycleButton）
                DialogLabel("selectWorld.gameMode", -50f)
                DialogControl(-50f) {
                    PlateButton(host.`manosaba$gameModeName`().string) {
                        playClick()
                        host.`manosaba$cycleGameMode`()
                        bump()
                    }
                }

                // 允许命令：开 / 关双段（复刻原版 onOffBuilder）
                DialogLabel("selectWorld.allowCommands.new", 12f)
                DialogControl(12f) {
                    SegmentToggle(host.`manosaba$allowCommands`()) { value ->
                        playClick()
                        if (value != host.`manosaba$allowCommands`()) {
                            host.`manosaba$toggleCommands`()
                        }
                        bump()
                    }
                }

                // 端口：自管文本，实时写回原版 EditBox；留空自动选用可用端口
                DialogLabel("lanServer.port", 74f)
                DialogControl(74f) {
                    PortInput(portText, host.`manosaba$port`().toString(), version) { bump() }
                }

                if (error != null) {
                    BasicText(
                        error.string,
                        modifier = Modifier.align(Alignment.Center).offset(y = 112.dp).width(740.dp),
                        style = dialogTextStyle(24f, COLOR_DIALOG_ACCENT).copy(textAlign = TextAlign.Center),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-36).dp),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DialogActionButton(
                        Component.translatable("lanServer.start").string,
                        accent = true,
                        enabled = error == null
                    ) {
                        // 切屏不经过可注入方法：先行释放 Compose 资源
                        disposeCompose()
                        host.`manosaba$create`()
                    }
                    DialogActionButton(Component.translatable("gui.cancel").string, cancelSound = true) {
                        disposeCompose()
                        host.`manosaba$cancel`()
                    }
                }
            }
        }
    }

    // ---------------- 行组件 ----------------

    @Composable
    private fun BoxScope.DialogLabel(textKey: String, cy: Float) {
        BasicText(
            Component.translatable(textKey).string,
            modifier = Modifier.align(Alignment.CenterStart).offset(x = 80.dp, y = cy.dp),
            style = dialogTextStyle(30f, COLOR_DIALOG_TEXT),
            maxLines = 1,
            softWrap = false
        )
    }

    /** 控件区：弹层中心偏右（与左侧标签分离），内容居中 */
    @Composable
    private fun BoxScope.DialogControl(cy: Float, content: @Composable () -> Unit) {
        Box(
            modifier = Modifier.align(Alignment.Center).offset(x = 120.dp, y = cy.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }

    /** 单按钮（游戏模式）：三态底板素材（ToggleBody），中心文字 */
    @Composable
    private fun PlateButton(text: String, onClick: () -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val plate = OptionsSprites[if (hovered) "ToggleBody_Highlighted" else "ToggleBody_Enabled"]

        Box(
            modifier = Modifier
                .size(340.dp, TOGGLE_HEIGHT.dp)
                .clickable(interactionSource = interaction, indication = null) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            plate?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            BasicText(text, style = rowTextStyle(29f))
        }
    }

    /** 双段开关（允许命令）：左「关闭」右「开启」，选中段为启用底板 */
    @Composable
    private fun SegmentToggle(rightSelected: Boolean, onChange: (Boolean) -> Unit) {
        Box(Modifier.size(340.dp, TOGGLE_HEIGHT.dp)) {
            Segment(0f, "options.off", !rightSelected) { onChange(false) }
            Segment(170f, "options.on", rightSelected) { onChange(true) }
            OptionsSprites["ToggleOutline"]?.let {
                Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            }
        }
    }

    @Composable
    private fun Segment(x: Float, textKey: String, selected: Boolean, onClick: () -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val plate = OptionsSprites[when {
            hovered -> "ToggleBody_Highlighted"
            selected -> "ToggleBody_Enabled"
            else -> "ToggleBody_Disabled"
        }]

        Box(
            modifier = Modifier
                .offset(x = x.dp)
                .size(170.dp, TOGGLE_HEIGHT.dp)
                .clickable(interactionSource = interaction, indication = null) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            plate?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            BasicText(Component.translatable(textKey).string, style = rowTextStyle(28f))
        }
    }

    /** 端口输入框：自管文本 + ▎光标；空文本时显示当前端口提示 */
    @Composable
    private fun PortInput(value: String, hint: String, version: Int, onActivate: () -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(340.dp, TOGGLE_HEIGHT.dp)
                .clickable(interactionSource = interaction, indication = null, onClick = onActivate),
            contentAlignment = Alignment.CenterStart
        ) {
            OptionsSprites["ToggleBody_Enabled"]?.let {
                Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            }
            val cursor = if (version % 2 == 0) "▎" else ""
            if (value.isEmpty()) {
                BasicText(
                    hint + cursor,
                    modifier = Modifier.padding(start = 22.dp),
                    style = rowTextStyle(28f, COLOR_DISABLED)
                )
            } else {
                BasicText(
                    value + cursor,
                    modifier = Modifier.padding(start = 22.dp),
                    style = rowTextStyle(30f),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }

    /** 弹层按钮：三态底板素材（与其它页面弹层同款），accent 为主操作粉字 */
    @Composable
    private fun DialogActionButton(
        text: String,
        accent: Boolean = false,
        enabled: Boolean = true,
        cancelSound: Boolean = false,
        onClick: () -> Unit
    ) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val plate = OptionsSprites[when {
            !enabled -> "ToggleBody_Disabled"
            hovered -> "ToggleBody_Highlighted"
            else -> "ToggleBody_Enabled"
        }]

        Box(
            modifier = Modifier
                .size(240.dp, TOGGLE_HEIGHT.dp)
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
            BasicText(text, style = rowTextStyle(28f, color))
        }
    }

    private companion object {
        /** 端口输入长度上限（5 位：65535） */
        const val PORT_MAX_LENGTH = 5
    }
}

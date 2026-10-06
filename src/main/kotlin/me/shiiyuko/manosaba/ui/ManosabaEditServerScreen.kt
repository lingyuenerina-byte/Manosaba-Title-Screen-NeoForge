package me.shiiyuko.manosaba.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import me.shiiyuko.manosaba.ManosabaMod
import me.shiiyuko.manosaba.bridge.ComposeInputBridge
import me.shiiyuko.manosaba.bridge.EditServerHost
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.EditServerScreen
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * 编辑服务器界面（TEST43）：原版 EditServerScreen（「添加服务器」与「编辑」共用）
 * 作为逻辑壳（名称 / 地址 EditBox、资源包策略、完成 / 取消回调），本类为内嵌
 * Compose 界面（不是当前屏幕），由 EditServerScreenMixin 创建并转发渲染与输入。
 *
 * 视觉与设置界面一致：标题屏「倒映」背景 + 磨砂底板 + 表单行（RowBox 体系）+ 底部按钮；
 * 名称 / 地址输入由本界面自管状态（不依赖原版 EditBox 的焦点与按键门控），
 * 每次按键实时写回原版 EditBox，保证 onAdd 提交与 responder 状态同步。
 */
class ManosabaEditServerUi(
    private val screen: EditServerScreen,
    private val ghostSource: ManosabaTitleScreen?
) : ComposeScreen(Component.literal("Manosaba Edit Server")), ComposeInputBridge.Target {

    private val mc = Minecraft.getInstance()
    private val host: EditServerHost get() = screen as EditServerHost

    private var tick by mutableStateOf(0)
    private var nameText by mutableStateOf(host.`manosaba$name`())
    private var ipText by mutableStateOf(host.`manosaba$ip`())
    private var focusedField by mutableStateOf(FIELD_NAME)
    private val scrollState = ScrollState(0)
    private var lastFrameSweep = 0L

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

    /** 宿主 render 每帧驱动：250ms 节流刷新（光标闪烁 / 按钮状态） */
    fun onHostFrame() {
        val now = Util.getMillis()
        if (now - lastFrameSweep >= 250L) {
            lastFrameSweep = now
            bump()
        }
    }

    /** 释放 Compose/Skia 资源（宿主 onClose / submit / cancel 路径调用） */
    fun disposeUi() {
        disposeCompose()
    }

    private fun focus(field: Int) {
        if (focusedField != field) {
            playClick()
            focusedField = field
        }
        bump()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && focusedField != FIELD_NONE) {
            if (focusedField == FIELD_NAME) {
                if (nameText.isNotEmpty()) {
                    nameText = nameText.dropLast(1)
                    host.`manosaba$setName`(nameText)
                }
            } else {
                if (ipText.isNotEmpty()) {
                    ipText = ipText.dropLast(1)
                    host.`manosaba$setIp`(ipText)
                }
            }
            bump()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        // 输入自管：写入原版 EditBox（responder 链同步按钮状态）；
        // 字符过滤等效原版 SharedConstants.isAllowedChatCharacter（控制字符与 § 除外）
        if (focusedField != FIELD_NONE && !chr.isISOControl() && chr != '\u00a7') {
            if (focusedField == FIELD_NAME) {
                if (nameText.length < NAME_MAX_LENGTH) {
                    nameText += chr
                    host.`manosaba$setName`(nameText)
                    bump()
                }
            } else {
                if (ipText.length < IP_MAX_LENGTH) {
                    ipText += chr
                    host.`manosaba$setIp`(ipText)
                    bump()
                }
            }
        }
        return true
    }

    // ---------------- 页面 ----------------

    @Composable
    override fun Content() {
        val version = tick

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
                "编辑服务器信息",
                modifier = Modifier.offset(x = 56.dp, y = 30.dp),
                style = rowTextStyle(62f, COLOR_TAB_LABEL_ACTIVE)
            )

            PageScroll(scrollState) {
                SectionHeader("服务器信息")
                InputRow("服务器名称", nameText, "输入服务器名称", focusedField == FIELD_NAME, version) {
                    focus(FIELD_NAME)
                }
                InputRow("服务器地址", ipText, "输入服务器地址", focusedField == FIELD_IP, version) {
                    focus(FIELD_IP)
                }
                PackRow(version)
            }

            BottomButtons(version)
        }
    }

    // ---------------- 输入行 ----------------

    @Composable
    private fun InputRow(
        label: String,
        value: String,
        hint: String,
        focused: Boolean,
        version: Int,
        onActivate: () -> Unit
    ) {
        RowBox(label) {
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier.offset(x = CTRL_LEFT.dp).fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(CTRL_WIDTH.dp, SELECT_HEIGHT.dp)
                        .clickable(interactionSource = interaction, indication = null, onClick = onActivate),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val plate = if (focused) "ToggleBody_Enabled" else "ToggleBody_Disabled"
                    OptionsSprites[plate]?.let {
                        Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    }
                    val cursor = if (focused && version % 2 == 0) "▎" else ""
                    if (value.isEmpty()) {
                        BasicText(
                            hint + cursor,
                            modifier = Modifier.padding(start = 22.dp),
                            style = rowTextStyle(30f, COLOR_DISABLED)
                        )
                    } else {
                        BasicText(
                            value + cursor,
                            modifier = Modifier.padding(start = 22.dp),
                            style = rowTextStyle(32f),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }

    /** 资源包策略行：点击循环 ENABLED → DISABLED → PROMPT（复刻原版 CycleButton） */
    @Composable
    private fun PackRow(version: Int) {
        @Suppress("UNUSED_VARIABLE") val v = version
        val status = host.`manosaba$packStatus`()
        RowBox("资源包") {
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            Box(
                modifier = Modifier.offset(x = CTRL_LEFT.dp).fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(CTRL_WIDTH.dp, SELECT_HEIGHT.dp)
                        .clickable(interactionSource = interaction, indication = null) {
                            playClick()
                            host.`manosaba$cyclePackStatus`()
                            bump()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val plate = if (hovered) "ToggleBody_Highlighted" else "ToggleBody_Enabled"
                    OptionsSprites[plate]?.let {
                        Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    }
                    BasicText(status.getName().string, style = rowTextStyle(32f))
                }
            }
        }
    }

    // ---------------- 底部按钮 ----------------

    @Composable
    private fun BottomButtons(version: Int) {
        @Suppress("UNUSED_VARIABLE") val v = version
        // 复刻原版 updateAddButtonStatus：地址合法且名称非空才可提交
        val canSubmit = ServerAddress.isValidAddress(ipText) && nameText.isNotEmpty()

        RowButton(477f, 945f, 471f, "完成", enabled = canSubmit, accent = true) {
            host.`manosaba$submit`()
        }
        RowButton(972f, 945f, 471f, "取消", cancelSound = true) {
            host.`manosaba$cancel`()
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

    private companion object {
        const val FIELD_NONE = -1
        const val FIELD_NAME = 0
        const val FIELD_IP = 1

        /** 原版 EditBox 默认长度上限（EditServerScreen 未单独设置名称上限） */
        const val NAME_MAX_LENGTH = 32

        /** 原版 ipEdit.setMaxLength(128) */
        const val IP_MAX_LENGTH = 128
    }
}

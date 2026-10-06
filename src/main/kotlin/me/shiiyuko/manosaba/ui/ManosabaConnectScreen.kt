package me.shiiyuko.manosaba.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.shiiyuko.manosaba.ManosabaMod
import me.shiiyuko.manosaba.bridge.ComposeInputBridge
import me.shiiyuko.manosaba.bridge.ConnectHost
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component

/**
 * 连接服务器界面（TEST45）：原版 ConnectScreen 作为逻辑壳（连接线程 / 状态回调 /
 * 取消语义 / 无障碍等待播报），本类为内嵌 Compose 界面（不是当前屏幕），
 * 由 ConnectScreenMixin 创建并转发渲染与输入。
 *
 * 视觉与设置界面一致：标题屏「倒映」背景 + 磨砂底板 + 居中状态文本 + 底部按钮；
 * 状态文本由连接线程经原版 updateStatus 动态更新，250ms 节流刷新即时呈现。
 * ESC 不可关闭（原版 shouldCloseOnEsc=false 语义），退出唯一入口为「取消」按钮。
 */
class ManosabaConnectUi(
    private val screen: ConnectScreen,
    private val ghostSource: ManosabaTitleScreen?
) : ComposeScreen(Component.literal("Manosaba Connect")), ComposeInputBridge.Target {

    private val mc = Minecraft.getInstance()
    private val host: ConnectHost get() = screen as ConnectHost

    private var tick by mutableStateOf(0)
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

    /** 宿主 render 每帧驱动：250ms 节流刷新（连接状态文本变化 / 悬停淡入） */
    fun onHostFrame() {
        val now = Util.getMillis()
        if (now - lastFrameSweep >= 250L) {
            lastFrameSweep = now
            bump()
        }
    }

    /** 释放 Compose/Skia 资源（取消按钮 / 兜底释放路径调用） */
    fun disposeUi() {
        disposeCompose()
    }

    // ---------------- 页面 ----------------

    @Composable
    override fun Content() {
        @Suppress("UNUSED_VARIABLE") val version = tick

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
                "连接服务器",
                modifier = Modifier.offset(x = 56.dp, y = 30.dp),
                style = rowTextStyle(62f, COLOR_TAB_LABEL_ACTIVE)
            )

            // 居中连接状态：由连接线程动态更新（正在连接 / 正在登录……）
            BasicText(
                host.`manosaba$status`().string,
                modifier = Modifier.align(Alignment.Center).offset(y = (-40).dp).width(1600.dp),
                style = rowTextStyle(40f).copy(textAlign = TextAlign.Center)
            )

            RowButton(724f, 945f, 471f, "取消", cancelSound = true) {
                host.`manosaba$cancel`()
            }
        }
    }

    // ---------------- 底部按钮 ----------------

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
}

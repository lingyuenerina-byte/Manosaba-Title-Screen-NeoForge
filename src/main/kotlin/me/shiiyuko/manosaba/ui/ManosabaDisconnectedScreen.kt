package me.shiiyuko.manosaba.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.shiiyuko.manosaba.ManosabaMod
import me.shiiyuko.manosaba.bridge.ComposeInputBridge
import me.shiiyuko.manosaba.bridge.DisconnectedHost
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component

/**
 * 连接失败界面（TEST39）：原版 DisconnectedScreen 作为逻辑壳（断开原因 /
 * 返回按钮行为 / 报告链接与目录），本类为内嵌 Compose 界面（不是当前屏幕），
 * 由 DisconnectedScreenMixin 创建并经 RenderTarget 转发渲染与输入。
 *
 * 视觉与设置界面一致：标题屏「倒映」背景（ghostSource 非空时）+ 磨砂底板 +
 * 纸面弹层（DialogBase）：标题 + 断开原因 + 返回按钮一排；
 * 与「实验性内容 / 数据包」等嵌入子屏相同，底层沿用魔女审判风格。
 *
 * 「返回」按钮是全屏唯一出口（原版 shouldCloseOnEsc=false，ESC 不关闭）：
 * 点击时先释放 Compose 资源再执行原版返回（切屏不经过可注入的 removed）。
 */
class ManosabaDisconnectedUi(
    private val screen: DisconnectedScreen,
    private val ghostSource: ManosabaTitleScreen?
) : ComposeScreen(Component.literal("Manosaba Disconnected")), ComposeInputBridge.RenderTarget {

    private val mc = Minecraft.getInstance()
    private val host: DisconnectedHost get() = screen as DisconnectedHost

    init {
        OptionsSprites.preload()
        OptionsUi.playClick = { playClick() }
    }

    /** 确认类操作音效：Sfx_System_Submit_001 */
    private fun playClick() {
        mc.soundManager.play(SimpleSoundInstance.forUI(ManosabaMod.UI_SUBMIT.get(), 1.0f))
    }

    /** 主按钮：释放 Compose 资源后执行原版返回行为（切屏，无 removed 注入可依赖） */
    private fun close() {
        playClick()
        disposeCompose()
        host.`manosaba$close`()
    }

    @Composable
    override fun Content() {
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

            Box(Modifier.align(Alignment.Center).size(880.dp, 480.dp)) {
                OptionsSprites["DialogBase"]?.let {
                    Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                }

                BasicText(
                    host.`manosaba$title`().string,
                    modifier = Modifier.align(Alignment.Center).offset(y = (-155).dp).width(740.dp),
                    style = dialogTextStyle(44f, COLOR_DIALOG_TITLE).copy(textAlign = TextAlign.Center),
                    maxLines = 1,
                    softWrap = false
                )

                BasicText(
                    host.`manosaba$reason`().string,
                    modifier = Modifier.align(Alignment.Center).offset(y = 15.dp).width(740.dp),
                    style = dialogTextStyle(28f, COLOR_DIALOG_TEXT).copy(textAlign = TextAlign.Center)
                )

                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-40).dp),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 主按钮（返回）：固定在最左，附加按钮出现时位置稳定
                    DialogActionButton(host.`manosaba$buttonText`().string, accent = true) {
                        close()
                    }
                    if (host.`manosaba$hasReportDir`()) {
                        DialogActionButton("打开报告目录") {
                            host.`manosaba$openReportDir`()
                        }
                    }
                    if (host.`manosaba$hasReportLink`()) {
                        DialogActionButton("向服务器报告") {
                            host.`manosaba$openReportLink`()
                        }
                    }
                }
            }
        }
    }

    /** 弹层按钮：三态底板素材（与其它页面弹层同款），accent 为主操作粉字 */
    @Composable
    private fun DialogActionButton(
        text: String,
        accent: Boolean = false,
        onClick: () -> Unit
    ) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val plate = OptionsSprites[if (hovered) "ToggleBody_Highlighted" else "ToggleBody_Enabled"]

        Box(
            modifier = Modifier
                .size(240.dp, TOGGLE_HEIGHT.dp)
                .clickable(interactionSource = interaction, indication = null) {
                    playClick()
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            plate?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            val color = when {
                accent && hovered -> COLOR_PINK_BRIGHT
                accent -> COLOR_PINK
                else -> COLOR_TEXT
            }
            BasicText(text, style = rowTextStyle(28f, color))
        }
    }
}

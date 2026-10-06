package me.shiiyuko.manosaba.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import it.unimi.dsi.fastutil.objects.Object2BooleanMap
import me.shiiyuko.manosaba.ManosabaMod
import me.shiiyuko.manosaba.bridge.ComposeInputBridge
import me.shiiyuko.manosaba.bridge.ExperimentsHost
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.worldselection.ExperimentsScreen
import net.minecraft.client.resources.language.I18n
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.server.packs.repository.Pack

/**
 * 实验性内容界面（TEST33）：原版 ExperimentsScreen 作为逻辑壳（FEATURE 数据包开关
 * 数据 / 完成写回回调），本类为内嵌 Compose 界面（不是当前屏幕），
 * 由 ExperimentsScreenMixin 创建并转发渲染与输入。
 *
 * 视觉与设置界面一致：标题屏「倒映」背景 + 磨砂底板 + 迷你行组件；
 * 每个 FEATURE 数据包一行（标题 + 双段开关），原版红字警告保留在列表顶部。
 */
class ManosabaExperimentsUi(
    private val screen: ExperimentsScreen,
    private val ghostSource: ManosabaTitleScreen?
) : ComposeScreen(Component.literal("Manosaba Experiments")), ComposeInputBridge.RenderTarget {

    private val mc = Minecraft.getInstance()
    private val host: ExperimentsHost get() = screen as ExperimentsHost

    /** FEATURE 源数据包 → 启用状态（原版 packs 字段，Linked 顺序稳定） */
    private val packs: Object2BooleanMap<Pack> get() = host.`manosaba$packs`()

    private var tick by mutableStateOf(0)
    private val scrollState = ScrollState(0)

    init {
        OptionsSprites.preload()
        OptionsUi.playClick = { playClick() }
        // 兜底重置弹层拦截标记（本屏无弹层，避免上一屏残留导致行悬停横幅不显示）
        OptionsUi.popupOpen = false
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

    // ---------------- 页面 ----------------

    @Composable
    override fun Content() {
        // 读取 tick（state）建立订阅：开关切换后由 bump() 驱动整页刷新
        @Suppress("UNUSED_VARIABLE") val version = tick

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
                "实验性内容",
                modifier = Modifier.offset(x = 56.dp, y = 30.dp),
                style = rowTextStyle(62f, COLOR_TAB_LABEL_ACTIVE)
            )

            ExperimentList()
            BottomButtons()
        }
    }

    @Composable
    private fun ExperimentList() {
        PageScroll(scrollState) {
            // 原版红字警告（selectWorld.experiments.info）
            TextBlock(I18n.get("selectWorld.experiments.info"), size = 30f, width = 1220f, color = COLOR_ERROR)

            packs.keys.forEach { pack ->
                key(pack) {
                    MiniLine(packTitle(pack)) {
                        MiniToggle(rightSelected = packs.getBoolean(pack)) { right ->
                            packs.put(pack, right)
                            bump()
                        }
                    }
                }
            }
        }
    }

    /** 复刻原版 getHumanReadableTitle：优先 "dataPack.<id>.name" 翻译，否则用包自带标题 */
    private fun packTitle(pack: Pack): String {
        val key = "dataPack." + pack.id + ".name"
        return if (I18n.exists(key)) I18n.get(key) else pack.title.string
    }

    // ---------------- 底部按钮 ----------------

    @Composable
    private fun BottomButtons() {
        FooterButton(50f, "完成", COLOR_PINK) {
            playClick()
            // 「完成」路径不经过 onClose（直接写回并切屏），在此提前释放 Compose 资源
            disposeCompose()
            host.`manosaba$onDone`()
        }
        FooterButton(375f, "取消", COLOR_TEXT) {
            playCancel()
            screen.onClose()
        }
    }
}

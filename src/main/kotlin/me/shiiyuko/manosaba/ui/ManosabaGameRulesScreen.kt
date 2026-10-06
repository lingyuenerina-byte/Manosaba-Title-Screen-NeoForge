package me.shiiyuko.manosaba.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.util.Optional
import java.util.function.Consumer
import me.shiiyuko.manosaba.ManosabaMod
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.language.I18n
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.world.level.GameRules

/**
 * 编辑游戏规则界面（TEST32）：原版 EditGameRulesScreen 的魔女审判化。
 *
 * 本类为纯 Compose 屏（不是嵌入壳模式，直接由 mc.setScreen 打开），
 * 从创建世界界面「更多」页进入；数据为原版 GameRules 深拷贝，
 * 「完成」经 exitCallback 写回创建世界界面，「取消」/ESC 丢弃。
 *
 * 数据构建复刻原版 RuleList：visitGameRuleTypes 按 Category 枚举顺序分组，
 * 分类内按规则 id 字母序；无效整数输入红字显示并禁用「完成」。
 * 视觉与设置界面一致：标题屏「倒映」背景 + 磨砂底板 + 迷你行组件。
 */
class ManosabaGameRulesScreen(
    private val gameRules: GameRules,
    private val ghostSource: ManosabaTitleScreen?,
    private val exitCallback: Consumer<Optional<GameRules>>
) : ComposeScreen(Component.translatable("editGamerule.title")) {

    private val mc = Minecraft.getInstance()

    /** 手动刷新计数：GameRules 的值本身不是 Compose state，开关切换后 bump() 驱动重绘 */
    private var tick by mutableStateOf(0)

    /** 无效输入的行 id 集合（原版 invalidEntries：非空时「完成」不可用） */
    private var invalidIds by mutableStateOf(emptySet<String>())

    private val scrollState = ScrollState(0)
    private val rows = buildRows()

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

    /**
     * 关闭屏幕（ESC / 取消按钮）：丢弃修改并释放 Compose 资源。
     * 注意：不调用 super.onClose()（那会 setScreen(null)），返回由 exitCallback 负责。
     */
    override fun onClose() {
        playCancel()
        disposeCompose()
        exitCallback.accept(Optional.empty())
    }

    // ---------------- 数据构建（复刻原版 RuleList 的排序） ----------------

    private sealed class RuleRow(val key: String) {
        class CategoryRule(val category: GameRules.Category) : RuleRow("category:" + category.name)
        class BoolRule(key: String, val label: String, val value: GameRules.BooleanValue) : RuleRow(key)
        class IntRule(key: String, val label: String, val value: GameRules.IntegerValue) : RuleRow(key)
    }

    private fun buildRows(): List<RuleRow> {
        val byCategory = mutableMapOf<GameRules.Category, MutableList<RuleRow>>()
        GameRules.visitGameRuleTypes(object : GameRules.GameRuleTypeVisitor {
            override fun visitBoolean(
                key: GameRules.Key<GameRules.BooleanValue>,
                type: GameRules.Type<GameRules.BooleanValue>
            ) {
                add(key, RuleRow.BoolRule(key.id, label(key), gameRules.getRule(key)))
            }

            override fun visitInteger(
                key: GameRules.Key<GameRules.IntegerValue>,
                type: GameRules.Type<GameRules.IntegerValue>
            ) {
                add(key, RuleRow.IntRule(key.id, label(key), gameRules.getRule(key)))
            }

            private fun add(key: GameRules.Key<*>, row: RuleRow) {
                byCategory.getOrPut(key.category) { mutableListOf() }.add(row)
            }
        })

        val list = mutableListOf<RuleRow>()
        for (category in GameRules.Category.values()) {
            val entries = byCategory[category] ?: continue
            list.add(RuleRow.CategoryRule(category))
            entries.sortBy { it.key }
            list.addAll(entries)
        }
        return list
    }

    private fun label(key: GameRules.Key<*>): String = I18n.get(key.descriptionId)

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
                "编辑游戏规则",
                modifier = Modifier.offset(x = 56.dp, y = 30.dp),
                style = rowTextStyle(62f, COLOR_TAB_LABEL_ACTIVE)
            )

            RuleList()
            BottomButtons()
        }
    }

    @Composable
    private fun RuleList() {
        PageScroll(scrollState) {
            rows.forEach { row ->
                when (row) {
                    is RuleRow.CategoryRule -> SectionHeader(I18n.get(row.category.descriptionId))
                    is RuleRow.BoolRule -> MiniLine(row.label) {
                        MiniToggle(rightSelected = row.value.get()) { right ->
                            row.value.set(right, null)
                            bump()
                        }
                    }
                    is RuleRow.IntRule -> IntRuleRow(row)
                }
            }
        }
    }

    @Composable
    private fun IntRuleRow(row: RuleRow.IntRule) {
        // 输入文本挂在行状态下（row 稳定引用），校验通过时原版 IntegerValue 会被直接写入
        var text by remember(row) { mutableStateOf(row.value.get().toString()) }
        MiniLine(row.label) {
            MiniInput(
                value = text,
                invalid = row.key in invalidIds,
                onChange = { input ->
                    text = input
                    invalidIds = if (row.value.tryDeserialize(input)) {
                        invalidIds - row.key
                    } else {
                        invalidIds + row.key
                    }
                }
            )
        }
    }

    // ---------------- 底部按钮 ----------------

    @Composable
    private fun BottomButtons() {
        FooterButton(50f, "完成", COLOR_PINK, enabled = invalidIds.isEmpty()) {
            playClick()
            disposeCompose()
            exitCallback.accept(Optional.of(gameRules))
        }
        FooterButton(375f, "取消", COLOR_TEXT) {
            onClose()
        }
    }
}

package me.shiiyuko.manosaba.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import net.minecraft.Util
import net.minecraft.client.KeyMapping
import net.minecraft.client.NarratorStatus
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.WinScreen
import net.minecraft.client.gui.screens.packs.PackSelectionModel
import net.minecraft.client.resources.language.I18n
import net.minecraft.util.CommonLinks
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.player.PlayerModelPart
import java.util.Locale

// ============================== 按键控制页（鼠标 / 操作 / 按键绑定入口） ==============================

@Composable
internal fun ManosabaOptionsScreen.ControlsPage() {
    val o = options
    @Suppress("UNUSED_VARIABLE") val version = tick

    SectionHeader("鼠标")

    val sensitivity = o.sensitivity().get()
    SliderRow("鼠标灵敏度", sensitivity.toFloat(), 0f, 1f, percentLabel(sensitivity)) { v ->
        o.sensitivity().set(v.toDouble())
        bump()
    }

    ToggleRow("鼠标反转", "关闭", "开启", rightSelected = o.invertYMouse().get()) { right ->
        o.invertYMouse().set(right)
        bump()
    }

    val wheel = o.mouseWheelSensitivity().get()
    SliderRow(
        "滚轮灵敏度", wheelSliderPos(wheel), -200f, 100f,
        String.format(Locale.ROOT, "%.2f", wheel), step = 1f
    ) { v ->
        o.mouseWheelSensitivity().set(wheelValueAt(v))
        bump()
    }

    ToggleRow("离散式滚动", "关闭", "开启", rightSelected = o.discreteMouseScroll().get()) { right ->
        o.discreteMouseScroll().set(right)
        bump()
    }

    ToggleRow("触摸屏模式", "关闭", "开启", rightSelected = o.touchscreen().get()) { right ->
        o.touchscreen().set(right)
        bump()
    }

    ToggleRow("原始鼠标输入", "关闭", "开启", rightSelected = o.rawMouseInput().get()) { right ->
        o.rawMouseInput().set(right)
        bump()
    }

    SectionHeader("操作")

    ToggleRow("潜行方式", "按住", "切换", rightSelected = o.toggleCrouch().get()) { right ->
        o.toggleCrouch().set(right)
        bump()
    }

    ToggleRow("疾跑方式", "按住", "切换", rightSelected = o.toggleSprint().get()) { right ->
        o.toggleSprint().set(right)
        bump()
    }

    ToggleRow("自动跳跃", "关闭", "开启", rightSelected = o.autoJump().get()) { right ->
        o.autoJump().set(right)
        bump()
    }

    ToggleRow("创造模式物品栏", "关闭", "开启", rightSelected = o.operatorItemsTab().get()) { right ->
        o.operatorItemsTab().set(right)
        bump()
    }

    SectionHeader("按键绑定")

    SelectRow("按键绑定", "打开按键绑定编辑界面") { openKeyBinds() }
}

// ============================== 按键绑定编辑视图 ==============================

@Composable
internal fun ManosabaOptionsScreen.KeyBindsPage() {
    val o = options
    @Suppress("UNUSED_VARIABLE") val version = tick

    ActionRow("按键绑定编辑", "返回按键控制", width = 320f) { closeKeyBinds() }
    TextBlock("点击右侧按键框后按下新的按键即可完成修改；按 Esc 清除绑定；鼠标按键可直接点击绑定。")

    var lastCategory: String? = null
    o.keyMappings.sorted().forEach { mapping ->
        if (mapping.category != lastCategory) {
            lastCategory = mapping.category
            SectionHeader(I18n.get(mapping.category))
        }
        KeyBindRow(mapping)
    }

    Box(Modifier.fillMaxWidth().height(ROW_STEP.dp)) {
        Box(
            modifier = Modifier.offset(x = LABEL_LEFT.dp).fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            SmallButton("全部重置为默认", width = 400f) { resetAllKeyBinds() }
        }
    }
}

@Composable
private fun ManosabaOptionsScreen.KeyBindRow(mapping: KeyMapping) {
    val o = options
    // KeyMapping 是普通对象（非 Compose 状态）：改键/点「默认」/「全部重置」后行文本与
    // 「默认」按钮可见性都靠 bump() 刷新。本行必须自行订阅 tick——否则页面级重组时，
    // 本行会因参数未变（同一 mapping 实例）命中跳过重组，文本停留在旧值
    @Suppress("UNUSED_VARIABLE") val version = tick
    val isCapturing = capturing === mapping
    val conflict = !isCapturing && !mapping.isUnbound() &&
        o.keyMappings.any { other -> other !== mapping && other.same(mapping) }
    val keyText = if (isCapturing) "> 按下按键 <" else mapping.translatedKeyMessage.string

    RowBox(I18n.get(mapping.name)) {
        Box(
            modifier = Modifier.offset(x = CTRL_LEFT.dp).fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            SmallButton(
                text = keyText,
                width = 330f,
                textColor = if (conflict) Color(0xFFE5484D) else COLOR_PINK_BRIGHT
            ) {
                startCapture(mapping)
            }
            if (!mapping.isDefault()) {
                Box(Modifier.offset(x = 352.dp)) {
                    SmallButton("默认", width = 110f) {
                        mapping.setToDefault()
                        KeyMapping.resetMapping()
                        o.save()
                        bump()
                    }
                }
            }
        }
    }
}

// ============================== 资源包页 ==============================

@Composable
internal fun ManosabaOptionsScreen.PacksPage() {
    @Suppress("UNUSED_VARIABLE") val version = tick

    LaunchedEffect(Unit) { ensurePacks() }

    SectionHeader("资源包")
    TextBlock("资源包会修改游戏的外观、音效与文本。启用或停用后，在离开本页签或关闭设置界面时自动应用更改。")
    ActionRow("资源包文件夹", "打开资源包文件夹", width = 380f) {
        Util.getPlatform().openPath(minecraft.resourcePackDirectory)
    }
    ActionRow("重新扫描", "重新扫描资源包", width = 380f) { rescanPacks() }

    val model = packModel
    if (model == null) {
        TextBlock("正在扫描资源包…")
        return
    }

    val selected = model.getSelected().toList()
    SectionHeader("已启用")
    if (selected.isEmpty()) {
        TextBlock("没有已启用的资源包。", size = 30f)
    }
    selected.forEach { entry -> PackRow(entry, selected = true) }

    val unselected = model.getUnselected().toList()
    SectionHeader("可用")
    if (unselected.isEmpty()) {
        TextBlock("没有新的可用资源包。", size = 30f)
    }
    unselected.forEach { entry -> PackRow(entry, selected = false) }
}

@Composable
private fun ManosabaOptionsScreen.PackRow(entry: PackSelectionModel.Entry, selected: Boolean) {
    val title = entry.title.string
    val incompatible = !entry.compatibility.isCompatible

    RowBox(title) {
        // 缩略图（TEST21 报修）：pack.png 渲染在文字标签左侧（字左缘 LABEL_LEFT 左移 80px），
        // 行高 97 中垂直居中（64px 缩略图 → y=16.5）；使资源包可凭图标辨认
        packIcon(entry)?.let { icon ->
            Image(
                icon, null,
                modifier = Modifier
                    .offset(x = (LABEL_LEFT - 80f).dp, y = 16.5f.dp)
                    .size(64.dp),
                contentScale = ContentScale.Fit
            )
        }
        Box(
            modifier = Modifier.offset(x = CTRL_LEFT.dp).fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            if (selected) {
                SmallButton(
                    text = if (entry.isRequired) "必需" else "停用",
                    width = 150f,
                    enabled = entry.canUnselect()
                ) {
                    entry.unselect()
                    bump()
                }
                Box(Modifier.offset(x = 164.dp)) {
                    SmallButton("上移", width = 120f, enabled = entry.canMoveUp()) {
                        entry.moveUp()
                        bump()
                    }
                }
                Box(Modifier.offset(x = 296.dp)) {
                    SmallButton("下移", width = 120f, enabled = entry.canMoveDown()) {
                        entry.moveDown()
                        bump()
                    }
                }
            } else {
                SmallButton(
                    text = "启用",
                    width = 150f,
                    enabled = entry.canSelect() && !incompatible
                ) {
                    entry.select()
                    bump()
                }
            }
            if (incompatible) {
                BasicText(
                    "版本不兼容",
                    modifier = Modifier.offset(x = 440.dp),
                    style = rowTextStyle(28f, COLOR_PINK)
                )
            }
        }
    }
}

// ============================== 辅助功能设置页 ==============================

@Composable
internal fun ManosabaOptionsScreen.AccessibilityPage() {
    val o = options
    @Suppress("UNUSED_VARIABLE") val version = tick

    SectionHeader("辅助功能")

    val narratorValues = NarratorStatus.values()
    SelectRow("讲述人", narratorLabel(o.narrator().get())) {
        showPopup(
            PopupSpec(
                "讲述人",
                narratorValues.map { narratorLabel(it) to (it == o.narrator().get()) }
            ) { index ->
                o.narrator().set(narratorValues[index])
                closePopup()
                bump()
            }
        )
    }

    ToggleRow("讲述人快捷键", "关闭", "开启", rightSelected = o.narratorHotkey().get()) { right ->
        o.narratorHotkey().set(right)
        bump()
    }

    val highContrastAvailable = minecraft.resourcePackRepository.availableIds.contains("high_contrast")
    ToggleRow(
        "高对比度", "关闭", "开启",
        rightSelected = o.highContrast().get(),
        enabled = highContrastAvailable
    ) { right ->
        o.highContrast().set(right)
        bump()
    }

    val notificationTime = o.notificationDisplayTime().get()
    SliderRow(
        "通知显示时长", notificationTime.toFloat(), 0.5f, 10f,
        String.format(Locale.ROOT, "%.1f×", notificationTime), step = 0.1f
    ) { v ->
        o.notificationDisplayTime().set(v.toDouble())
        bump()
    }

    ToggleRow("隐藏闪电闪光", "关闭", "开启", rightSelected = o.hideLightningFlash().get()) { right ->
        o.hideLightningFlash().set(right)
        bump()
    }

    val darkness = o.darknessEffectScale().get()
    SliderRow("黑暗效果强度", darkness.toFloat(), 0f, 1f, percentLabel(darkness)) { v ->
        o.darknessEffectScale().set(v.toDouble())
        bump()
    }

    val damageTilt = o.damageTiltStrength().get()
    SliderRow("受伤视角倾斜", damageTilt.toFloat(), 0f, 1f, percentLabel(damageTilt)) { v ->
        o.damageTiltStrength().set(v.toDouble())
        bump()
    }

    ToggleRow("简化 F3 调试信息", "关闭", "开启", rightSelected = o.reducedDebugInfo().get()) { right ->
        o.reducedDebugInfo().set(right)
        bump()
    }

    SectionHeader("开始画面")

    val panorama = o.panoramaSpeed().get()
    SliderRow("全景图旋转速度", panorama.toFloat(), 0f, 1f, percentLabel(panorama)) { v ->
        o.panoramaSpeed().set(v.toDouble())
        bump()
    }

    ToggleRow("隐藏闪烁标语", "关闭", "开启", rightSelected = o.hideSplashTexts().get()) { right ->
        o.hideSplashTexts().set(right)
        bump()
    }

    ToggleRow("暗色 Mojang 背景", "关闭", "开启", rightSelected = o.darkMojangStudiosBackground().get()) { right ->
        o.darkMojangStudiosBackground().set(right)
        bump()
    }

    // 标题背景周目切换（由主界面「临时切换背景」按钮迁移而来）：
    // 0 = 一周目（background_ema），1 = 二周目（background_next），后续由正式周目逻辑替换；
    // 无标题屏（从暂停菜单打开）时该开关无意义，置灰
    ToggleRow(
        "标题背景", "一周目", "二周目",
        rightSelected = titleParent?.weekIndex == 1,
        enabled = titleParent != null
    ) { right ->
        titleParent?.weekIndex = if (right) 1 else 0
        bump()
    }
}

private fun narratorLabel(value: NarratorStatus): String = when (value) {
    NarratorStatus.OFF -> "关闭"
    NarratorStatus.ALL -> "全部"
    NarratorStatus.CHAT -> "仅聊天"
    NarratorStatus.SYSTEM -> "仅系统"
}

// ============================== 自定义皮肤页 ==============================

@Composable
internal fun ManosabaOptionsScreen.SkinPage() {
    val o = options
    @Suppress("UNUSED_VARIABLE") val version = tick

    SectionHeader("皮肤部件")

    PlayerModelPart.values().forEach { part ->
        ToggleRow(partLabel(part), "隐藏", "显示", rightSelected = o.isModelPartEnabled(part)) { right ->
            o.toggleModelPart(part, right)
            bump()
        }
    }

    SectionHeader("主手")

    ToggleRow("主手", "左手", "右手", rightSelected = o.mainHand().get() == HumanoidArm.RIGHT) { right ->
        o.mainHand().set(if (right) HumanoidArm.RIGHT else HumanoidArm.LEFT)
        bump()
    }
}

private fun partLabel(part: PlayerModelPart): String = when (part) {
    PlayerModelPart.CAPE -> "披风"
    PlayerModelPart.JACKET -> "外套"
    PlayerModelPart.LEFT_SLEEVE -> "左袖"
    PlayerModelPart.RIGHT_SLEEVE -> "右袖"
    PlayerModelPart.LEFT_PANTS_LEG -> "左裤腿"
    PlayerModelPart.RIGHT_PANTS_LEG -> "右裤腿"
    PlayerModelPart.HAT -> "帽子"
}

// ============================== 在线选项页 ==============================

@Composable
internal fun ManosabaOptionsScreen.OnlinePage() {
    val o = options
    @Suppress("UNUSED_VARIABLE") val version = tick

    SectionHeader("多人游戏")

    ToggleRow("Realms 邀请通知", "关闭", "开启", rightSelected = o.realmsNotifications().get()) { right ->
        o.realmsNotifications().set(right)
        bump()
    }

    ToggleRow("允许服务器列出玩家", "关闭", "开启", rightSelected = o.allowServerListing().get()) { right ->
        o.allowServerListing().set(right)
        bump()
    }

    minecraft.level?.let { level ->
        StaticRow("当前世界难度", level.difficulty.displayName.string)
    }
}

// ============================== 遥测数据页 ==============================

@Composable
internal fun ManosabaOptionsScreen.TelemetryPage() {
    val o = options
    @Suppress("UNUSED_VARIABLE") val version = tick

    SectionHeader("遥测数据")

    val allowsTelemetry = minecraft.allowsTelemetry()
    val extraAvailable = minecraft.extraTelemetryAvailable()

    ToggleRow(
        "发送额外遥测数据", "关闭", "开启",
        rightSelected = o.telemetryOptInExtra().get(),
        enabled = allowsTelemetry && extraAvailable
    ) { right ->
        o.telemetryOptInExtra().set(right)
        bump()
    }

    val state = when {
        !allowsTelemetry -> "未启用"
        o.telemetryOptInExtra().get() && extraAvailable -> "全部（包含可选数据）"
        else -> "最少（仅必需数据）"
    }
    StaticRow("当前状态", state)

    TextBlock("遥测数据用于帮助改进 Minecraft。可选遥测包含游戏模式、启动耗时等匿名统计信息，不会收集聊天内容或可识别个人身份的信息。可随时在本页关闭。")
}

// ============================== 鸣谢与授权页 ==============================

@Composable
internal fun ManosabaOptionsScreen.CreditsPage() {
    @Suppress("UNUSED_VARIABLE") val version = tick
    val screen = this

    SectionHeader("关于本模组")

    TextBlock("本项目由 LingyueNerina 制作，基于 Shiiyuko 的开源项目移植至 NeoForge 1.21.1。特别鸣谢 B站UP主 @真寻酱哟- 提供灵感与项目支持，及 Deepseek-Flash 提供开发辅助。")
    TextBlock("「魔法少女的魔女审判」主题标题界面是爱好者制作的非官方同人模组，与「魔法少女的魔女审判」原作开发团队及 Mojang Studios 均无关联。")
    TextBlock("界面素材与音乐的相关权利归「魔法少女的魔女审判」原作权利方所有；Minecraft 相关内容的一切权利归 Mojang Studios 所有。")

    SectionHeader("Minecraft 鸣谢与授权")

    ActionRow("鸣谢名单", "查看鸣谢名单", width = 420f) {
        screen.minecraft.setScreen(WinScreen(false, Runnable { screen.minecraft.setScreen(screen) }))
    }
    ActionRow("著作权说明", "打开著作权说明页面", width = 420f) {
        ConfirmLinkScreen.confirmLinkNow(screen, CommonLinks.ATTRIBUTION)
    }
    ActionRow("许可协议", "打开许可协议页面", width = 420f) {
        ConfirmLinkScreen.confirmLinkNow(screen, CommonLinks.LICENSES)
    }
}

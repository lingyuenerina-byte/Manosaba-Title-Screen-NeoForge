package me.shiiyuko.manosaba.ui

import androidx.compose.runtime.Composable
import com.mojang.blaze3d.platform.VideoMode
import me.shiiyuko.manosaba.ManosabaClientConfig
import me.shiiyuko.manosaba.ManosabaMod
import net.minecraft.client.AttackIndicatorStatus
import net.minecraft.client.CloudStatus
import net.minecraft.client.GraphicsStatus
import net.minecraft.client.ParticleStatus
import net.minecraft.client.PrioritizeChunkUpdates
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.player.ChatVisiblity
import net.neoforged.fml.ModList
import java.util.Locale
import java.util.Optional
import kotlin.math.roundToInt

// ============================== 文字页（聊天与文本） ==============================

@Composable
internal fun ManosabaOptionsScreen.TextPage() {
    val o = options
    @Suppress("UNUSED_VARIABLE") val version = tick

    SectionHeader("文本")

    val textSpeed = ManosabaClientConfig.TEXT_DISPLAY_SPEED.get()
    SliderRow("文本显示速度", textSpeed.toFloat(), 1f, 10f, "$textSpeed", step = 1f,
        onCommit = ::saveManosabaConfig) { v ->
        ManosabaClientConfig.TEXT_DISPLAY_SPEED.set(v.roundToInt())
        bump()
    }

    val autoPlay = ManosabaClientConfig.AUTO_PLAY_INTERVAL.get()
    SliderRow("自动播放间隔时间", autoPlay.toFloat(), 1f, 10f, "$autoPlay", step = 1f,
        onCommit = ::saveManosabaConfig) { v ->
        ManosabaClientConfig.AUTO_PLAY_INTERVAL.set(v.roundToInt())
        bump()
    }

    val skipUnread = ManosabaClientConfig.SKIP_UNREAD_TEXT.get()
    ToggleRow("文本跳过模式", "全部", "仅已读", rightSelected = !skipUnread) { right ->
        ManosabaClientConfig.SKIP_UNREAD_TEXT.set(!right)
        saveManosabaConfig()
        bump()
    }

    val branchHint = ManosabaClientConfig.SHOW_BRANCH_HINT.get()
    ToggleRow("在重要分支选项处显示提示", "关闭", "开启", rightSelected = branchHint) { right ->
        ManosabaClientConfig.SHOW_BRANCH_HINT.set(right)
        saveManosabaConfig()
        bump()
    }

    SelectRow("语言 / Language", currentLanguageName()) {
        val languages = minecraft.languageManager.languages
        val codes = languages.keys.toList()
        showPopup(
            PopupSpec("语言 / Language", codes.map { code ->
                val info = languages[code]
                val label = when {
                    info == null -> code
                    info.region.isEmpty() -> info.name
                    else -> "${info.name} (${info.region})"
                }
                label to (code == options.languageCode)
            }) { index ->
                applyLanguage(codes[index])
                closePopup()
            }
        )
    }

    SectionHeader("聊天")

    val visibilities = ChatVisiblity.values()
    SelectRow("聊天显示", chatVisibilityLabel(o.chatVisibility().get())) {
        showPopup(
            PopupSpec("聊天显示", visibilities.map { chatVisibilityLabel(it) to (it == o.chatVisibility().get()) }) { i ->
                o.chatVisibility().set(visibilities[i])
                closePopup()
                bump()
            }
        )
    }

    ToggleRow("聊天颜色", "关闭", "开启", rightSelected = o.chatColors().get()) { right ->
        o.chatColors().set(right)
        bump()
    }

    ToggleRow("聊天链接", "关闭", "开启", rightSelected = o.chatLinks().get()) { right ->
        o.chatLinks().set(right)
        bump()
    }

    ToggleRow("链接确认提示", "关闭", "开启", rightSelected = o.chatLinksPrompt().get()) { right ->
        o.chatLinksPrompt().set(right)
        bump()
    }

    val chatOpacity = o.chatOpacity().get()
    SliderRow("聊天透明度", chatOpacity.toFloat(), 0f, 1f, percentLabel(chatOpacity)) { v ->
        o.chatOpacity().set(v.toDouble())
        bump()
    }

    ToggleRow("文字背景", "仅聊天", "全局", rightSelected = !o.backgroundForChatOnly().get()) { right ->
        o.backgroundForChatOnly().set(!right)
        bump()
    }

    val bgOpacity = o.textBackgroundOpacity().get()
    SliderRow("文字背景不透明度", bgOpacity.toFloat(), 0f, 1f, percentLabel(bgOpacity)) { v ->
        o.textBackgroundOpacity().set(v.toDouble())
        bump()
    }

    val chatScale = o.chatScale().get()
    SliderRow("聊天大小", chatScale.toFloat(), 0f, 1f, percentLabel(chatScale)) { v ->
        o.chatScale().set(v.toDouble())
        bump()
    }

    val chatLineSpacing = o.chatLineSpacing().get()
    SliderRow("聊天行距", chatLineSpacing.toFloat(), 0f, 1f, percentLabel(chatLineSpacing)) { v ->
        o.chatLineSpacing().set(v.toDouble())
        bump()
    }

    val chatDelay = o.chatDelay().get()
    SliderRow("聊天延迟", chatDelay.toFloat(), 0f, 6f, chatDelayLabel(chatDelay), step = 0.1f) { v ->
        o.chatDelay().set(v.toDouble())
        bump()
    }

    val chatWidth = o.chatWidth().get()
    SliderRow("聊天宽度", chatWidth.toFloat(), 0f, 1f, percentLabel(chatWidth)) { v ->
        o.chatWidth().set(v.toDouble())
        bump()
    }

    val chatHeightFocused = o.chatHeightFocused().get()
    SliderRow("聊天高度（聚焦）", chatHeightFocused.toFloat(), 0f, 1f, percentLabel(chatHeightFocused)) { v ->
        o.chatHeightFocused().set(v.toDouble())
        bump()
    }

    val chatHeightUnfocused = o.chatHeightUnfocused().get()
    SliderRow("聊天高度（未聚焦）", chatHeightUnfocused.toFloat(), 0f, 1f, percentLabel(chatHeightUnfocused)) { v ->
        o.chatHeightUnfocused().set(v.toDouble())
        bump()
    }

    ToggleRow("命令自动补全", "关闭", "开启", rightSelected = o.autoSuggestions().get()) { right ->
        o.autoSuggestions().set(right)
        bump()
    }

    ToggleRow("隐藏匹配名称", "关闭", "开启", rightSelected = o.hideMatchedNames().get()) { right ->
        o.hideMatchedNames().set(right)
        bump()
    }

    ToggleRow("仅显示安全聊天", "关闭", "开启", rightSelected = o.onlyShowSecureChat().get()) { right ->
        o.onlyShowSecureChat().set(right)
        bump()
    }

    SectionHeader("字体")

    ToggleRow("强制 Unicode 字体", "关闭", "开启", rightSelected = o.forceUnicodeFont().get()) { right ->
        o.forceUnicodeFont().set(right)
        bump()
    }

    ToggleRow("日语字形变体", "关闭", "开启", rightSelected = o.japaneseGlyphVariants().get()) { right ->
        o.japaneseGlyphVariants().set(right)
        bump()
    }
}

private fun chatVisibilityLabel(value: ChatVisiblity): String = when (value) {
    ChatVisiblity.FULL -> "全部"
    ChatVisiblity.SYSTEM -> "仅命令"
    ChatVisiblity.HIDDEN -> "隐藏"
}

private fun chatDelayLabel(value: Double): String =
    if (value <= 0.0) "立即" else String.format(Locale.ROOT, "%.1f 秒", value)

// ============================== 图形页（图形 / 视频 / 视角） ==============================

@Composable
internal fun ManosabaOptionsScreen.GraphicsPage() {
    val o = options
    @Suppress("UNUSED_VARIABLE") val version = tick

    SectionHeader("显示")

    ToggleRow("显示模式", "窗口", "全屏", rightSelected = o.fullscreen().get()) { right ->
        o.fullscreen().set(right)
        bump()
    }

    SelectRow("画面分辨率", "${minecraft.window.width} x ${minecraft.window.height}") {
        val resolutions = resolutionOptions()
        showPopup(
            PopupSpec("画面分辨率", resolutions.map { (label, size) ->
                label to (size.first == minecraft.window.width && size.second == minecraft.window.height)
            }) { index ->
                val (w, h) = resolutions[index].second
                if (w != minecraft.window.width || h != minecraft.window.height) {
                    applyResolution(w, h)
                }
                closePopup()
            }
        )
    }

    SelectRow("全屏分辨率", currentFullscreenModeLabel()) {
        val monitor = minecraft.window.findBestMonitor()
        if (monitor == null) {
            showPopup(PopupSpec("全屏分辨率", listOf("当前设备不可用" to true)) { closePopup() })
            return@SelectRow
        }
        val modes: List<VideoMode> = (0 until monitor.modeCount).map { monitor.getMode(it) }
        val pref = minecraft.window.preferredFullscreenVideoMode
        val rows = buildList {
            add("使用当前桌面分辨率" to pref.isEmpty)
            modes.forEach { m ->
                val selected = pref.isPresent && pref.get().let {
                    it.width == m.width && it.height == m.height && it.refreshRate == m.refreshRate
                }
                add("${m.width} x ${m.height} @ ${m.refreshRate}Hz" to selected)
            }
        }
        showPopup(PopupSpec("全屏分辨率", rows) { index ->
            val mode = if (index == 0) Optional.empty<VideoMode>() else Optional.of(modes[index - 1])
            minecraft.window.setPreferredFullscreenVideoMode(mode)
            minecraft.window.changeFullscreenVideoMode()
            closePopup()
            bump()
        })
    }

    val guiScale = o.guiScale().get()
    SelectRow("界面尺寸", if (guiScale == 0) "自动" else "$guiScale") {
        val maxScale = runCatching {
            minecraft.window.calculateScale(0, minecraft.isEnforceUnicode)
        }.getOrDefault(4)
        val values = (0..maxScale.coerceIn(1, 4)).toMutableList()
        if (guiScale !in values) values.add(guiScale)
        showPopup(
            PopupSpec("界面尺寸", values.map { v -> (if (v == 0) "自动" else "$v") to (v == guiScale) }) { index ->
                o.guiScale().set(values[index])
                closePopup()
                bump()
            }
        )
    }

    val framerate = o.framerateLimit().get()
    SelectRow("最大帧数", frameRateLabel(framerate)) {
        showPopup(
            PopupSpec("最大帧数", ManosabaOptionsScreen.FRAMERATE_OPTIONS.map { v ->
                frameRateLabel(v) to (v == framerate || (v >= 260 && framerate >= 260))
            }) { index ->
                o.framerateLimit().set(ManosabaOptionsScreen.FRAMERATE_OPTIONS[index])
                closePopup()
                bump()
            }
        )
    }

    ToggleRow("垂直同步", "关闭", "开启", rightSelected = o.enableVsync().get()) { right ->
        o.enableVsync().set(right)
        bump()
    }

    ToggleRow("自动保存指示器", "关闭", "开启", rightSelected = o.showAutosaveIndicator().get()) { right ->
        o.showAutosaveIndicator().set(right)
        bump()
    }

    SectionHeader("画质")

    val gfxValues = GraphicsStatus.values()
    SelectRow("图像品质", graphicsLabel(o.graphicsMode().get())) {
        showPopup(
            PopupSpec("图像品质", gfxValues.map { graphicsLabel(it) to (it == o.graphicsMode().get()) }) { index ->
                o.graphicsMode().set(gfxValues[index])
                closePopup()
                bump()
            }
        )
    }

    val renderDistance = o.renderDistance().get()
    SliderRow("渲染距离", renderDistance.toFloat(), 2f, 32f, "$renderDistance", step = 1f) { v ->
        o.renderDistance().set(v.roundToInt())
        bump()
    }

    val simulationDistance = o.simulationDistance().get()
    SliderRow("模拟距离", simulationDistance.toFloat(), 5f, 32f, "$simulationDistance", step = 1f) { v ->
        o.simulationDistance().set(v.roundToInt())
        bump()
    }

    val entityDistance = o.entityDistanceScaling().get()
    SliderRow("实体渲染距离", entityDistance.toFloat(), 0.5f, 5f, percentLabel(entityDistance), step = 0.05f) { v ->
        o.entityDistanceScaling().set(v.toDouble())
        bump()
    }

    val biomeBlend = o.biomeBlendRadius().get()
    val biomeLabel = "${biomeBlend * 2 + 1}×${biomeBlend * 2 + 1}"
    SliderRow("生物群系过渡距离", biomeBlend.toFloat(), 0f, 7f, biomeLabel, step = 1f) { v ->
        o.biomeBlendRadius().set(v.roundToInt())
        bump()
    }

    ToggleRow("平滑光照", "关闭", "开启", rightSelected = o.ambientOcclusion().get()) { right ->
        o.ambientOcclusion().set(right)
        bump()
    }

    ToggleRow("实体阴影", "关闭", "开启", rightSelected = o.entityShadows().get()) { right ->
        o.entityShadows().set(right)
        bump()
    }

    val gamma = o.gamma().get()
    SliderRow("亮度", gamma.toFloat(), 0f, 1f, percentLabel(gamma)) { v ->
        o.gamma().set(v.toDouble())
        bump()
    }

    val mipmap = o.mipmapLevels().get()
    SliderRow("纹理多级渐远", mipmap.toFloat(), 0f, 4f, if (mipmap == 0) "关闭" else "$mipmap", step = 1f) { v ->
        o.mipmapLevels().set(v.roundToInt())
        bump()
    }

    val cloudValues = CloudStatus.values()
    SelectRow("云", cloudLabel(o.cloudStatus().get())) {
        showPopup(
            PopupSpec("云", cloudValues.map { cloudLabel(it) to (it == o.cloudStatus().get()) }) { index ->
                o.cloudStatus().set(cloudValues[index])
                closePopup()
                bump()
            }
        )
    }

    val particleValues = ParticleStatus.values()
    SelectRow("粒子效果", particleLabel(o.particles().get())) {
        showPopup(
            PopupSpec("粒子效果", particleValues.map { particleLabel(it) to (it == o.particles().get()) }) { index ->
                o.particles().set(particleValues[index])
                closePopup()
                bump()
            }
        )
    }

    val attackValues = AttackIndicatorStatus.values()
    SelectRow("攻击指示器", attackLabel(o.attackIndicator().get())) {
        showPopup(
            PopupSpec("攻击指示器", attackValues.map { attackLabel(it) to (it == o.attackIndicator().get()) }) { index ->
                o.attackIndicator().set(attackValues[index])
                closePopup()
                bump()
            }
        )
    }

    val chunkValues = PrioritizeChunkUpdates.values()
    SelectRow("优先区块更新", chunkUpdateLabel(o.prioritizeChunkUpdates().get())) {
        showPopup(
            PopupSpec("优先区块更新", chunkValues.map { chunkUpdateLabel(it) to (it == o.prioritizeChunkUpdates().get()) }) { index ->
                o.prioritizeChunkUpdates().set(chunkValues[index])
                closePopup()
                bump()
            }
        )
    }

    val blurriness = o.menuBackgroundBlurriness().get()
    SliderRow("菜单背景模糊度", blurriness.toFloat(), 0f, 10f, if (blurriness == 0) "关闭" else "$blurriness", step = 1f) { v ->
        o.menuBackgroundBlurriness().set(v.roundToInt())
        bump()
    }

    SectionHeader("视角与效果")

    val fov = o.fov().get()
    SliderRow("视场角", fov.toFloat(), 30f, 110f, "$fov", step = 1f) { v ->
        o.fov().set(v.roundToInt())
        bump()
    }

    ToggleRow("视角摇晃", "关闭", "开启", rightSelected = o.bobView().get()) { right ->
        o.bobView().set(right)
        bump()
    }

    val fovEffect = o.fovEffectScale().get()
    SliderRow("视场角效果", fovEffect.toFloat(), 0f, 1f, percentLabel(fovEffect)) { v ->
        o.fovEffectScale().set(v.toDouble())
        bump()
    }

    val screenEffect = o.screenEffectScale().get()
    SliderRow("屏幕扭曲效果", screenEffect.toFloat(), 0f, 1f, percentLabel(screenEffect)) { v ->
        o.screenEffectScale().set(v.toDouble())
        bump()
    }

    val glintSpeed = o.glintSpeed().get()
    SliderRow("附魔光效速度", glintSpeed.toFloat(), 0f, 1f, percentLabel(glintSpeed)) { v ->
        o.glintSpeed().set(v.toDouble())
        bump()
    }

    val glintStrength = o.glintStrength().get()
    SliderRow("附魔光效强度", glintStrength.toFloat(), 0f, 1f, percentLabel(glintStrength)) { v ->
        o.glintStrength().set(v.toDouble())
        bump()
    }

    CompatSection()
}

// ============================== 第三方模组兼容入口 ==============================

/**
 * 已适配模组的设置入口（检测到模组时显示在图形页末尾，未安装则整节隐藏）。
 * 扩展点：新增模组适配只需追加一条 CompatEntry（modId + 按钮标签 + 候选屏幕类名）。
 */
private class CompatEntry(val modId: String, val label: String, val screenClasses: List<String>)

private val COMPAT_ENTRIES = listOf(
    // Sodium（钠）：新版（含 NeoForge 构建，实测 0.8.13）入口为静态工厂
    // VideoSettingsScreen.createScreen(parent)；旧版为 SodiumOptionsGUI
    // 静态工厂或公开的 (Screen) 构造器
    CompatEntry("sodium", "Sodium 视频设置", listOf(
        "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen",
        "net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI",
        "net.caffeinemc.mods.sodium.client.gui.SodiumVideoOptionsScreen",
        "me.jellysquid.mods.sodium.client.gui.SodiumVideoOptionsScreen"
    )),
    // Iris Shaders：光影包选择界面（ShaderPackScreen(parent)）
    CompatEntry("iris", "Iris 光影设置", listOf(
        "net.irisshaders.iris.gui.screen.ShaderPackScreen"
    ))
)

/** 图形页末尾的「模组兼容」小节：仅列出已加载模组的入口 */
@Composable
internal fun ManosabaOptionsScreen.CompatSection() {
    val loaded = COMPAT_ENTRIES.filter { entry ->
        runCatching { ModList.get().isLoaded(entry.modId) }.getOrDefault(false)
    }
    if (loaded.isEmpty()) return

    SectionHeader("模组兼容")
    loaded.forEach { entry ->
        ActionRow(entry.label, "打开") { openCompat(entry) }
    }
}

/**
 * 反射创建模组界面并切换：先尝试静态工厂 createScreen(parent)（覆盖 Sodium 0.6+），
 * 再尝试公开构造器 (Screen)（覆盖 Iris 与旧版 Sodium）；类 / 方法缺失时记录日志、
 * 保持原界面（不抛异常，未适配版本自动降级为无入口）。
 */
private fun ManosabaOptionsScreen.openCompat(entry: CompatEntry) {
    val screen = entry.screenClasses.firstNotNullOfOrNull { className ->
        runCatching {
            val cls = Class.forName(className)
            runCatching {
                cls.getMethod("createScreen", Screen::class.java).invoke(null, this) as? Screen
            }.getOrNull() ?: runCatching {
                cls.getConstructor(Screen::class.java).newInstance(this) as? Screen
            }.getOrNull()
        }.onFailure {
            ManosabaMod.LOGGER.warn("[Manosaba] Compat screen unavailable: $className (${it.javaClass.simpleName})")
        }.getOrNull()
    }
    if (screen != null) {
        openExternalScreen(screen)
    } else {
        ManosabaMod.LOGGER.error("[Manosaba] Failed to open compat screen for mod '${entry.modId}'")
    }
}

private fun ManosabaOptionsScreen.currentFullscreenModeLabel(): String {
    val pref = minecraft.window.preferredFullscreenVideoMode
    return if (pref.isEmpty) "使用当前桌面分辨率"
    else pref.get().let { "${it.width} x ${it.height} @ ${it.refreshRate}Hz" }
}

private fun graphicsLabel(value: GraphicsStatus): String = when (value) {
    GraphicsStatus.FAST -> "流畅"
    GraphicsStatus.FANCY -> "高品质"
    GraphicsStatus.FABULOUS -> "极佳"
}

private fun cloudLabel(value: CloudStatus): String = when (value) {
    CloudStatus.OFF -> "关闭"
    CloudStatus.FAST -> "快速"
    CloudStatus.FANCY -> "高品质"
}

private fun particleLabel(value: ParticleStatus): String = when (value) {
    ParticleStatus.ALL -> "全部"
    ParticleStatus.DECREASED -> "减少"
    ParticleStatus.MINIMAL -> "最少"
}

private fun attackLabel(value: AttackIndicatorStatus): String = when (value) {
    AttackIndicatorStatus.OFF -> "关闭"
    AttackIndicatorStatus.CROSSHAIR -> "准星"
    AttackIndicatorStatus.HOTBAR -> "快捷栏"
}

private fun chunkUpdateLabel(value: PrioritizeChunkUpdates): String = when (value) {
    PrioritizeChunkUpdates.NONE -> "无"
    PrioritizeChunkUpdates.PLAYER_AFFECTED -> "跟随玩家"
    PrioritizeChunkUpdates.NEARBY -> "附近区块"
}

// ============================== 声音页 ==============================

@Composable
internal fun ManosabaOptionsScreen.SoundPage() {
    val o = options
    @Suppress("UNUSED_VARIABLE") val version = tick

    fun snapVolume(v: Float): Double = (v * 10f).roundToInt() / 10.0

    SectionHeader("音量")

    val master = o.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).get()
    SliderRow("主音量", master.toFloat(), 0f, 1f, "${(master * 10).roundToInt()}") { v ->
        o.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(snapVolume(v))
        bump()
    }

    val music = o.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MUSIC).get()
    SliderRow("背景音乐", music.toFloat(), 0f, 1f, "${(music * 10).roundToInt()}") { v ->
        o.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MUSIC).set(snapVolume(v))
        bump()
    }

    val records = o.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.RECORDS).get()
    SliderRow("唱片机与音符盒", records.toFloat(), 0f, 1f, "${(records * 10).roundToInt()}") { v ->
        o.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.RECORDS).set(snapVolume(v))
        bump()
    }

    val weather = o.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.WEATHER).get()
    SliderRow("天气音效", weather.toFloat(), 0f, 1f, "${(weather * 10).roundToInt()}") { v ->
        o.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.WEATHER).set(snapVolume(v))
        bump()
    }

    val blocks = o.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.BLOCKS).get()
    SliderRow("方块音效", blocks.toFloat(), 0f, 1f, "${(blocks * 10).roundToInt()}") { v ->
        o.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.BLOCKS).set(snapVolume(v))
        bump()
    }

    val hostile = o.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.HOSTILE).get()
    SliderRow("敌对生物", hostile.toFloat(), 0f, 1f, "${(hostile * 10).roundToInt()}") { v ->
        o.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.HOSTILE).set(snapVolume(v))
        bump()
    }

    val neutral = o.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.NEUTRAL).get()
    SliderRow("友好生物", neutral.toFloat(), 0f, 1f, "${(neutral * 10).roundToInt()}") { v ->
        o.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.NEUTRAL).set(snapVolume(v))
        bump()
    }

    val players = o.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.PLAYERS).get()
    SliderRow("玩家", players.toFloat(), 0f, 1f, "${(players * 10).roundToInt()}") { v ->
        o.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.PLAYERS).set(snapVolume(v))
        bump()
    }

    val ambient = o.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.AMBIENT).get()
    SliderRow("环境音效", ambient.toFloat(), 0f, 1f, "${(ambient * 10).roundToInt()}") { v ->
        o.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.AMBIENT).set(snapVolume(v))
        bump()
    }

    val voice = o.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.VOICE).get()
    SliderRow("角色语音", voice.toFloat(), 0f, 1f, "${(voice * 10).roundToInt()}") { v ->
        o.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.VOICE).set(snapVolume(v))
        bump()
    }

    SectionHeader("输出与字幕")

    SelectRow("声音设备", soundDeviceLabel(o.soundDevice().get())) {
        val devices = listOf("") + minecraft.soundManager.availableSoundDevices
        val current = o.soundDevice().get()
        showPopup(
            PopupSpec("声音设备", devices.map { soundDeviceLabel(it) to (it == current) }) { index ->
                o.soundDevice().set(devices[index])
                closePopup()
                bump()
            }
        )
    }

    ToggleRow("字幕", "关闭", "开启", rightSelected = o.showSubtitles().get()) { right ->
        o.showSubtitles().set(right)
        bump()
    }

    ToggleRow("定向音频", "关闭", "开启", rightSelected = o.directionalAudio().get()) { right ->
        o.directionalAudio().set(right)
        bump()
    }
}

private fun soundDeviceLabel(device: String): String = when {
    device.isEmpty() -> "默认设备"
    device.startsWith("OpenAL Soft on ") -> device.substring("OpenAL Soft on ".length)
    else -> device
}

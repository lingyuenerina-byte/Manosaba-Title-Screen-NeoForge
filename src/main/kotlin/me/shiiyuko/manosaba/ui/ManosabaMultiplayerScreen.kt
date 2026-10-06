package me.shiiyuko.manosaba.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.shiiyuko.manosaba.ManosabaMod
import me.shiiyuko.manosaba.bridge.ComposeInputBridge
import me.shiiyuko.manosaba.bridge.MultiplayerHost
import net.minecraft.SharedConstants
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.LoadingDotsText
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import org.jetbrains.skia.Image
import org.lwjgl.glfw.GLFW
import java.net.UnknownHostException
import java.util.IdentityHashMap
import java.util.concurrent.ScheduledThreadPoolExecutor

/**
 * 多人游戏界面（TEST26）：原版 JoinMultiplayerScreen 作为逻辑壳（服务器列表 / 局域网检测 /
 * Ping / 子屏跳转），本类为内嵌 Compose 界面（不是当前屏幕），
 * 由 JoinMultiplayerScreenMixin 创建并转发渲染与输入。
 *
 * 视觉与设置界面一致：标题屏「倒映」背景 + 磨砂底板 + 列表行组件 + 底部按钮；
 * 服务器 Ping 由本界面自行触发（原版是在列表行 render 中触发，此处渲染已被接管）。
 */
class ManosabaMultiplayerUi(
    private val screen: JoinMultiplayerScreen,
    private val ghostSource: ManosabaTitleScreen?
) : ComposeScreen(Component.literal("Manosaba Multiplayer")), ComposeInputBridge.Target {

    private val mc = Minecraft.getInstance()
    private val host: MultiplayerHost get() = screen as MultiplayerHost

    private var tick by mutableStateOf(0)
    private var confirmDelete by mutableStateOf<ServerData?>(null)
    private val scrollState = ScrollState(0)

    /** 双击进入检测（复刻原版列表 lastClickTime 语义） */
    private var lastClickEntry: ServerSelectionList.Entry? = null
    private var lastClickTime = 0L

    /** 服务器图标解码缓存：byte[] 引用 → 图标（null = 已尝试且不可用） */
    private val iconCache = IdentityHashMap<ByteArray, ImageBitmap?>()

    /** Ping 线程池：dispose 后惰性重建（子屏往返时同一实例会复用） */
    private var pingPool: ScheduledThreadPoolExecutor? = null
    private var lastPingSweep = 0L

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

    /** 原版屏幕 tick 驱动：250ms 节流触发 Ping 刷新与重绘 */
    fun onHostTick() {
        val now = Util.getMillis()
        if (now - lastPingSweep >= 250L) {
            lastPingSweep = now
            ensurePings()
            bump()
        }
    }

    /**
     * 数据变化后的主动刷新（宿主 mixin 在 init 重建后调用）：
     * 同屏 setScreen 不触发 removed、不重建 Compose 场景，编辑保存 / 删除等路径
     * 必须由宿主显式通知，重组后才能立即呈现新的服务器列表。
     * 同时重绑全局点击音效（OptionsUi 为多界面共享单例）。
     */
    fun onHostDataChanged() {
        OptionsUi.playClick = { playClick() }
        bump()
    }

    /** 释放 Ping 线程池与 Compose/Skia 资源（宿主 removed / dispose 时调用） */
    fun disposeUi() {
        pingPool?.shutdownNow()
        pingPool = null
        disposeCompose()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        // 删除确认弹层打开时 Esc 只关闭弹层；其余情况 ESC 交还宿主（原版 onClose → 返回上一屏）
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && confirmDelete != null) {
            playCancel()
            confirmDelete = null
            return true
        }
        if (confirmDelete == null) {
            // 原版键盘语义：回车加入选中服务器、F5 刷新列表
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (host.`manosaba$selectedEntry`() != null) {
                    playClick()
                    host.`manosaba$prepareConnect`()
                    screen.joinSelectedServer()
                    return true
                }
            }
            if (keyCode == GLFW.GLFW_KEY_F5) {
                playClick()
                host.`manosaba$refresh`()
                return true
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    // ---------------- Ping（复刻原版 OnlineServerEntry.render 的触发逻辑） ----------------

    private fun pingPool(): ScheduledThreadPoolExecutor {
        val pool = pingPool
        if (pool != null && !pool.isShutdown) return pool
        // 池因 dispose（宿主 removed / 删除确认释放）被关闭后重建：旧池的 ping 任务已中断，
        // 停留 PINGING 的条目不会再收到完成回调，重置为 INITIAL 让 ensurePings 重新发起
        host.`manosaba$entries`().forEach { entry ->
            if (entry is ServerSelectionList.OnlineServerEntry) {
                val data = entry.serverData
                if (data.state() == ServerData.State.PINGING) {
                    data.setState(ServerData.State.INITIAL)
                }
            }
        }
        val fresh = ScheduledThreadPoolExecutor(4) { runnable ->
            Thread(runnable, "Manosaba Server Pinger").apply { isDaemon = true }
        }
        pingPool = fresh
        return fresh
    }

    private fun ensurePings() {
        host.`manosaba$entries`().forEach { entry ->
            if (entry is ServerSelectionList.OnlineServerEntry) {
                val data = entry.serverData
                if (data.state() == ServerData.State.INITIAL) {
                    data.setState(ServerData.State.PINGING)
                    data.motd = CommonComponents.EMPTY
                    data.status = CommonComponents.EMPTY
                    pingPool().submit { pingServer(data) }
                }
            }
        }
    }

    private fun pingServer(data: ServerData) {
        try {
            screen.pinger.pingServer(
                data,
                { mc.execute { screen.servers.save() } },
                {
                    data.setState(
                        if (data.protocol == SharedConstants.getCurrentVersion().protocolVersion) {
                            ServerData.State.SUCCESSFUL
                        } else {
                            ServerData.State.INCOMPATIBLE
                        }
                    )
                    mc.execute { bump() }
                }
            )
        } catch (e: UnknownHostException) {
            data.setState(ServerData.State.UNREACHABLE)
            data.motd = CANT_RESOLVE_TEXT
            mc.execute { bump() }
        } catch (e: Exception) {
            data.setState(ServerData.State.UNREACHABLE)
            data.motd = CANT_CONNECT_TEXT
            mc.execute { bump() }
        }
    }

    // ---------------- 页面 ----------------

    @Composable
    override fun Content() {
        val version = tick
        val deleting = confirmDelete

        Box(modifier = Modifier.fillMaxSize()) {
            // 背景「倒映」：主界面同构图的幽灵层 + 磨砂底板（与设置页一致）
            ghostSource?.GhostLayer(GHOST_ALPHA)
            OptionsSprites["OptionsUnderlay"]?.let { bitmap ->
                Image(
                    bitmap, null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }

            BasicText(
                "多人游戏",
                modifier = Modifier.offset(x = 56.dp, y = 30.dp),
                style = rowTextStyle(62f, COLOR_TAB_LABEL_ACTIVE)
            )

            MultiplayerList(version)
            BottomButtons(version)

            deleting?.let { DeleteConfirmLayer(it) }
        }
    }

    // ---------------- 服务器列表 ----------------

    @Composable
    private fun MultiplayerList(version: Int) {
        @Suppress("UNUSED_VARIABLE") val v = version
        val entries = host.`manosaba$entries`()
        val selected = host.`manosaba$selectedEntry`()

        PageScroll(scrollState) {
            entries.forEach { entry ->
                key(entry) {
                    ServerRow(entry, entry === selected, version) { clicked -> onRowClick(clicked) }
                }
            }
        }
    }

    /**
     * 列表项点击状态机（复刻原版列表双击语义）：单击 = 仅选中（选择框语义）；
     * 250ms 内再次点击同一行 = 进入该服务器（「加入服务器」按钮 / 回车键为并行入口）；
     * 编辑、删除仍使用下方独立按钮。LAN 扫描提示行为纯展示，不参与选择。
     */
    private fun onRowClick(entry: ServerSelectionList.Entry) {
        if (entry is ServerSelectionList.LANHeader) return
        screen.setSelected(entry)
        val now = Util.getMillis()
        if (entry === lastClickEntry && now - lastClickTime < DOUBLE_CLICK_MS) {
            lastClickEntry = null
            playClick()
            host.`manosaba$prepareConnect`()
            screen.joinSelectedServer()
        } else {
            lastClickEntry = entry
            lastClickTime = now
        }
        bump()
    }

    @Composable
    private fun ServerRow(
        entry: ServerSelectionList.Entry,
        selected: Boolean,
        version: Int,
        onClick: (ServerSelectionList.Entry) -> Unit
    ) {
        @Suppress("UNUSED_VARIABLE") val v = version
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT.dp)
                .hoverable(interaction)
                .pointerInput(entry) {
                    detectTapGestures { onClick(entry) }
                }
        ) {
            // 选中定格红横幅（与设置页行悬停同款长红笔刷素材）；未选中悬停时轻微提亮
            if (selected) {
                OptionsSprites["Item_Highlighted"]?.let {
                    Image(
                        it, null,
                        modifier = Modifier
                            .offset(x = BANNER_LEFT.dp, y = ((ROW_HEIGHT - BANNER_HEIGHT) / 2f).dp)
                            .size((BANNER_RIGHT - BANNER_LEFT).dp, BANNER_HEIGHT.dp),
                        contentScale = ContentScale.FillBounds
                    )
                }
            } else if (hovered) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.06f)))
            }

            when (entry) {
                is ServerSelectionList.LANHeader -> LanHeaderContent(version)
                is ServerSelectionList.OnlineServerEntry -> OnlineEntryContent(entry, version)
                is ServerSelectionList.NetworkServerEntry -> NetworkEntryContent(entry, version)
                else -> {}
            }
        }
    }

    /** 局域网扫描提示行（原版为居中两行，此处合并为单行 + 动态省略号） */
    @Composable
    private fun LanHeaderContent(version: Int) {
        @Suppress("UNUSED_VARIABLE") val v = version
        BasicText(
            Component.translatable("lanServer.scanning").string + LoadingDotsText.get(Util.getMillis()),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            style = rowTextStyle(30f, COLOR_TEXT_DIM).copy(textAlign = TextAlign.Center)
        )
    }

    @Composable
    private fun OnlineEntryContent(entry: ServerSelectionList.OnlineServerEntry, version: Int) {
        @Suppress("UNUSED_VARIABLE") val v = version
        val data = entry.serverData

        // 图标：byte[] 引用变化时重新解码（Ping 成功后由网络线程写入）
        val iconBytes = data.getIconBytes()
        val icon = remember(iconBytes) {
            iconBytes?.let { bytes ->
                iconCache.getOrPut(bytes) {
                    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
                }
            }
        }
        EntryIcon(icon)

        BasicText(
            data.name,
            modifier = Modifier.offset(x = 76.dp, y = 8.dp).width(900.dp),
            style = rowTextStyle(30f),
            maxLines = 1,
            softWrap = false
        )

        val motd = data.motd
        if (motd != null && motd.string.isNotEmpty()) {
            BasicText(
                motd.string,
                modifier = Modifier.offset(x = 76.dp, y = 38.dp).width(900.dp),
                style = rowTextStyle(26f, COLOR_TEXT_DIM),
                maxLines = 1,
                softWrap = false
            )
        }

        val (statusText, statusColor) = when (data.state()) {
            ServerData.State.INITIAL, ServerData.State.PINGING -> "正在 Ping……" to COLOR_TEXT_DIM
            ServerData.State.INCOMPATIBLE -> "版本不兼容（${data.version.string}）" to COLOR_PINK
            ServerData.State.UNREACHABLE -> "无法连接" to COLOR_PINK
            ServerData.State.SUCCESSFUL -> {
                val players = data.players
                val info = if (players != null) {
                    "在线 ${players.online()}/${players.max()} · ${data.ping}ms"
                } else {
                    "${data.ping}ms"
                }
                info to COLOR_TEXT
            }
        }
        BasicText(
            statusText,
            modifier = Modifier.offset(x = 1040.dp, y = 20.dp).width(570.dp),
            style = rowTextStyle(27f, statusColor).copy(textAlign = TextAlign.Right),
            maxLines = 1,
            softWrap = false
        )
    }

    @Composable
    private fun NetworkEntryContent(entry: ServerSelectionList.NetworkServerEntry, version: Int) {
        @Suppress("UNUSED_VARIABLE") val v = version
        val lan = entry.serverData
        EntryIcon(null)

        BasicText(
            Component.translatable("lanServer.title").string,
            modifier = Modifier.offset(x = 76.dp, y = 8.dp).width(900.dp),
            style = rowTextStyle(30f),
            maxLines = 1,
            softWrap = false
        )
        BasicText(
            lan.getMotd(),
            modifier = Modifier.offset(x = 76.dp, y = 38.dp).width(900.dp),
            style = rowTextStyle(26f, COLOR_TEXT_DIM),
            maxLines = 1,
            softWrap = false
        )

        val address = if (mc.options.hideServerAddress) {
            Component.translatable("selectServer.hiddenAddress").string
        } else {
            lan.getAddress()
        }
        BasicText(
            address,
            modifier = Modifier.offset(x = 1040.dp, y = 20.dp).width(570.dp),
            style = rowTextStyle(27f, COLOR_TEXT_DIM).copy(textAlign = TextAlign.Right),
            maxLines = 1,
            softWrap = false
        )
    }

    @Composable
    private fun EntryIcon(icon: ImageBitmap?) {
        Box(Modifier.offset(x = 20.dp, y = 12.dp).size(44.dp)) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            if (icon != null) {
                Image(icon, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            }
        }
    }

    // ---------------- 底部按钮 ----------------

    @Composable
    private fun BottomButtons(version: Int) {
        @Suppress("UNUSED_VARIABLE") val v = version
        val selected = host.`manosaba$selectedEntry`()
        val canJoin = selected != null && selected !is ServerSelectionList.LANHeader
        val canEdit = selected is ServerSelectionList.OnlineServerEntry

        // 第一行：加入 / 直接连接 / 添加（3 列等宽等距：w=306、gap=24，外缘 477→1443）
        RowButton(477f, 945f, 306f, "加入服务器", enabled = canJoin, accent = true) {
            host.`manosaba$prepareConnect`()
            screen.joinSelectedServer()
        }
        RowButton(807f, 945f, 306f, "直接连接") { host.`manosaba$directJoin`() }
        RowButton(1137f, 945f, 306f, "添加服务器") { host.`manosaba$addServer`() }

        // 第二行：编辑 / 删除 / 刷新 / 返回（4 列等宽等距：w=228、gap=18，外缘与第一行完全对齐）
        RowButton(477f, 1010f, 228f, "编辑", enabled = canEdit) { host.`manosaba$editServer`() }
        RowButton(723f, 1010f, 228f, "删除", enabled = canEdit, accent = true) {
            confirmDelete = (selected as ServerSelectionList.OnlineServerEntry).serverData
        }
        RowButton(969f, 1010f, 228f, "刷新") { host.`manosaba$refresh`() }
        RowButton(1215f, 1010f, 228f, "返回", cancelSound = true) { screen.onClose() }
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

    // ---------------- 删除确认弹层 ----------------

    @Composable
    private fun DeleteConfirmLayer(data: ServerData) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
            // 全屏点击层：点击弹层以外任意处取消
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        playCancel()
                        confirmDelete = null
                    }
            )
            Box(Modifier.align(Alignment.Center).size(880.dp, 360.dp)) {
                OptionsSprites["DialogBase"]?.let {
                    Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                }
                BasicText(
                    "确定要删除服务器\n「${data.name}」吗？",
                    modifier = Modifier.align(Alignment.Center).offset(y = (-40).dp),
                    style = dialogTextStyle(38f, COLOR_DIALOG_TEXT).copy(textAlign = TextAlign.Center)
                )
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-42).dp),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DialogActionButton("取消", cancelSound = true) {
                        confirmDelete = null
                    }
                    DialogActionButton("删除", danger = true) {
                        confirmDelete = null
                        // 先释放 Compose 场景再走原版删除回调：同屏 setScreen 不触发 removed、
                        // 场景不重建，列表无法感知删除结果；释放后回屏下一帧自动重建并读取新列表
                        disposeUi()
                        host.`manosaba$deleteSelected`()
                    }
                }
            }
        }
    }

    @Composable
    private fun DialogActionButton(
        text: String,
        danger: Boolean = false,
        cancelSound: Boolean = false,
        onClick: () -> Unit
    ) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val plate = OptionsSprites[if (hovered) "ToggleBody_Highlighted" else "ToggleBody_Enabled"]

        Box(
            modifier = Modifier
                .size(230.dp, TOGGLE_HEIGHT.dp)
                .clickable(interactionSource = interaction, indication = null) {
                    if (cancelSound) playCancel() else playClick()
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            plate?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            val color = when {
                danger && hovered -> COLOR_PINK_BRIGHT
                danger -> COLOR_PINK
                else -> COLOR_TEXT
            }
            BasicText(text, style = rowTextStyle(30f, color))
        }
    }

    private companion object {
        const val ROW_HEIGHT = 68f

        /** 双击进入判定窗口（毫秒，复刻原版列表语义） */
        const val DOUBLE_CLICK_MS = 250L

        val CANT_RESOLVE_TEXT: Component =
            Component.translatable("multiplayer.status.cannot_resolve").withColor(-65536)
        val CANT_CONNECT_TEXT: Component =
            Component.translatable("multiplayer.status.cannot_connect").withColor(-65536)
    }
}

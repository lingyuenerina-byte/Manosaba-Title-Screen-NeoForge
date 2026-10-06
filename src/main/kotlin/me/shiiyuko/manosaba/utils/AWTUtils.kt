package me.shiiyuko.manosaba.utils

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import java.awt.Component
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.event.KeyEvent as AwtKeyEvent

private val keyCodeMap = mapOf(
    GLFW.GLFW_KEY_SPACE to AwtKeyEvent.VK_SPACE,
    GLFW.GLFW_KEY_APOSTROPHE to AwtKeyEvent.VK_QUOTE,
    GLFW.GLFW_KEY_COMMA to AwtKeyEvent.VK_COMMA,
    GLFW.GLFW_KEY_MINUS to AwtKeyEvent.VK_MINUS,
    GLFW.GLFW_KEY_PERIOD to AwtKeyEvent.VK_PERIOD,
    GLFW.GLFW_KEY_SLASH to AwtKeyEvent.VK_SLASH,
    GLFW.GLFW_KEY_0 to AwtKeyEvent.VK_0,
    GLFW.GLFW_KEY_1 to AwtKeyEvent.VK_1,
    GLFW.GLFW_KEY_2 to AwtKeyEvent.VK_2,
    GLFW.GLFW_KEY_3 to AwtKeyEvent.VK_3,
    GLFW.GLFW_KEY_4 to AwtKeyEvent.VK_4,
    GLFW.GLFW_KEY_5 to AwtKeyEvent.VK_5,
    GLFW.GLFW_KEY_6 to AwtKeyEvent.VK_6,
    GLFW.GLFW_KEY_7 to AwtKeyEvent.VK_7,
    GLFW.GLFW_KEY_8 to AwtKeyEvent.VK_8,
    GLFW.GLFW_KEY_9 to AwtKeyEvent.VK_9,
    GLFW.GLFW_KEY_SEMICOLON to AwtKeyEvent.VK_SEMICOLON,
    GLFW.GLFW_KEY_EQUAL to AwtKeyEvent.VK_EQUALS,
    GLFW.GLFW_KEY_A to AwtKeyEvent.VK_A,
    GLFW.GLFW_KEY_B to AwtKeyEvent.VK_B,
    GLFW.GLFW_KEY_C to AwtKeyEvent.VK_C,
    GLFW.GLFW_KEY_D to AwtKeyEvent.VK_D,
    GLFW.GLFW_KEY_E to AwtKeyEvent.VK_E,
    GLFW.GLFW_KEY_F to AwtKeyEvent.VK_F,
    GLFW.GLFW_KEY_G to AwtKeyEvent.VK_G,
    GLFW.GLFW_KEY_H to AwtKeyEvent.VK_H,
    GLFW.GLFW_KEY_I to AwtKeyEvent.VK_I,
    GLFW.GLFW_KEY_J to AwtKeyEvent.VK_J,
    GLFW.GLFW_KEY_K to AwtKeyEvent.VK_K,
    GLFW.GLFW_KEY_L to AwtKeyEvent.VK_L,
    GLFW.GLFW_KEY_M to AwtKeyEvent.VK_M,
    GLFW.GLFW_KEY_N to AwtKeyEvent.VK_N,
    GLFW.GLFW_KEY_O to AwtKeyEvent.VK_O,
    GLFW.GLFW_KEY_P to AwtKeyEvent.VK_P,
    GLFW.GLFW_KEY_Q to AwtKeyEvent.VK_Q,
    GLFW.GLFW_KEY_R to AwtKeyEvent.VK_R,
    GLFW.GLFW_KEY_S to AwtKeyEvent.VK_S,
    GLFW.GLFW_KEY_T to AwtKeyEvent.VK_T,
    GLFW.GLFW_KEY_U to AwtKeyEvent.VK_U,
    GLFW.GLFW_KEY_V to AwtKeyEvent.VK_V,
    GLFW.GLFW_KEY_W to AwtKeyEvent.VK_W,
    GLFW.GLFW_KEY_X to AwtKeyEvent.VK_X,
    GLFW.GLFW_KEY_Y to AwtKeyEvent.VK_Y,
    GLFW.GLFW_KEY_Z to AwtKeyEvent.VK_Z,
    GLFW.GLFW_KEY_LEFT_BRACKET to AwtKeyEvent.VK_OPEN_BRACKET,
    GLFW.GLFW_KEY_BACKSLASH to AwtKeyEvent.VK_BACK_SLASH,
    GLFW.GLFW_KEY_RIGHT_BRACKET to AwtKeyEvent.VK_CLOSE_BRACKET,
    GLFW.GLFW_KEY_GRAVE_ACCENT to AwtKeyEvent.VK_BACK_QUOTE,
    GLFW.GLFW_KEY_ESCAPE to AwtKeyEvent.VK_ESCAPE,
    GLFW.GLFW_KEY_ENTER to AwtKeyEvent.VK_ENTER,
    GLFW.GLFW_KEY_TAB to AwtKeyEvent.VK_TAB,
    GLFW.GLFW_KEY_BACKSPACE to AwtKeyEvent.VK_BACK_SPACE,
    GLFW.GLFW_KEY_INSERT to AwtKeyEvent.VK_INSERT,
    GLFW.GLFW_KEY_DELETE to AwtKeyEvent.VK_DELETE,
    GLFW.GLFW_KEY_RIGHT to AwtKeyEvent.VK_RIGHT,
    GLFW.GLFW_KEY_LEFT to AwtKeyEvent.VK_LEFT,
    GLFW.GLFW_KEY_DOWN to AwtKeyEvent.VK_DOWN,
    GLFW.GLFW_KEY_UP to AwtKeyEvent.VK_UP,
    GLFW.GLFW_KEY_PAGE_UP to AwtKeyEvent.VK_PAGE_UP,
    GLFW.GLFW_KEY_PAGE_DOWN to AwtKeyEvent.VK_PAGE_DOWN,
    GLFW.GLFW_KEY_HOME to AwtKeyEvent.VK_HOME,
    GLFW.GLFW_KEY_END to AwtKeyEvent.VK_END,
    GLFW.GLFW_KEY_CAPS_LOCK to AwtKeyEvent.VK_CAPS_LOCK,
    GLFW.GLFW_KEY_SCROLL_LOCK to AwtKeyEvent.VK_SCROLL_LOCK,
    GLFW.GLFW_KEY_NUM_LOCK to AwtKeyEvent.VK_NUM_LOCK,
    GLFW.GLFW_KEY_PRINT_SCREEN to AwtKeyEvent.VK_PRINTSCREEN,
    GLFW.GLFW_KEY_PAUSE to AwtKeyEvent.VK_PAUSE,
    GLFW.GLFW_KEY_F1 to AwtKeyEvent.VK_F1,
    GLFW.GLFW_KEY_F2 to AwtKeyEvent.VK_F2,
    GLFW.GLFW_KEY_F3 to AwtKeyEvent.VK_F3,
    GLFW.GLFW_KEY_F4 to AwtKeyEvent.VK_F4,
    GLFW.GLFW_KEY_F5 to AwtKeyEvent.VK_F5,
    GLFW.GLFW_KEY_F6 to AwtKeyEvent.VK_F6,
    GLFW.GLFW_KEY_F7 to AwtKeyEvent.VK_F7,
    GLFW.GLFW_KEY_F8 to AwtKeyEvent.VK_F8,
    GLFW.GLFW_KEY_F9 to AwtKeyEvent.VK_F9,
    GLFW.GLFW_KEY_F10 to AwtKeyEvent.VK_F10,
    GLFW.GLFW_KEY_F11 to AwtKeyEvent.VK_F11,
    GLFW.GLFW_KEY_F12 to AwtKeyEvent.VK_F12,
    GLFW.GLFW_KEY_KP_0 to AwtKeyEvent.VK_NUMPAD0,
    GLFW.GLFW_KEY_KP_1 to AwtKeyEvent.VK_NUMPAD1,
    GLFW.GLFW_KEY_KP_2 to AwtKeyEvent.VK_NUMPAD2,
    GLFW.GLFW_KEY_KP_3 to AwtKeyEvent.VK_NUMPAD3,
    GLFW.GLFW_KEY_KP_4 to AwtKeyEvent.VK_NUMPAD4,
    GLFW.GLFW_KEY_KP_5 to AwtKeyEvent.VK_NUMPAD5,
    GLFW.GLFW_KEY_KP_6 to AwtKeyEvent.VK_NUMPAD6,
    GLFW.GLFW_KEY_KP_7 to AwtKeyEvent.VK_NUMPAD7,
    GLFW.GLFW_KEY_KP_8 to AwtKeyEvent.VK_NUMPAD8,
    GLFW.GLFW_KEY_KP_9 to AwtKeyEvent.VK_NUMPAD9,
    GLFW.GLFW_KEY_LEFT_SHIFT to AwtKeyEvent.VK_SHIFT,
    GLFW.GLFW_KEY_LEFT_CONTROL to AwtKeyEvent.VK_CONTROL,
    GLFW.GLFW_KEY_LEFT_ALT to AwtKeyEvent.VK_ALT,
    GLFW.GLFW_KEY_RIGHT_SHIFT to AwtKeyEvent.VK_SHIFT,
    GLFW.GLFW_KEY_RIGHT_CONTROL to AwtKeyEvent.VK_CONTROL,
    GLFW.GLFW_KEY_RIGHT_ALT to AwtKeyEvent.VK_ALT
)

fun glfwToAwtKeyCode(glfwKeyCode: Int) = keyCodeMap[glfwKeyCode] ?: AwtKeyEvent.VK_UNDEFINED

internal object AWTUtils {
    val awtComponent = object : Component() {}

    private val mouseModifiers = listOf(
        GLFW.GLFW_MOUSE_BUTTON_1 to InputEvent.BUTTON1_DOWN_MASK,
        GLFW.GLFW_MOUSE_BUTTON_2 to InputEvent.BUTTON2_DOWN_MASK,
        GLFW.GLFW_MOUSE_BUTTON_3 to InputEvent.BUTTON3_DOWN_MASK
    )

    private val keyModifiers = listOf(
        (GLFW.GLFW_KEY_LEFT_CONTROL to GLFW.GLFW_KEY_RIGHT_CONTROL) to InputEvent.CTRL_DOWN_MASK,
        (GLFW.GLFW_KEY_LEFT_SHIFT to GLFW.GLFW_KEY_RIGHT_SHIFT) to InputEvent.SHIFT_DOWN_MASK,
        (GLFW.GLFW_KEY_LEFT_ALT to GLFW.GLFW_KEY_RIGHT_ALT) to InputEvent.ALT_DOWN_MASK
    )

    fun getAwtMods(windowHandle: Long): Int {
        val mouseMods = mouseModifiers.fold(0) { acc, (button, mask) ->
            if (GLFW.glfwGetMouseButton(windowHandle, button) == GLFW.GLFW_PRESS) acc or mask else acc
        }
        val keyMods = keyModifiers.fold(0) { acc, (keys, mask) ->
            if (isKeyPressed(windowHandle, keys.first, keys.second)) acc or mask else acc
        }
        return mouseMods or keyMods
    }

    private fun isKeyPressed(handle: Long, left: Int, right: Int) =
        GLFW.glfwGetKey(handle, left) == GLFW.GLFW_PRESS ||
        GLFW.glfwGetKey(handle, right) == GLFW.GLFW_PRESS

    fun glfwToAwtButton(glfwButton: Int) = when (glfwButton) {
        GLFW.GLFW_MOUSE_BUTTON_1 -> MouseEvent.BUTTON1
        GLFW.GLFW_MOUSE_BUTTON_2 -> MouseEvent.BUTTON2
        GLFW.GLFW_MOUSE_BUTTON_3 -> MouseEvent.BUTTON3
        else -> MouseEvent.BUTTON1
    }

    fun createMouseEvent(mouseX: Int, mouseY: Int, awtMods: Int, button: Int, eventType: Int) =
        MouseEvent(
            awtComponent, eventType, System.currentTimeMillis(), awtMods,
            mouseX, mouseY, 1, false,
            // 移动类事件的 button 按 AWT 规范应为 NOBUTTON，按键状态由 modifiers 携带
            if (eventType == MouseEvent.MOUSE_MOVED || eventType == MouseEvent.MOUSE_DRAGGED) MouseEvent.NOBUTTON
            else glfwToAwtButton(button)
        )

    fun createMouseWheelEvent(x: Int, y: Int, scrollY: Double, awtMods: Int, eventType: Int) =
        MouseWheelEvent(
            awtComponent, eventType, System.currentTimeMillis(), awtMods,
            x, y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, (-scrollY).toInt()
        )

    @OptIn(InternalComposeUiApi::class)
    fun createKeyEvent(awtId: Int, time: Long, awtMods: Int, key: Int, char: Char, location: Int): KeyEvent {
        val handle = Minecraft.getInstance().window.window
        return KeyEvent(
            key = Key(key, location),
            type = when (awtId) {
                AwtKeyEvent.KEY_PRESSED -> KeyEventType.KeyDown
                AwtKeyEvent.KEY_RELEASED -> KeyEventType.KeyUp
                else -> KeyEventType.Unknown
            },
            codePoint = char.code,
            nativeEvent = AwtKeyEvent(awtComponent, awtId, time, awtMods, key, char, location),
            isCtrlPressed = isKeyPressed(handle, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL),
            isAltPressed = isKeyPressed(handle, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT),
            isShiftPressed = isKeyPressed(handle, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT)
        )
    }
}

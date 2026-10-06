package me.shiiyuko.manosaba.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.shiiyuko.manosaba.utils.SpriteAtlas
import me.shiiyuko.manosaba.utils.UnitySpriteParser
import org.jetbrains.skia.Image

private const val BUTTON_SCALE = 0.5f

class ExitDialog(
    private val onCancel: () -> Unit,
    private val onConfirm: () -> Unit
) {
    private var dialogAtlasImage: Image? = null
    private var dialogAtlasData: SpriteAtlas? = null
    private var commonAtlasImage: Image? = null
    private var commonAtlasData: SpriteAtlas? = null

    private var dialogBase: ImageBitmap? = null
    private var topFrame: ImageBitmap? = null
    private var bottomFrame: ImageBitmap? = null
    private var buttonDefault: ImageBitmap? = null
    private var buttonHighlighted: ImageBitmap? = null

    init {
        loadResources()
    }

    private fun loadResources() = runCatching {
        dialogAtlasImage = UnitySpriteParser.loadAtlasImageFromResources("/assets/UI_Dialog.png")
        dialogAtlasData = UnitySpriteParser.loadAtlasDataFromResources("/assets/UI_Dialog.json")

        commonAtlasImage = UnitySpriteParser.loadAtlasImageFromResources("/assets/UI_Common.png")
        commonAtlasData = UnitySpriteParser.loadAtlasDataFromResources("/assets/UI_Common.json")

        dialogAtlasImage?.let { img ->
            dialogAtlasData?.sprites?.let { sprites ->
                sprites["DialogBase"]?.let { dialogBase = UnitySpriteParser.cropSprite(img, it).toComposeImageBitmap() }
                sprites["TopFrame"]?.let { topFrame = UnitySpriteParser.cropSprite(img, it).toComposeImageBitmap() }
                sprites["BottomFrame"]?.let { bottomFrame = UnitySpriteParser.cropSprite(img, it).toComposeImageBitmap() }
            }
        }

        commonAtlasImage?.let { img ->
            commonAtlasData?.sprites?.let { sprites ->
                sprites["ButtonBase_Default"]?.let { buttonDefault = UnitySpriteParser.cropSprite(img, it).toComposeImageBitmap() }
                sprites["ButtonBase_Highlighted"]?.let { buttonHighlighted = UnitySpriteParser.cropSprite(img, it).toComposeImageBitmap() }
            }
        }
    }.onFailure { it.printStackTrace() }

    @Composable
    fun Content() {
        val dialogBaseBitmap = remember { dialogBase }
        val topFrameBitmap = remember { topFrame }
        val bottomFrameBitmap = remember { bottomFrame }
        val btnDefault = remember { buttonDefault }
        val btnHighlighted = remember { buttonHighlighted }

        var closingState by remember { mutableStateOf<Boolean?>(null) }

        val alpha = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 200)
            )
        }

        LaunchedEffect(closingState) {
            if (closingState != null) {
                alpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 200)
                )
                if (closingState == true) onConfirm() else onCancel()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha.value }
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {  } // Consume pointer events
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f),
                contentAlignment = Alignment.Center
            ) {
                dialogBaseBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Dialog Background",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                }

                topFrameBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Top Frame",
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                        contentScale = ContentScale.FillWidth
                    )
                }

                bottomFrameBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Bottom Frame",
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        contentScale = ContentScale.FillWidth
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(40.dp)
                ) {
                    BasicText(
                        text = "即将结束游戏。",
                        style = TextStyle(
                            fontFamily = manosabaFont,
                            color = Color(0xFF332B2B),
                            fontSize = 24.sp
                        )
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(40.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DialogButton(
                            normalSprite = btnDefault,
                            highlightedSprite = btnHighlighted,
                            text = "取消",
                            textColor = Color(0xFFD1BDB7),
                            onClick = { closingState = false }
                        )

                        DialogButton(
                            normalSprite = btnDefault,
                            highlightedSprite = btnHighlighted,
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = Color(0xFFF7879B))) { append("结") }
                                withStyle(SpanStyle(color = Color.White)) { append("束") }
                            },
                            onClick = { closingState = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogButton(
    normalSprite: ImageBitmap?,
    highlightedSprite: ImageBitmap?,
    text: Any,
    textColor: Color = Color.White,
    onClick: () -> Unit
) {
    normalSprite ?: return

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val currentSprite = highlightedSprite?.takeIf { isHovered } ?: normalSprite

    Box(
        modifier = Modifier
            .width((normalSprite.width * BUTTON_SCALE).dp)
            .height((normalSprite.height * BUTTON_SCALE).dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = currentSprite,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        when (text) {
            is String -> BasicText(
                text = text,
                style = TextStyle(
                    color = textColor,
                    fontFamily = manosabaFont,
                    fontSize = 30.sp
                )
            )
            is AnnotatedString -> BasicText(
                text = text,
                style = TextStyle(
                    fontFamily = manosabaFont,
                    fontSize = 30.sp
                )
            )
        }
    }
}

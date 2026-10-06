package me.shiiyuko.manosaba.utils

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.InputStream

data class SpriteData(
    val name: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

data class SpriteAtlas(
    val name: String,
    val sprites: Map<String, SpriteData>
)

object UnitySpriteParser {

    private val gson = Gson()

    fun parseAtlas(inputStream: InputStream): SpriteAtlas =
        parseAtlas(inputStream.bufferedReader().use { it.readText() })

    fun parseAtlas(json: String): SpriteAtlas {
        val jsonObject = gson.fromJson(json, JsonObject::class.java)
        val atlasName = jsonObject["m_Name"].asString
        val names = jsonObject.getAsJsonArray("m_PackedSpriteNamesToIndex").map { it.asString }

        val sprites = jsonObject.getAsJsonArray("m_RenderDataMap")
            .mapIndexedNotNull { index, element ->
                names.getOrNull(index)?.let { name ->
                    val rect = element.asJsonObject
                        .getAsJsonObject("Value")
                        .getAsJsonObject("m_TextureRect")
                    name to SpriteData(
                        name = name,
                        x = rect["m_X"].asFloat,
                        y = rect["m_Y"].asFloat,
                        width = rect["m_Width"].asFloat,
                        height = rect["m_Height"].asFloat
                    )
                }
            }.toMap()

        return SpriteAtlas(atlasName, sprites)
    }

    fun cropSprite(atlas: Image, sprite: SpriteData, atlasHeight: Int = atlas.height): Image {
        val skiaY = atlasHeight - sprite.y - sprite.height
        val (width, height) = sprite.width.toInt() to sprite.height.toInt()

        return Surface.makeRasterN32Premul(width, height).run {
            canvas.drawImageRect(
                atlas,
                Rect.makeXYWH(sprite.x, skiaY, sprite.width, sprite.height),
                Rect.makeWH(sprite.width, sprite.height)
            )
            makeImageSnapshot()
        }
    }

    fun loadAtlasDataFromResources(jsonPath: String): SpriteAtlas? =
        javaClass.getResourceAsStream(jsonPath)?.use { parseAtlas(it) }

    fun loadAtlasImageFromResources(imagePath: String): Image? =
        javaClass.getResourceAsStream(imagePath)?.use { Image.makeFromEncoded(it.readBytes()) }
}

package me.shiiyuko.manosaba.utils

import org.lwjgl.opengl.GL11C
import org.lwjgl.opengl.GL13C
import org.lwjgl.opengl.GL14C
import org.lwjgl.opengl.GL20C
import org.lwjgl.opengl.GL21C
import org.lwjgl.opengl.GL30C
import org.lwjgl.opengl.GL33C
import org.lwjgl.system.MemoryStack

object GlStateUtils {
    private var savedState: State? = null

    fun save() {
        savedState = State.capture()
    }

    fun restore() {
        savedState?.restore()
    }

    private fun Int.setEnabled(enabled: Boolean) =
        if (enabled) GL11C.glEnable(this) else GL11C.glDisable(this)

    private class State(
        val blendEnabled: Boolean,
        val blendSrcRgb: Int,
        val blendDstRgb: Int,
        val blendSrcAlpha: Int,
        val blendDstAlpha: Int,
        val depthTestEnabled: Boolean,
        val depthMask: Boolean,
        val depthFunc: Int,
        val cullEnabled: Boolean,
        val cullFace: Int,
        val activeTexture: Int,
        val textureBindings2D: IntArray,
        val samplerBindings: IntArray,
        val program: Int,
        val vaoBinding: Int,
        val colorMaskR: Boolean,
        val colorMaskG: Boolean,
        val colorMaskB: Boolean,
        val colorMaskA: Boolean,
        val unpackAlignment: Int,
        val pixelUnpackBufferBinding: Int,
        val scissorTestEnabled: Boolean,
        val scissorBox: IntArray
    ) {
        fun restore() {
            GL11C.GL_BLEND.setEnabled(blendEnabled)
            GL14C.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha)

            GL11C.GL_DEPTH_TEST.setEnabled(depthTestEnabled)
            GL11C.glDepthMask(depthMask)
            GL11C.glDepthFunc(depthFunc)

            GL11C.GL_CULL_FACE.setEnabled(cullEnabled)
            GL11C.glCullFace(cullFace)

            textureBindings2D.indices.forEach { i ->
                GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + i)
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, textureBindings2D[i])
                GL33C.glBindSampler(i, samplerBindings[i])
            }
            GL13C.glActiveTexture(activeTexture)

            GL20C.glUseProgram(program)
            GL30C.glBindVertexArray(vaoBinding)
            GL11C.glColorMask(colorMaskR, colorMaskG, colorMaskB, colorMaskA)
            GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, unpackAlignment)
            GL21C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, pixelUnpackBufferBinding)

            GL11C.GL_SCISSOR_TEST.setEnabled(scissorTestEnabled)
            GL11C.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3])
        }

        companion object {
            fun capture(): State = MemoryStack.stackPush().use { stack ->
                val maxTextureUnits = GL11C.glGetInteger(GL20C.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)
                val activeTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE)

                val textureBindings = IntArray(maxTextureUnits)
                val samplerBindings = IntArray(maxTextureUnits)

                (0 until maxTextureUnits).forEach { i ->
                    GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + i)
                    textureBindings[i] = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D)
                    samplerBindings[i] = GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING)
                }
                GL13C.glActiveTexture(activeTexture)

                val colorMask = stack.malloc(4).also { GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, it) }
                val scissorBox = stack.mallocInt(4).also { GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX, it) }

                State(
                    blendEnabled = GL11C.glIsEnabled(GL11C.GL_BLEND),
                    blendSrcRgb = GL11C.glGetInteger(GL14C.GL_BLEND_SRC_RGB),
                    blendDstRgb = GL11C.glGetInteger(GL14C.GL_BLEND_DST_RGB),
                    blendSrcAlpha = GL11C.glGetInteger(GL14C.GL_BLEND_SRC_ALPHA),
                    blendDstAlpha = GL11C.glGetInteger(GL14C.GL_BLEND_DST_ALPHA),
                    depthTestEnabled = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST),
                    depthMask = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK),
                    depthFunc = GL11C.glGetInteger(GL11C.GL_DEPTH_FUNC),
                    cullEnabled = GL11C.glIsEnabled(GL11C.GL_CULL_FACE),
                    cullFace = GL11C.glGetInteger(GL11C.GL_CULL_FACE_MODE),
                    activeTexture = activeTexture,
                    textureBindings2D = textureBindings,
                    samplerBindings = samplerBindings,
                    program = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM),
                    vaoBinding = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING),
                    colorMaskR = colorMask[0].toInt() != 0,
                    colorMaskG = colorMask[1].toInt() != 0,
                    colorMaskB = colorMask[2].toInt() != 0,
                    colorMaskA = colorMask[3].toInt() != 0,
                    unpackAlignment = GL11C.glGetInteger(GL11C.GL_UNPACK_ALIGNMENT),
                    pixelUnpackBufferBinding = GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING),
                    scissorTestEnabled = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST),
                    scissorBox = intArrayOf(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3])
                )
            }
        }
    }
}

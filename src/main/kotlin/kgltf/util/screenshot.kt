package kgltf.util

import org.lwjgl.glfw.GLFW.glfwGetFramebufferSize
import org.lwjgl.opengl.GL11.*
import org.lwjgl.stb.STBImageWrite.stbi_write_png
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun makeScreenshot(width: Int, height: Int, buffer: ByteBuffer, format: Int = GL_RGBA): ByteBuffer {
    glReadPixels(0, 0, width, height, format, GL_UNSIGNED_BYTE, buffer)
    return buffer
}

private fun makeScreenshot(width: Int, height: Int, format: Int = GL_RGBA): ByteBuffer {
    val bpp = getBytesPerPixel(format)
    val buffer = MemoryUtil.memAlloc(width * height * bpp)
    return makeScreenshot(width, height, buffer, format)
}

private inline fun <R> ByteBuffer.ensureFree(block: (ByteBuffer) -> R): R {
    try {
        return block(this)
    } finally {
        MemoryUtil.memFree(this)
    }
}

fun makeScreenshot(window: Long, format: Int = GL_RGBA): ByteArray {
    require(isSupportedFormat(format))
    MemoryStack.stackPush().use { stack ->
        val width = stack.mallocInt(1)
        val height = stack.mallocInt(1)
        glfwGetFramebufferSize(window, width, height)
        makeScreenshot(width[0], height[0], format).ensureFree { buffer ->
            val byteArray = ByteArray(buffer.capacity())
            buffer.get(byteArray)
            return byteArray
        }
    }
}

fun saveScreenshot(prefix: String, window: Long, format: Int = GL_RGBA): File {
    require(isSupportedFormat(format))
    MemoryStack.stackPush().use { stack ->
        val width = stack.mallocInt(1)
        val height = stack.mallocInt(1)
        glfwGetFramebufferSize(window, width, height)
        makeScreenshot(width[0], height[0], format).ensureFree { buffer ->
            val channels = getBytesPerPixel(format)
            val fileName = getFileName(prefix)
            stbi_write_png(fileName, width[0], height[0], channels, buffer, 0)
            return File(fileName).absoluteFile
        }
    }
}

private fun getFileName(prefix: String): String {
    val current = LocalDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")
    val formatted = current.format(formatter)
    return "${prefix}_$formatted.png"
}

private fun getBytesPerPixel(format: Int): Int {
    return requireNotNull(bytesPerPixel[format]) { "Unknown format $format" }
}

private fun isSupportedFormat(format: Int) = format in bytesPerPixel

private val bytesPerPixel = mapOf(
        GL_RGB to 3,
        GL_RGBA to 4
)


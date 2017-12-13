package kgltf.util

import org.lwjgl.glfw.GLFW.glfwGetFramebufferSize
import org.lwjgl.opengl.GL11.*
import org.lwjgl.stb.STBImageWrite.stbi_write_png
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.ByteBuffer

fun makeScreenshot(width: Int, height: Int, buffer: ByteBuffer, format: Int = GL_RGBA): ByteBuffer {
    glReadPixels(0, 0, width, height, format, GL_UNSIGNED_BYTE, buffer)
    return buffer
}

private fun makeScreenshot(width: Int, height: Int, format: Int = GL_RGBA): ByteBuffer {
    val bpp = getBytesPerPixel(format)
    val buffer = MemoryUtil.memAlloc(width * height * bpp)
    return makeScreenshot(width, height, buffer, format)
}

fun makeScreenshot(window: Long, format: Int = GL_RGBA): ByteArray {
    require(isSupportedFormat(format))
    MemoryStack.stackPush().use { stack ->
        val width = stack.mallocInt(1)
        val height = stack.mallocInt(1)
        glfwGetFramebufferSize(window, width, height)
        makeScreenshot(width[0], height[0], format).ensureMemoryFree { buffer ->
            val byteArray = ByteArray(buffer.capacity())
            buffer.get(byteArray)
            return byteArray
        }
    }
}

fun saveScreenshot(fileName: String, window: Long, format: Int = GL_RGBA): File {
    require(isSupportedFormat(format))
    MemoryStack.stackPush().use { stack ->
        val width = stack.mallocInt(1)
        val height = stack.mallocInt(1)
        glfwGetFramebufferSize(window, width, height)
        makeScreenshot(width[0], height[0], format).ensureMemoryFree { image ->
            val channels = getBytesPerPixel(format)
            val byteStride = width[0] * channels
            MemoryUtil.memAlloc(height[0] * byteStride).ensureMemoryFree { flippedImage ->
                flipImage(image, height[0], byteStride, flippedImage)
                stbi_write_png(fileName, width[0], height[0], channels, flippedImage, byteStride)
            }
        }
    }
    return File(fileName).absoluteFile
}

private fun flipImage(src: ByteBuffer, height: Int, byteStride: Int, dest: ByteBuffer): ByteBuffer {
    val rowData = ByteArray(byteStride)
    val destPositions = (0 until height).map { it * byteStride }.reversed()
    destPositions.forEach { destPosition ->
        dest.position(destPosition)
        src.get(rowData)
        dest.put(rowData)
    }
    dest.rewind()
    return dest
}

private fun getBytesPerPixel(format: Int): Int {
    return requireNotNull(bytesPerPixel[format]) { "Unknown format $format" }
}

private fun isSupportedFormat(format: Int) = format in bytesPerPixel

private val bytesPerPixel = mapOf(
        GL_RGB to 3,
        GL_RGBA to 4
)

private inline fun <R> ByteBuffer.ensureMemoryFree(block: (ByteBuffer) -> R): R {
    try {
        return block(this)
    } finally {
        MemoryUtil.memFree(this)
    }
}

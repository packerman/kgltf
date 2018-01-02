package kgltf.util

import org.lwjgl.stb.STBImage
import org.lwjgl.stb.STBImage.stbi_failure_reason
import org.lwjgl.stb.STBImage.stbi_load
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.ByteBuffer

fun loadImageFromFile(file: File): ByteBuffer {
    MemoryStack.stackPush().use { stack ->
        val x = stack.mallocInt(1)
        val y = stack.mallocInt(1)
        val channelsInFile = stack.mallocInt(1)
        return requireNotNull(stbi_load(file.path, x, y, channelsInFile, 0)) { "Cannot load image: ${stbi_failure_reason()}" }
    }
}

inline fun <R> ByteBuffer.ensureImageFree(block: (ByteBuffer) -> R): R {
    try {
        return block(this)
    } finally {
        STBImage.stbi_image_free(this)
    }
}

fun diffImage(image1: ByteBuffer, image2: ByteBuffer): ByteBuffer {
    check(image1.capacity() == image2.capacity())
    val diff = MemoryUtil.memAlloc(image1.capacity()) ?: error("Cannot allocate buffer")
    for (i in 0 until image1.capacity()) {
        diff.put(Math.abs(image1.get(i) - image2.get(i)).toByte())
    }
    diff.position(0)
    return diff
}

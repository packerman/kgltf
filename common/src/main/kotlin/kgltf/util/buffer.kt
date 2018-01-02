package kgltf.util

import org.lwjgl.system.MemoryUtil
import java.nio.Buffer
import java.nio.ByteBuffer

inline fun <T : Buffer, R> T.use(block: (T) -> R): R {
    mark()
    try {
        return block(this)
    } finally {
        reset()
    }
}

fun ByteBuffer.toArray(): ByteArray {
    val array = ByteArray(remaining())
    use {
        get(array)
        return array
    }
}

inline fun <R> ByteBuffer.ensureMemoryFree(block: (ByteBuffer) -> R): R {
    try {
        return block(this)
    } finally {
        MemoryUtil.memFree(this)
    }
}

fun allocateBufferFor(byteArray: ByteArray): ByteBuffer =
        MemoryUtil.memAlloc(byteArray.size).use {
            it.put(byteArray)
        }

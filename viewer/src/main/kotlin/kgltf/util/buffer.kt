package kgltf.util

import org.joml.*
import org.lwjgl.system.MemoryUtil
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.FloatBuffer

fun FloatBuffer.toFloatIterable(): Iterable<Float> {
    return Iterable {
        object : Iterator<Float> {
            override fun hasNext() = hasRemaining()

            override fun next() = get()
        }
    }
}

fun FloatBuffer.toVector2Iterable(): Iterable<Vector2fc> {
    check(capacity() % 2 == 0)
    return Iterable {
        object : Iterator<Vector2fc> {
            override fun hasNext() = hasRemaining()

            override fun next() = Vector2f(get(), get())
        }
    }
}

fun FloatBuffer.toVector3fIterable(): Iterable<Vector3fc> {
    check(capacity() % 3 == 0)
    return Iterable {
        object : Iterator<Vector3fc> {
            override fun hasNext() = hasRemaining()

            override fun next() = Vector3f(get(), get(), get())
        }
    }
}

fun FloatBuffer.toVector4fIterable(): Iterable<Vector4fc> {
    check(capacity() % 4 == 0)
    return Iterable {
        object : Iterator<Vector4fc> {
            override fun hasNext() = hasRemaining()

            override fun next() = Vector4f(get(), get(), get(), get())
        }
    }
}

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

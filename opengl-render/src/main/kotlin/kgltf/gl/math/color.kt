package kgltf.gl.math

import kotlin.math.max
import kotlin.math.min

data class Color(val r: Float, val g: Float, val b: Float, val a: Float) {

    init {
        check(r in 0f..1f)
        check(g in 0f..1f)
        check(b in 0f..1f)
        check(a in 0f..1f)
    }

    constructor(rgba: Int) : this(
            ((rgba and 0xff000000.toInt()) ushr 24) / 255f,
            ((rgba and 0x00ff0000) ushr 16) / 255f,
            ((rgba and 0x0000ff00) ushr 8) / 255f,
            (rgba and 0x000000ff) / 255f
    )
}

fun Color.get(dest: FloatArray, offset: Int = 0): FloatArray {
    dest[offset] = r
    dest[offset + 1] = g
    dest[offset + 2] = b
    dest[offset + 3] = a
    return dest
}

fun Float.clamp(a: Float = 0f, b: Float = 1f) = min(max(this, a), b)

operator fun Color.plus(other: Color) = Color(
        (r + other.r).clamp(),
        (g + other.g).clamp(),
        (b + other.b).clamp(),
        (a + other.a).clamp())

operator fun Float.times(color: Color) = Color(
        (this * color.r).clamp(),
        (this * color.g).clamp(),
        (this * color.b).clamp(),
        (this * color.a).clamp())

fun Color.lerp(alpha: Float, other: Color): Color {
    check(alpha in 0f..1f)
    return (1 - alpha) * this + alpha * other
}

object Colors {
    val RED = Color(0xff0000ff.toInt())
    val GREEN = Color(0x00ff00ff)
    val BLUE = Color(0x0000ffff)

    val BLACK = Color(0x000000ff)
    val WHITE = Color(0xffffffff.toInt())
    val GRAY = Color(0x808080ff.toInt())
}

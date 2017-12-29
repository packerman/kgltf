package kgltf.util

object FloatMath {

    fun toRadians(a: Float): Float = Math.toRadians(a.toDouble()).toFloat()
    fun toRadians(a: Double): Float = Math.toRadians(a).toFloat()
}

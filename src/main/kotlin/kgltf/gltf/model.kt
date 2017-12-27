package kgltf.gltf

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class Gltf(val scenes: List<Scene>,
                val nodes: List<Node>,
                val meshes: List<Mesh>,
                val cameras: List<Camera>?,
                val buffers: List<Buffer>,
                val bufferViews: List<BufferView>,
                val accessors: List<Accessor>,
                val asset: Asset,
                val extensionsRequired: List<String>?,
                val extensionsUsed: List<String>?) {

    companion object {
        private val gson = Gson()
        private val type = object : TypeToken<Gltf>() {}.type

        fun load(json: String): Gltf = gson.fromJson(json, type)
    }
}

interface Named {
    val name: String?
}

fun Named.provideName(prefix: String, index: Int) = name ?: "${prefix}_$index"

data class Scene(override val name: String?,
                 val nodes: List<Int>) : Named

data class Node(override val name: String?,
                val mesh: Int?,
                val camera: Int?,
                val matrix: List<Float>?,
                val rotation: List<Float>?,
                val translation: List<Float>?,
                val scale: List<Float>?,
                val children: List<Int>?) : Named

data class Camera(override val name: String?,
                  val type: String,
                  val perspective: Perspective?,
                  val orthographic: Orthographic?) : Named

data class Perspective(val aspectRatio: Float, val yfov: Float, val znear: Float, val zfar: Float?)

data class Orthographic(val xmag: Float, val ymag: Float, val znear: Float, val zfar: Float)

data class Mesh(override val name: String?,
                val primitives: List<Primitive>) : Named

data class Primitive(val attributes: Map<String, Int>,
                     val indices: Int?,
                     val mode: Int?)

data class Buffer(override val name: String?,
                  val uri: String,
                  val byteLength: Int) : Named

data class BufferView(override val name: String?,
                      val buffer: Int,
                      val byteOffset: Int,
                      val byteLength: Int,
                      val target: Int) : Named

data class Accessor(override val name: String?,
                    val bufferView: Int,
                    val byteOffset: Int,
                    val componentType: Int,
                    val count: Int,
                    val type: String,
                    val max: List<Float>,
                    val min: List<Float>) : Named

data class Asset(val version: String)

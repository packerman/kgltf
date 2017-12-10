import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class Root(val scenes: List<Scene>,
                val nodes: List<Node>,
                val meshes: List<Mesh>,
                val cameras: List<Camera>?,
                val buffers: List<Buffer>,
                val bufferViews: List<BufferView>,
                val accessors: List<Accessor>,
                val asset: Asset) {

    companion object {
        private val gson = Gson()
        private val type = object : TypeToken<Root>() {}.type

        fun load(json: String): Root = gson.fromJson(json, type)
    }
}

data class Scene(val name: String?,
                 val nodes: List<Int>)

data class Node(val name: String?,
                val mesh: Int?,
                val camera: Int?,
                val matrix: List<Float>?,
                val rotation: List<Float>?,
                val translation: List<Float>?,
                val scale: List<Float>?)

data class Camera(val type: String,
                  val perspective: Perspective?,
                  val orthographic: Orthographic?)

data class Perspective(val aspectRatio: Float, val yfov: Float, val znear: Float, val zfar: Float?)

data class Orthographic(val xmag: Float, val ymag: Float, val znear: Float, val zfar: Float)

data class Mesh(val name: String?,
                val primitives: List<Primitive>)

data class Primitive(val attributes: Map<String, Int>,
                     val indices: Int?,
                     val mode: Int?)

data class Buffer(val name: String?,
                  val uri: String,
                  val byteLength: Int)

data class BufferView(val name: String?,
                      val buffer: Int,
                      val byteOffset: Int,
                      val byteLength: Int,
                      val target: Int)

data class Accessor(val name: String?,
                    val bufferView: Int,
                    val byteOffset: Int,
                    val componentType: Int,
                    val count: Int,
                    val type: String,
                    val max: List<Float>,
                    val min: List<Float>)

data class Asset(val version: String)

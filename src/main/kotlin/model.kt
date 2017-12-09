import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class Root(val scenes: List<Scene>,
                val nodes: List<Node>,
                val meshes: List<Mesh>,
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
                val mesh: Int)

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

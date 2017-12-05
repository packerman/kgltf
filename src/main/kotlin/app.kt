import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL20.glVertexAttribPointer
import org.lwjgl.opengl.GL30.*
import java.net.URI
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

fun main(args: Array<String>) {

    val uri = getSampleModelUri("TriangleWithoutIndices", "glTF")

    val app = Cache().use { cache ->
        val gltf = Root.load(cache.strings.get(uri))
        val data = downloadGltfData(uri, gltf, cache)
        GltfViewer(gltf, data)
    }

    val config = Config(width = 1024,
            height = 640,
            title = "glTF")

    launch(app, config)
}

fun getSampleModelUri(name: String, variant: String): URI {
    return URI("https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/$name/$variant/$name.gltf")
}

data class GltfData(val buffers: List<ByteArray>)

fun downloadGltfData(uri: URI, root: Root, cache: Cache): GltfData {

    val executor = Executors.newFixedThreadPool(2)
    try {
        val bufferFutures: List<Future<ByteArray>> = root.buffers.map { buffer ->
            executor.submit(Callable<ByteArray> {
                val data = cache.bytes.get(uri.resolve(buffer.uri))
                check(data.size == buffer.byteLength)
                data
            })
        }

        val buffersData: List<ByteArray> = bufferFutures.map { it.get() }
        return GltfData(buffersData)
    } finally {
        executor.shutdown()
    }
}

class GLPrimitive(val vertexArrayId: Int, val mode: Int, val count: Int) {
    fun render() {
        glBindVertexArray(vertexArrayId)
        glDrawArrays(mode, 0, count)
    }
}

class GltfViewer(val gltf: Root, val data: GltfData) : Application() {

    private val bufferId = IntArray(gltf.bufferViews.size)

    private val primitivesNum = gltf.meshes.map { it.primitives.size }.sum()
    private val primitives = ArrayList<GLPrimitive>(primitivesNum)

    private val vertexArrayId = IntArray(primitivesNum)

    private val locations = mapOf("POSITION" to 0)

    override fun init() {
        initBufferViews()
        initMeshes()
    }

    private fun initMeshes() {
        glGenVertexArrays(vertexArrayId)

        var primitiveIndex = 0
        gltf.meshes.forEach { mesh ->
            mesh.primitives.forEach { primitive ->
                glBindVertexArray(vertexArrayId[primitiveIndex])
                primitive.attributes.forEach { (attribute, accessorIndex) ->
                    val accessor = gltf.accessors[accessorIndex]
                    val bufferView = gltf.bufferViews[accessor.bufferView]
                    glBindBuffer(bufferView.target, bufferId[accessor.bufferView])
                    locations[attribute]?.let { location ->
                        glEnableVertexAttribArray(location)
                        glVertexAttribPointer(location,
                                numberOfComponents(accessor.type), accessor.componentType, false, 0, accessor.byteOffset.toLong())
                    }
                    primitives.add(GLPrimitive(
                            vertexArrayId[primitiveIndex],
                            primitive.mode ?: GL_TRIANGLES,
                            gltf.accessors[primitive.attributes.values.first()].count
                    ))
                }
                primitiveIndex++
            }
        }
    }

    private fun initBufferViews() {
        glGenBuffers(bufferId)
        gltf.bufferViews.forEachIndexed { i, bufferView ->
            with(bufferView) {
                check(target in supportedTargets) { "Unsupported target" }
                glBindBuffer(target, bufferId[i])
                glBufferData(target, byteLength.toLong(), GL_STATIC_DRAW)
                val mappedBuffer = glMapBuffer(target, GL_WRITE_ONLY) ?: throw RuntimeException("Cannot allocate buffer")
                mappedBuffer.put(
                        data.buffers[buffer],
                        bufferOffset,
                        byteLength
                )
                glUnmapBuffer(target)
                glBindBuffer(target, 0)
            }
        }
    }

    override fun render() {
        renderScene(gltf.scenes[0])
    }

    private fun renderScene(scene: Scene) {
        for (primitive in primitives) {
            primitive.render()
        }
    }

    override fun resize(width: Int, height: Int) {

    }

    override fun shutdown() {
        glDeleteBuffers(bufferId)
        glDeleteVertexArrays(vertexArrayId)
    }

    companion object {
        val supportedTargets = setOf(GL_ARRAY_BUFFER)

        fun sizeInBytes(componentType: Int) =
                when (componentType) {
                    GL_BYTE -> 1
                    GL_UNSIGNED_BYTE -> 1
                    GL_SHORT -> 2
                    GL_UNSIGNED_SHORT -> 2
                    GL_UNSIGNED_INT -> 4
                    GL_FLOAT -> 4
                    else -> throw IllegalArgumentException("Unknown component type $componentType")
                }

        fun numberOfComponents(type: String) =
                when (type) {
                    "SCALAR" -> 1
                    "VEC2" -> 2
                    "VEC3" -> 3
                    "VEC4" -> 4
                    "MAT2" -> 4
                    "MAT3" -> 9
                    "MAT4" -> 16
                    else -> throw IllegalArgumentException("Unknown type $type")
                }
    }
}

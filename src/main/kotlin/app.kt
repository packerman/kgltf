import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.lwjgl.opengl.GL15.*
import java.net.URI
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

fun main(args: Array<String>) {

    val uri = getSampleModelUri("TriangleWithoutIndices", "glTF")

    val gltfData = Cache().use { cache ->
        downloadGltfAsset(uri, cache)
    }

    val app = GltfViewer(gltfData)

    val config = Config(width = 1024,
            height = 640,
            title = "glTF")

    launch(app, config)
}

fun getSampleModelUri(name: String, variant: String): URI {
    return URI("https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/$name/$variant/$name.gltf")
}

data class GltfData(val model: Root, val buffers: List<ByteArray>)

fun downloadGltfAsset(uri: URI, cache: Cache): GltfData {
    val json = cache.strings.get(uri)
    val gson = Gson()

    val root: Root = gson.fromJson(json, object : TypeToken<Root>() {}.type)

    val executor = Executors.newFixedThreadPool(2)
    try {
        val bufferFutures: List<Future<ByteArray>> = root.buffers.map { buffer ->
            executor.submit(Callable<ByteArray> {
                cache.bytes.get(uri.resolve(buffer.uri))
            })
        }

        val buffersData: List<ByteArray> = bufferFutures.map { it.get() }
        return GltfData(root, buffersData)
    } finally {
        executor.shutdown()
    }
}

class GltfViewer(val gltfData: GltfData) : Application() {

    private val bufferId = IntArray(gltfData.model.bufferViews.size)

    override fun init() {
        glGenBuffers(bufferId)
        gltfData.model.bufferViews.forEachIndexed { i, bufferView ->
            with(bufferView) {
                check(target in supportedTargets) { "Unsupported target" }
                glBindBuffer(target, bufferId[i])
                glBufferData(target, byteLength.toLong(), GL_STATIC_DRAW)
                val mappedBuffer = glMapBuffer(target, GL_WRITE_ONLY) ?: throw RuntimeException("Cannot allocate buffer")
                mappedBuffer.put(
                        gltfData.buffers[buffer],
                        bufferOffset,
                        byteLength
                )
                glUnmapBuffer(target)
                glBindBuffer(target, 0)
            }
        }
    }

    override fun render() {

    }

    override fun resize(width: Int, height: Int) {

    }

    override fun shutdown() {
        glDeleteBuffers(bufferId)
    }

    companion object {
        val supportedTargets = setOf(GL_ARRAY_BUFFER)
    }
}

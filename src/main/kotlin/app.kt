import Variant.Gltf
import Variant.GltfEmbedded
import kgltf.data.Cache
import kgltf.data.DataUri
import java.net.URI
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

fun main(args: Array<String>) {
    val uri = getSampleModelUri(KhronosSample.Triangle, Gltf)

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

enum class KhronosSample(_alternateName: String? = null) {
    TriangleWithoutIndices,
    Triangle,
    SimpleMeshes;

    val sampleName: String = _alternateName ?: name

    override fun toString() = sampleName
}

enum class Variant(val value: String) {
    Gltf("glTF"),
    GltfEmbedded("glTF-Embedded");

    override fun toString() = value
}

fun getSampleModelUri(sample: KhronosSample, variant: Variant = Gltf): URI {
    return URI("https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/$sample/$variant/$sample.gltf")
}

data class GltfData(val buffers: List<ByteArray>)

fun downloadGltfData(uri: URI, root: Root, cache: Cache): GltfData {

    val executor = Executors.newFixedThreadPool(2)
    try {
        val bufferFutures: List<Future<ByteArray>> = root.buffers.map { buffer ->
            executor.submit(Callable<ByteArray> {
                val bufferUri = uri.resolve(buffer.uri)
                val data = when (bufferUri.scheme) {
                    "http" -> cache.bytes.get(bufferUri)
                    "https" -> cache.bytes.get(bufferUri)
                    "data" -> DataUri.encode(bufferUri)
                    else -> throw IllegalStateException("Unknown scheme ${bufferUri.scheme}")
                }
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

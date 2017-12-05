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

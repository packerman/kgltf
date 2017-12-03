import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.net.URI
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

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
        return GltfData(root,
                buffersData)
    } finally {
        executor.shutdown()
    }
}

fun main(args: Array<String>) {

    val uri = getSampleModelUri("TriangleWithoutIndices", "glTF")

    Cache().use { cache ->
        downloadGltfAsset(uri, cache)
    }
}

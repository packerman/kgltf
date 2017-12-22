package kgltf.app

import com.google.gson.JsonObject
import kgltf.app.glfw.Application
import kgltf.app.glfw.Config
import kgltf.app.glfw.Launcher
import kgltf.data.Cache
import kgltf.data.DataUri
import kgltf.gltf.Buffer
import kgltf.util.fromJson
import kgltf.util.map
import kgltf.util.parseJsonObject
import java.io.File
import java.net.URI
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

class ApplicationRunner(val config: Config) {

    private val downloadDirectory = File("downloaded_files")

    fun runFor(uri: URI) {
        runByDelegate(uri) { it }
    }

    fun runByDelegate(uri: URI, delegateCreator: (Application) -> Application) {
        Cache(downloadDirectory).use { cache ->
            val jsonTree = parseJsonObject(cache.strings.get(uri))
            val data = downloadGltfData(uri, jsonTree, cache)
            cache.flush()

            Launcher(config).run { window: Long ->
                delegateCreator(GltfViewer(window, jsonTree, data))
            }
        }
    }

    private fun downloadGltfData(uri: URI, root: JsonObject, cache: Cache): GltfData {
        val executor = Executors.newFixedThreadPool(2)
        try {
            val bufferFutures: List<Future<ByteArray>> = root.map("buffers") { element ->
                executor.submit(Callable<ByteArray> {
                    val buffer: Buffer = fromJson(element)
                    val bufferUri = uri.resolve(buffer.uri)
                    val data = when (bufferUri.scheme) {
                        "http" -> cache.bytes.get(bufferUri)
                        "https" -> cache.bytes.get(bufferUri)
                        "data" -> DataUri.encode(bufferUri)
                        else -> error("Unknown scheme ${bufferUri.scheme}")
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
}

data class GltfData(val buffers: List<ByteArray>)

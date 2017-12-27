package kgltf.app

import com.google.gson.JsonElement
import kgltf.app.glfw.Application
import kgltf.app.glfw.Config
import kgltf.app.glfw.FilterList
import kgltf.app.glfw.Launcher
import kgltf.data.Cache
import kgltf.data.Downloader
import kgltf.extension.GltfExtension
import kgltf.extension.registerExtensions
import kgltf.gltf.Gltf
import kgltf.gltf.provideName
import kgltf.util.fromJson
import kgltf.util.parseJson
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger

typealias LoadingFunction = (JsonElement) -> GltfExtension

object ExtensionsLoader {

    private val extensions = HashMap<String, LoadingFunction>()

    fun registerExtension(extensionName: String, loadingFunction: LoadingFunction) {
        extensions[extensionName] = loadingFunction
    }

    fun loadExtension(extensionName: String, jsonElement: JsonElement): GltfExtension? =
            extensions[extensionName]?.invoke(jsonElement)
}

fun Gltf.isExtensionRequired(extensionName: String) =
        extensionsRequired != null && extensionName in extensionsRequired

class Downloading(val gltf: Gltf, val bufferFuture: List<Future<ByteArray>>) {
    val buffers: List<ByteArray>
        get() = bufferFuture.mapIndexed { index, future ->
            val data = future.get()
            val buffer = gltf.buffers[index]
            check(data.size == buffer.byteLength)
            logger.fine { "Download ${buffer.provideName("buffer", index)}" }
            data
        }
}

fun Downloading.collectData() = GltfData(buffers)

fun Gltf.startDownloadData(downloader: Downloader) =
        Downloading(this, bufferFuture = buffers.map { buffer ->
            downloader.downloadBytes(buffer.uri)
        })

inline fun <R> ExecutorService.use(block: (ExecutorService) -> R): R {
    try {
        return block(this)
    } finally {
        shutdown()
    }
}

class ApplicationRunner(val config: Config) {

    private val downloadDirectory = File("downloaded_files")

    fun runFor(uri: URI) {
        runByDelegate(uri) { it }
    }

    fun runByDelegate(uri: URI, delegateCreator: (Application) -> Application) {
        LoggingConfiguration.setUp()
        registerExtensions()
        logger.info("Download files")
        Cache(downloadDirectory).use { cache ->
            val jsonTree = parseJson(cache.strings.get(uri))
            val gltf: Gltf = fromJson(jsonTree)
            val extensions = loadExtensions(gltf, jsonTree)
            Executors.newFixedThreadPool(2).use { executor ->
                val downloader = Downloader(uri, cache, executor)
                val downloading = gltf.startDownloadData(downloader)
                extensions.forEach { it.startDownloadFiles(downloader) }
                val data = downloading.collectData()
                extensions.forEach { it.collectDownloadedFiles() }
                cache.flush()

                logger.info("Init GL context")
                Launcher(config, FilterList(extensions)).run { window: Long ->
                    delegateCreator(GltfViewer(window, gltf, jsonTree, data, extensions))
                }
            }
        }
    }

    private fun loadExtensions(gltf: Gltf, jsonElement: JsonElement): List<GltfExtension> {
        if (gltf.extensionsUsed == null) return emptyList()
        val loadedExtensions = ArrayList<GltfExtension>(gltf.extensionsUsed.size)
        gltf.extensionsUsed.forEach { extensionName ->
            val extension = ExtensionsLoader.loadExtension(extensionName, jsonElement)
            if (extension == null) {
                if (gltf.isExtensionRequired(extensionName)) {
                    error("Required extension $extensionName cannot be loaded ")
                } else {
                    logger.warning { "Extension $extensionName cannot be loaded, skipping" }
                }
            } else {
                loadedExtensions.add(extension)
                logger.info("Extension $extensionName loaded")
            }
        }
        return loadedExtensions
    }
}

data class GltfData(val buffers: List<ByteArray>)

object LoggingConfiguration {

    fun setUp() {
        javaClass.getResourceAsStream(filePath).use { inputStream ->
            try {
                LogManager.getLogManager().readConfiguration(inputStream)
            } catch (e: IOException) {
                Logger.getGlobal().log(Level.SEVERE, e) { "Cannot read logging configuration from file: ${filePath}" }
            }
        }
    }

    private val filePath = "/logging.properties"
}

private val logger = Logger.getLogger("kgltf.app")

package kgltf.app

import ExtensionsLoader
import com.google.gson.JsonElement
import kgltf.app.glfw.Application
import kgltf.app.glfw.Config
import kgltf.app.glfw.Launcher
import kgltf.data.Cache
import kgltf.data.Downloader
import kgltf.extension.GltfExtension
import kgltf.gl.FilterList
import kgltf.gltf.Gltf
import kgltf.gltf.GltfData
import kgltf.gltf.genericName
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

fun Gltf.isExtensionRequired(extensionName: String): Boolean =
        extensionsRequired?.contains(extensionName) == true

class Downloading(val gltf: Gltf,
                  val bufferFuture: List<Future<ByteArray>>,
                  val imageFuture: List<Future<ByteArray>>) {
    val buffers: List<ByteArray>
        get() = bufferFuture.mapIndexed { i, future ->
            future.get().also {
                val buffer = gltf.buffers[i]
                check(it.size == buffer.byteLength)
                logger.fine { "Download ${buffer.genericName(i)}" }
            }
        }
    val images: List<ByteArray>
        get() = imageFuture.mapIndexed { i, future ->
            future.get().also {
                val image = requireNotNull(gltf.images)[i]
                logger.fine { "Download ${image.genericName(i)}" }
            }
        }
}

fun Downloading.collectData() = GltfData(buffers, images)

fun Gltf.startDownloadData(downloader: Downloader) =
        Downloading(this,
                bufferFuture = buffers.map { buffer ->
                    downloader.downloadBytes(buffer.uri)
                },
                imageFuture = images?.map { image ->
                    downloader.downloadBytes(image.uri)
                } ?: emptyList())

inline fun <R> ExecutorService.use(block: (ExecutorService) -> R): R {
    try {
        return block(this)
    } finally {
        shutdown()
    }
}

open class ApplicationRunner(val config: Config) {

    private val downloadDirectory = File("downloaded_files")

    fun runFor(uri: URI) {
        LoggingConfiguration.setUp()
        logger.info("Download files")
        Cache(downloadDirectory).use { cache ->
            val jsonTree = parseJson(cache.strings.get(uri))
            val gltf: Gltf = fromJson(jsonTree)
            val transformedGltf = transformGltfModel(gltf)
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
                    delegateApplication(GltfViewer(window, transformedGltf, data, extensions))
                }
            }
        }
    }

    open fun delegateApplication(application: Application): Application = application

    open fun transformGltfModel(gltf: Gltf): Gltf = gltf

    private fun loadExtensions(gltf: Gltf, jsonElement: JsonElement): List<GltfExtension> {
        if (gltf.extensionsUsed == null) return emptyList()
        val loadedExtensions = ArrayList<GltfExtension>(gltf.extensionsUsed?.size ?: 0)
        gltf.extensionsUsed?.forEach { extensionName ->
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

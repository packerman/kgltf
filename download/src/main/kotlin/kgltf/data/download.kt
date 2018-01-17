package kgltf.data

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kgltf.util.splitExt
import java.io.Closeable
import java.io.File
import java.net.URI
import java.net.URL
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.logging.Logger

class Cache(val cacheDirectory: File) : Closeable {

    private val entries = ConcurrentHashMap<URI, FileEntry>()

    private val entriesFile = File(cacheDirectory, ".files.json")

    init {
        cacheDirectory.mkdirs()
        if (entriesFile.exists()) {
            val savedEntries = loadEntries(entriesFile)
            entries.putAll(savedEntries)
        }
    }

    val strings = object : InnerCache<String>() {
        override fun read(url: URL): String = url.readText()
        override fun read(file: File): String = file.readText()
        override fun write(file: File, data: String) = file.writeText(data)
    }

    val bytes = object : InnerCache<ByteArray>() {
        override fun read(url: URL): ByteArray = url.readBytes()
        override fun read(file: File): ByteArray = file.readBytes()
        override fun write(file: File, data: ByteArray) = file.writeBytes(data)
    }

    fun flush() {
        saveEntries(entriesFile, entries)
    }

    override fun close() {
        flush()
    }

    inner abstract class InnerCache<T> {

        fun get(uri: URI): T {
            val entry = entries[uri]
            return if (entry != null) {
                read(entry.file)
            } else {
                logger.info { "Download $uri" }
                val data = read(uri.toURL())
                val (prefix, suffix) = File(uri.path).splitExt()
                val dash = if (prefix.length > 1) "-" else "--"
                val newFile = File.createTempFile(prefix + dash, suffix, cacheDirectory)
                write(newFile, data)
                entries[uri] = FileEntry(newFile, Date())
                data
            }
        }

        protected abstract fun read(url: URL): T
        protected abstract fun read(file: File): T
        protected abstract fun write(file: File, data: T)
    }

    data class FileEntry(val file: File, val date: Date)

    companion object {
        private val type = object : TypeToken<Map<URI, FileEntry>>() {}.type
        private val gson = GsonBuilder()
                .setPrettyPrinting()
                .create()

        fun saveEntries(file: File, entries: Map<URI, FileEntry>) {
            file.writeText(gson.toJson(entries))
        }

        fun loadEntries(file: File): Map<URI, FileEntry> {
            val json = file.readText()
            val parsed = gson.fromJson<Map<URI, FileEntry>>(json, type)
            return parsed
        }
    }
}

class Downloader(val baseUri: URI, val cache: Cache, val executorService: ExecutorService) {

    fun downloadText(uri: String): Future<String> = downloadText(URI(uri))

    fun downloadBytes(uri: String): Future<ByteArray> = downloadBytes(URI(uri))

    fun downloadText(uri: URI): Future<String> = executorService.submit(
            Callable<String> {
                val resolvedUri = baseUri.resolve(uri)
                when (resolvedUri.scheme) {
                    "http", "https" -> cache.strings.get(resolvedUri)
                    "data" -> DataUri.encodeText(resolvedUri)
                    "file" -> File(resolvedUri).readText()
                    else -> error("Unknown scheme ${resolvedUri.scheme}")
                }
            }
    )

    fun downloadBytes(uri: URI): Future<ByteArray> = executorService.submit(
            Callable<ByteArray> {
                val resolvedUri = baseUri.resolve(uri)
                when (resolvedUri.scheme) {
                    "http", "https" -> cache.bytes.get(resolvedUri)
                    "data" -> DataUri.encodeBytes(resolvedUri)
                    "file" -> File(resolvedUri).readBytes()
                    else -> error("Unknown scheme ${resolvedUri.scheme}")
                }
            }
    )
}

private val logger = Logger.getLogger("kgltf.data")

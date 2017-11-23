import org.apache.http.client.cache.HttpCacheEntry
import org.apache.http.client.cache.HttpCacheStorage
import org.apache.http.client.cache.HttpCacheUpdateCallback
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.cache.CachingHttpClientBuilder

class MyHttpCacheStorage : HttpCacheStorage {
    private val cache = mutableMapOf<String, HttpCacheEntry>()

    override fun getEntry(key: String): HttpCacheEntry? {
        println(cache)
        val cached = cache[key]
        println("${Thread.currentThread()} get entry $key: $cached")
        return cached
    }

    override fun putEntry(key: String, entry: HttpCacheEntry) {
        println("${Thread.currentThread()} put entry $key: $entry")
        cache[key] = entry
    }

    override fun removeEntry(key: String) {
        cache.remove(key)
    }

    override fun updateEntry(key: String, callback: HttpCacheUpdateCallback) {
        println("update entry: $key")
        cache[key] = callback.update(cache[key])
    }


}

fun readString(httpClient: CloseableHttpClient, uri: String): String {
    val request = HttpGet(uri)
    return httpClient.execute(request).use { response ->
        response.entity.content.use { stream ->
            val bytes = stream.readBytes()
            String(bytes)
        }
    }
}

fun main(args: Array<String>) {

    val httpClient = CachingHttpClientBuilder.create()
            .setHttpCacheStorage(MyHttpCacheStorage())
            .build()

    readString(httpClient, "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/TriangleWithoutIndices/glTF/TriangleWithoutIndices.gltf")

    readString(httpClient, "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/TriangleWithoutIndices/glTF/TriangleWithoutIndices.gltf")
}



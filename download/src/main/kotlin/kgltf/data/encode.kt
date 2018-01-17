package kgltf.data

import java.net.URI
import java.nio.charset.Charset
import java.util.*

object DataUri {

    private val decoder = Base64.getDecoder()

    fun encodeBytes(uri: URI): ByteArray {
        val matchResult = requireNotNull(dataUriRegex.matchEntire(uri.schemeSpecificPart)) { "Uri '${uri.toString().substring(0, 30)}...' doesn't match data scheme" }
        val (mediaType, base64) = matchResult.destructured
        check(mediaType in supportedBinaryMediaTypes) { "Unsupported media type: $mediaType" }
        return decoder.decode(base64)
    }

    fun encodeText(uri: URI, charset: Charset = Charsets.UTF_8): String {
        val matchResult = requireNotNull(dataUriRegex.matchEntire(uri.schemeSpecificPart)) { "Uri '${uri.toString().substring(0, 30)}...' doesn't match data scheme" }
        val (mediaType, base64) = matchResult.destructured
        check(mediaType in supportedTextMediaTypes) { "Unsupported media type: $mediaType" }
        return String(decoder.decode(base64), charset)
    }

    private val nameStart = Regex("[\\p{Alnum}!#$&\\-^_]")
    private val name = Regex("\\p{Alnum}${nameStart}*")
    private val dataUriRegex = Regex("(${name}/${name}?);base64,(\\p{Graph}+)")

    private val supportedBinaryMediaTypes = setOf(
            "application/octet-stream",
            "image/png"
    )

    private val supportedTextMediaTypes = setOf(
            "text/plain"
    )
}

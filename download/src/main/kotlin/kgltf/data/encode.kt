package kgltf.data

import java.net.URI
import java.util.*

object DataUri {

    private val decoder = Base64.getDecoder()

    fun encode(uri: URI): ByteArray {
        val matchResult = requireNotNull(dataUriRegex.matchEntire(uri.schemeSpecificPart)) { "Uri '${uri.toString().substring(0, 30)}...' doesn't match data scheme" }
        val (mediaType, base64) = matchResult.destructured
        check(mediaType in supportedMediaTypes) { "Unsupported media type: $mediaType" }
        return decoder.decode(base64)
    }

    private val nameStart = Regex("[\\p{Alnum}!#$&\\-^_]")
    private val name = Regex("\\p{Alnum}${nameStart}*")
    private val dataUriRegex = Regex("(${name}/${name}?);base64,(\\p{Graph}+)")

    private val supportedMediaTypes = setOf(
            "application/octet-stream",
            "image/png"
    )
}

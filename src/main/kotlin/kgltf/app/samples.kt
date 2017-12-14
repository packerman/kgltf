package kgltf.app

import java.net.URI

enum class KhronosSample(_alternateName: String? = null) {
    TriangleWithoutIndices,
    Triangle,
    SimpleMeshes,
    Cameras;

    val sampleName: String = _alternateName ?: name

    override fun toString() = sampleName
}

enum class Variant(val value: String) {
    Gltf("glTF"),
    GltfEmbedded("glTF-Embedded");

    override fun toString() = value
}

fun getSampleModelUri(sample: KhronosSample, variant: Variant = Variant.Gltf): URI {
    return URI("https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/$sample/$variant/$sample.gltf")
}

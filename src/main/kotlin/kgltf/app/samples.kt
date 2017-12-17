package kgltf.app

import java.net.URI

enum class KhronosSample(val variants: Set<Variant> = VariantSet.basic,
                         alternateName: String? = null) {
    TriangleWithoutIndices,
    Triangle,
    SimpleMeshes,
    Cameras;

    val sampleName: String = alternateName ?: name

    override fun toString() = sampleName
}

enum class Variant(val value: String) {
    Gltf("glTF"),
    GltfEmbedded("glTF-Embedded");

    override fun toString() = value
}

object VariantSet {
    val basic = setOf(Variant.Gltf, Variant.GltfEmbedded)
}

fun getSampleModelUri(sample: KhronosSample, variant: Variant = Variant.Gltf): URI {
    require(variant in sample.variants)
    return URI("https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/$sample/$variant/$sample.gltf")
}

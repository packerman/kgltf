package kgltf.app

import java.net.URI

enum class KhronosSample(val variants: Set<Variant> = VariantSet.basic,
                         alternateName: String? = null) {
    TriangleWithoutIndices,
    Triangle,
    SimpleMeshes,
    Cameras,
    Box(VariantSet.full);

    val sampleName: String = alternateName ?: name

    override fun toString() = sampleName
}

enum class Variant(val value: String, val useExperimentalExtensions: Boolean = false) {
    Gltf("glTF"),
    GltfEmbedded("glTF-Embedded"),
    GltfTechniqueWebGL("glTF-techniqueWebGL", true);

    override fun toString() = value
}

object VariantSet {
    val basic = setOf(Variant.Gltf, Variant.GltfEmbedded)
    val full = Variant.values().toSet()
}

fun getSampleModelUri(sample: KhronosSample, variant: Variant = Variant.Gltf): URI {
    require(variant in sample.variants) { "No $variant for model $sample" }
    val branch = if (variant.useExperimentalExtensions) "2.0-experimental-extensions" else "master"
    return URI("https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/$branch/2.0/$sample/$variant/$sample.gltf")
}

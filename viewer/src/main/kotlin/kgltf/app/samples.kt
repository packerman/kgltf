package kgltf.app

import java.net.URI

enum class KhronosSample(val variants: Set<Variant> = VariantSet.basic,
                         alternateName: String? = null) {
    TriangleWithoutIndices,
    Triangle,
    SimpleMeshes,
    Cameras,
    Box(VariantSet.full),
    BoxTextured(VariantSet.full),
    Duck(VariantSet.full),
    TwoCylinderEngine(VariantSet.full, "2CylinderEngine"),
    ReciprocatingSaw(VariantSet.full),
    GearboxAssy(VariantSet.full),
    Buggy(VariantSet.full);

    val sampleName: String = alternateName ?: name

    override fun toString() = sampleName
}

fun getSampleByName(name: String): KhronosSample? = KhronosSample.values().firstOrNull { it.sampleName == name }

enum class Variant(val value: String, val useExperimentalExtensions: Boolean = false) {
    Gltf("glTF"),
    GltfEmbedded("glTF-Embedded"),
    GltfTechniqueWebGL("glTF-techniqueWebGL", true);

    override fun toString() = value
}

fun getVariantByName(name: String): Variant? = Variant.values().firstOrNull { it.value == name }

object VariantSet {
    val basic = setOf(Variant.Gltf, Variant.GltfEmbedded)
    val full = Variant.values().toSet()
}

fun getSampleModelUri(sample: KhronosSample, variant: Variant = Variant.Gltf): URI {
    require(variant in sample.variants) { "No $variant for model $sample" }
    val branch = if (variant.useExperimentalExtensions) "2.0-experimental-extensions" else "master"
    return URI("https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/$branch/2.0/$sample/$variant/$sample.gltf")
}

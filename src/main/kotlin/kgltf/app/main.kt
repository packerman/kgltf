package kgltf.app

import kgltf.app.glfw.Config

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println(usageMessage)
        System.exit(1)
    }
    val sample = requireNotNull(getSampleByName(args[0])) { "Unknown model '${args[0]}'" }
    val variant = if (args.size == 2)
        requireNotNull(getVariantByName(args[1])) { "Unknown variant variant '${args[1]}'" }
    else
        Variant.Gltf
    check(variant in sample.variants) { "Variant $variant is not available for sample $sample" }

    val uri = getSampleModelUri(sample, variant)

    val config = Config(width = 1024,
            height = 640,
            title = "glTF",
            samples = 4)

    SampleApplicationRunner(config, sample).runFor(uri)
}

val usageMessage: String by lazy {
    buildString {
        appendln("""
    Argument <Model> <Variant>
    Default variant: ${Variant.Gltf.value}
    """)
        appendln("Available models")
        KhronosSample.values().forEachIndexed { i, sample ->
            appendln("${i + 1}. $sample")
        }
        appendln()
        appendln("Available variants")
        Variant.values().forEachIndexed { i, variant ->
            appendln("${i + 1}. $variant")
        }
    }
}


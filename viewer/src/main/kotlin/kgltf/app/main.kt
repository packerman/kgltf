package kgltf.app

import kgltf.app.glfw.Config
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Options
import java.io.File
import java.net.URI

fun main(args: Array<String>) {
    val parser = DefaultParser()
    val options = Options().apply {
        addOption("s", "sample", true, "Khronos sample name")
        addOption("v", "variant", true, "Sample variant")
        addOption("u", "url", true, "Url to model")
        addOption("l", "list", false, "List available models and variants")
    }
    val line = parser.parse(options, args)

    val config = Config(width = 1024,
            height = 640,
            title = "glTF",
            samples = 4,
            stickyKeys = true)

    when {
        line.hasOption("sample") -> {
            val sampleName = line.getOptionValue("sample")
            val sample = requireNotNull(getSampleByName(sampleName)) { "Unknown model '$sampleName'" }
            val variant = if (line.hasOption("variant")) {
                val variantName = line.getOptionValue("variant")
                requireNotNull(getVariantByName(variantName)) { "Unknown variant variant '$variantName'" }
            } else Variant.Gltf
            check(variant in sample.variants) { "Variant $variant is not available for sample $sample" }
            val uri = getSampleModelUri(sample, variant)
            SampleApplicationRunner(config, sample, variant).runFor(uri)
        }
        line.hasOption("url") -> {
            val uri = normalizeUri(URI(line.getOptionValue("url")))
            ApplicationRunner(config).runFor(uri)
        }
        line.hasOption("list") -> {
            println(buildString {
                appendln("Available models")
                KhronosSample.values().forEachIndexed { i, sample ->
                    appendln("${i + 1}. $sample")
                }
                appendln()
                appendln("Available variants")
                Variant.values().forEachIndexed { i, variant ->
                    appendln("${i + 1}. $variant")
                }
            })
        }
        else -> {
            HelpFormatter()
                    .printHelp("viewer", options)
        }
    }
}

fun normalizeUri(uri: URI) = when {
    uri.isAbsolute -> uri
    uri.host != null -> URI("http", uri.schemeSpecificPart, uri.fragment)
    else -> URI("file", null, File(uri.path).absolutePath, uri.fragment)
}

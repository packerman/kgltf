package kgltf.app

import kgltf.app.KhronosSample.*
import kgltf.app.Variant.Gltf
import kgltf.app.Variant.GltfTechniqueWebGL
import kgltf.app.glfw.Application
import kgltf.app.glfw.Config
import java.io.File

private val extensionVariants = setOf(GltfTechniqueWebGL)

private val additionalVariants = mapOf(
        Box to extensionVariants,
        BoxTextured to extensionVariants,
        Duck to extensionVariants
        )

fun main(args: Array<String>) {

    fun createRunnerForSample(config: Config, sample: KhronosSample, variant: Variant) =
            object : SampleApplicationRunner(config, sample) {
                override fun delegateApplication(application: Application): Application =
                        object : Application by application {
                            override fun render() {
                                val size = application.framebufferSize
                                val dir = File("screenshots", size.toString())
                                dir.mkdirs()

                                application.render()
                                val nameSuffix = if (variant == Gltf) "" else "_${variant.name}"
                                val fileName = File(dir, "${sample.name}$nameSuffix.png").path
                                application.screenshot(fileName)
                                application.stop()
                            }
                        }
            }

    fun runWithConfig(config: Config) {
        fun runForSampleAndVariant(sample: KhronosSample, variant: Variant) {
            val uri = getSampleModelUri(sample, variant)
            createRunnerForSample(config, sample, variant).runFor(uri)
        }

        values().forEach { sample ->
            runForSampleAndVariant(sample, Gltf)
            additionalVariants[sample]?.let { variants ->
                variants.forEach {
                    runForSampleAndVariant(sample, it)
                }
            }
        }
    }

    val configBig = Config(width = 1024, height = 640, title = "Test", visible = false, samples = 4)
    runWithConfig(configBig)

    val configSmall = configBig.copy(width = configBig.width / 2, height = configBig.height / 2)
    runWithConfig(configSmall)
}

package kgltf.app

import kgltf.app.glfw.Application
import kgltf.app.glfw.Config
import java.io.File
import java.nio.file.Paths

fun main(args: Array<String>) {

    fun runWithConfig(config: Config) {
        val dir = Paths.get("screenshot_images", "${config.width}x${config.height}").toFile()
        dir.mkdirs()
        val runner = ApplicationRunner(config)
        KhronosSample.values().forEach { sample ->
            val uri = getSampleModelUri(sample, Variant.Gltf)
            runner.runByDelegate(uri) { app ->
                object : Application by app {
                    override fun render() {
                        app.render()
                        val fileName = File(dir, "image_${sample.name}.png").path
                        app.screenshot(fileName)
                        app.stop()
                    }
                }
            }
        }
    }

    val configBig = Config(width = 1024, height = 640, title = "Test", visible = false)
    runWithConfig(configBig)

    val configSmall = configBig.copy(width = configBig.width / 2, height = configBig.height / 2)
    runWithConfig(configSmall)
}

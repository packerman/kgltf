package kgltf.app

import kgltf.app.glfw.Config

fun main(args: Array<String>) {
    val uri = getSampleModelUri(KhronosSample.Box, Variant.GltfTechniqueWebGL)

    val config = Config(width = 1024,
            height = 640,
            title = "glTF",
            samples = 4)

    ApplicationRunner(config).runFor(uri)
}

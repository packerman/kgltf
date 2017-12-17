package kgltf.app

import kgltf.app.glfw.Config

fun main(args: Array<String>) {
    val uri = getSampleModelUri(KhronosSample.Cameras, Variant.Gltf)

    val config = Config(width = 1024,
            height = 640,
            title = "glTF")

    ApplicationRunner(config).runFor(uri)
}

package kgltf.app

import kgltf.app.glfw.Config
import org.lwjgl.glfw.GLFW

fun main(args: Array<String>) {
    val uri = getSampleModelUri(KhronosSample.SimpleMeshes, Variant.Gltf)

    val config = Config(width = 1024,
            height = 640,
            title = "glTF",
            profile = GLFW.GLFW_OPENGL_COMPAT_PROFILE)

    ApplicationRunner(config).runFor(uri)
}

package kgltf.app

import com.google.gson.JsonObject
import kgltf.app.glfw.GlfwApplication
import kgltf.render.Color
import kgltf.render.Colors
import kgltf.render.gl.GLRenderer
import kgltf.render.gl.GLRendererBuilder
import kgltf.util.checkGLError
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

class GltfViewer(window: Long, val gltf: JsonObject, val data: GltfData) : GlfwApplication(window) {

    private lateinit var renderer: GLRenderer

    private var cameraIndex = 0
    private var sceneIndex = 0

    private val logger = Logger.getLogger("kgltf.viewer")

    override fun init() {
        setClearColor(Colors.BLACK)

        val capabilities = GL.getCapabilities()
        renderer = GLRendererBuilder.createRenderer(capabilities, gltf, data)

        checkGLError()
    }

    private fun setClearColor(color: Color) {
        glClearColor(color.r, color.g, color.b, color.a)
    }

    override fun render() {
        glClear(GL_COLOR_BUFFER_BIT)
        if (renderer.camerasCount == 0) {
            renderer.render(sceneIndex)
        } else {
            renderer.render(sceneIndex, cameraIndex)
        }
        checkGLError()
    }

    override fun resize(width: Int, height: Int) {
        logger.info { "resize $width $height" }
        glViewport(0, 0, width, height)
    }

    override fun shutdown() {
        if (::renderer.isInitialized) {
            renderer.dispose()
        }
        checkGLError()
    }

    override fun onKey(key: Int, action: Int, x: Double, y: Double) {
        fun keyPressed(keySymbol: Int) = key == keySymbol && action == GLFW_PRESS
        when {
            keyPressed(GLFW_KEY_C) -> if (renderer.camerasCount > 0) {
                cameraIndex = (cameraIndex + 1) % renderer.camerasCount
            }
            keyPressed(GLFW_KEY_S) -> sceneIndex = (sceneIndex + 1) % renderer.scenesCount
            keyPressed(GLFW_KEY_P) -> {
                val current = LocalDateTime.now()
                val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")
                val formatted = current.format(formatter)
                val fileName = "screenshot_$formatted.png"
                val savedFile = screenshot(fileName)
                logger.info("Saved screenshot to ${savedFile}")
            }
        }
    }
}

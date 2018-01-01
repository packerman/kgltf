package kgltf.app

import kgltf.app.glfw.GlfwApplication
import kgltf.extension.GltfExtension
import kgltf.gl.GLRenderer
import kgltf.gl.checkGLError
import kgltf.gl.math.Color
import kgltf.gl.math.Colors
import kgltf.gltf.Gltf
import kgltf.gltf.GltfData
import kgltf.render.GLRendererBuilder
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

class GltfViewer(window: Long, val gltf: Gltf, val data: GltfData, val extensions: List<GltfExtension>) : GlfwApplication(window) {

    private lateinit var renderer: GLRenderer

    private var cameraIndex = 0
    private var sceneIndex = 0

    private val logger = Logger.getLogger("kgltf.viewer")

    override fun init() {
        logger.info("Init application")
        setClearColor(Colors.GRAY)

        val capabilities = GL.getCapabilities()
        extensions.forEach(GltfExtension::initialize)
        renderer = GLRendererBuilder.createRenderer(capabilities, gltf, data, extensions)
        renderer.init()
        checkGLError()
    }

    private fun setClearColor(color: Color) {
        glClearColor(color.r, color.g, color.b, color.a)
    }

    override fun render() {
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
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
        renderer.resize(width, height)

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
            keyPressed(GLFW_KEY_ESCAPE) -> stop()
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
            keyPressed(GLFW_KEY_F) -> toggleFullscreen()
        }
    }
}

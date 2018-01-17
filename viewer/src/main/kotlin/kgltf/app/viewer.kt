package kgltf.app

import kgltf.app.glfw.GlfwApplication
import kgltf.extension.GltfExtension
import kgltf.gl.GLNode
import kgltf.gl.GLRenderer
import kgltf.gl.checkGLError
import kgltf.gl.math.Color
import kgltf.gl.math.Colors
import kgltf.gl.math.PerspectiveCamera
import kgltf.gltf.Gltf
import kgltf.gltf.GltfData
import kgltf.render.GLRendererBuilder
import org.joml.*
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

class GltfViewer(window: Long, val gltf: Gltf, val data: GltfData,
                 val extensions: List<GltfExtension>) : GlfwApplication(window) {

    private lateinit var renderer: GLRenderer

    private var cameraIndex = 0
    private var sceneIndex = 0

    private lateinit var cameraMovers: List<Mover>

    private val logger = Logger.getLogger("kgltf.viewer")

    override fun init() {
        logger.info("Init application")
        setClearColor(Colors.GRAY)

        val capabilities = GL.getCapabilities()
        extensions.forEach(GltfExtension::initialize)
        renderer = GLRendererBuilder.createRenderer(capabilities, gltf, data, extensions)
        renderer.init()

        if (renderer.camerasCount == 0) {
            renderer.addCamera(PerspectiveCamera(1f, Math.toRadians(45.0).toFloat(), 1f, 1000f))
        }

        cameraMovers = renderer.cameraNodes.map(::Mover)
        checkGLError()
    }

    private fun setClearColor(color: Color) {
        glClearColor(color.r, color.g, color.b, color.a)
    }

    override fun render() {
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        extensions.forEach { extension ->
            extension.preRender(renderer.context)
        }
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
            keyPressed(GLFW_KEY_DOWN) -> {
                cameraMovers[cameraIndex].translate(0f, 0f, 1f)
            }
            keyPressed(GLFW_KEY_UP) -> {
                cameraMovers[cameraIndex].translate(0f, 0f, -1f)
            }
            keyPressed(GLFW_KEY_U) -> {
                cameraMovers[cameraIndex].rotate(Math.toRadians(-5.0).toFloat(), 1f, 0f, 0f)
            }
            keyPressed(GLFW_KEY_J) -> {
                cameraMovers[cameraIndex].rotate(Math.toRadians(5.0).toFloat(), 1f, 0f, 0f)
            }
            keyPressed(GLFW_KEY_H) -> {
                cameraMovers[cameraIndex].rotate(Math.toRadians(-5.0).toFloat(), 0f, 1f, 0f)
            }
            keyPressed(GLFW_KEY_K) -> {
                cameraMovers[cameraIndex].rotate(Math.toRadians(5.0).toFloat(), 0f, 1f, 0f)
            }
        }
    }
}

class Mover(val node: GLNode) {

    private val translation = Vector3f(0f, 0f, 0f)

    private val rotation = Matrix4f()

    private val quaternion = Quaternionf()

    fun translate(dx: Float, dy: Float, dz: Float) {
        translation.add(dx, dy, dz)
        node.localTransform.translation = translation
        node.updateTransforms(matrixStack)
    }

    fun rotate(angle: Float, x: Float, y: Float, z: Float) {
        rotation.rotate(angle, x, y, z)
        quaternion.setFromNormalized(rotation)
        node.localTransform.rotation = quaternion
        node.updateTransforms(matrixStack)
    }

    companion object {
        private val matrixStack = MatrixStackf(2)
    }
}

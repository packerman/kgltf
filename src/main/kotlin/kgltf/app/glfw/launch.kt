package kgltf.app.glfw

import kgltf.util.makeScreenshot
import kgltf.util.saveScreenshot
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL20.GL_SHADING_LANGUAGE_VERSION
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.Platform
import java.io.File
import java.io.IOException
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger

interface Application {
    fun init()
    fun render()
    fun resize(width: Int, height: Int)

    fun shutdown()

    fun stop()

    fun onMouse(button: Int, action: Int, x: Double, y: Double)

    fun onMouseMove(x: Double, y: Double)

    fun onKey(key: Int, action: Int, x: Double, y: Double)
    fun getKeyState(key: Int): Int
    fun screenshot(): ByteArray
    fun screenshot(fileName: String): File
}

abstract class GlfwApplication(protected val window: Long) : Application {

    override fun init() {}
    override fun resize(width: Int, height: Int) {}
    override fun shutdown() {}

    override fun stop() {
        glfwSetWindowShouldClose(window, true)
    }

    override fun onMouse(button: Int, action: Int, x: Double, y: Double) {}
    override fun onMouseMove(x: Double, y: Double) {}
    override fun onKey(key: Int, action: Int, x: Double, y: Double) {}

    override fun getKeyState(key: Int): Int {
        return glfwGetKey(window, key)
    }

    override fun screenshot(): ByteArray {
        return makeScreenshot(window)
    }

    override fun screenshot(fileName: String): File {
        return saveScreenshot(fileName, window)
    }
}

data class Config(val width: Int = 640,
                  val height: Int = 480,
                  val title: String = "",
                  val visible: Boolean = true,
                  val glDebug: Boolean = false,
                  val stickyKeys: Boolean = false)

class Launcher(val config: Config) {

    fun run(createApplication: (Long) -> Application) {
        LoggingConfiguration.setUp()

        GLFWErrorCallback.createPrint(System.err).set()

        if (!glfwInit()) {
            error("Unable to initialize GLFW")
        }
        val window: Long = createWindow()
        val runtime = Runtime(window, createApplication(window))

        try {
            runtime.initApplication()
            runtime.setUpApplicationCallbacks()

            while (!glfwWindowShouldClose(window)) {
                runtime.display()
            }
        } finally {
            runtime.dispose()
        }
    }

    private fun createWindow(): Long {
        if (Platform.get() == Platform.MACOSX) {
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        }
        if (config.glDebug) {
            glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE)
        }

        val window = glfwCreateWindow(config.width, config.height, config.title, NULL, NULL)
        check(window != NULL) { "Failed to create the GLFW window" }
        if (config.stickyKeys) {
            glfwSetInputMode(window, GLFW_STICKY_KEYS, 1)
        }

        glfwMakeContextCurrent(window)

        glfwSwapInterval(1)

        if (config.visible) {
            glfwShowWindow(window)
        }

        GL.createCapabilities()

        logger.config { "GL vendor: ${glGetString(GL_VENDOR)}" }
        logger.config { "GL renderer: ${glGetString(GL_RENDERER)}" }
        logger.config { "GL version: ${glGetString(GL_VERSION)}" }
        logger.config { "GLSL version: ${glGetString(GL_SHADING_LANGUAGE_VERSION)}" }
        return window
    }

    private class Runtime(val window: Long, val app: Application) {

        fun initApplication() {
            stackPush().use { stack ->
                val width = stack.mallocInt(1)
                val pHeight = stack.mallocInt(1)
                glfwGetFramebufferSize(window, width, pHeight)

                app.init()
                app.resize(width[0], pHeight[0])
            }
        }

        fun setUpApplicationCallbacks() {
            val xPos = DoubleArray(1)
            val yPos = DoubleArray(1)

            glfwSetKeyCallback(window) { _, key, _, action, _ ->
                glfwGetCursorPos(window, xPos, yPos)
                app.onKey(key, action, xPos[0], yPos[0])
            }

            glfwSetCursorPosCallback(window) { _, x, y ->
                app.onMouseMove(x, y)
            }

            glfwSetMouseButtonCallback(window) { _, button, action, _ ->
                glfwGetCursorPos(window, xPos, yPos)
                app.onMouse(button, action, xPos[0], yPos[0])
            }

            glfwSetFramebufferSizeCallback(window) { _, width, height ->
                logger.config { "resize ${width}x$height" }
                app.resize(width, height)
            }
        }

        fun display() {
            app.render()

            glfwSwapBuffers(window)
            glfwPollEvents()
        }

        fun dispose() {
            app.shutdown()

            glfwFreeCallbacks(window)
            glfwDestroyWindow(window)

            glfwTerminate()
            glfwSetErrorCallback(null).free()
        }
    }
}

object LoggingConfiguration {

    fun setUp() {
        javaClass.getResourceAsStream(filePath).use { inputStream ->
            try {
                LogManager.getLogManager().readConfiguration(inputStream)
            } catch (e: IOException) {
                Logger.getGlobal().log(Level.SEVERE, e) { "Cannot read logging configuration from file: ${filePath}" }
            }
        }
    }

    private val filePath = "/logging.properties"
}

private val logger = Logger.getLogger("kgltf.launcher")

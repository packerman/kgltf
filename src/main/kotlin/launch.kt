import kgltf.util.saveScreenshot
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL20.GL_SHADING_LANGUAGE_VERSION
import org.lwjgl.opengl.GLUtil
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.Platform
import java.io.File
import java.io.IOException
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger

abstract class Application(protected val window: Long) {

    open fun init() {}
    abstract fun render()
    open fun resize(width: Int, height: Int) {}
    open fun shutdown() {}

    open fun onMouse(button: Int, action: Int, x: Double, y: Double) {}
    open fun onMouseMove(x: Double, y: Double) {}
    open fun onKey(key: Int, action: Int, x: Double, y: Double) {}

    fun getKeyState(key: Int): Int {
        return glfwGetKey(window, key)
    }

    fun screenshot(fileName: String): File {
        return saveScreenshot(fileName, window)
    }
}

data class Config(val width: Int = 640,
                  val height: Int = 480,
                  val title: String = "",
                  val glDebug: Boolean = false,
                  val stickyKeys: Boolean = false)

fun launch(config: Config = Config(), appCreator: (Long) -> Application): Long {
    LoggingConfiguration.setUp()

    GLFWErrorCallback.createPrint(System.err).set()

    if (!glfwInit()) {
        error("Unable to initialize GLFW")
    }

    if (Platform.get() == Platform.MACOSX) {
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
    }
    if (config.glDebug) {
        glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE)
    }

    val window: Long = glfwCreateWindow(config.width, config.height, config.title, NULL, NULL)
    check(window != NULL) { "Failed to create the GLFW window" }
    if (config.stickyKeys) {
        glfwSetInputMode(window, GLFW_STICKY_KEYS, 1)
    }

    glfwMakeContextCurrent(window)

    glfwSwapInterval(1)

    glfwShowWindow(window)

    GL.createCapabilities()
    val debugProc = GLUtil.setupDebugMessageCallback()
    when {
        debugProc != null -> logger.info("Enabled GL debug mode")
        config.glDebug -> logger.warning("Failed to enable GL debug mode")
    }

    logger.config { "GL vendor: ${glGetString(GL_VENDOR)}" }
    logger.config { "GL renderer: ${glGetString(GL_RENDERER)}" }
    logger.config { "GL version: ${glGetString(GL_VERSION)}" }
    logger.config { "GLSL version: ${glGetString(GL_SHADING_LANGUAGE_VERSION)}" }

    val app = appCreator(window)
    try {
        stackPush().use { stack ->
            val width = stack.mallocInt(1)
            val pHeight = stack.mallocInt(1)
            glfwGetFramebufferSize(window, width, pHeight)

            app.init()
            app.resize(width[0], pHeight[0])
        }

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

        while (!glfwWindowShouldClose(window)) {
            app.render()

            glfwSwapBuffers(window)
            glfwPollEvents()
        }
    } catch (e: Exception) {
        logger.log(Level.SEVERE, "Error", e)
    } finally {
        app.shutdown()

        debugProc?.free()

        glfwFreeCallbacks(window)
        glfwDestroyWindow(window)

        glfwTerminate()
        glfwSetErrorCallback(null).free()
    }
    return window
}

object LoggingConfiguration {

    fun setUp() {
        javaClass.getResourceAsStream(filePath).use { inputStream ->
            try {
                LogManager.getLogManager().readConfiguration(inputStream)
            } catch (e: IOException) {
                Logger.getGlobal().log(Level.SEVERE, e) { "Cannot read logging configuration from file: $filePath" }
            }
        }
    }

    private val filePath = "/logging.properties"
}

private val logger = Logger.getLogger("kgltf.launcher")

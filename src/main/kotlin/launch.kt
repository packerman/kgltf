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

abstract class Application {
    open fun init() {}
    abstract fun render()
    open fun resize(width: Int, height: Int) {}
    open fun shutdown() {}

    open fun onMouse(button: Int, action: Int, x: Double, y: Double) {}
    open fun onMouseMove(x: Double, y: Double) {}
    open fun onKey(key: Int, action: Int, x: Double, y: Double) {}

}

data class Config(val width: Int = 640,
                  val height: Int = 480,
                  val title: String = "",
                  val glDebug: Boolean = false,
                  val stickyKeys: Boolean = false)

fun launch(app: Application, config: Config = Config()) {
    GLFWErrorCallback.createPrint(System.err).set()

    if (!glfwInit()) {
        throw IllegalStateException("Unable to initialize GLFW")
    }

    if (Platform.get() == Platform.MACOSX) {
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2)
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
    }
    if (config.glDebug) {
        glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE)
    }

    val window = glfwCreateWindow(config.width, config.height, config.title, NULL, NULL)
    if (window == NULL) {
        throw RuntimeException("Failed to create the GLFW window")
    }
    if (config.stickyKeys) {
        glfwSetInputMode(window, GLFW_STICKY_KEYS, 1)
    }

    glfwMakeContextCurrent(window)

    glfwSwapInterval(1)

    glfwShowWindow(window)

    GL.createCapabilities()
    val debugProc = GLUtil.setupDebugMessageCallback()
    if (debugProc != null) {
        println("Enabled GL debug mode")
    } else if (config.glDebug) {
        println("Failed to enable gl debug mode")
    }

    println("GL vendor: ${glGetString(GL_VENDOR)}")
    println("GL renderer: ${glGetString(GL_RENDERER)}")
    println("GL version: ${glGetString(GL_VERSION)}")
    println("GLSL version: ${glGetString(GL_SHADING_LANGUAGE_VERSION)}")

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
        app.resize(width, height)
    }

    while (!glfwWindowShouldClose(window)) {
        app.render()

        glfwSwapBuffers(window)
        glfwPollEvents()
    }

    app.shutdown()

    if (debugProc != null) {
        debugProc.free()
    }

    glfwFreeCallbacks(window)
    glfwDestroyWindow(window)

    glfwTerminate()
    glfwSetErrorCallback(null).free()
}

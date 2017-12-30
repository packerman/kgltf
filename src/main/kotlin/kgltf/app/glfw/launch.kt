package kgltf.app.glfw

import kgltf.util.makeScreenshot
import kgltf.util.saveScreenshot
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL20.GL_SHADING_LANGUAGE_VERSION
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.NULL
import java.io.File
import java.util.logging.Logger

data class Size(val width: Int, val height: Int) {
    override fun toString(): String = "${width}x$height"
}

enum class GLProfile(val majorVersion: Int,
                     val minorVersion: Int,
                     val profile: Int,
                     val forwardCompatible: Boolean) {

    OpenGl33(3, 3, GLFW_OPENGL_CORE_PROFILE, true),
    OpenGl21(2, 1, GLFW_OPENGL_ANY_PROFILE, false);

    init {
        require(equalOrAbove(2, 1))
        require(equalOrAbove(3, 2) || profile == GLFW_OPENGL_ANY_PROFILE)
        require(equalOrAbove(3, 0) || !forwardCompatible)
    }

    override fun toString(): String {
        return "GL $majorVersion.$minorVersion (profile=$profile, forwardCompatible=$forwardCompatible)"
    }


}

fun GLProfile.equalOrAbove(major: Int, minor: Int): Boolean {
    return when {
        majorVersion > major -> true
        majorVersion == major && minorVersion >= minor -> true
        else -> false
    }
}

interface ProfileFilter {
    fun isProfileAccepted(profile: GLProfile): Boolean = true
}

class FilterList(val filters: List<ProfileFilter>) : ProfileFilter {
    override fun isProfileAccepted(profile: GLProfile): Boolean =
            filters.all { it.isProfileAccepted(profile) }
}

class ProfileTypeFilter(val type: Int) : ProfileFilter {
    override fun isProfileAccepted(profile: GLProfile): Boolean =
            when (type) {
                GLFW_OPENGL_ANY_PROFILE -> true
                GLFW_OPENGL_COMPAT_PROFILE -> profile.profile == GLFW_OPENGL_ANY_PROFILE || profile.profile == GLFW_OPENGL_COMPAT_PROFILE
                else -> profile.profile == GLFW_OPENGL_CORE_PROFILE
            }
}

interface Application {
    fun init()
    fun render()
    fun resize(width: Int, height: Int)
    fun shutdown()

    fun stop()

    fun onMouse(button: Int, action: Int, x: Double, y: Double) {}
    fun onMouseMove(x: Double, y: Double) {}
    fun onKey(key: Int, action: Int, x: Double, y: Double) {}
    fun getKeyState(key: Int): Int

    fun screenshot(): ByteArray
    fun screenshot(fileName: String): File

    val windowSize: Size
    val framebufferSize: Size
}

abstract class GlfwApplication(val window: Long) : Application {
    override fun stop() {
        glfwSetWindowShouldClose(window, true)
    }

    override fun getKeyState(key: Int): Int = glfwGetKey(window, key)

    override fun screenshot(): ByteArray = makeScreenshot(window)

    override fun screenshot(fileName: String): File = saveScreenshot(fileName, window)

    override val windowSize: Size
        get() {
            MemoryStack.stackPush().use { stack ->
                val width = stack.mallocInt(1)
                val height = stack.mallocInt(1)
                glfwGetWindowSize(window, width, height)
                return Size(width[0], height[0])
            }
        }

    override val framebufferSize: Size
        get() {
            MemoryStack.stackPush().use { stack ->
                val width = stack.mallocInt(1)
                val height = stack.mallocInt(1)
                glfwGetFramebufferSize(window, width, height)
                return Size(width[0], height[0])
            }
        }
}

data class Config(val width: Int,
                  val height: Int,
                  val title: String,
                  val profile: Int = GLFW_OPENGL_ANY_PROFILE,
                  val visible: Boolean = true,
                  val samples: Int = GLFW_DONT_CARE,
                  val glDebug: Boolean = false,
                  val stickyKeys: Boolean = false)

class Launcher(val config: Config, val profileFilter: ProfileFilter) {

    fun run(createApplication: (Long) -> Application) {
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
        val window = createWindowWithBestProfile()
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

    private fun createWindowWithBestProfile(): Long {
        val typeFilter = ProfileTypeFilter(config.profile)
        for (profile in GLProfile.values()) {
            val accepted = typeFilter.isProfileAccepted(profile) && profileFilter.isProfileAccepted(profile)
            if (!accepted) {
                logger.finer { "Skipping profile $profile" }
                continue
            }
            logger.finer { "Trying profile $profile" }
            val window = tryCreateWindowWithProfile(profile)
            if (window != NULL) {
                return window
            }
        }
        return NULL
    }

    private fun tryCreateWindowWithProfile(profile: GLProfile): Long {
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, profile.majorVersion)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, profile.minorVersion)
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, if (profile.forwardCompatible) GLFW_TRUE else GLFW_FALSE)
        glfwWindowHint(GLFW_OPENGL_PROFILE, profile.profile)
        if (config.glDebug) {
            glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE)
        }
        glfwWindowHint(GLFW_SAMPLES, config.samples)

        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        return glfwCreateWindow(config.width, config.height, config.title, NULL, NULL)
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

private val logger = Logger.getLogger("kgltf.app")


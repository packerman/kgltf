package kgltf.app.glfw

import kgltf.gl.GLProfile
import kgltf.gl.ProfileFilter
import kgltf.gl.ProfileType
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

class ProfileTypeFilter(val type: Int) : ProfileFilter {
    override fun isProfileAccepted(profile: GLProfile): Boolean =
            when (type) {
                GLFW_OPENGL_ANY_PROFILE -> true
                GLFW_OPENGL_COMPAT_PROFILE -> profile.type == ProfileType.Any || profile.type == ProfileType.Compatible
                else -> profile.type == ProfileType.Core
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

    fun toggleFullscreen()

    fun screenshot(): ByteArray
    fun screenshot(fileName: String): File

    val windowSize: Size
    val framebufferSize: Size
}

abstract class GlfwApplication(val window: Long) : Application {

    private val fullscreenToggle = FullscreenToggle(window)

    override fun stop() {
        glfwSetWindowShouldClose(window, true)
    }

    override fun getKeyState(key: Int): Int = glfwGetKey(window, key)

    override fun toggleFullscreen() {
        fullscreenToggle.toggle()
    }

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

class FullscreenToggle(private val window: Long) {

    private val xpos = intArrayOf(0)
    private val ypos = intArrayOf(0)
    private val width = intArrayOf(0)
    private val height = intArrayOf(0)

    fun toggle() {
        val monitor = glfwGetWindowMonitor(window)
        if (monitor == NULL) {
            glfwGetWindowSize(window, width, height)
            glfwGetWindowPos(window, xpos, ypos)
            val primaryMonitor = glfwGetPrimaryMonitor()
            val mode = glfwGetVideoMode(primaryMonitor)
            glfwSetWindowMonitor(window, primaryMonitor, 0, 0, mode.width(), mode.height(), mode.refreshRate())
            return
        }
        if (width[0] < 1 || height[0] < 1) {
            logger.warning { "Cannot set windowed mode" }
            return
        }
        glfwSetWindowMonitor(window, NULL,
                xpos[0], ypos[0], width[0], height[0], GLFW_DONT_CARE)
    }
}

data class Config(val width: Int,
                  val height: Int,
                  val title: String,
                  val fullscreen: Boolean = false,
                  val profile: Int = GLFW_OPENGL_ANY_PROFILE,
                  val visible: Boolean = true,
                  val samples: Int = GLFW_DONT_CARE,
                  val glDebug: Boolean = false,
                  val stickyKeys: Boolean = false)

fun ProfileType.toGlfwType(): Int = when (this) {
    ProfileType.Core -> GLFW_OPENGL_CORE_PROFILE
    ProfileType.Compatible -> GLFW_OPENGL_COMPAT_PROFILE
    ProfileType.Any -> GLFW_OPENGL_ANY_PROFILE
    else -> error("Unknown profile $this")
}

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
        glfwWindowHint(GLFW_OPENGL_PROFILE, profile.type.toGlfwType())
        if (config.glDebug) {
            glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE)
        }
        glfwWindowHint(GLFW_SAMPLES, config.samples)

        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        return if (config.fullscreen) {
            val monitor = glfwGetPrimaryMonitor()
            val mode = glfwGetVideoMode(monitor)
            glfwWindowHint(GLFW_RED_BITS, mode.redBits())
            glfwWindowHint(GLFW_GREEN_BITS, mode.greenBits())
            glfwWindowHint(GLFW_BLUE_BITS, mode.blueBits())
            glfwWindowHint(GLFW_REFRESH_RATE, mode.refreshRate())
            glfwCreateWindow(mode.width(), mode.height(), config.title, monitor, NULL)
        } else {
            glfwCreateWindow(config.width, config.height, config.title, NULL, NULL)
        }
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


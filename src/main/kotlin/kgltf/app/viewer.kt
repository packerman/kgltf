package kgltf.app

import kgltf.app.glfw.GlfwApplication
import kgltf.gltf.Root
import kgltf.render.*
import kgltf.util.checkGLError
import kgltf.util.sums
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL30.glDeleteVertexArrays
import org.lwjgl.opengl.GL30.glGenVertexArrays
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

class GltfViewer(window: Long, val gltf: Root, val data: GltfData) : GlfwApplication(window) {

    private val bufferId = IntArray(gltf.bufferViews.size)

    private val primitivesNum = gltf.meshes.map { it.primitives.size }.sum()
    private val startPrimitiveIndex = gltf.meshes.map { it.primitives.size }.sums()

    private val vertexArrayId = IntArray(primitivesNum)

    private val bufferViews = ArrayList<GLBufferView>(gltf.bufferViews.size)
    private val accessors = ArrayList<GLAccessor>(gltf.accessors.size)
    private val meshes = ArrayList<GLMesh>(gltf.meshes.size)
    private val nodes = ArrayList<GLNode>(gltf.nodes.size)
    private val scenes = ArrayList<GLScene>(gltf.scenes.size)

    private val camerasNum = gltf.cameras?.size ?: 0
    private val cameras = ArrayList<Camera>(camerasNum)
    private val cameraNodes = ArrayList<GLNode>(camerasNum)

    private lateinit var renderer: Renderer

    private var cameraIndex = 0
    private var sceneIndex = 0

    private val logger = Logger.getLogger("kgltf.viewer")

    override fun init() {
        setClearColor(Colors.BLACK)
        initBufferViews()
        initAccessors()
        initMeshes()
        initCameras()
        initNodes()
        initScenes()
        renderer = Renderer(scenes, cameraNodes)
        checkGLError()
    }

    private fun setClearColor(color: Color) {
        glClearColor(color.r, color.g, color.b, color.a)
    }

    private fun initBufferViews() {
        glGenBuffers(bufferId)
        bufferViews.addAll(
                gltf.bufferViews.mapIndexed { i, bufferView ->
                    with(bufferView) {
                        check(target in supportedTargets) { "Unsupported target" }
                        val data = data.buffers[buffer]
                        GLBufferView(target, bufferId[i], byteLength).apply {
                            bind()
                            initWithData(data, byteOffset)
                            unbind()
                        }
                    }
                }
        )
    }

    private fun initAccessors() {
        accessors.addAll(
                gltf.accessors.map { accessor ->
                    GLAccessor(
                            bufferViews[accessor.bufferView],
                            accessor.byteOffset.toLong(),
                            accessor.componentType,
                            accessor.count,
                            numberOfComponents(accessor.type))
                }
        )
    }

    private fun initMeshes() {
        glGenVertexArrays(vertexArrayId)
        gltf.meshes.forEachIndexed { i, mesh ->
            val primitives = ArrayList<GLPrimitive>(mesh.primitives.size)
            mesh.primitives.forEachIndexed { j, primitive ->
                val primitiveIndex = startPrimitiveIndex[i] + j
                val mode = primitive.mode ?: GL_TRIANGLES
                val attributes = primitive.attributes.mapValues { accessors[it.value] }
                val glPrimitive = if (primitive.indices != null) {
                    GLPrimitiveIndex(vertexArrayId[primitiveIndex], mode, attributes, accessors[primitive.indices])
                } else {
                    GLPrimitive(vertexArrayId[primitiveIndex], mode, attributes)
                }
                primitives.add(glPrimitive)
            }
            val glMesh = GLMesh(primitives)
            glMesh.init()
            meshes.add(glMesh)
        }
    }

    private fun initCameras() {
        cameras.addAll(
                gltf.cameras?.map { camera ->
                    require((camera.type == "perspective" && camera.perspective != null && camera.orthographic == null) ||
                            (camera.type == "orthographic" && camera.orthographic != null && camera.perspective == null))
                    when {
                        camera.perspective != null -> {
                            with(camera.perspective) {
                                if (zfar != null) {
                                    PerspectiveCamera(aspectRatio, yfov, znear, zfar)
                                } else {
                                    PerspectiveCamera(aspectRatio, yfov, znear)
                                }
                            }
                        }
                        camera.orthographic != null -> {
                            with(camera.orthographic) {
                                OrthographicCamera(xmag, ymag, znear, zfar)
                            }
                        }
                        else -> error("Unknown camera type")
                    }
                } ?: emptyList()
        )
    }

    private fun initNodes() {
        nodes.addAll(
                gltf.nodes.map { node ->
                    val hasTransformations = node.translation != null || node.rotation != null || node.scale != null
                    require(node.matrix == null || !hasTransformations)
                    val transform = Transform()
                    when {
                        node.matrix != null -> {
                            require(node.matrix.size == 16)
                            transform.matrix = Matrix4f(
                                    node.matrix[0], node.matrix[1], node.matrix[2], node.matrix[3],
                                    node.matrix[4], node.matrix[5], node.matrix[6], node.matrix[7],
                                    node.matrix[8], node.matrix[9], node.matrix[10], node.matrix[11],
                                    node.matrix[12], node.matrix[13], node.matrix[14], node.matrix[15])
                        }
                        hasTransformations -> {
                            if (node.translation != null) {
                                require(node.translation.size == 3)
                                transform.translation = Vector3f(node.translation[0], node.translation[1], node.translation[2])
                            }
                            if (node.rotation != null) {
                                require(node.rotation.size == 4)
                                transform.rotation = Quaternionf(node.rotation[0], node.rotation[1], node.rotation[2], node.rotation[3])
                            }
                            if (node.scale != null) {
                                require(node.scale.size == 3)
                                transform.scale = Vector3f(node.scale[0], node.scale[1], node.scale[2])
                            }
                        }
                    }
                    val camera = if (node.camera != null) cameras[node.camera] else null
                    val glNode = GLNode(transform,
                            mesh = if (node.mesh != null) meshes[node.mesh] else null,
                            camera = camera)
                    if (glNode.camera != null) {
                        cameraNodes.add(glNode)
                    }
                    glNode
                }
        )
    }

    private fun initScenes() {
        scenes.addAll(
                gltf.scenes.map { scene ->
                    GLScene(scene.nodes.map { nodes[it] })
                }
        )
    }

    override fun render() {
        glClear(GL_COLOR_BUFFER_BIT)
        if (cameraNodes.isEmpty()) {
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
        glDeleteBuffers(bufferId)
        glDeleteVertexArrays(vertexArrayId)
        Programs.clear()
    }

    override fun onKey(key: Int, action: Int, x: Double, y: Double) {
        fun keyPressed(keySymbol: Int) = key == keySymbol && action == GLFW_PRESS
        when {
            keyPressed(GLFW_KEY_C) -> if (cameraNodes.isNotEmpty()) {
                cameraIndex = (cameraIndex + 1) % cameraNodes.size
            }
            keyPressed(GLFW_KEY_S) -> sceneIndex = (sceneIndex + 1) % scenes.size
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

    companion object {
        val supportedTargets = setOf(GL_ARRAY_BUFFER, GL_ELEMENT_ARRAY_BUFFER)

        val sizeInBytes = mapOf(GL_BYTE to 1,
                GL_UNSIGNED_BYTE to 1,
                GL_SHORT to 2,
                GL_UNSIGNED_SHORT to 2,
                GL_UNSIGNED_INT to 4,
                GL_FLOAT to 4)

        val numberOfComponents = mapOf("SCALAR" to 1,
                "VEC2" to 2,
                "VEC3" to 3,
                "VEC4" to 4,
                "MAT2" to 4,
                "MAT3" to 9,
                "MAT4" to 16)

        fun sizeInBytes(componentType: Int) =
                requireNotNull(sizeInBytes[componentType]) { "Unknown component type $componentType" }

        fun numberOfComponents(type: String) =
                requireNotNull(numberOfComponents[type]) { "Unknown type $type" }
    }
}

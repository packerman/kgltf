package kgltf.render.gl

import kgltf.app.GltfData
import kgltf.gltf.*
import kgltf.render.Camera
import kgltf.render.OrthographicCamera
import kgltf.render.PerspectiveCamera
import kgltf.render.Transform
import kgltf.util.sums
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15.glGenBuffers
import org.lwjgl.opengl.GL30.glGenVertexArrays
import org.lwjgl.opengl.GLCapabilities

abstract class GLRendererBuilder(root: Root, data: GltfData) : Visitor(root, data) {

    abstract val programBuilder: ProgramBuilder

    protected val bufferId = IntArray(root.bufferViews.size)

    private val bufferViews = ArrayList<GLBufferView>(root.bufferViews.size)
    private val accessors = ArrayList<GLAccessor>(root.accessors.size)
    private val meshes = ArrayList<GLMesh>(root.meshes.size)
    private val nodes = ArrayList<GLNode>(root.nodes.size)
    protected val scenes = ArrayList<GLScene>(root.scenes.size)

    protected val primitivesNum = root.meshes.map { it.primitives.size }.sum()
    private val startPrimitiveIndex = root.meshes.map { it.primitives.size }.sums()

    private val camerasNum = root.cameras?.size ?: 0
    private val cameras = ArrayList<Camera>(camerasNum)
    protected val cameraNodes = ArrayList<GLNode>(camerasNum)

    open fun init() {
        glGenBuffers(bufferId)
    }

    final override fun visitBufferView(index: Int, bufferView: BufferView) {
        bufferViews.add(
                with(bufferView) {
                    val data = data.buffers[buffer]
                    GLBufferView(target, bufferId[index], byteLength).apply {
                        bind()
                        initWithData(data, byteOffset)
                        unbind()
                    }
                })
    }

    final override fun visitAccessor(accessor: Accessor) {
        accessors.add(
                GLAccessor(
                        bufferViews[accessor.bufferView],
                        accessor.byteOffset.toLong(),
                        accessor.componentType,
                        accessor.count,
                        numberOfComponents(accessor.type)))
    }

    final override fun visitMesh(index: Int, mesh: Mesh) {
        val primitives = ArrayList<GLPrimitive>(mesh.primitives.size)
        mesh.primitives.forEachIndexed { j, primitive ->
            val primitiveIndex = startPrimitiveIndex[index] + j
            val mode = primitive.mode ?: GL11.GL_TRIANGLES
            val attributes = primitive.attributes.mapValues { accessors[it.value] }
            val glPrimitive = if (primitive.indices != null) {
                createIndexedPrimitive(primitiveIndex, accessors[primitive.indices], mode, attributes)
            } else {
                createPrimitive(primitiveIndex, mode, attributes)
            }
            primitives.add(glPrimitive)
        }
        val glMesh = GLMesh(primitives)
        glMesh.init(programBuilder)
        meshes.add(glMesh)
    }

    abstract fun createIndexedPrimitive(primitiveIndex: Int, indices: GLAccessor, mode: Int, attributes: Map<String, GLAccessor>): GLPrimitive
    abstract fun createPrimitive(primitiveIndex: Int, mode: Int, attributes: Map<String, GLAccessor>): GLPrimitive

    final override fun visitCamera(camera: kgltf.gltf.Camera) {
        cameras.add(
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
        )
    }

    final override fun visitNode(node: Node) {
        val transform = Transform()
        if (node.matrix != null) {
            transform.matrix = Matrix4f(
                    node.matrix[0], node.matrix[1], node.matrix[2], node.matrix[3],
                    node.matrix[4], node.matrix[5], node.matrix[6], node.matrix[7],
                    node.matrix[8], node.matrix[9], node.matrix[10], node.matrix[11],
                    node.matrix[12], node.matrix[13], node.matrix[14], node.matrix[15])
        } else {
            if (node.translation != null) {
                transform.translation = Vector3f(node.translation[0], node.translation[1], node.translation[2])
            }
            if (node.rotation != null) {
                transform.rotation = Quaternionf(node.rotation[0], node.rotation[1], node.rotation[2], node.rotation[3])
            }
            if (node.scale != null) {
                transform.scale = Vector3f(node.scale[0], node.scale[1], node.scale[2])
            }
        }
        val glNode = GLNode(transform,
                mesh = if (node.mesh != null) meshes[node.mesh] else null,
                camera = if (node.camera != null) cameras[node.camera] else null)
        nodes.add(glNode)
        if (glNode.camera != null) {
            cameraNodes.add(glNode)
        }
    }

    final override fun visitScene(scene: Scene) {
        scenes.add(GLScene(scene.nodes.map { nodes[it] }))
    }

    abstract fun build(): GLRenderer

    companion object {
        fun createRenderer(capabilities: GLCapabilities, gltf: Root, data: GltfData): GLRenderer {
            val builder = when {
                capabilities.OpenGL33 -> GL3RendererBuilder(gltf, data)
                capabilities.OpenGL21 -> GL2RendererBuilder(gltf, data)
                else -> error("Cannot create renderer for the current GL capabilities")
            }
            builder.init()
            builder.visit()
            return builder.build()
        }
    }
}

class GL2RendererBuilder(root: Root, data: GltfData) : GLRendererBuilder(root, data) {

    override val programBuilder = ProgramBuilder("/shader/gl21")

    override fun createIndexedPrimitive(primitiveIndex: Int, indices: GLAccessor, mode: Int, attributes: Map<String, GLAccessor>) =
            GL2IndexedPrimitive(indices, mode, attributes)

    override fun createPrimitive(primitiveIndex: Int, mode: Int, attributes: Map<String, GLAccessor>) =
            GL2Primitive(mode, attributes)

    override fun build(): GLRenderer {
        return GLRenderer(scenes, cameraNodes,
                GL2Disposable(bufferId, programBuilder))
    }
}

class GL3RendererBuilder(root: Root, data: GltfData) : GLRendererBuilder(root, data) {

    override val programBuilder: ProgramBuilder = ProgramBuilder("/shader/gl33")
    private val vertexArrayId = IntArray(primitivesNum)

    override fun init() {
        super.init()
        glGenVertexArrays(vertexArrayId)
    }

    override fun createIndexedPrimitive(primitiveIndex: Int, indices: GLAccessor, mode: Int, attributes: Map<String, GLAccessor>) =
            GL3IndexedPrimitive(vertexArrayId[primitiveIndex], indices, mode, attributes)

    override fun createPrimitive(primitiveIndex: Int, mode: Int, attributes: Map<String, GLAccessor>) =
            GL3Primitive(vertexArrayId[primitiveIndex], mode, attributes)

    override fun build(): GLRenderer {
        return GLRenderer(scenes, cameraNodes,
                GL3Disposable(vertexArrayId, bufferId, programBuilder))
    }
}

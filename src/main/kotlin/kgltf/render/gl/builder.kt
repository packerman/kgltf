package kgltf.render.gl

import com.google.gson.JsonElement
import kgltf.app.GltfData
import kgltf.extension.GltfExtension
import kgltf.gltf.*
import kgltf.render.Camera
import kgltf.render.OrthographicCamera
import kgltf.render.PerspectiveCamera
import kgltf.render.Transform
import kgltf.util.sums
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL11.GL_TRIANGLES
import org.lwjgl.opengl.GL15.glGenBuffers
import org.lwjgl.opengl.GL30.glGenVertexArrays
import org.lwjgl.opengl.GLCapabilities
import java.util.logging.Logger
import kgltf.gltf.Camera as GltfCamera

abstract class GLRendererBuilder(gltf: Gltf, json: JsonElement, data: GltfData, val extensions: List<GltfExtension>) : Visitor(gltf, json, data) {

    abstract val programBuilder: ProgramBuilder

    protected val bufferId = IntArray(gltf.bufferViews.size)

    private val bufferViews = ArrayList<GLBufferView>(gltf.bufferViews.size)
    private val accessors = ArrayList<GLAccessor>(gltf.accessors.size)
    private val materials = ArrayList<GLMaterial>(gltf.materials?.size ?: 0)
    private val meshes = ArrayList<GLMesh>(gltf.meshes.size)
    private val nodes = ArrayList<GLNode>(gltf.nodes.size)
    protected val scenes = ArrayList<GLScene>(gltf.scenes.size)

    protected val primitivesNum = gltf.meshes.map { it.primitives.size }.sum()
    private val startPrimitiveIndex = gltf.meshes.map { it.primitives.size }.sums()

    private val camerasNum = gltf.cameras?.size ?: 0
    private val cameras = ArrayList<Camera>(camerasNum)
    protected val cameraNodes = ArrayList<GLNode>(camerasNum)

    open fun init() {
        glGenBuffers(bufferId)
    }

    final override fun visitBufferView(index: Int, bufferView: BufferView, json: JsonElement) {
        bufferViews.add(
                with(bufferView) {
                    val data = data.buffers[buffer]
                    GLBufferView(target, bufferId[index], byteLength).apply {
                        bind()
                        initWithData(data, byteOffset)
                        unbind()
                    }
                })
        logger.fine { "Init ${bufferView.genericName(index)}" }
    }

    final override fun visitAccessor(index: Int, accessor: Accessor, json: JsonElement) {
        accessors.add(
                GLAccessor(
                        bufferViews[accessor.bufferView],
                        accessor.byteOffset.toLong(),
                        accessor.componentType,
                        accessor.count,
                        numberOfComponents(accessor.type)))
    }

    private fun createMaterialFromExtension(index: Int, material: Material): GLMaterial? {
        for (extension in extensions) {
            val extendedMaterial = extension.createMaterial(index)
            if (extendedMaterial != null) {
                logger.fine { "Material ${material.genericName(index)} extended by ${extension.name} extension" }
                return extendedMaterial
            }
        }
        return null
    }

    private fun createPbrMaterial(material: Material): FlatMaterial {
        val program = programBuilder["flat"]
        val baseColorFactor = material.pbrMetallicRoughness?.baseColorFactor?.let { factor ->
            Vector4f(factor[0], factor[1], factor[2], factor[3])
        }
        return if (baseColorFactor != null) FlatMaterial(program, baseColorFactor) else FlatMaterial(program)
    }

    override fun visitMaterial(index: Int, material: Material, json: JsonElement) {
        materials.add(createMaterialFromExtension(index, material) ?: createPbrMaterial(material))
        logger.fine { "Init ${material.genericName(index)}" }
    }

    final override fun visitMesh(index: Int, mesh: Mesh, json: JsonElement) {
        fun getMaterial(primitive: Primitive) =
                if (primitive.material != null)
                    materials[primitive.material]
                else
                    FlatMaterial(programBuilder["flat"], Vector4f(0.5f, 0.5f, 0.5f, 1f))

        fun getSemanticByName(name: String) =
                requireNotNull(attributeSemantics[name]) { "Unknown attribute semantic '$name'" }

        val primitives = ArrayList<GLPrimitive>(mesh.primitives.size)
        mesh.primitives.forEachIndexed { j, primitive ->
            val primitiveIndex = startPrimitiveIndex[index] + j
            val mode = primitive.mode ?: GL_TRIANGLES
            val attributes = primitive.attributes.entries.associateBy(
                    { getSemanticByName(it.key) },
                    { accessors[it.value] }
            )
            val glPrimitive = if (primitive.indices != null) {
                createIndexedPrimitive(primitiveIndex, accessors[primitive.indices], mode, attributes, getMaterial(primitive))
            } else {
                createPrimitive(primitiveIndex, mode, attributes, getMaterial(primitive))
            }
            primitives.add(glPrimitive)
        }
        val glMesh = GLMesh(primitives)
        glMesh.init()
        meshes.add(glMesh)
        logger.fine { "Init ${mesh.genericName(index)}" }
    }

    abstract fun createIndexedPrimitive(primitiveIndex: Int, indices: GLAccessor, mode: Int, attributes: Map<Semantic, GLAccessor>, material: GLMaterial): GLPrimitive
    abstract fun createPrimitive(primitiveIndex: Int, mode: Int, attributes: Map<Semantic, GLAccessor>, material: GLMaterial): GLPrimitive

    final override fun visitCamera(index: Int, camera: kgltf.gltf.Camera, json: JsonElement) {
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

    final override fun visitNode(index: Int, node: Node, json: JsonElement) {
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
        logger.fine { "Build ${node.genericName(index)}" }
    }

    final override fun visitScene(index: Int, scene: Scene, json: JsonElement) {
        scenes.add(GLScene(scene.nodes.map { nodes[it] }))
        logger.fine { "Build ${scene.genericName(index)}" }
    }

    abstract fun build(): GLRenderer

    companion object {
        fun createRenderer(capabilities: GLCapabilities, gltf: Gltf, json: JsonElement, data: GltfData, extensions: List<GltfExtension>): GLRenderer {
            val builder = when {
                capabilities.OpenGL33 -> GL3RendererBuilder(gltf, json, data, extensions)
                capabilities.OpenGL21 -> GL2RendererBuilder(gltf, json, data, extensions)
                else -> error("Cannot create renderer for the current GL capabilities")
            }
            builder.init()
            builder.visit()
            return builder.build()
        }
    }
}

class GL2RendererBuilder(gltf: Gltf, root: JsonElement, data: GltfData, extensions: List<GltfExtension>) : GLRendererBuilder(gltf, root, data, extensions) {

    override val programBuilder = ProgramBuilder("/shader/gl21")

    override fun createIndexedPrimitive(primitiveIndex: Int, indices: GLAccessor, mode: Int, attributes: Map<Semantic, GLAccessor>, material: GLMaterial) =
            GL2IndexedPrimitive(indices, mode, attributes, material)

    override fun createPrimitive(primitiveIndex: Int, mode: Int, attributes: Map<Semantic, GLAccessor>, material: GLMaterial) =
            GL2Primitive(mode, attributes, material)

    override fun build(): GLRenderer {
        return GLRenderer(scenes, cameraNodes,
                GL2Disposable(bufferId, programBuilder))
    }
}

class GL3RendererBuilder(gltf: Gltf, root: JsonElement, data: GltfData, extensions: List<GltfExtension>) : GLRendererBuilder(gltf, root, data, extensions) {

    override val programBuilder: ProgramBuilder = ProgramBuilder("/shader/gl33")
    private val vertexArrayId = IntArray(primitivesNum)

    override fun init() {
        super.init()
        glGenVertexArrays(vertexArrayId)
    }

    override fun createIndexedPrimitive(primitiveIndex: Int, indices: GLAccessor, mode: Int, attributes: Map<Semantic, GLAccessor>, material: GLMaterial) =
            GL3IndexedPrimitive(vertexArrayId[primitiveIndex], indices, mode, attributes, material)

    override fun createPrimitive(primitiveIndex: Int, mode: Int, attributes: Map<Semantic, GLAccessor>, material: GLMaterial) =
            GL3Primitive(vertexArrayId[primitiveIndex], mode, attributes, material)

    override fun build(): GLRenderer {
        return GLRenderer(scenes, cameraNodes,
                GL3Disposable(vertexArrayId, bufferId, programBuilder))
    }
}

private val logger = Logger.getLogger("render.gl")

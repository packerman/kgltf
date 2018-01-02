package kgltf.render

import kgltf.extension.GltfExtension
import kgltf.gl.*
import kgltf.gl.math.Camera
import kgltf.gl.math.OrthographicCamera
import kgltf.gl.math.PerspectiveCamera
import kgltf.gl.math.Transform
import kgltf.gltf.*
import kgltf.util.sums
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL15.glGenBuffers
import org.lwjgl.opengl.GL30.glGenVertexArrays
import org.lwjgl.opengl.GLCapabilities
import java.util.logging.Logger
import kgltf.gltf.Camera as GltfCamera

abstract class GLRendererBuilder(gltf: Gltf, data: GltfData, val extensions: List<GltfExtension>) : Visitor(gltf, data) {

    abstract val programBuilder: ProgramBuilder

    protected val bufferId = IntArray(gltf.bufferViews.size)
    private val textureCount = gltf.textures?.size ?: 0
    protected val textureId = IntArray(textureCount)

    private val bufferViews = ArrayList<GLBufferView>(gltf.bufferViews.size)
    private val textures = ArrayList<GLTexture>(textureCount)
    private val accessors = ArrayList<GLAccessor>(gltf.accessors.size)
    private val materials = ArrayList<GLMaterial>(gltf.materials?.size ?: 0)
    private val meshes = ArrayList<GLMesh>(gltf.meshes.size)
    private val nodes = Array(gltf.nodes.size) { GLNode.emptyNode }
    protected val scenes = ArrayList<GLScene>(gltf.scenes.size)

    protected val primitivesCount = gltf.meshes.map { it.primitives.size }.sum()
    private val startPrimitiveIndex = gltf.meshes.map { it.primitives.size }.sums()

    private val camerasCount = gltf.cameras?.size ?: 0
    private val cameras = ArrayList<Camera>(camerasCount)
    protected val cameraNodes = ArrayList<GLNode>(camerasCount)

    open fun init() {
        glGenBuffers(bufferId)
        glGenTextures(textureId)
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
        logger.fine { "Init ${bufferView.genericName(index)}" }
    }

    override fun visitTexture(index: Int, texture: Texture) {
        val sampler = texture.sampler?.let { gltf.samplers?.get(it) }
        val parameters = GLTextureParameters(
                magFilter = sampler?.magFilter ?: GL_LINEAR,
                minFilter = sampler?.minFilter ?: GL_NEAREST_MIPMAP_LINEAR,
                wrapS = sampler?.wrapS ?: GL_REPEAT,
                wrapT = sampler?.wrapT ?: GL_REPEAT
        )
        val data = texture.source?.let { data.images[it] } ?: error("No source data for texture ${texture.genericName(index)}")
        val glTexture = GLTexture(textureId[index], parameters).apply {
            glActiveTexture(GL_TEXTURE0)
            bind()
            initWithData(data)
//            unbind()
        }
        textures.add(glTexture)
        logger.fine { "Init ${texture.genericName(index)}" }
    }

    final override fun visitAccessor(index: Int, accessor: Accessor) {
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

    private fun createPbrMaterial(material: Material): GLMaterial {
        val baseColorFactor = material.pbrMetallicRoughness?.baseColorFactor?.let { factor ->
            Vector4f(factor[0], factor[1], factor[2], factor[3])
        } ?: Vector4f(1f, 1f, 1f, 1f)
        val texture = material.pbrMetallicRoughness?.baseColorTexture?.index?.let { textures[it] }
        return if (texture != null) {
            TextureMaterial(programBuilder["texture"], baseColorFactor)
        } else {
            FlatMaterial(programBuilder["flat"], baseColorFactor)
        }
    }

    override fun visitMaterial(index: Int, material: Material) {
        materials.add(createMaterialFromExtension(index, material) ?: createPbrMaterial(material))
        logger.fine { "Init ${material.genericName(index)}" }
    }

    final override fun visitMesh(index: Int, mesh: Mesh) {
        fun getMaterial(primitive: Primitive) =
                primitive.material?.let { materials[it] } ?: createPbrMaterial(Material(null, null))

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
            val glPrimitive = primitive.indices?.let { createIndexedPrimitive(primitiveIndex, accessors[it], mode, attributes, getMaterial(primitive)) } ?:
                    createPrimitive(primitiveIndex, mode, attributes, getMaterial(primitive))
            primitives.add(glPrimitive)
        }
        val glMesh = GLMesh(primitives)
        glMesh.init()
        meshes.add(glMesh)
        logger.fine { "Init ${mesh.genericName(index)}" }
    }

    abstract fun createIndexedPrimitive(primitiveIndex: Int, indices: GLAccessor, mode: Int, attributes: Map<Semantic, GLAccessor>, material: GLMaterial): GLPrimitive
    abstract fun createPrimitive(primitiveIndex: Int, mode: Int, attributes: Map<Semantic, GLAccessor>, material: GLMaterial): GLPrimitive

    final override fun visitCamera(index: Int, camera: kgltf.gltf.Camera) {
        val newCamera = camera.perspective?.let {
            PerspectiveCamera(it.aspectRatio, it.yfov, it.znear, it.zfar ?: Float.POSITIVE_INFINITY)
        } ?: camera.orthographic?.let {
            OrthographicCamera(it.xmag, it.ymag, it.znear, it.zfar)
        }
        cameras.add(requireNotNull(newCamera) { "Unknown typ of camera" })
    }

    final override fun visitNode(index: Int, node: Node) {
        val transform = Transform()
        if (node.matrix != null) {
            node.matrix?.let { m ->
                transform.matrix = Matrix4f(
                        m[0], m[1], m[2], m[3],
                        m[4], m[5], m[6], m[7],
                        m[8], m[9], m[10], m[11],
                        m[12], m[13], m[14], m[15])
            }
        } else {
            node.translation?.let { t ->
                transform.translation = Vector3f(t[0], t[1], t[2])
            }
            node.rotation?.let { r ->
                transform.rotation = Quaternionf(r[0], r[1], r[2], r[3])
            }
            node.scale?.let { s ->
                transform.scale = Vector3f(s[0], s[1], s[2])
            }

            if (node.scale != null) {

            }
        }
        val children = node.children?.map { nodes[it] } ?: emptyList()
        val glNode = GLNode(transform,
                mesh = node.mesh?.let { meshes[it] },
                camera = node.camera?.let { cameras[it] },
                children = children)
        nodes[index] = glNode
        if (glNode.camera != null) {
            cameraNodes.add(glNode)
        }
        logger.fine { "Build ${node.genericName(index)}" }
    }

    final override fun visitScene(index: Int, scene: Scene) {
        scenes.add(GLScene(scene.nodes.map { nodes[it] }))
        logger.fine { "Build ${scene.genericName(index)}" }
    }

    fun createRenderingContext() = RenderingContext(nodes.toList())

    abstract fun build(): GLRenderer

    companion object {
        fun createRenderer(capabilities: GLCapabilities, gltf: Gltf, data: GltfData, extensions: List<GltfExtension>): GLRenderer {
            val builder = when {
                capabilities.OpenGL33 -> GL3RendererBuilder(gltf, data, extensions)
                capabilities.OpenGL21 -> GL2RendererBuilder(gltf, data, extensions)
                else -> error("Cannot create renderer for the current GL capabilities")
            }
            builder.init()
            builder.visit()
            return builder.build()
        }
    }
}

class GL2RendererBuilder(gltf: Gltf, data: GltfData, extensions: List<GltfExtension>) : GLRendererBuilder(gltf, data, extensions) {

    override val programBuilder = ProgramBuilder("/shader/gl21")

    override fun createIndexedPrimitive(primitiveIndex: Int, indices: GLAccessor, mode: Int, attributes: Map<Semantic, GLAccessor>, material: GLMaterial) =
            GL2IndexedPrimitive(indices, mode, attributes, material)

    override fun createPrimitive(primitiveIndex: Int, mode: Int, attributes: Map<Semantic, GLAccessor>, material: GLMaterial) =
            GL2Primitive(mode, attributes, material)

    override fun build(): GLRenderer {
        return GLRenderer(createRenderingContext(), scenes, cameraNodes,
                GL2Disposable(bufferId, programBuilder))
    }
}

class GL3RendererBuilder(gltf: Gltf, data: GltfData, extensions: List<GltfExtension>) : GLRendererBuilder(gltf, data, extensions) {

    override val programBuilder: ProgramBuilder = ProgramBuilder("/shader/gl33")
    private val vertexArrayId = IntArray(primitivesCount)

    override fun init() {
        super.init()
        glGenVertexArrays(vertexArrayId)
    }

    override fun createIndexedPrimitive(primitiveIndex: Int, indices: GLAccessor, mode: Int, attributes: Map<Semantic, GLAccessor>, material: GLMaterial) =
            GL3IndexedPrimitive(vertexArrayId[primitiveIndex], indices, mode, attributes, material)

    override fun createPrimitive(primitiveIndex: Int, mode: Int, attributes: Map<Semantic, GLAccessor>, material: GLMaterial) =
            GL3Primitive(vertexArrayId[primitiveIndex], mode, attributes, material)

    override fun build(): GLRenderer {
        return GLRenderer(createRenderingContext(), scenes, cameraNodes,
                GL3Disposable(vertexArrayId, bufferId, programBuilder))
    }
}

private val logger = Logger.getLogger("render.gl")

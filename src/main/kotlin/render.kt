import kgltf.render.*
import kgltf.util.checkGLError
import kgltf.util.sums
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL30.glDeleteVertexArrays
import org.lwjgl.opengl.GL30.glGenVertexArrays

class GltfViewer(val gltf: Root, val data: GltfData) : Application() {

    private val bufferId = IntArray(gltf.bufferViews.size)

    private val primitivesNum = gltf.meshes.map { it.primitives.size }.sum()
    private val startPrimitiveIndex = gltf.meshes.map { it.primitives.size }.sums()
    private val drawables = ArrayList<() -> Unit>(primitivesNum)

    private val vertexArrayId = IntArray(primitivesNum)

    private val color = FloatArray(4)

    private val locations = mapOf("POSITION" to 0)

    private val mvp = Matrix4f()
    private val mvpArray = FloatArray(16)

    private val bufferViews = ArrayList<GLBufferView>(gltf.bufferViews.size)
    private val accessors = ArrayList<GLAccessor>(gltf.accessors.size)
    private val meshes = ArrayList<GLMesh>(gltf.meshes.size)
    private val nodes = ArrayList<GLNode>(gltf.nodes.size)
    private val scenes = ArrayList<GLScene>(gltf.scenes.size)

    override fun init() {
        setClearColor(Colors.BLACK)
        initBufferViews()
        initAccessors()
        initMeshes()
        initNodes()
        initScenes()
        checkGLError()
    }

    private fun setClearColor(color: Color) {
        glClearColor(color.r, color.g, color.b, color.a)
    }

    private fun initBufferViews() {
        glGenBuffers(bufferId)
        gltf.bufferViews.forEachIndexed { i, bufferView ->
            with(bufferView) {
                check(target in supportedTargets) { "Unsupported target" }
                glBindBuffer(target, bufferId[i])
                glBufferData(target, byteLength.toLong(), GL_STATIC_DRAW)
                val mappedBuffer = glMapBuffer(target, GL_WRITE_ONLY) ?: throw RuntimeException("Cannot allocate buffer")
                mappedBuffer.put(
                        data.buffers[buffer],
                        byteOffset,
                        byteLength
                )
                glUnmapBuffer(target)
                glBindBuffer(target, 0)
                bufferViews.add(GLBufferView(target, bufferId[i]))
            }
        }
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
                    GLNode(meshes[node.mesh], transform)
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
        Programs.flat.use {
            scenes[0].render()
        }
        checkGLError()
    }

    override fun resize(width: Int, height: Int) {
        glViewport(0, 0, width, height)
    }

    override fun shutdown() {
        glDeleteBuffers(bufferId)
        glDeleteVertexArrays(vertexArrayId)
    }

    companion object {
        val supportedTargets = setOf(GL_ARRAY_BUFFER, GL_ELEMENT_ARRAY_BUFFER)

        fun sizeInBytes(componentType: Int) =
                when (componentType) {
                    GL_BYTE -> 1
                    GL_UNSIGNED_BYTE -> 1
                    GL_SHORT -> 2
                    GL_UNSIGNED_SHORT -> 2
                    GL_UNSIGNED_INT -> 4
                    GL_FLOAT -> 4
                    else -> throw IllegalArgumentException("Unknown component type $componentType")
                }

        fun numberOfComponents(type: String) =
                when (type) {
                    "SCALAR" -> 1
                    "VEC2" -> 2
                    "VEC3" -> 3
                    "VEC4" -> 4
                    "MAT2" -> 4
                    "MAT3" -> 9
                    "MAT4" -> 16
                    else -> throw IllegalArgumentException("Unknown type $type")
                }
    }
}

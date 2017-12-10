import kgltf.render.*
import kgltf.util.*
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.glUniform4fv
import org.lwjgl.opengl.GL20.glUniformMatrix4fv
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
            Programs.flat.use {
                uniforms["mvp"]?.let { location ->
                    glUniformMatrix4fv(location, false, mvp.get(mvpArray))
                }
                uniforms["color"]?.let { location ->
                    glUniform4fv(location, Colors.GRAY.get(color))
                }
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
                    glPrimitive.init(locations)
                    glPrimitive.unbind()
                    primitives.add(glPrimitive)
                }
                meshes.add(GLMesh(primitives))
            }
        }
    }

    private fun initNodes() {
        nodes.addAll(
                gltf.nodes.map { GLNode(meshes[it.mesh]) }
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

import org.joml.Matrix4f
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import util.Color
import util.Colors
import util.checkGLError
import util.get

class GltfViewer(val gltf: Root, val data: GltfData) : Application() {

    private val bufferId = IntArray(gltf.bufferViews.size)

    private val primitivesNum = gltf.meshes.map { it.primitives.size }.sum()
    private val primitives = ArrayList<GLPrimitive>(primitivesNum)

    private val vertexArrayId = IntArray(primitivesNum)

    private val color = FloatArray(4)

    private val locations = mapOf("POSITION" to 0)

    private val mvp = Matrix4f()
    private val mvpArray = FloatArray(16)

    override fun init() {
        setClearColor(Colors.BLACK)
        initBufferViews()
        initMeshes()
        checkGLError()
    }

    private fun setClearColor(color: Color) {
        glClearColor(color.r, color.g, color.b, color.a)
    }

    private fun initMeshes() {
        glGenVertexArrays(vertexArrayId)

        var primitiveIndex = 0
        gltf.meshes.forEach { mesh ->
            Programs.flat.use {
                uniforms["mvp"]?.let { location ->
                    glUniformMatrix4fv(location,false, mvp.get(mvpArray))
                }
                uniforms["color"]?.let { location ->
                    glUniform4fv(location, Colors.GRAY.get(color))
                }
                mesh.primitives.forEach { primitive ->
                    glBindVertexArray(vertexArrayId[primitiveIndex])
                    primitive.attributes.forEach { (attribute, accessorIndex) ->
                        val accessor = gltf.accessors[accessorIndex]
                        val bufferView = gltf.bufferViews[accessor.bufferView]
                        glBindBuffer(bufferView.target, bufferId[accessor.bufferView])
                        locations[attribute]?.let { location ->
                            glEnableVertexAttribArray(location)
                            glVertexAttribPointer(location,
                                    numberOfComponents(accessor.type), accessor.componentType, false, 0, accessor.byteOffset.toLong())
                        }
                        primitives.add(GLPrimitive(
                                vertexArrayId[primitiveIndex],
                                primitive.mode ?: GL_TRIANGLES,
                                gltf.accessors[primitive.attributes.values.first()].count
                        ))
                    }
                    primitiveIndex++
                }
            }
        }
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
                        bufferOffset,
                        byteLength
                )
                glUnmapBuffer(target)
                glBindBuffer(target, 0)
            }
        }
    }

    override fun render() {
        glClear(GL_COLOR_BUFFER_BIT)
        Programs.flat.use {
            renderScene(gltf.scenes[0])
        }
        checkGLError()
    }

    private fun renderScene(scene: Scene) {
        for (primitive in primitives) {
            primitive.render()
        }
    }

    override fun resize(width: Int, height: Int) {
        glViewport(0, 0, width, height)
    }

    override fun shutdown() {
        glDeleteBuffers(bufferId)
        glDeleteVertexArrays(vertexArrayId)
    }

    companion object {
        val supportedTargets = setOf(GL_ARRAY_BUFFER)

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

class GLPrimitive(val vertexArrayId: Int, val mode: Int, val count: Int) {
    fun render() {
        glBindVertexArray(vertexArrayId)
        glDrawArrays(mode, 0, count)
    }
}

package kgltf.gl

import kgltf.gl.AttributeSemantic.*
import kgltf.gl.Shader.compile
import kgltf.gl.UniformSemantic.ModelViewInverseTranspose
import kgltf.gl.UniformSemantic.ModelViewProjection
import kgltf.gl.math.Color
import kgltf.gl.math.get
import kgltf.util.Disposable
import kgltf.util.warnWhen
import org.joml.Matrix3fc
import org.joml.Matrix4fc
import org.joml.Vector4fc
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.GL_TRUE
import org.lwjgl.opengl.GL20.*
import java.net.URL
import java.util.*
import kotlin.collections.HashMap

class ProgramBuilder(val shaderDirectory: String) : Disposable {

    private val suppliers = mapOf(
            "flat" to {
                build("flat",
                        attributeSemantics = mapOf(Position to "position"),
                        uniformSemantics = mapOf(ModelViewProjection to "modelViewProjectionMatrix"),
                        uniformParameters = setOf("color"))
            },
            "normal" to {
                build("normal",
                        attributeSemantics = mapOf(Position to "position",
                                Normal to "normal"),
                        uniformSemantics = mapOf(ModelViewProjection to "modelViewProjectionMatrix",
                                ModelViewInverseTranspose to "normalMatrix "))
            },
            "texture" to {
                build("texture",
                        attributeSemantics = mapOf(Position to "position",
                                TexCoord0 to "texCoord"),
                        uniformSemantics = mapOf(ModelViewProjection to "modelViewProjectionMatrix"),
                        uniformParameters = setOf("color", "sampler")
                        )
            })

    private val programs = HashMap<String, GLProgram>()


    operator fun get(name: String): GLProgram = programs.computeIfAbsent(name) {
        val supplier = requireNotNull(suppliers[it]) { "Program $name doesn't exist" }
        supplier()
    }

    fun build(name: String, attributeSemantics: Map<Semantic, String> = emptyMap(),
              uniformSemantics: Map<Semantic, String> = emptyMap(),
              uniformParameters: Set<String> = emptySet()): GLProgram {
        val shaders = collectShadersForProgram(name)
        val program = GLProgram.link(shaders)
        shaders.forEach(::glDeleteShader)
        val attributeMap = attributeSemantics.mapValues { getAttributeLocation(program, it.value) }
        val uniformMap = uniformSemantics.mapValues { getUniformLocation(program, it.value) }
        val parameterMap = uniformParameters.associate { it to getUniformLocation(program, it) }
        return GLProgram(name, program, attributeMap, uniformMap, parameterMap)
    }

    private fun collectShadersForProgram(name: String): IntArray {
        fun getResource(path: String) = requireNotNull(GLProgram::class.java.getResource(path)) { "Cannot find resource $path" }
        return intArrayOf(
                compile(GL_VERTEX_SHADER, getResource("$shaderDirectory/$name.vert")),
                compile(GL_FRAGMENT_SHADER, getResource("$shaderDirectory/$name.frag"))
        )
    }

    override fun dispose() {
        programs.values.forEach(GLProgram::delete)
        programs.clear()
    }
}

class GLProgram(val name: String, val program: Int,
                val attributeSemantics: Map<Semantic, Int>,
                val uniformSemantics: Map<Semantic, Int>,
                val uniformParameters: Map<String, Int>) {

    fun use() {
        glUseProgram(program)
    }

    inline fun use(receiver: GLProgram.() -> Unit) {
        this.use()
        this.receiver()
    }

    fun validate() {
        glValidateProgram(program)
        val status = glGetProgrami(program, GL_VALIDATE_STATUS)
        check(status == GL_TRUE) { "Cannot validate program: ${glGetProgramInfoLog(program)}" }
    }

    fun delete() {
        glDeleteProgram(program)
    }

    companion object {
        fun link(shaders: IntArray): Int {
            val program = glCreateProgram()
            for (shader in shaders) {
                glAttachShader(program, shader)
            }
            glLinkProgram(program)
            val status = glGetProgrami(program, GL_LINK_STATUS)
            check(status == GL_TRUE) { "Cannot link program: ${glGetProgramInfoLog(program)}" }
            return program
        }
    }
}

fun getAttributeLocation(program: Int, name: String): Int =
        glGetAttribLocation(program, name).also { location ->
            warnWhen(location < 0) { "Attribute '$name' has '$location' in program $program" }
        }

fun getUniformLocation(program: Int, name: String): Int =
        glGetUniformLocation(program, name).also { location ->
            warnWhen(location < 0) { "Uniform '$name' has '$location' in program $program" }
        }

object Shader {
    fun compile(type: Int, source: String): Int {
        val shader = glCreateShader(type)
        glShaderSource(shader, source)
        glCompileShader(shader)
        val status = glGetShaderi(shader, GL_COMPILE_STATUS)
        check(status == GL_TRUE) { "Cannot compile shader: ${glGetShaderInfoLog(shader)}" }
        return shader
    }

    fun compile(type: Int, source: URL) = compile(type, source.readText())
}

interface Semantic {
    val symbolicName: String
}

enum class UniformSemantic(val type: Int) : Semantic {
    Local(GL_FLOAT_MAT4),
    Model(GL_FLOAT_MAT4),
    View(GL_FLOAT_MAT4),
    Projection(GL_FLOAT_MAT4),
    ModelView(GL_FLOAT_MAT4),
    ModelViewProjection(GL_FLOAT_MAT4),
    ModelInverse(GL_FLOAT_MAT4),
    ViewInverse(GL_FLOAT_MAT4),
    ProjectionInverse(GL_FLOAT_MAT4),
    ModelViewInverse(GL_FLOAT_MAT4),
    ModelViewProjectionInverse(GL_FLOAT_MAT4),
    ModelInverseTranspose(GL_FLOAT_MAT3),
    ModelViewInverseTranspose(GL_FLOAT_MAT3),
    ViewPort(GL_FLOAT_MAT4);

    override val symbolicName = name.toUpperCase(Locale.ROOT)
}

val uniformSemantics: Map<String, Semantic> = UniformSemantic.values().associateBy { it.symbolicName }

enum class AttributeSemantic(_alternateName: String? = null) : Semantic {
    Position,
    Normal,
    TexCoord0("TexCoord_0"),
    TexCoord1("TexCoord_1"),
    Color0("Color_0"),
    Color1("Color_1"),
    Joint;

    override val symbolicName = (_alternateName ?: name).toUpperCase(Locale.ROOT)
}

val attributeSemantics: Map<String, Semantic> = values().associateBy { it.symbolicName }

object UniformSetter {
    private val color = FloatArray(4)
    private val matrix4f = BufferUtils.createFloatBuffer(16)
    private val matrix3f = BufferUtils.createFloatBuffer(9)
    private val vector4f = BufferUtils.createFloatBuffer(4)

    fun set(location: Int, value: Matrix4fc) {
        glUniformMatrix4fv(location, false, value.get(matrix4f))
    }

    fun set(location: Int, value: Matrix3fc) {
        glUniformMatrix3fv(location, false, value.get(matrix3f))
    }

    fun set(location: Int, value: Color) {
        glUniform4fv(location, value.get(color))
    }

    fun set(location: Int, value: Vector4fc) {
        glUniform4fv(location, value.get(vector4f))
    }
}

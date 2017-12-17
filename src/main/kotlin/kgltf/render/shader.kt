package kgltf.render

import kgltf.util.buildMap
import kgltf.util.warn
import org.joml.Matrix4fc
import org.lwjgl.opengl.GL11.GL_TRUE
import org.lwjgl.opengl.GL20.*
import java.net.URL

object Programs {

    private val suppliers = mapOf(
            "flat" to {
                Program.build("flat",
                        attributes = setOf(AttributeName.POSITION),
                        uniforms = setOf(UniformName.MODEL_VIEW_PROJECTION_MATRIX, UniformName.COLOR)
                )
            },
            "normal" to {
                Program.build("normal",
                        attributes = setOf(AttributeName.POSITION, AttributeName.NORMAL),
                        uniforms = setOf(UniformName.MODEL_VIEW_PROJECTION_MATRIX, UniformName.NORMAL_MATRIX)
                )
            })

    private val programs = HashMap<String, Program>()


    operator fun get(name: String): Program = programs.computeIfAbsent(name) {
        val supplier = requireNotNull(suppliers[it]) { "Program $name doesn't exist" }
        supplier()
    }

    fun clear() {
        programs.values.forEach(Program::delete)
        programs.clear()
    }
}

class Program(val name: String, val program: Int, val attributes: Map<String, Int>, val uniforms: Map<String, Int>) {

    fun use() {
        glUseProgram(program)
    }

    inline fun use(receiver: Program.() -> Unit) {
        this.use()
        this.receiver()
    }

    fun delete() {
        glDeleteProgram(program)
    }

    companion object {
        fun build(name: String, attributes: Set<String>, uniforms: Set<String>): Program {
            val shaders = collectShadersForProgram(name)
            val program = linkProgram(shaders)
            shaders.forEach(::glDeleteShader)
            val attributeMap = attributes.buildMap { glGetAttribLocation(program, it) }
            warnAboutNegativeLocations(attributeMap, name)
            val uniformMap = uniforms.buildMap { glGetUniformLocation(program, it) }
            warnAboutNegativeLocations(uniformMap, name)
            return Program(name, program, attributeMap, uniformMap)
        }

        private fun collectShadersForProgram(name: String) = intArrayOf(
                compileShader(GL_VERTEX_SHADER, Program::class.java.getResource("/shader/$name.vert")),
                compileShader(GL_FRAGMENT_SHADER, Program::class.java.getResource("/shader/$name.frag"))
        )

        private fun warnAboutNegativeLocations(locations: Map<String, Int>, programName: String) {
            for ((name, location) in locations) {
                warn(location < 0) { "'$name' has location $location in program '$programName'" }
            }
        }
    }
}

fun linkProgram(shaders: IntArray): Int {
    val program = glCreateProgram()
    for (shader in shaders) {
        glAttachShader(program, shader)
    }
    glLinkProgram(program)
    val status = glGetProgrami(program, GL_LINK_STATUS)
    check(status == GL_TRUE) { "Cannot link program: ${glGetProgramInfoLog(program)}" }
    return program
}

fun compileShader(type: Int, source: String): Int {
    val shader = glCreateShader(type)
    glShaderSource(shader, source)
    glCompileShader(shader)
    val status = glGetShaderi(shader, GL_COMPILE_STATUS)
    check(status == GL_TRUE) { "Cannot compile shader: ${glGetShaderInfoLog(shader)}" }
    return shader
}

fun compileShader(type: Int, source: URL) = compileShader(type, source.readText())


object AttributeName {
    const val POSITION = "position"
    const val NORMAL = "normal"
}

object UniformName {
    const val COLOR = "color"
    const val MODEL_VIEW_PROJECTION_MATRIX = "modelViewProjectionMatrix"
    const val NORMAL_MATRIX = "normalMatrix"
}

object UniformSetter {
    private val color = FloatArray(4)
    private val matrix4f = FloatArray(16)

    fun set(location: Int, value: Matrix4fc) {
        glUniformMatrix4fv(location, false, value.get(matrix4f))
    }

    fun set(location: Int, value: Color) {
        glUniform4fv(location, value.get(color))
    }
}

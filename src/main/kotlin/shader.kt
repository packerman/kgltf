import org.lwjgl.opengl.GL11.GL_FALSE
import org.lwjgl.opengl.GL20.*
import util.buildMap
import util.warn
import java.net.URL

object Programs {
    val flat: Program by lazy {
        Program.create("flat",
                attributes = setOf("position"),
                uniforms = setOf("mvp", "color")
        )
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

    companion object {
        fun create(name: String, attributes: Set<String>, uniforms: Set<String>): Program {
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
    if (status == GL_FALSE) {
        throw RuntimeException("Cannot link program: ${glGetProgramInfoLog(program)}")
    }
    return program
}

fun compileShader(type: Int, source: String): Int {
    val shader = glCreateShader(type)
    glShaderSource(shader, source)
    glCompileShader(shader)
    val status = glGetShaderi(shader, GL_COMPILE_STATUS)
    if (status == GL_FALSE) {
        throw RuntimeException("Cannot compile shader: ${glGetShaderInfoLog(shader)}")
    }
    return shader
}

fun compileShader(type: Int, source: URL) = compileShader(type, source.readText())

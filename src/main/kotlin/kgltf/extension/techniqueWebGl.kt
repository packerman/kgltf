package kgltf.extension

import com.google.gson.JsonElement
import kgltf.app.glfw.GLProfile
import kgltf.data.Downloader
import kgltf.gltf.Named
import kgltf.gltf.provideName
import kgltf.util.buildMap
import kgltf.util.fromJson
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_SAMPLE_ALPHA_TO_COVERAGE
import org.lwjgl.opengl.GL20.*
import java.util.concurrent.Future
import java.util.logging.Logger

class TechniqueWebGl(val jsonElement: JsonElement) : GltfExtension(EXTENSION_NAME) {

    private val techniqueWebGl: TechniqueWebGlModel = fromJson(jsonElement)

    private lateinit var futures: List<Future<String>>
    private lateinit var shaderSources: List<String>

    private lateinit var compiledShaders: List<Int>
    private lateinit var linkedPrograms: List<LinkedProgram>

    override fun isProfileAccepted(profile: GLProfile): Boolean =
            profile == GLProfile.OpenGl21

    override fun startDownloadFiles(downloader: Downloader) {
        futures = techniqueWebGl.shaders.map { downloader.downloadText(it.uri) }
    }

    override fun collectDownloadedFiles() {
        shaderSources = futures.mapIndexed { i, future ->
            val source = future.get()
            logger.fine { "Download ${techniqueWebGl.shaders[i].provideName("shader", i)}" }
            source
        }
    }

    override fun initialize() {
        compiledShaders = techniqueWebGl.shaders.mapIndexed { i, shader ->
            kgltf.render.gl.Shader.compile(shader.type, reformatSource(shaderSources[i])).also {
                logger.fine { "Compile shader ${shader.provideName("shader", i)}" }
            }
        }
        linkedPrograms = techniqueWebGl.programs.mapIndexed { i, program ->
            val id = kgltf.render.gl.Program.link(
                    intArrayOf(compiledShaders[program.vertexShader],
                            compiledShaders[program.fragmentShader]))
            logger.fine { "Link program ${program.provideName("program", i)}" }
            val attributes = program.attributes.buildMap { glGetAttribLocation(id, it) }
            LinkedProgram(id, attributes)
        }
    }

    companion object {
        const val EXTENSION_NAME = "KHR_technique_webgl"
    }
}

private data class TechniqueWebGlModel(
        val techniques: List<Technique>,
        val programs: List<Program>,
        val shaders: List<Shader>
)

private data class Technique(
        val attributes: Map<String, String>,
        val parameters: Map<String, Parameter>,
        val uniforms: Map<String, String>
)

private data class Parameter(
        val type: Int,
        val semantic: String
)

private val supportedTypes: Set<Int> = setOf(
        GL_BYTE,
        GL_UNSIGNED_BYTE,
        GL_SHORT,
        GL_UNSIGNED_SHORT,
        GL_INT,
        GL_UNSIGNED_INT,
        GL_FLOAT,
        GL_FLOAT_VEC2,
        GL_FLOAT_VEC3,
        GL_FLOAT_VEC4,
        GL_INT_VEC2,
        GL_INT_VEC3,
        GL_INT_VEC4,
        GL_BOOL,
        GL_BOOL_VEC2,
        GL_BOOL_VEC3,
        GL_BOOL_VEC4,
        GL_FLOAT_MAT2,
        GL_FLOAT_MAT3,
        GL_FLOAT_MAT4,
        GL_SAMPLER_2D
)

private val states = setOf(
        GL_BLEND,
        GL_CULL_FACE,
        GL_DEPTH_TEST,
        GL_POLYGON_OFFSET_FILL,
        GL_SAMPLE_ALPHA_TO_COVERAGE
)

private val uniformSemantics = mapOf(
        "LOCAL" to GL_FLOAT_MAT4,
        "MODEL" to GL_FLOAT_MAT4,
        "VIEW" to GL_FLOAT_MAT4,
        "PROJECTION" to GL_FLOAT_MAT4,
        "MODELVIEW" to GL_FLOAT_MAT4,
        "MODELVIEWPROJECTION" to GL_FLOAT_MAT4,
        "MODELINVERSE" to GL_FLOAT_MAT4,
        "VIEWINVERSE" to GL_FLOAT_MAT4,
        "PROJECTIONINVERSE" to GL_FLOAT_MAT4,
        "MODELVIEWINVERSE" to GL_FLOAT_MAT4,
        "MODELVIEWPROJECTIONINVERSE" to GL_FLOAT_MAT4,
        "MODELINVERSETRANSPOSE" to GL_FLOAT_MAT3,
        "MODELVIEWINVERSETRANSPOSE" to GL_FLOAT_MAT3,
        "VIEWPORT" to GL_FLOAT_MAT4
)

private val attributeSemantics = setOf(
        "POSITION",
        "NORMAL",
        "TEXCOORD_0",
        "COLOR_0",
        "JOINT"
)

private data class Program(
        val attributes: List<String>,
        val fragmentShader: Int,
        val vertexShader: Int,
        override val name: String?) : Named

private data class Shader(
        val type: Int,
        val uri: String,
        override val name: String?) : Named

private data class LinkedProgram(val id: Int,
                                 val attributes: Map<String, Int>)

private fun reformatSource(source: String): String = buildString {
    appendln("#version 120")
    appendln()
    source.lines().forEach { line ->
        if (line.startsWith("precision")) {
            appendln("#ifdef GL_ES")
            appendln(line)
            appendln("#endif")
        } else {
            appendln(line)
        }
    }
}

private val logger = Logger.getLogger("extension.technique.webgl")
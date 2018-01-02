package kgltf.extension

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import kgltf.data.Downloader
import kgltf.gl.*
import kgltf.gltf.Named
import kgltf.gltf.genericName
import kgltf.util.buildMap
import kgltf.util.fromJson
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_SAMPLE_ALPHA_TO_COVERAGE
import org.lwjgl.opengl.GL20.*
import java.util.concurrent.Future
import java.util.logging.Logger

class TechniqueWebGl(jsonElement: JsonElement) : GltfExtension(EXTENSION_NAME) {

    private val techniqueWebGl: TechniqueWebGlModel = fromJson(jsonElement)

    private lateinit var futures: List<Future<String>>
    private lateinit var shaderSources: List<String>

    private lateinit var compiledShaders: List<Int>
    private lateinit var linkedPrograms: List<LinkedProgram>
    private lateinit var glTechniques: List<GLTechnique>

    override fun isProfileAccepted(profile: GLProfile): Boolean =
            profile == GLProfile.OpenGl21

    override fun startDownloadFiles(downloader: Downloader) {
        futures = techniqueWebGl.shaders.map { downloader.downloadText(it.uri) }
    }

    override fun collectDownloadedFiles() {
        shaderSources = futures.mapIndexed { i, future ->
            future.get().also {
                logger.fine { "Download ${techniqueWebGl.shaders[i].genericName(i)}" }
            }
        }
    }

    override fun initialize() {
        compiledShaders = techniqueWebGl.shaders.mapIndexed { i, shader ->
            kgltf.gl.Shader.compile(shader.type, reformatSource(shaderSources[i])).also {
                logger.fine { "Compile shader ${shader.genericName(i)}" }
            }
        }
        linkedPrograms = techniqueWebGl.programs.mapIndexed { i, program ->
            val id = GLProgram.link(
                    intArrayOf(compiledShaders[program.vertexShader],
                            compiledShaders[program.fragmentShader]))
            logger.fine { "Link program ${program.genericName(i)}" }
            val attributes = program.attributes.buildMap { getAttributeLocation(id, it) }
            LinkedProgram(id, attributes)
        }
        glTechniques = techniqueWebGl.techniques.map { buildGlTechnique(it) }
    }

    override fun createMaterial(index: Int): GLMaterial? {
        val material = techniqueWebGl.materials[index]
        return material.technique?.let {
            val technique = techniqueWebGl.techniques[it]
            val glTechnique = glTechniques[it]
            val program = glTechnique.program
            val setters = HashMap<String, ParameterValueSetter>()
            for (parameter in program.uniformParameters.keys) {
                val value = material.values?.get(parameter) ?:
                        technique.parameters[parameter]?.value
                        ?: error("No value for parameter '$parameter' in material $index")
                val type = requireNotNull(technique.parameters[parameter]).type
                setters[parameter] = createParameterValueSetter(value, type)
            }
            TechniqueMaterial(glTechnique, setters)
        }
    }

    private fun buildGlTechnique(technique: Technique): GLTechnique = with(technique) {
        val program = linkedPrograms[program]
        val attributeMap = HashMap<Semantic, Int>()
        for ((attributeName, parameterName) in attributes) {
            val parameter = requireNotNull(parameters[parameterName]) { "No parameter for attribute '$attributeName' provided" }
            val semanticName = requireNotNull(parameter.semantic) { "Attribute parameter '$parameterName' has to have semantic" }
            val semantic = requireNotNull(attributeSemantics[semanticName]) { "Unknown semantic name '$semanticName'" }
            attributeMap[semantic] = getAttributeLocation(program.id, attributeName)
        }
        val uniformMap = HashMap<Semantic, Int>()
        val parameterMap = HashMap<String, Int>()
        val nodeTransforms = HashMap<Int, Int>()
        for ((uniformName, parameterName) in uniforms) {
            val parameter = requireNotNull(parameters[parameterName]) { "No parameter for uniform '$uniformName' provided" }
            val location = getUniformLocation(program.id, uniformName)
            when {
                parameter.node != null -> nodeTransforms[parameter.node] = location
                parameter.semantic != null -> {
                    val semanticName = parameter.semantic
                    val semantic = requireNotNull(uniformSemantics[semanticName]) { "Unknown semantic name '$semanticName'" }
                    check(!uniformMap.containsKey(semantic)) { "There are two attributes of the same '$semantic' semantic." }
                    uniformMap[semantic] = location
                }
                else -> parameterMap[parameterName] = location
            }
        }
        val glProgram = GLProgram("", program.id, attributeMap, uniformMap, parameterMap)
        val toEnable = states?.enable ?: emptyList()
        GLTechnique(glProgram, toEnable, nodeTransforms)
    }

    companion object {
        const val EXTENSION_NAME = "KHR_technique_webgl"
    }
}

private data class TechniqueWebGlModel(
        val techniques: List<Technique>,
        val programs: List<Program>,
        val shaders: List<Shader>,
        val materials: List<Material>)

private data class Technique(
        val attributes: Map<String, String>,
        val parameters: Map<String, Parameter>,
        val uniforms: Map<String, String>,
        val program: Int,
        val states: States?)

private data class States(
        val enable: List<Int>?)

private data class Material(
        val values: Map<String, JsonArray>?,
        val technique: Int?
)

private data class Parameter(
        val type: Int,
        val semantic: String?,
        val node: Int?,
        val value: JsonArray?)


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

typealias ParameterValueSetter = (Int) -> Unit

fun createParameterValueSetter(value: JsonArray, type: Int): ParameterValueSetter {
    fun JsonArray.getFloat(i: Int) = get(i).asJsonPrimitive.asFloat
    fun JsonArray.getInt(i: Int) = get(i).asJsonPrimitive.asInt
    when (type) {
        GL_FLOAT_VEC4 -> {
            check(value.size() == 4)
            val v0 = value.getFloat(0)
            val v1 = value.getFloat(1)
            val v2 = value.getFloat(2)
            val v3 = value.getFloat(3)
            return { location -> glUniform4f(location, v0, v1, v2, v3) }
        }
        GL_FLOAT_VEC3 -> {
            check(value.size() == 3)
            val v0 = value.getFloat(0)
            val v1 = value.getFloat(1)
            val v2 = value.getFloat(2)
            return { location -> glUniform3f(location, v0, v1, v2) }
        }
        GL_FLOAT -> {
            check(value.size() == 1)
            val v0 = value.getFloat(0)
            return { location -> glUniform1f(location, v0) }
        }
        GL_SAMPLER_2D -> {
            check(value.size() == 1)
            val v0 = value.getInt(0)
            return { location -> glUniform1i(location, v0) }
        }
        else -> error("Unknown type $type")
    }
}

private val states = setOf(
        GL_BLEND,
        GL_CULL_FACE,
        GL_DEPTH_TEST,
        GL_POLYGON_OFFSET_FILL,
        GL_SAMPLE_ALPHA_TO_COVERAGE
)

private data class Program(
        val attributes: List<String>,
        val fragmentShader: Int,
        val vertexShader: Int,
        override val name: String?) : Named

private fun Program.genericName(i: Int) = genericName("program", i)

private data class Shader(
        val type: Int,
        val uri: String,
        override val name: String?) : Named

private fun Shader.genericName(i: Int) = genericName("shader", i)

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

private data class GLTechnique(val program: GLProgram,
                               val states: List<Int>,
                               val nodeTransforms: Map<Int, Int>) {

    fun applyToProgram(context: RenderingContext) {
        nodeTransforms.forEach { (node, location) ->
            UniformSetter.set(location, context.nodes[node].transform.matrix)
        }
    }
}

private data class TechniqueMaterial(val technique: GLTechnique,
                                     val setters: Map<String, ParameterValueSetter>) : GLMaterial() {
    override val program: GLProgram = technique.program

    override fun applyToProgram(context: RenderingContext) {
        setters.forEach { (name, setter) ->
            val location = requireNotNull(program.uniformParameters[name])
            setter(location)
        }
        technique.applyToProgram(context)
    }
}

private val logger = Logger.getLogger("extension.technique.webgl")

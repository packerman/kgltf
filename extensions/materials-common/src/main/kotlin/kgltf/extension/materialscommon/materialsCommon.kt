package kgltf.extension.materialscommon

import com.google.gson.JsonElement
import kgltf.extension.GltfExtension
import kgltf.gl.GLMaterial
import kgltf.gl.GLProgram
import kgltf.gl.RenderingContext
import kgltf.util.buildMap
import kgltf.util.fromJson
import kgltf.util.warnWhen
import org.joml.Vector4f
import org.joml.Vector4fc
import java.util.logging.Logger

class MaterialsCommonExtension(jsonElement: JsonElement) : GltfExtension(EXTENSION_NAME) {

    private val model: MaterialsCommonModel = fromJson(jsonElement)
    private val lightToNode = model.lightToNodeMap()

    init {
        logger.fine("Number of lights: ${model.extensions?.lights?.size ?: 0}")
        warnWhen(model.extensions?.lights?.size.let { size -> size == null || size == 0 }) { "No lights defined for scene." }
    }

    private val lights = createLights(model.extensions?.lights ?: defaultLightList, lightToNode)
    private val lightUniformSetter = LightUniformSetter(lights)

    private val programBuilder = createMaterialsCommonProgramBuilder()

    private val colorProperties = mapOf(
            "BLINN" to setOf("ambient", "diffuse", "emission", "specular"),
            "PHONG" to setOf("ambient", "diffuse", "emission", "specular")
    )

    override fun createMaterial(index: Int): GLMaterial? =
            model.materials[index].extensions?.materialProperties?.let { properties ->
                val defaultColor = Vector4f(0f, 0f, 0f, 1f)
                when (properties.technique) {
                    "BLINN" -> {
                        val values = properties.values
                        val programName = if (isSamplerValue(values["diffuse"])) "blinn_texture" else "blinn"
                        val program = programBuilder[programName]
                        val valueSetters: Map<String, UniformValueSetter> = buildMap {
                            for (colorProperty in requireNotNull(colorProperties["BLINN"])) {
                                put(colorProperty, colorValueSetterFromElement(values[colorProperty], defaultColor))
                            }
                            put("shininess", parameterValueSetterFromElement(values["shininess"], 0f))
                        }
                        GLBlinnMaterialMaterial(program, valueSetters)
                    }
                    "PHONG" -> {
                        val values = properties.values
                        val programName = if (isSamplerValue(values["diffuse"])) "phong_texture" else "phong"
                        val program = programBuilder[programName]
                        val valueSetters: Map<String, UniformValueSetter> = buildMap {
                            for (colorProperty in requireNotNull(colorProperties["PHONG"])) {
                                put(colorProperty, colorValueSetterFromElement(values[colorProperty], defaultColor))
                            }
                            put("shininess", parameterValueSetterFromElement(values["shininess"], 0f))
                        }
                        GLBlinnMaterialMaterial(program, valueSetters)
                    }
                    else -> error("Unknown technique: ${properties.technique}")
                }
            }

    override fun preRender(context: RenderingContext) {
        lightUniformSetter.update(context)
        programBuilder.usedPrograms.forEach { program ->
            program.use {
                lightUniformSetter.applyToProgram(this)
            }
        }
    }

    override fun dispose() {
        programBuilder.dispose()
    }

    companion object {
        const val EXTENSION_NAME = "KHR_materials_common"

        fun isSamplerValue(jsonElement: JsonElement?) =
                jsonElement != null && (
                        (jsonElement.isJsonArray && jsonElement.asJsonArray.size() == 1) ||
                                (jsonElement.isJsonObject && jsonElement.asJsonObject.has("index")))

        fun colorValueSetterFromElement(jsonElement: JsonElement?, defaultValue: Vector4fc) = when {
            jsonElement == null -> FloatVec4ValueSetter(defaultValue.x(), defaultValue.y(), defaultValue.z(), defaultValue.w())
            jsonElement.isJsonArray -> {
                val jsonArray = jsonElement.asJsonArray
                when (jsonArray.size()) {
                    1 -> {
                        SamplerValueSetter(jsonArray[0].asJsonPrimitive.asInt)
                    }
                    4 -> {
                        FloatVec4ValueSetter(jsonArray[0].asJsonPrimitive.asFloat,
                                jsonArray[1].asJsonPrimitive.asFloat,
                                jsonArray[2].asJsonPrimitive.asFloat,
                                jsonArray[3].asJsonPrimitive.asFloat)
                    }
                    else -> error("Unknown value: $jsonElement")
                }
            }
            jsonElement.isJsonObject -> {
                val jsonObject = jsonElement.asJsonObject
                SamplerValueSetter(jsonObject.get("index").asJsonPrimitive.asInt)
            }
            else -> error("Unknown value: $jsonElement")
        }

        fun parameterValueSetterFromElement(jsonElement: JsonElement?, defaultValue: Float) = when {
            jsonElement == null -> FloatValueSetter(defaultValue)
            jsonElement.isJsonArray -> {
                val jsonArray = jsonElement.asJsonArray
                when (jsonArray.size()) {
                    1 -> FloatValueSetter(jsonArray[0].asJsonPrimitive.asFloat)
                    else -> error("Unknown value: $jsonElement")
                }
            }
            else -> error("Unknown value: $jsonElement")
        }

        fun createLights(lights: List<Light>, lightToNode: Map<Int, Int>): List<GLLight> {
            return lights.mapIndexed { i, light ->
                when (light.type) {
                    "directional" -> {
                        val directionalLight = requireNotNull(light.directional)
                        check(directionalLight.color == null || directionalLight.color.size == 4)
                        GLDirectionalLight(lightToNode[i],
                                createColor(directionalLight.color))
                    }
                    else -> error("Unknown type of light: ${light.type}")
                }
            }
        }

        fun createColor(color: List<Float>?) =
                if (color != null) Vector4f(color[0], color[1], color[2], color[3]) else defaultLightColor

        val defaultLightColor: Vector4fc = Vector4f(0f, 0f, 0f, 1f)

        val defaultLightList = listOf(
                Light(type = "directional",
                        directional = DirectionalLight(
                                color = listOf(1f, 1f, 1f, 1f)),
                        point = null)
        )
    }
}

class GLBlinnMaterialMaterial(override val program: GLProgram,
                              val valueSetters: Map<String, UniformValueSetter>) : GLMaterial {

    override fun applyToProgram(context: RenderingContext) {
        for ((parameter, setter) in valueSetters) {
            program.uniformParameters[parameter]?.let { location ->
                setter.set(location, context)
            }
        }
    }
}

private val logger = Logger.getLogger("extension.materialscommon")

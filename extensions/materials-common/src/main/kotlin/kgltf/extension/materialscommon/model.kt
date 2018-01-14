package kgltf.extension.materialscommon

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import kgltf.util.buildMap

data class MaterialsCommonModel(
        val materials: List<Material>,
        val nodes: List<Node>,
        val extensions: GlobalExtensions?
)

data class GlobalExtensions(
        @SerializedName(MaterialsCommonExtension.EXTENSION_NAME)
        val lights: List<Light>
)

data class Light(
        val type: String,
        val directional: DirectionalLight?,
        val point: PointLight?
)

data class DirectionalLight(
        val color: List<Float>?
)

data class PointLight(
        val color: List<Float>?
)

data class Node(
        val extensions: NodeExtensions?
)

data class NodeExtensions(
        @SerializedName(MaterialsCommonExtension.EXTENSION_NAME)
        val nodeProperties: NodeProperties
)

data class NodeProperties(
        val light: Int
)

data class Material(
        val extensions: MaterialExtensions?
)

data class MaterialExtensions(
        @SerializedName(MaterialsCommonExtension.EXTENSION_NAME)
        val materialProperties: MaterialProperties
)

data class MaterialProperties(
        val doubleSided: Boolean,
        val technique: String,
        val transparent: Boolean,
        val values: Map<String, JsonElement>
)

fun MaterialsCommonModel.lightToNodeMap(): Map<Int, Int> = buildMap {
    nodes.forEachIndexed { i, node ->
        node.extensions?.nodeProperties?.let { properties ->
            put(properties.light, i)
        }
    }
}

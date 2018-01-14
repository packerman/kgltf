import com.google.gson.JsonElement
import kgltf.extension.GltfExtension
import kgltf.extension.materialscommon.MaterialsCommonExtension
import kgltf.extension.techniquewebgl.TechniqueWebGl

typealias LoadingFunction = (JsonElement) -> GltfExtension

object ExtensionsLoader {

    private val extensions = HashMap<String, LoadingFunction>()

    fun registerExtension(extensionName: String, loadingFunction: LoadingFunction) {
        extensions[extensionName] = loadingFunction
    }

    fun loadExtension(extensionName: String, jsonElement: JsonElement): GltfExtension? =
            extensions[extensionName]?.invoke(jsonElement)

    init {
        registerExtension(TechniqueWebGl.EXTENSION_NAME, ::TechniqueWebGl)
        registerExtension(MaterialsCommonExtension.EXTENSION_NAME, ::MaterialsCommonExtension)
    }
}

import com.google.gson.JsonElement
import kgltf.extension.GltfExtension
import kgltf.extension.TechniqueWebGl

fun registerExtensions() {
    ExtensionsLoader.registerExtension(TechniqueWebGl.EXTENSION_NAME, ::TechniqueWebGl)
}

typealias LoadingFunction = (JsonElement) -> GltfExtension

object ExtensionsLoader {

    private val extensions = HashMap<String, LoadingFunction>()

    fun registerExtension(extensionName: String, loadingFunction: LoadingFunction) {
        extensions[extensionName] = loadingFunction
    }

    fun loadExtension(extensionName: String, jsonElement: JsonElement): GltfExtension? =
            extensions[extensionName]?.invoke(jsonElement)
}

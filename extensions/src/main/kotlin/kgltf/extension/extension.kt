package kgltf.extension

import com.google.gson.JsonElement
import kgltf.data.Downloader
import kgltf.gl.GLMaterial
import kgltf.gl.ProfileFilter

abstract class GltfExtension(val name: String) : ProfileFilter {
    open fun startDownloadFiles(downloader: Downloader) {}

    open fun collectDownloadedFiles() {}
    open fun initialize() {}

    open fun createMaterial(index: Int): GLMaterial? = null
}

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

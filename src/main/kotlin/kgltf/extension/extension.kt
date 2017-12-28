package kgltf.extension

import kgltf.app.ExtensionsLoader
import kgltf.app.glfw.ProfileFilter
import kgltf.data.Downloader
import kgltf.render.gl.GLMaterial

abstract class GltfExtension(val name: String) : ProfileFilter {
    open fun startDownloadFiles(downloader: Downloader) {}

    open fun collectDownloadedFiles() {}
    open fun initialize() {}

    open fun createMaterial(index: Int): GLMaterial? = null
}

fun registerExtensions() {
    ExtensionsLoader.registerExtension(TechniqueWebGl.EXTENSION_NAME, ::TechniqueWebGl)
}


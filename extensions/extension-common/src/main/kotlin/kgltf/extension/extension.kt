package kgltf.extension

import kgltf.data.Downloader
import kgltf.gl.GLMaterial
import kgltf.gl.ProfileFilter

abstract class GltfExtension(val name: String) : ProfileFilter {
    open fun startDownloadFiles(downloader: Downloader) {}

    open fun collectDownloadedFiles() {}
    open fun initialize() {}

    open fun createMaterial(index: Int): GLMaterial? = null
}

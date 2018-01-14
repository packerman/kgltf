package kgltf.extension

import kgltf.data.Downloader
import kgltf.gl.GLMaterial
import kgltf.gl.ProfileFilter
import kgltf.gl.RenderingContext
import kgltf.util.Disposable

abstract class GltfExtension(val name: String) : ProfileFilter, Disposable {
    open fun startDownloadFiles(downloader: Downloader) {}

    open fun collectDownloadedFiles() {}
    open fun initialize() {}

    open fun createMaterial(index: Int): GLMaterial? = null
    open fun preRender(context: RenderingContext) {}

    override fun dispose() {}
}

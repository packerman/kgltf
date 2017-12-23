package kgltf.gltf

import com.google.gson.JsonElement
import kgltf.app.GltfData
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER

abstract class Visitor(val gltf: Gltf, val jsonElement: JsonElement, val data: GltfData) {

    fun visit() {
        val jsonObject = jsonElement.asJsonObject
        val jsonBufferViews = jsonObject.getAsJsonArray("bufferViews")
        val jsonAccessors = jsonObject.getAsJsonArray("accessors")
        val jsonMeshes = jsonObject.getAsJsonArray("meshes")
        val jsonCameras = jsonObject.getAsJsonArray("cameras")
        val jsonNodes = jsonObject.getAsJsonArray("nodes")
        val jsonScenes = jsonObject.getAsJsonArray("scenes")

        with(gltf) {
            bufferViews.forEachIndexed { index, bufferView ->
                check(bufferView.target in supportedTargets) { "Unsupported target" }
                visitBufferView(index, bufferView, jsonBufferViews.get(index))
            }
            accessors.forEachIndexed { index, accessor -> visitAccessor(index, accessor, jsonAccessors.get(index)) }
            meshes.forEachIndexed { index, mesh -> visitMesh(index, mesh, jsonMeshes.get(index)) }
            cameras?.forEachIndexed { index, camera ->
                require(when (camera.type) {
                    "perspective" -> camera.perspective != null && camera.orthographic == null
                    "orthographic" -> camera.orthographic != null && camera.perspective == null
                    else -> false
                })
                visitCamera(index, camera, jsonCameras.get(index))
            }
            nodes.forEachIndexed { index, node ->
                require(node.matrix == null || (node.translation == null && node.rotation == null && node.scale == null))
                require(node.matrix == null || node.matrix.size == 16)
                require(node.translation == null || node.translation.size == 3)
                require(node.rotation == null || node.rotation.size == 4)
                require(node.scale == null || node.scale.size == 3)
                visitNode(index, node, jsonNodes.get(index))
            }
            scenes.forEachIndexed { index, scene -> visitScene(index, scene, jsonScenes.get(index)) }
        }
    }

    abstract fun visitBufferView(index: Int, bufferView: BufferView, json: JsonElement)
    abstract fun visitAccessor(index: Int, accessor: Accessor, json: JsonElement)
    abstract fun visitMesh(index: Int, mesh: Mesh, json: JsonElement)
    abstract fun visitCamera(index: Int, camera: Camera, json: JsonElement)
    abstract fun visitNode(index: Int, node: Node, json: JsonElement)
    abstract fun visitScene(index: Int, scene: Scene, json: JsonElement)

    companion object {
        val supportedTargets = setOf(GL_ARRAY_BUFFER, GL_ELEMENT_ARRAY_BUFFER)

        val sizeInBytes = mapOf(GL_BYTE to 1,
                GL_UNSIGNED_BYTE to 1,
                GL_SHORT to 2,
                GL_UNSIGNED_SHORT to 2,
                GL_UNSIGNED_INT to 4,
                GL_FLOAT to 4)

        val numberOfComponents = mapOf("SCALAR" to 1,
                "VEC2" to 2,
                "VEC3" to 3,
                "VEC4" to 4,
                "MAT2" to 4,
                "MAT3" to 9,
                "MAT4" to 16)

        fun sizeInBytes(componentType: Int) =
                requireNotNull(sizeInBytes[componentType]) { "Unknown component type $componentType" }

        fun numberOfComponents(type: String) =
                requireNotNull(numberOfComponents[type]) { "Unknown type $type" }
    }
}

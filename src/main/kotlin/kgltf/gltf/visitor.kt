package kgltf.gltf

import com.google.gson.JsonObject
import kgltf.app.GltfData
import kgltf.util.*
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER

abstract class Visitor(val root: JsonObject, val data: GltfData) {

    fun visit() {
        root.forEachObjectIndexed("bufferViews") { index, jsonObject ->
            check(jsonObject.getAsInt("target") in supportedTargets) { "Unsupported target" }
            visitBufferView(index, fromJson(jsonObject), jsonObject)
        }
        root.forEachObjectIndexed("accessors") { index, jsonObject -> visitAccessor(index, fromJson(jsonObject), jsonObject) }
        root.forEachObjectIndexed("meshes") { index, jsonObject -> visitMesh(index, fromJson(jsonObject), jsonObject) }
        root.forEachObjectIndexed("cameras") { index, jsonObject ->
            require(when (jsonObject.getAsString("type")) {
                "perspective" -> jsonObject.has("perspective") && !jsonObject.has("orthographic")
                "orthographic" -> jsonObject.has("orthographic") && !jsonObject.has("perspective")
                else -> false
            })
            visitCamera(index, fromJson(jsonObject), jsonObject)
        }
        root.forEachObjectIndexed("nodes") { index, jsonObject ->
            require(!jsonObject.has("matrix") || (!jsonObject.has("translation") && !jsonObject.has("rotation") && !jsonObject.has("scale")))
            require(!jsonObject.has("matrix") || jsonObject.count("matrix") == 16)
            require(!jsonObject.has("translation") || jsonObject.count("translation") == 3)
            require(!jsonObject.has("rotation") || jsonObject.count("rotation") == 4)
            require(!jsonObject.has("scale") || jsonObject.count("scale") == 3)
            visitNode(index, fromJson(jsonObject), jsonObject)
        }
        root.forEachObjectIndexed("scenes") { index, jsonObject -> this.visitScene(index, fromJson(jsonObject), jsonObject) }
    }

    abstract fun visitBufferView(index: Int, bufferView: BufferView, jsonObject: JsonObject)
    abstract fun visitAccessor(index: Int, accessor: Accessor, jsonObject: JsonObject)
    abstract fun visitMesh(index: Int, mesh: Mesh, jsonObject: JsonObject)
    abstract fun visitCamera(index: Int, camera: Camera, jsonObject: JsonObject)
    abstract fun visitNode(index: Int, node: Node, jsonObject: JsonObject)
    abstract fun visitScene(index: Int, scene: Scene, jsonObject: JsonObject)

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

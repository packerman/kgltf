package kgltf.gltf

import kgltf.app.GltfData
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER

abstract class Visitor(val root: Root, val data: GltfData) {

    fun visit() {
        root.bufferViews.forEachIndexed { index, bufferView ->
            check(bufferView.target in supportedTargets) { "Unsupported target" }
            visitBufferView(index, bufferView)
        }
        root.accessors.forEach(this::visitAccessor)
        root.meshes.forEachIndexed(this::visitMesh)
        root.cameras?.forEach { camera ->
            require(when (camera.type) {
                "perspective" -> camera.perspective != null && camera.orthographic == null
                "orthographic" -> camera.orthographic != null && camera.perspective == null
                else -> false
            })
            visitCamera(camera)
        }
        root.nodes.forEach { node ->
            require(node.matrix == null || (node.translation == null && node.rotation == null && node.scale == null))
            require(node.matrix == null || node.matrix.size == 16)
            require(node.translation == null || node.translation.size == 3)
            require(node.rotation == null || node.rotation.size == 4)
            require(node.scale == null || node.scale.size == 3)
            visitNode(node)
        }
        root.scenes.forEach { scene ->
            visitScene(scene)
        }
    }

    abstract fun visitBufferView(index: Int, bufferView: BufferView)
    abstract fun visitAccessor(accessor: Accessor)
    abstract fun visitMesh(index: Int, mesh: Mesh)
    abstract fun visitCamera(camera: Camera)
    abstract fun visitNode(node: Node)
    abstract fun visitScene(scene: Scene)

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

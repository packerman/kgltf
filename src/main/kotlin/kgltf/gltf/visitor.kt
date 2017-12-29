package kgltf.gltf

import kgltf.app.GltfData
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER

abstract class Visitor(val gltf: Gltf, val data: GltfData) {

    fun visit() {
        with(gltf) {
            bufferViews.forEachIndexed { index, bufferView ->
                check(bufferView.target in supportedTargets) { "Unsupported target" }
                visitBufferView(index, bufferView)
            }
            accessors.forEachIndexed { index, accessor -> visitAccessor(index, accessor) }
            materials?.forEachIndexed { index, material ->
                material.pbrMetallicRoughness?.baseColorFactor?.let {
                    require(it.size == 4)
                }
                visitMaterial(index, material)
            }
            meshes.forEachIndexed { index, mesh -> visitMesh(index, mesh) }
            cameras?.forEachIndexed { index, camera ->
                require(when (camera.type) {
                    "perspective" -> camera.perspective != null && camera.orthographic == null
                    "orthographic" -> camera.orthographic != null && camera.perspective == null
                    else -> false
                })
                visitCamera(index, camera)
            }
            val sortedNodes = TopologicalSort(nodes).sorted

            sortedNodes.forEach { index ->
                val node = nodes[index]
                require(node.matrix == null || (node.translation == null && node.rotation == null && node.scale == null))
                require(node.matrix == null || node.matrix.size == 16)
                require(node.translation == null || node.translation.size == 3)
                require(node.rotation == null || node.rotation.size == 4)
                require(node.scale == null || node.scale.size == 3)
                visitNode(index, node)
            }
            scenes.forEachIndexed { index, scene -> visitScene(index, scene) }
        }
    }

    abstract fun visitBufferView(index: Int, bufferView: BufferView)
    abstract fun visitAccessor(index: Int, accessor: Accessor)
    abstract fun visitMaterial(index: Int, material: Material)
    abstract fun visitMesh(index: Int, mesh: Mesh)
    abstract fun visitCamera(index: Int, camera: Camera)
    abstract fun visitNode(index: Int, node: Node)
    abstract fun visitScene(index: Int, scene: Scene)

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

class TopologicalSort(private val nodes: List<Node>) {

    private val _sorted = ArrayList<Int>(nodes.size)
    private val visited = HashSet<Int>(nodes.size)

    init {
        sort()
        check(_sorted.size == nodes.size)
        check(visited.size == nodes.size)
    }

    val sorted: List<Int> = _sorted

    private fun sort() {
        (0 until nodes.size).forEach { i ->
            if (i !in visited) {
                visit(i)
            }
        }
    }

    private fun visit(i: Int) {
        nodes[i].children?.forEach { j ->
            check(j !in visited) { "Scene graph node is not a proper tree" }
            visit(j)
        }
        visited.add(i)
        _sorted.add(i)
    }
}

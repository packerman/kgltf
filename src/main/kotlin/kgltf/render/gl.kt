package kgltf.render

import org.lwjgl.opengl.GL11.glDrawArrays
import org.lwjgl.opengl.GL11.glDrawElements
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL20.glVertexAttribPointer
import org.lwjgl.opengl.GL30.glBindVertexArray
import java.util.logging.Logger

class GLBufferView(val target: Int, val buffer: Int) {

    fun bind() {
        glBindBuffer(target, buffer)
    }
}

class GLAccessor(val bufferView: GLBufferView,
                 val byteOffset: Long,
                 val componentType: Int,
                 val count: Int,
                 val numberOfComponents: Int) {

    fun setVertexAttribPointer(index: Int) {
        glVertexAttribPointer(index, numberOfComponents, componentType, false, 0, byteOffset)
    }
}

open class GLPrimitive(val vertexArray: Int,
                       val mode: Int,
                       val attributes: Map<String, GLAccessor>) {

    private val count = attributes.values.first().count
    private val targets: Set<Int> = attributes.values.mapTo(HashSet()) { it.bufferView.target }

    open fun init(attributeLocations: Map<String, Int>) {
        glBindVertexArray(vertexArray)
        attributes.forEach { (attribute, accessor) ->
            accessor.bufferView.bind()
            attributeLocations[attribute]?.let { location ->
                glEnableVertexAttribArray(location)
                accessor.setVertexAttribPointer(location)
            }
        }
    }

    fun render() {
        glBindVertexArray(vertexArray)
        draw()
    }

    protected open fun draw() {
        glDrawArrays(mode, 0, count)
    }

    open fun unbind() {
        glBindVertexArray(0)
        targets.forEach {
            glBindBuffer(it, 0)
        }
    }
}

class GLPrimitiveIndex(vertexArray: Int,
                       mode: Int,
                       attributes: Map<String, GLAccessor>,
                       val indices: GLAccessor) : GLPrimitive(vertexArray, mode, attributes) {

    override fun init(attributeLocations: Map<String, Int>) {
        super.init(attributeLocations)
        indices.bufferView.bind()
    }

    override fun draw() {
        glDrawElements(mode, indices.count, indices.componentType, indices.byteOffset)
    }

    override fun unbind() {
        super.unbind()
        glBindBuffer(indices.bufferView.target, 0)
    }
}

class GLMesh(val primitives: List<GLPrimitive>) {
    fun render() {
        primitives.forEach(GLPrimitive::render)
    }
}

class GLNode(val mesh: GLMesh) {
    fun render() {
        mesh.render()
    }
}

class GLScene(val nodes: List<GLNode>) {
    fun render() {
        nodes.forEach(GLNode::render)
    }
}

private val logger = Logger.getLogger("kgltf.gl")
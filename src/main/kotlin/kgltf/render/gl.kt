package kgltf.render

import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.lwjgl.opengl.GL11.glDrawArrays
import org.lwjgl.opengl.GL11.glDrawElements
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL20.glVertexAttribPointer
import org.lwjgl.opengl.GL30.glBindVertexArray

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

    private lateinit var program: Program
    private lateinit var attributeLocations: Map<String, Int>

    private val normalMatrix = Matrix4f()
    private val modelViewProjectionMatrix = Matrix4f()
    private val modelViewMatrix = Matrix4f()

    fun init() {
        val hasNormals = primitives.any { it.attributes.containsKey("NORMAL") }
        program = if (hasNormals) Programs.normal else Programs.flat
        program.use {
            attributeLocations = getSemanticAttributesLocation(this)
            primitives.forEach { primitive ->
                primitive.init(attributeLocations)
                primitive.unbind()
            }
        }
    }

    fun render(modelTransform: Transform, cameraTransform: CameraTransform) {
        program.use {
            uniforms[UniformName.MODEL_VIEW_PROJECTION_MATRIX]?.let { location ->
                modelViewProjectionMatrix.set(cameraTransform.projectionViewMatrix)
                        .mul(modelTransform.matrix)
                UniformSetter.set(location, modelViewProjectionMatrix)
            }
            modelViewMatrix.set(cameraTransform.viewMatrix)
                    .mul(modelTransform.matrix)
            uniforms[UniformName.NORMAL_MATRIX]?.let { location ->
                normalMatrix.set(modelViewMatrix).invert().transpose()
                UniformSetter.set(location, normalMatrix)
            }
            uniforms[UniformName.COLOR]?.let { location ->
                UniformSetter.set(location, Colors.GRAY)
            }
            primitives.forEach(GLPrimitive::render)
        }
    }

    companion object {
        private val mapping = mapOf("POSITION" to AttributeName.POSITION,
                "TEXCOORD_0" to "uv",
                "NORMAL" to AttributeName.NORMAL)

        fun getSemanticAttributesLocation(program: Program) = HashMap<String, Int>().apply {
            for ((semanticAttribute, programAttribute) in mapping) {
                program.attributes[programAttribute]?.let { location ->
                    set(semanticAttribute, location)
                }
            }
        }.toMap()
    }
}

class GLNode(val transform: Transform,
             val mesh: GLMesh? = null,
             val camera: Camera? = null) {

    fun render(cameraTransform: CameraTransform) {
        mesh?.render(transform, cameraTransform)
    }

    companion object {
        internal val defaultCameraNode = GLNode(camera = IdentityCamera(), transform = Transform())
    }
}

class GLScene(val nodes: List<GLNode>) {
    fun render(cameraTransform: CameraTransform) {
        nodes.forEach { it.render(cameraTransform) }
    }
}

class CameraTransform {

    private val _viewMatrix = Matrix4f()
    val viewMatrix: Matrix4fc = _viewMatrix

    private val _projectionViewMatrix = Matrix4f()
    val projectionViewMatrix: Matrix4fc = _projectionViewMatrix

    fun set(camera: Camera, transform: Transform) {
        _projectionViewMatrix
                .set(camera.projectionMatrix)
                .mul(transform.matrix.invert(_viewMatrix))
    }
}

class Renderer(val scenes: List<GLScene>, val cameraNodes: List<GLNode>) {

    private val cameraTransforms = CameraTransform()

    fun render(sceneNum: Int, cameraNum: Int? = null) {
        require((cameraNum == null && cameraNodes.isEmpty()) ||
                (cameraNum != null && (cameraNum in 0 until cameraNodes.size)))
        require(sceneNum in 0 until scenes.size)

        val cameraNode = if (cameraNum != null) cameraNodes[cameraNum] else GLNode.defaultCameraNode

        render(scenes[sceneNum], cameraNode)
    }

    private fun render(scene: GLScene, cameraNode: GLNode) {
        val camera = requireNotNull(cameraNode.camera)
        cameraTransforms.set(camera, cameraNode.transform)
        scene.render(cameraTransforms)
    }
}

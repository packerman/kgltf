package kgltf.render.gl

import kgltf.render.Camera
import kgltf.render.Colors
import kgltf.render.IdentityCamera
import kgltf.render.Transform
import kgltf.util.Disposable
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.lwjgl.opengl.GL11.glDrawArrays
import org.lwjgl.opengl.GL11.glDrawElements
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL20.glVertexAttribPointer
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glDeleteVertexArrays

class GLBufferView(val target: Int, val buffer: Int, val byteLength: Int) {

    fun bind() {
        glBindBuffer(target, buffer)
    }

    fun init() {
        glBufferData(target, byteLength.toLong(), GL_STATIC_DRAW)
    }

    fun copyBufferData(data: ByteArray, offset: Int = 0) {
        val mappedBuffer = requireNotNull(glMapBuffer(target, GL_WRITE_ONLY)) { "Cannot allocate buffer" }
        mappedBuffer.put(data, offset, byteLength)
        glUnmapBuffer(target)
    }

    fun unbind() {
        glBindBuffer(target, 0)
    }
}

fun GLBufferView.initWithData(data: ByteArray, offset: Int = 0) {
    init()
    copyBufferData(data, offset)
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

abstract class GLPrimitive(val mode: Int,
                           val attributes: Map<String, GLAccessor>) {

    protected val targets: Set<Int> = attributes.values.mapTo(HashSet()) { it.bufferView.target }

    abstract fun init(attributeLocations: Map<String, Int>)
    abstract fun render()
    abstract fun unbind()

    protected fun initAttributes(attributeLocations: Map<String, Int>) {
        attributes.forEach { (attribute, accessor) ->
            accessor.bufferView.bind()
            attributeLocations[attribute]?.let { location ->
                glEnableVertexAttribArray(location)
                accessor.setVertexAttribPointer(location)
            }
        }
    }

    protected fun unbindBufferTargets() {
        targets.forEach {
            glBindBuffer(it, 0)
        }
    }
}

class GL2Primitive(mode: Int,
                   attributes: Map<String, GLAccessor>) : GLPrimitive(mode, attributes) {

    private val attributeLocations = HashMap<String, Int>()
    private val count = attributes.values.first().count

    override fun init(attributeLocations: Map<String, Int>) {
        this.attributeLocations.putAll(attributeLocations)
    }

    override fun render() {
        initAttributes(attributeLocations)
        glDrawArrays(mode, 0, count)
    }

    override fun unbind() {
        unbindBufferTargets()
    }
}

class GL2IndexedPrimitive(val indices: GLAccessor,
                          mode: Int,
                          attributes: Map<String, GLAccessor>) : GLPrimitive(mode, attributes) {
    private val attributeLocations = HashMap<String, Int>()

    override fun init(attributeLocations: Map<String, Int>) {
        this.attributeLocations.putAll(attributeLocations)
    }

    override fun render() {
        initAttributes(attributeLocations)
        indices.bufferView.bind()
        glDrawElements(mode, indices.count, indices.componentType, indices.byteOffset)
    }

    override fun unbind() {
        unbindBufferTargets()
        glBindBuffer(indices.bufferView.target, 0)
    }
}

class GL3Primitive(val vertexArray: Int,
                   mode: Int,
                   attributes: Map<String, GLAccessor>) : GLPrimitive(mode, attributes) {

    private val count = attributes.values.first().count

    override fun init(attributeLocations: Map<String, Int>) {
        glBindVertexArray(vertexArray)
        initAttributes(attributeLocations)
    }

    override fun render() {
        glBindVertexArray(vertexArray)
        glDrawArrays(mode, 0, count)
    }

    override fun unbind() {
        glBindVertexArray(0)
        unbindBufferTargets()
    }
}

class GL3IndexedPrimitive(val vertexArray: Int,
                          val indices: GLAccessor,
                          mode: Int,
                          attributes: Map<String, GLAccessor>) : GLPrimitive(mode, attributes) {

    override fun init(attributeLocations: Map<String, Int>) {
        glBindVertexArray(vertexArray)
        initAttributes(attributeLocations)
        indices.bufferView.bind()
    }

    override fun render() {
        glBindVertexArray(vertexArray)
        glDrawElements(mode, indices.count, indices.componentType, indices.byteOffset)
    }

    override fun unbind() {
        glBindVertexArray(0)
        unbindBufferTargets()
        glBindBuffer(indices.bufferView.target, 0)
    }
}

class GLMesh(val primitives: List<GLPrimitive>) {

    private lateinit var program: Program
    private lateinit var attributeLocations: Map<String, Int>

    private val normalMatrix = Matrix4f()
    private val modelViewProjectionMatrix = Matrix4f()
    private val modelViewMatrix = Matrix4f()

    fun init(programBuilder: ProgramBuilder) {
        val hasNormals = primitives.any { it.attributes.containsKey("NORMAL") }
        program = if (hasNormals) programBuilder["normal"] else programBuilder["flat"]
        program.use {
            attributeLocations = getSemanticAttributesLocation(this)
            primitives.forEach { primitive ->
                primitive.init(attributeLocations)
                validate()
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

class GL2Disposable(val bufferId: IntArray, val programs: ProgramBuilder) : Disposable {
    override fun dispose() {
        glDeleteBuffers(bufferId)
        programs.dispose()
    }
}

class GL3Disposable(val vertexArrayId: IntArray, bufferId: IntArray, programs: ProgramBuilder) : Disposable {

    private val gl2Disposer = GL2Disposable(bufferId, programs)

    override fun dispose() {
        gl2Disposer.dispose()
        glDeleteVertexArrays(vertexArrayId)
    }
}

class GLRenderer(val scenes: List<GLScene>, val cameraNodes: List<GLNode>, val disposable: Disposable) : Disposable {

    val scenesCount: Int = scenes.size
    val camerasCount: Int = cameraNodes.size

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

    override fun dispose() {
        disposable.dispose()
    }
}

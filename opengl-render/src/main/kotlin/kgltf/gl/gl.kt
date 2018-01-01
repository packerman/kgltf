package kgltf.gl

import kgltf.gl.UniformSemantic.*
import kgltf.gl.math.Camera
import kgltf.gl.math.IdentityCamera
import kgltf.gl.math.Transform
import kgltf.util.Disposable
import kgltf.util.ensureMemoryFree
import org.joml.*
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL20.glVertexAttribPointer
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glDeleteVertexArrays
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

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

data class GLTextureParameters(val magFilter: Int?, val minFilter: Int?,
                               val wrapS: Int, val wrapT: Int) {
    fun apply() {
        magFilter?.let { glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, it) }
        minFilter?.let { glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, it) }
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrapS)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrapT)
    }
}

class GLTexture(val texture: Int, val parameters: GLTextureParameters) {

    fun bind() {
        glBindTexture(GL_TEXTURE_2D, texture)
    }

    fun init() {
        parameters.apply()
    }

    fun copyTextureData(data: ByteArray) {
        MemoryUtil.memAlloc(data.size).ensureMemoryFree { buffer ->
            buffer.put(data)
            buffer.position(0)
            MemoryStack.stackPush().use { stack ->
                val width = stack.mallocInt(1)
                val height = stack.mallocInt(1)
                val channels = stack.mallocInt(1)
                val image = stbi_load_from_memory(buffer, width, height, channels, 0) ?: error("Failed to load image: ${stbi_failure_reason()}")
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width[0], height[0], 0, GL_RGB, GL_UNSIGNED_BYTE, image)
                stbi_image_free(image)
            }
        }
    }

    fun unbind() {
        glBindTexture(GL_TEXTURE_2D, 0)
    }
}

fun GLTexture.initWithData(data: ByteArray) {
    init()
    copyTextureData(data)
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
                           val attributes: Map<Semantic, GLAccessor>,
                           val material: GLMaterial) {

    protected val targets: Set<Int> = attributes.values.mapTo(HashSet()) { it.bufferView.target }
    protected val program = material.program

    private val normalMatrix = Matrix4f()
    private val normalMatrix3x3 = Matrix3f()
    private val modelViewProjectionMatrix = Matrix4f()
    private val modelViewMatrix = Matrix4f()

    abstract fun init()
    abstract fun draw()
    abstract fun unbind()

    fun render(cameraTransform: CameraTransform, modelMatrix: Matrix4fc) {
        program.use {
            applyMatrices(cameraTransform, modelMatrix)
            material.applyToProgram()
            draw()
        }
    }

    private fun GLProgram.applyMatrices(cameraTransform: CameraTransform, modelMatrix: Matrix4fc) {
        uniformSemantics[Projection]?.let { location ->
            UniformSetter.set(location, cameraTransform.projectionMatrix)
        }
        modelViewProjectionMatrix.set(cameraTransform.projectionViewMatrix)
                .mul(modelMatrix)
        uniformSemantics[ModelViewProjection]?.let { location ->
            UniformSetter.set(location, modelViewProjectionMatrix)
        }
        modelViewMatrix.set(cameraTransform.viewMatrix)
                .mul(modelMatrix)
        uniformSemantics[ModelView]?.let { location ->
            UniformSetter.set(location, modelViewMatrix)
        }
        uniformSemantics[ModelViewInverseTranspose]?.let { location ->
            normalMatrix.set(modelViewMatrix).invert().transpose()
            UniformSetter.set(location, normalMatrix.get3x3(normalMatrix3x3))
        }
    }

    protected fun initAttributes() {
        val attributeLocations = program.attributeSemantics
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
                   attributes: Map<Semantic, GLAccessor>,
                   material: GLMaterial) : GLPrimitive(mode, attributes, material) {

    private val count = attributes.values.first().count

    override fun init() {}

    override fun draw() {
        initAttributes()
        glDrawArrays(mode, 0, count)
    }

    override fun unbind() {
        unbindBufferTargets()
    }
}

class GL2IndexedPrimitive(val indices: GLAccessor,
                          mode: Int,
                          attributes: Map<Semantic, GLAccessor>,
                          material: GLMaterial) : GLPrimitive(mode, attributes, material) {


    override fun init() {}

    override fun draw() {
        initAttributes()
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
                   attributes: Map<Semantic, GLAccessor>,
                   material: GLMaterial) : GLPrimitive(mode, attributes, material) {

    private val count = attributes.values.first().count

    override fun init() {
        glBindVertexArray(vertexArray)
        initAttributes()
    }

    override fun draw() {
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
                          attributes: Map<Semantic, GLAccessor>,
                          material: GLMaterial) : GLPrimitive(mode, attributes, material) {

    override fun init() {
        glBindVertexArray(vertexArray)
        initAttributes()
        indices.bufferView.bind()
    }

    override fun draw() {
        glBindVertexArray(vertexArray)
        glDrawElements(mode, indices.count, indices.componentType, indices.byteOffset)
    }

    override fun unbind() {
        glBindVertexArray(0)
        unbindBufferTargets()
        glBindBuffer(indices.bufferView.target, 0)
    }
}

abstract class GLMaterial {
    abstract val program: GLProgram
    abstract fun applyToProgram()
}

class FlatMaterial(override val program: GLProgram,
                   val baseColorFactor: Vector4fc = defaultBaseColorFactor) : GLMaterial() {

    override fun applyToProgram() {
        program.uniformParameters["color"]?.let { location ->
            UniformSetter.set(location, baseColorFactor)
        }
    }

    companion object {
        val defaultBaseColorFactor: Vector4fc = Vector4f(1f, 1f, 1f, 1f)
    }
}

class GLMesh(val primitives: List<GLPrimitive>) {

    fun init() {
        primitives.forEach { primitive ->
            primitive.init()
            primitive.unbind()
        }
    }

    fun render(modelMatrix: Matrix4fc, cameraTransform: CameraTransform) {
        primitives.forEach { it.render(cameraTransform, modelMatrix) }
    }
}

class GLNode(val transform: Transform,
             val children: List<GLNode>,
             val mesh: GLMesh? = null,
             val camera: Camera? = null) {

    fun render(cameraTransform: CameraTransform, matrixStack: MatrixStackf) {
        matrixStack.pushMatrix()
        matrixStack.mul(transform.matrix)
        children.forEach { child ->
            child.render(cameraTransform, matrixStack)
        }
        mesh?.render(matrixStack, cameraTransform)
        matrixStack.popMatrix()
    }

    companion object {
        val emptyNode = GLNode(
                transform = Transform(),
                children = emptyList())
        internal val defaultCameraNode = GLNode(
                camera = IdentityCamera(),
                transform = Transform(),
                children = emptyList())
    }
}

class GLScene(val nodes: List<GLNode>) {

    private val matrixStack = MatrixStackf(16)

    fun render(cameraTransform: CameraTransform) {
        nodes.forEach { it.render(cameraTransform, matrixStack) }
    }
}

class CameraTransform {

    private val _projectionMatrix = Matrix4f()
    val projectionMatrix: Matrix4fc = _projectionMatrix

    private val _viewMatrix = Matrix4f()
    val viewMatrix: Matrix4fc = _viewMatrix

    private val _projectionViewMatrix = Matrix4f()
    val projectionViewMatrix: Matrix4fc = _projectionViewMatrix

    fun set(camera: Camera, transform: Transform) {
        _projectionMatrix.set(camera.projectionMatrix)
        _projectionMatrix
                .mul(transform.matrix.invert(_viewMatrix), _projectionViewMatrix)
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

    fun init() {
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_CULL_FACE)
    }

    fun render(sceneNum: Int, cameraNum: Int? = null) {
        require((cameraNum == null && cameraNodes.isEmpty()) ||
                (cameraNum != null && (cameraNum in 0 until cameraNodes.size)))
        require(sceneNum in 0 until scenes.size)

        val cameraNode = if (cameraNum != null) cameraNodes[cameraNum] else GLNode.defaultCameraNode

        render(scenes[sceneNum], cameraNode)
    }

    fun resize(width: Int, height: Int) {
        val aspectRatio = width.toFloat() / height
        cameraNodes.forEach {
            requireNotNull(it.camera).update(aspectRatio)
        }
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

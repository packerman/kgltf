package kgltf.gl

import kgltf.gl.UniformSemantic.*
import kgltf.gl.math.Camera
import kgltf.gl.math.IdentityCamera
import kgltf.gl.math.Transform
import kgltf.util.Disposable
import kgltf.util.ensureMemoryFree
import org.joml.*
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL14.GL_MIRRORED_REPEAT
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.stb.STBImage.*
import org.lwjgl.stb.STBImageResize.stbir_resize_uint8
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.logging.Logger

class GLBufferView(val target: Int, val buffer: Int, val byteLength: Int, val byteStride: Int = 0) {

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

data class GLTextureParameters(val magFilter: Int, val minFilter: Int,
                               val wrapS: Int, val wrapT: Int) {
    fun apply() {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrapS)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrapT)
    }

    val usesMipmapping = minFilter in mipmappingFilters
    val needsPowerOfTwo = (wrapS in wrappingModeNeedingPowerOfTwo) || (wrapT in wrappingModeNeedingPowerOfTwo) ||
            usesMipmapping

    companion object {
        val mipmappingFilters = setOf(GL_NEAREST_MIPMAP_NEAREST, GL_NEAREST_MIPMAP_LINEAR, GL_LINEAR_MIPMAP_NEAREST, GL_LINEAR_MIPMAP_LINEAR)
        val wrappingModeNeedingPowerOfTwo = setOf(GL_REPEAT, GL_MIRRORED_REPEAT)
    }
}

class GLTexture(val texture: Int, val parameters: GLTextureParameters) {

    fun bind() {
        glActiveTexture(GL_TEXTURE0)
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
                val potWidth = powerOfTwoNotLess(width[0])
                val potHeight = powerOfTwoNotLess(height[0])
                if (width[0] != potHeight || height[0] != potWidth) {
                    resizeImage(image, width[0], height[0], potWidth, potHeight, channels[0])?.ensureMemoryFree { resizedImage ->
                        logger.warning { "Resize image from ${width[0]}x${height[0]} to ${potWidth}x$potHeight" }
                        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, potWidth, potHeight, 0, GL_RGB, GL_UNSIGNED_BYTE, resizedImage)
                    } ?: error("Cannot resize image")
                } else {
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width[0], height[0], 0, GL_RGB, GL_UNSIGNED_BYTE, image)
                }
                stbi_image_free(image)
            }
        }
        if (parameters.usesMipmapping) {
            glGenerateMipmap(GL_TEXTURE_2D)
        }
    }

    fun unbind() {
        glBindTexture(GL_TEXTURE_2D, 0)
    }

    companion object {
        private fun resizeImage(originalImage: ByteBuffer,
                                originalWidth: Int, originalHeight: Int,
                                resizedWidth: Int, resizedHeight: Int,
                                channels: Int): ByteBuffer? {
            val resizedImage = MemoryUtil.memAlloc(resizedHeight * resizedHeight * channels)
            val success = stbir_resize_uint8(originalImage, originalWidth, originalHeight, 0,
                    resizedImage, resizedWidth, resizedHeight, 0,
                    channels)
            return if (success) {
                resizedImage
            } else {
                MemoryUtil.memFree(resizedImage)
                null
            }
        }
    }

    fun powerOfTwoNotLess(n: Int): Int {
        var k = 1
        while (k < n) {
            k *= 2
        }
        return k
    }
}

fun GLTexture.initWithData(data: ByteArray) {
    init()
    copyTextureData(data)
}

data class TextureUnit(val symbol: Int, val number: Int) {

    fun makeActive() {
        glActiveTexture(symbol)
    }

    fun setUniform(location: Int) {
        glUniform1i(location, number)
    }
}

val textureUnits = listOf(
        TextureUnit(GL_TEXTURE0, 0),
        TextureUnit(GL_TEXTURE1, 1),
        TextureUnit(GL_TEXTURE2, 2),
        TextureUnit(GL_TEXTURE3, 3),
        TextureUnit(GL_TEXTURE4, 4),
        TextureUnit(GL_TEXTURE5, 5),
        TextureUnit(GL_TEXTURE6, 6),
        TextureUnit(GL_TEXTURE7, 7),
        TextureUnit(GL_TEXTURE8, 8),
        TextureUnit(GL_TEXTURE9, 9),
        TextureUnit(GL_TEXTURE10, 10),
        TextureUnit(GL_TEXTURE11, 11),
        TextureUnit(GL_TEXTURE12, 12),
        TextureUnit(GL_TEXTURE13, 13),
        TextureUnit(GL_TEXTURE14, 14),
        TextureUnit(GL_TEXTURE15, 15),
        TextureUnit(GL_TEXTURE16, 16),
        TextureUnit(GL_TEXTURE17, 17),
        TextureUnit(GL_TEXTURE18, 18),
        TextureUnit(GL_TEXTURE19, 19),
        TextureUnit(GL_TEXTURE20, 20),
        TextureUnit(GL_TEXTURE21, 21),
        TextureUnit(GL_TEXTURE22, 22),
        TextureUnit(GL_TEXTURE23, 23),
        TextureUnit(GL_TEXTURE24, 24),
        TextureUnit(GL_TEXTURE25, 25),
        TextureUnit(GL_TEXTURE26, 26),
        TextureUnit(GL_TEXTURE27, 27),
        TextureUnit(GL_TEXTURE28, 28),
        TextureUnit(GL_TEXTURE29, 29),
        TextureUnit(GL_TEXTURE30, 30),
        TextureUnit(GL_TEXTURE31, 31)
)

class GLAccessor(val bufferView: GLBufferView,
                 val byteOffset: Long,
                 val componentType: Int,
                 val count: Int,
                 val numberOfComponents: Int) {

    fun setVertexAttribPointer(index: Int) {
        glVertexAttribPointer(index, numberOfComponents, componentType, false, bufferView.byteStride, byteOffset)
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

    fun render(context: RenderingContext, cameraTransform: CameraTransform, modelMatrix: Matrix4fc) {
        program.use {
            applyMatrices(cameraTransform, modelMatrix)
            material.applyToProgram(context)
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

data class RenderingContext(val nodes: List<GLNode>)

interface GLMaterial {
    val program: GLProgram
    fun applyToProgram(context: RenderingContext)
}

class FlatMaterial(override val program: GLProgram,
                   val baseColorFactor: Vector4fc) : GLMaterial {

    override fun applyToProgram(context: RenderingContext) {
        program.uniformParameters["color"]?.let { location ->
            UniformSetter.set(location, baseColorFactor)
        }
    }
}

class TextureMaterial(override val program: GLProgram,
                      val baseColorFactor: Vector4fc) : GLMaterial {

    override fun applyToProgram(context: RenderingContext) {
        program.uniformParameters["color"]?.let { location ->
            UniformSetter.set(location, baseColorFactor)
        }
        program.uniformParameters["sampler"]?.let { location ->
            glUniform1i(location, 0)
        }
    }
}

class GLMesh(val primitives: List<GLPrimitive>) {

    fun init() {
        primitives.forEach { primitive ->
            primitive.init()
            primitive.unbind()
        }
    }

    fun render(context: RenderingContext, modelMatrix: Matrix4fc, cameraTransform: CameraTransform) {
        primitives.forEach { it.render(context, cameraTransform, modelMatrix) }
    }
}

class GLNode(private val localTransform: Transform,
             val children: List<GLNode>,
             val mesh: GLMesh? = null,
             val camera: Camera? = null) {

    private val _transformMatrix = Matrix4f()
    val transformMatrix: Matrix4fc
        get() = _transformMatrix

    fun render(context: RenderingContext, cameraTransform: CameraTransform) {
        children.forEach { child ->
            child.render(context, cameraTransform)
        }
        mesh?.render(context, _transformMatrix, cameraTransform)
    }

    fun updateTransforms(matrixStack: MatrixStackf) {
        matrixStack.pushMatrix()
        matrixStack.mul(localTransform.matrix)
        _transformMatrix.set(matrixStack)
        children.forEach { child ->
            child.updateTransforms(matrixStack)
        }
        matrixStack.popMatrix()
    }

    companion object {
        val emptyNode = GLNode(
                localTransform = Transform(),
                children = emptyList())
        internal val defaultCameraNode = GLNode(
                camera = IdentityCamera(),
                localTransform = Transform(),
                children = emptyList())
    }
}

class GLScene(val nodes: List<GLNode>) {

    private val matrixStack = MatrixStackf(16)

    fun render(context: RenderingContext, cameraTransform: CameraTransform) {
        nodes.forEach { it.render(context, cameraTransform) }
    }

    fun updateTransforms() {
        nodes.forEach { it.updateTransforms(matrixStack) }
    }
}

class CameraTransform {

    private val _projectionMatrix = Matrix4f()
    val projectionMatrix: Matrix4fc = _projectionMatrix

    private val _viewMatrix = Matrix4f()
    val viewMatrix: Matrix4fc = _viewMatrix

    private val _projectionViewMatrix = Matrix4f()
    val projectionViewMatrix: Matrix4fc = _projectionViewMatrix

    fun set(camera: Camera, transformMatrix: Matrix4fc) {
        _projectionMatrix.set(camera.projectionMatrix)
        _projectionMatrix
                .mul(transformMatrix.invert(_viewMatrix), _projectionViewMatrix)
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

class GLRenderer(val context: RenderingContext,
                 val scenes: List<GLScene>,
                 val cameraNodes: List<GLNode>,
                 val disposable: Disposable) : Disposable {

    val scenesCount: Int = scenes.size
    val camerasCount: Int = cameraNodes.size

    private val cameraTransforms = CameraTransform()

    fun init() {
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_CULL_FACE)
        scenes.forEach { it.updateTransforms() }
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
        cameraTransforms.set(camera, cameraNode.transformMatrix)
        scene.render(context, cameraTransforms)
    }

    override fun dispose() {
        disposable.dispose()
    }
}

private val logger = Logger.getLogger("render.gl")

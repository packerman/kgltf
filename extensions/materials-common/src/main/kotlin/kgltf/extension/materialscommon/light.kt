package kgltf.extension.materialscommon

import kgltf.gl.GLProgram
import kgltf.gl.RenderingContext
import org.joml.Matrix3f
import org.joml.Vector3f
import org.joml.Vector3fc
import org.joml.Vector4fc
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.GL_FALSE
import org.lwjgl.opengl.GL11.GL_TRUE
import org.lwjgl.opengl.GL20.*
import java.nio.FloatBuffer
import java.nio.IntBuffer

abstract class GLLight(val color: Vector4fc) {
    abstract val isDirectional: Boolean

    fun getColor(offset: Int, buffer: FloatBuffer) {
        color.get(offset, buffer)
    }

    fun getIsDirectional(offset: Int, buffer: IntBuffer) {
        buffer.put(offset, if (isDirectional) GL_TRUE else GL_FALSE)
    }

    abstract fun getVector(context: RenderingContext, offset: Int, buffer: FloatBuffer)
}

class GLDirectionalLight(val nodeIndex: Int?,
                         color: Vector4fc) : GLLight(color) {

    private val nodeTransform = Matrix3f()
    private val direction = Vector3f()

    init {
        direction.set(initialDirection)
    }

    override val isDirectional: Boolean = true

    override fun getVector(context: RenderingContext, offset: Int, buffer: FloatBuffer) {
        nodeIndex?.let { i ->
            context.nodes[i].transformMatrix.get3x3(nodeTransform)
            initialDirection.mul(nodeTransform, direction)
        }
        direction.get(offset, buffer)
    }

    companion object {
        val initialDirection: Vector3fc = Vector3f(0f, 0f, -1f)
    }
}

class GLPointLight(val nodeIndex: Int?,
                   color: Vector4fc) : GLLight(color) {

    private val position = Vector3f()

    override val isDirectional: Boolean = false

    override fun getVector(context: RenderingContext, offset: Int, buffer: FloatBuffer) {
        nodeIndex?.let { i ->
            context.nodes[i].transformMatrix.getTranslation(position)
        }
        position.get(offset, buffer)
    }
}

class LightUniformSetter(val lights: List<GLLight>) {

    private val lightCount = lights.size

    private val lightColorBuffer = BufferUtils.createFloatBuffer(lightCount * 4)
    private val lightVectorBuffer = BufferUtils.createFloatBuffer(lightCount * 3)
    private val lightIsDirectionalBuffer = BufferUtils.createIntBuffer(lightCount)

    fun update(context: RenderingContext) {
        lights.forEachIndexed { i, light ->
            light.getColor(4 * i, lightColorBuffer)
            light.getVector(context, 3 * i, lightVectorBuffer)
            light.getIsDirectional(i, lightIsDirectionalBuffer)
        }
    }

    fun applyToProgram(program: GLProgram) = program.uniformParameters.let { parameters ->
        parameters["lightCount"]?.let { location ->
            glUniform1i(location, lightCount)
        }
        parameters["lightPosition"]?.let { location ->
            glUniform3fv(location, lightVectorBuffer)
        }
        parameters["lightColor"]?.let { location ->
            glUniform4fv(location, lightColorBuffer)
        }
        parameters["lightIsDirectional"]?.let { location ->
            glUniform1iv(location, lightIsDirectionalBuffer)
        }
    }
}

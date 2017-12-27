package kgltf.util

import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL20.glGetAttribLocation
import org.lwjgl.opengl.GL20.glGetUniformLocation
import org.lwjgl.opengl.GL30.GL_INVALID_FRAMEBUFFER_OPERATION

fun getAttributeLocation(program: Int, name: String): Int =
        glGetAttribLocation(program, name).also { location ->
            warnWhen(location < 0) { "Attribute '$name' has '$location' in program $program" }
        }

fun getUniformLocation(program: Int, name: String): Int =
        glGetUniformLocation(program, name).also { location ->
            warnWhen(location < 0) { "Uniform '$name' has '$location' in program $program" }
        }

fun checkGLError() {
    val error = glGetError()
    check(error == GL_NO_ERROR) {
        when (error) {
            GL_INVALID_ENUM -> "GL_INVALID_ENUM"
            GL_INVALID_VALUE -> "GL_INVALID_VALUE"
            GL_INVALID_OPERATION -> "GL_INVALID_OPERATION"
            GL_INVALID_FRAMEBUFFER_OPERATION -> "GL_INVALID_FRAMEBUFFER_OPERATION"
            GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY"
            GL_STACK_UNDERFLOW -> "GL_STACK_UNDERFLOW"
            GL_STACK_OVERFLOW -> "GL_STACK_OVERFLOW"
            else -> "Unknown GL error"
        }
    }
}


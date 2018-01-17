package kgltf.extension.materialscommon

import kgltf.gl.*
import kgltf.gl.AttributeSemantic.*
import org.lwjgl.opengl.GL20.*

private val materialsCommonPrograms = mapOf(
        "blinn" to ProgramDescription(
                "blinn",
                attributeSemantics = mapOf(
                        Position to "position",
                        TexCoord0 to "uv",
                        Normal to "normal"
                ),
                uniformSemantics = mapOf(
                        UniformSemantic.ModelViewProjection to "modelViewProjectionMatrix",
                        UniformSemantic.ModelView to "modelViewMatrix",
                        UniformSemantic.ModelViewInverseTranspose to "normalMatrix"
                ),
                uniformParameters = setOf(
                        "emission",
                        "ambient",
                        "diffuse",
                        "specular",
                        "shininess",
                        "lightCount",
                        "lightPosition",
                        "lightColor",
                        "lightIsDirectional"
                )),
        "blinn_texture" to ProgramDescription(
                "blinn_texture",
                attributeSemantics = mapOf(
                        Position to "position",
                        TexCoord0 to "uv",
                        Normal to "normal"
                ),
                uniformSemantics = mapOf(
                        UniformSemantic.ModelViewProjection to "modelViewProjectionMatrix",
                        UniformSemantic.ModelView to "modelViewMatrix",
                        UniformSemantic.ModelViewInverseTranspose to "normalMatrix"
                ),
                uniformParameters = setOf(
                        "emission",
                        "ambient",
                        "diffuse",
                        "specular",
                        "shininess",
                        "lightCount",
                        "lightPosition",
                        "lightColor",
                        "lightIsDirectional"
                )),
        "phong" to ProgramDescription(
                "phong",
                attributeSemantics = mapOf(
                        Position to "position",
                        TexCoord0 to "uv",
                        Normal to "normal"
                ),
                uniformSemantics = mapOf(
                        UniformSemantic.ModelViewProjection to "modelViewProjectionMatrix",
                        UniformSemantic.ModelView to "modelViewMatrix",
                        UniformSemantic.ModelViewInverseTranspose to "normalMatrix"
                ),
                uniformParameters = setOf(
                        "emission",
                        "ambient",
                        "diffuse",
                        "specular",
                        "shininess",
                        "lightCount",
                        "lightPosition",
                        "lightColor",
                        "lightIsDirectional"
                )),
        "phong_texture" to ProgramDescription(
                "phong_texture",
                attributeSemantics = mapOf(
                        Position to "position",
                        TexCoord0 to "uv",
                        Normal to "normal"
                ),
                uniformSemantics = mapOf(
                        UniformSemantic.ModelViewProjection to "modelViewProjectionMatrix",
                        UniformSemantic.ModelView to "modelViewMatrix",
                        UniformSemantic.ModelViewInverseTranspose to "normalMatrix"
                ),
                uniformParameters = setOf(
                        "emission",
                        "ambient",
                        "diffuse",
                        "specular",
                        "shininess",
                        "lightCount",
                        "lightPosition",
                        "lightColor",
                        "lightIsDirectional"
                )),
        "lambert" to ProgramDescription(
                "lambert",
                attributeSemantics = mapOf(
                        Position to "position",
                        Normal to "normal"
                ),
                uniformSemantics = mapOf(
                        UniformSemantic.ModelViewProjection to "modelViewProjectionMatrix",
                        UniformSemantic.ModelView to "modelViewMatrix",
                        UniformSemantic.ModelViewInverseTranspose to "normalMatrix"
                ),
                uniformParameters = setOf(
                        "emission",
                        "ambient",
                        "diffuse",
                        "lightCount",
                        "lightPosition",
                        "lightColor",
                        "lightIsDirectional"
                )),
        "lambert_texture" to ProgramDescription(
                "lambert_texture",
                attributeSemantics = mapOf(
                        Position to "position",
                        TexCoord0 to "uv",
                        Normal to "normal"
                ),
                uniformSemantics = mapOf(
                        UniformSemantic.ModelViewProjection to "modelViewProjectionMatrix",
                        UniformSemantic.ModelView to "modelViewMatrix",
                        UniformSemantic.ModelViewInverseTranspose to "normalMatrix"
                ),
                uniformParameters = setOf(
                        "emission",
                        "ambient",
                        "diffuse",
                        "lightCount",
                        "lightPosition",
                        "lightColor",
                        "lightIsDirectional"
                ))
        )

fun createMaterialsCommonProgramBuilder(): ProgramBuilder {
    return ProgramBuilder("/shader/materials_common", materialsCommonPrograms)
}

interface UniformValueSetter {
    fun set(location: Int, renderingContext: RenderingContext)
}

class FloatVec4ValueSetter(private val v0: Float,
                           private val v1: Float,
                           private val v2: Float,
                           private val v3: Float) : UniformValueSetter {
    override fun set(location: Int, renderingContext: RenderingContext) {
        glUniform4f(location, v0, v1, v2, v3)
    }
}

class SamplerValueSetter(private val v0: Int) : UniformValueSetter {
    override fun set(location: Int, renderingContext: RenderingContext) {
        textureUnits[v0].makeActive()
        renderingContext.textures[v0].bind()
        textureUnits[v0].setUniform(location)
    }
}

class FloatValueSetter(private val v0: Float) : UniformValueSetter {
    override fun set(location: Int, renderingContext: RenderingContext) {
        glUniform1f(location, v0)
    }
}

package kgltf.app

import kgltf.app.glfw.Config
import kgltf.gltf.*
import kgltf.util.FloatMath
import org.joml.Matrix4f
import org.joml.Matrix4fc

val sampleGltfTransformers: Map<KhronosSample, (Gltf) -> Gltf> =
        mapOf(
                KhronosSample.SimpleMeshes to { gltf ->
                    val additionalCamera = Camera.of(Orthographic(1f, 1f, -1f, 1f))
                    val matrix = Matrix4f()
                            .translate(0.75f, 0f, 0f)
                    gltf.addCamera("addedCamera", additionalCamera, matrix.getList())
                },
                KhronosSample.Box to { gltf ->
                    val additionalCamera = Camera.of(Perspective(1f,
                            FloatMath.toRadians(45f),
                            0.01f,
                            1000f))
                    val matrix = Matrix4f()
                            .translate(1f, 1.25f, 1.75f)
                            .rotate(FloatMath.toRadians(30f), 0f, 1f, 0f)
                            .rotate(FloatMath.toRadians(-32f), 1f, 0f, 0f)
                    gltf.addCamera("addedCamera", additionalCamera, matrix.getList())
                },
                KhronosSample.BoxTextured to { gltf ->
                    val additionalCamera = Camera.of(Perspective(1f,
                            FloatMath.toRadians(45f),
                            0.01f,
                            1000f))
                    val matrix = Matrix4f()
                            .translate(1f, 1.25f, 1.75f)
                            .rotate(FloatMath.toRadians(30f), 0f, 1f, 0f)
                            .rotate(FloatMath.toRadians(-32f), 1f, 0f, 0f)
                    gltf.addCamera("addedCamera", additionalCamera, matrix.getList())
                })

open class SampleApplicationRunner(config: Config, val sample: KhronosSample) : ApplicationRunner(config) {
    final override fun transformGltfModel(gltf: Gltf): Gltf {
        val transformer = sampleGltfTransformers[sample]
        return if (transformer == null) gltf else transformer(gltf)
    }
}

fun Matrix4fc.getList(): List<Float> {
    val m = FloatArray(16)
    this.get(m)
    return m.toList()
}

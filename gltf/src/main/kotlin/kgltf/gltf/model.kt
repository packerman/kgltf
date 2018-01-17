package kgltf.gltf

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class Gltf(val scenes: List<Scene>,
                val nodes: List<Node>,
                val meshes: List<Mesh>,
                val cameras: List<Camera>?,
                val buffers: List<Buffer>,
                val bufferViews: List<BufferView>,
                val images: List<Image>?,
                val samplers: List<Sampler>?,
                val textures: List<Texture>?,
                val accessors: List<Accessor>,
                val materials: List<Material>?,
                val asset: Asset,
                val extensionsRequired: List<String>?,
                val extensionsUsed: List<String>?) {

    companion object {
        private val gson = Gson()
        private val type = object : TypeToken<Gltf>() {}.type

        fun load(json: String): Gltf = gson.fromJson(json, type)
    }
}

fun Gltf.addCamera(name: String, camera: Camera, transform: List<Float>): Gltf {
    val cameraList = (cameras ?: emptyList()).toMutableList()
    val nodeList = nodes.toMutableList()
    val newNode = Node(name = name,
            camera = cameraList.size,
            matrix = transform)
    val newScenes = scenes.map { scene ->
        val sceneNodes = scene.nodes.toMutableList()
        sceneNodes.add(nodeList.size)
        scene.copy(nodes = sceneNodes)
    }
    cameraList.add(camera)
    nodeList.add(newNode)
    return copy(scenes = newScenes,
            nodes = nodeList,
            cameras = cameraList)
}

fun Gltf.setNodeTransform(node: Int, transform: List<Float>): Gltf =
        copy(nodes = nodes.mapIndexed { i, n ->
            if (i == node)
                n.copy(matrix = transform)
            else n
        })

interface Named {
    val name: String?
}

fun Named.genericName(prefix: String, index: Int) = name ?: "${prefix}_$index"

data class Scene(override val name: String?,
                 val nodes: List<Int>) : Named

fun Scene.genericName(i: Int) = genericName("scene", i)

data class Node(override val name: String? = null,
                val mesh: Int? = null,
                val camera: Int? = null,
                val matrix: List<Float>? = null,
                val rotation: List<Float>? = null,
                val translation: List<Float>? = null,
                val scale: List<Float>? = null,
                val children: List<Int>? = null) : Named

fun Node.genericName(i: Int) = genericName("node", i)

data class Camera(override val name: String?,
                  val type: String,
                  val perspective: Perspective?,
                  val orthographic: Orthographic?) : Named {

    companion object {
        fun of(perspective: Perspective): Camera =
                Camera(null, "perspective", perspective, null)

        fun of(orthographic: Orthographic): Camera =
                Camera(null, "orthographic", null, orthographic)
    }
}

data class Perspective(val aspectRatio: Float, val yfov: Float, val znear: Float, val zfar: Float?)

data class Orthographic(val xmag: Float, val ymag: Float, val znear: Float, val zfar: Float)

data class Mesh(override val name: String?,
                val primitives: List<Primitive>) : Named

fun Mesh.genericName(i: Int) = genericName("mesh", i)

data class Primitive(val attributes: Map<String, Int>,
                     val indices: Int?,
                     val mode: Int?,
                     val material: Int?)

data class Buffer(override val name: String?,
                  val uri: String,
                  val byteLength: Int) : Named

fun Buffer.genericName(i: Int) = genericName("buffer", i)

data class BufferView(override val name: String?,
                      val buffer: Int,
                      val byteOffset: Int,
                      val byteLength: Int,
                      val byteStride: Int?,
                      val target: Int) : Named

fun BufferView.genericName(i: Int) = genericName("bufferView", i)

data class Image(override val name: String?,
                 val uri: String) : Named

fun Image.genericName(i: Int) = genericName("image", i)

data class Sampler(override val name: String?,
                   val magFilter: Int?,
                   val minFilter: Int?,
                   val wrapS: Int?,
                   val wrapT: Int?) : Named

fun Sampler.genericName(i: Int) = genericName("sampler", i)

data class Texture(override val name: String?,
                   val sampler: Int?,
                   val source: Int?) : Named

fun Texture.genericName(i: Int) = genericName("texture", i)

data class Accessor(override val name: String?,
                    val bufferView: Int,
                    val byteOffset: Int,
                    val componentType: Int,
                    val count: Int,
                    val type: String,
                    val max: List<Float>,
                    val min: List<Float>) : Named

data class Material(override val name: String?,
                    val pbrMetallicRoughness: PbrMetallicRoughness?) : Named

fun Material.genericName(i: Int) = genericName("material", i)

data class PbrMetallicRoughness(val baseColorFactor: List<Float>?,
                                val baseColorTexture: ColorTexture?,
                                val metallicFactor: Float?)

data class ColorTexture(val index: Int)

data class Asset(val version: String)

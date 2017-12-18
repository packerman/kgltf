import kgltf.app.KhronosSample
import kgltf.app.Variant
import kgltf.app.glfw.Config
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.lwjgl.glfw.GLFW.GLFW_OPENGL_COMPAT_PROFILE

@RunWith(Parameterized::class)
class GL2ViewerTest(testedSample: KhronosSample, testedVariant: Variant) : ViewerTestCommon(testedSample, testedVariant) {

    override val config = Config(
            width = 1024,
            height = 640,
            title = "test",
            visible = false,
            profile = GLFW_OPENGL_COMPAT_PROFILE)

    companion object {

        @Parameters(name = "{index}: testModelAndVariant({0}, {1})")
        @JvmStatic
        fun data(): Array<Array<Any>> =
                arrayOf(
                        arrayOf<Any>(KhronosSample.TriangleWithoutIndices, Variant.Gltf),
                        arrayOf<Any>(KhronosSample.Triangle, Variant.Gltf),
                        arrayOf<Any>(KhronosSample.SimpleMeshes, Variant.Gltf),
                        arrayOf<Any>(KhronosSample.Cameras, Variant.Gltf)
                )
    }
}

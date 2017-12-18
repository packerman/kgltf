import kgltf.app.KhronosSample
import kgltf.app.Variant
import kgltf.app.glfw.Config
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class GL3ViewerTest(testedSample: KhronosSample, testedVariant: Variant) : ViewerTestCommon(testedSample, testedVariant) {

    override val config = Config(
            width = 1024,
            height = 640,
            title = "test",
            visible = false)

    companion object {

        @Parameters(name = "{index}: testModelAndVariant({0}, {1})")
        @JvmStatic
        fun data(): Array<Array<Any>> =
                KhronosSample.values()
                        .flatMap { sample -> sample.variants.map { variant -> arrayOf<Any>(sample, variant) } }
                        .toTypedArray()
    }
}

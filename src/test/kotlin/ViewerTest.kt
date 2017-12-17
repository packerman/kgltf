import kgltf.app.ApplicationRunner
import kgltf.app.KhronosSample
import kgltf.app.Variant
import kgltf.app.getSampleModelUri
import kgltf.app.glfw.Application
import kgltf.app.glfw.Config
import kgltf.app.glfw.Size2D
import kgltf.util.ensureImageFree
import kgltf.util.loadImageFromFile
import kgltf.util.toArray
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThan
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.lwjgl.stb.STBImage.stbi_set_flip_vertically_on_load
import java.io.File
import java.nio.ByteBuffer

@RunWith(Parameterized::class)
class ViewerTest(val testedSample: KhronosSample, val testedVariant: Variant) {

    private val config = Config(
            width = 1024,
            height = 640,
            title = "test",
            visible = false)

    private val requiredSimilarity = 0.99

    private lateinit var runner: ApplicationRunner

    companion object {

        private val directory: File
        private const val directoryPropertyName = "kgltf.test.asset.directory"

        init {
            val directoryPropertyValue = requireNotNull(System.getProperty(directoryPropertyName)) { "You need to specify a directory with test assets in '$directoryPropertyName' property" }
            directory = File(directoryPropertyValue).absoluteFile.normalize()
            check(directory.isDirectory)
        }

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            stbi_set_flip_vertically_on_load(true)
        }

        @Parameters(name = "{index}: testModelAndVariant({0}, {1})")
        @JvmStatic
        fun data(): Array<Array<Any>> =
                KhronosSample.values()
                        .flatMap { sample -> sample.variants.map { variant -> arrayOf<Any>(sample, variant) } }
                        .toTypedArray()
    }

    @Before
    fun setUp() {
        runner = ApplicationRunner(config)
    }

    @Test
    fun testModelAndVariant() {
        val uri = getSampleModelUri(testedSample, testedVariant)
        runner.runByDelegate(uri) { app ->
            object : Application by app {
                override fun render() {
                    app.render()
                    val actualScreenshot = app.screenshot()
                    expectedScreenshotForModel(app.framebufferSize, testedSample).ensureImageFree { image ->
                        val expectedScreenshot = image.toArray()
                        assertThat(actualScreenshot.size, equalTo(expectedScreenshot.size))
                        assertThat(similarity(actualScreenshot, expectedScreenshot), greaterThan(requiredSimilarity))
                    }
                    stop()
                }
            }
        }
    }

    private fun expectedScreenshotForModel(size: Size2D, sample: KhronosSample): ByteBuffer {
        val screenshotDirectory = directory
                .resolve("screenshots")
                .resolve("${size.width}x${size.height}")
        val screenshotFile = screenshotDirectory.resolve("${sample.name}.png")
        return loadImageFromFile(screenshotFile)
    }

    private fun similarity(first: ByteArray, second: ByteArray): Double {
        val distance = byteArrayDistance(first, second)
        val expectedRandomDistance = first.size.toLong() * 0x100 / 3
        return (expectedRandomDistance - distance).toDouble() / expectedRandomDistance
    }

    private fun byteArrayDistance(first: ByteArray, second: ByteArray) =
            first.zip(second)
                    .asSequence()
                    .map { (b1, b2) -> Math.abs(b1 - b2).toLong() }
                    .sum()
}

import kgltf.app.KhronosSample
import kgltf.app.SampleApplicationRunner
import kgltf.app.Variant
import kgltf.app.getSampleModelUri
import kgltf.app.glfw.Application
import kgltf.app.glfw.Config
import kgltf.app.glfw.Size
import kgltf.util.ensureImageFree
import kgltf.util.loadImageFromFile
import kgltf.util.toArray
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.lwjgl.stb.STBImage
import java.io.File
import java.nio.ByteBuffer
import java.util.logging.Logger

abstract class ViewerTestCommon(val testedSample: KhronosSample, val testedVariant: Variant) {
    abstract val config: Config

    private val requiredSimilarity = 0.99

    @Before
    fun setUp() {
    }

    @Test
    fun testModelAndVariant() {
        val uri = getSampleModelUri(testedSample, testedVariant)
        object : SampleApplicationRunner(config, testedSample) {
            override fun delegateApplication(application: Application): Application =
                    object : Application by application {
                        override fun render() {
                            application.render()
                            val actualScreenshot = application.screenshot()
                            expectedScreenshotForModel(application.framebufferSize, testedSample, testedVariant).ensureImageFree { image ->
                                val expectedScreenshot = image.toArray()
                                assertThat(actualScreenshot.size, CoreMatchers.equalTo(expectedScreenshot.size))
                                val similarity = similarity(actualScreenshot, expectedScreenshot)
                                if (similarity < 1) {
                                    logger.info { "Similarity: $similarity" }
                                }
                                assertThat(similarity, Matchers.greaterThan(requiredSimilarity))
                            }
                            stop()
                        }
                    }
        }.runFor(uri)
    }

    companion object {
        private val directory: File
        private const val directoryPropertyName = "kgltf.test.asset.directory"

        init {
            val directoryPropertyValue = requireNotNull(System.getProperty(directoryPropertyName)) { "You need to specify a directory with test assets in '$directoryPropertyName' property" }
            directory = File(directoryPropertyValue).absoluteFile.normalize()
            check(directory.isDirectory) { "'$directory' is not a directory" }
        }

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            STBImage.stbi_set_flip_vertically_on_load(true)
        }

        fun expectedScreenshotForModel(size: Size, sample: KhronosSample, variant: Variant): ByteBuffer {
            val screenshotDirectory = directory
                    .resolve("screenshots")
                    .resolve("${size.width}x${size.height}")
            val variantScreenshotFile = screenshotDirectory.resolve("${sample.name}_${variant.name}.png")
            val screenshotFile =
                    if (variantScreenshotFile.exists()) variantScreenshotFile
                    else screenshotDirectory.resolve("${sample.name}.png")
            return loadImageFromFile(screenshotFile)
        }

        fun similarity(first: ByteArray, second: ByteArray): Double {
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
}

private val logger = Logger.getLogger("testing")

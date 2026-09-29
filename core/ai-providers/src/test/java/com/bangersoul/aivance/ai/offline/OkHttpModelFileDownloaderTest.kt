package com.bangersoul.aivance.ai.offline

import com.bangersoul.aivance.core.common.result.Result
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Verifies [OkHttpModelFileDownloader]'s resumable download behavior: full
 * downloads, HTTP Range resume from a partial file, restart when the server
 * ignores ranges, and interrupted-transfer handling that keeps the partial.
 */
class OkHttpModelFileDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var downloader: OkHttpModelFileDownloader
    private lateinit var tempDir: File

    private val modelBytes = ByteArray(256 * 1024) { index -> (index % 251).toByte() }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        downloader = OkHttpModelFileDownloader()
        tempDir = java.nio.file.Files.createTempDirectory("model-dl-test").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    private fun url(): String = server.url("/model.task").toString()

    private fun destination(): File = File(tempDir, "gemma.task")

    @Test
    fun `full download writes the file and reports progress to 1`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(Buffer().write(modelBytes))
        )

        val progress = mutableListOf<Float>()
        val result = downloader.download(url(), destination()) { progress.add(it) }

        assertTrue(result is Result.Success)
        val file = destination()
        assertTrue(file.exists())
        assertTrue(file.readBytes().contentEquals(modelBytes))
        assertEquals(1f, progress.last())
        // The final file is the destination, not a leftover .part.
        assertTrue(!File(tempDir, "gemma.task.part").exists())
    }

    @Test
    fun `resume continues from the existing partial file with a Range header`() = runTest {
        // Seed a partial file with the first half of the model.
        val half = modelBytes.size / 2
        val partFile = File(tempDir, "gemma.task.part")
        partFile.writeBytes(modelBytes.copyOfRange(0, half))

        // Server honors ranges: 206 with only the missing tail.
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes $half-${modelBytes.size - 1}/${modelBytes.size}")
                .setHeader("Content-Length", (modelBytes.size - half).toString())
                .setBody(Buffer().write(modelBytes.copyOfRange(half, modelBytes.size)))
        )

        val progress = mutableListOf<Float>()
        val result = downloader.download(url(), destination()) { progress.add(it) }

        assertTrue(result is Result.Success)
        assertTrue(destination().readBytes().contentEquals(modelBytes))
        // The Range header was actually sent.
        val recorded = server.takeRequest()
        assertEquals("bytes=$half-", recorded.getHeader("Range"))
        // Progress went from 50% to 100%.
        assertTrue(progress.first() >= 0.5f)
        assertEquals(1f, progress.last())
    }

    @Test
    fun `server ignoring range restarts from scratch`() = runTest {
        // Seed a partial file that a 200 response should overwrite.
        File(tempDir, "gemma.task.part").writeBytes(byteArrayOf(1, 2, 3))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(Buffer().write(modelBytes))
        )

        val result = downloader.download(url(), destination()) {}

        assertTrue(result is Result.Success)
        assertTrue(destination().readBytes().contentEquals(modelBytes))
    }

    @Test
    fun `416 promotes a complete partial file`() = runTest {
        File(tempDir, "gemma.task.part").writeBytes(modelBytes)
        server.enqueue(MockResponse().setResponseCode(416))

        val result = downloader.download(url(), destination()) {}

        assertTrue(result is Result.Success)
        assertTrue(destination().readBytes().contentEquals(modelBytes))
    }

    @Test
    fun `interrupted transfer keeps the partial file for retry`() = runTest {
        // The server disconnects mid-body, so OkHttp reads a prefix then hits EOF
        // before Content-Length is satisfied — the downloader must keep the
        // partial file so a retry can resume from it.
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(modelBytes))
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )

        val result = downloader.download(url(), destination()) {}

        assertTrue(result is Result.Failure)
        // Partial file preserved (non-empty, smaller than the full model).
        val partFile = File(tempDir, "gemma.task.part")
        assertTrue(partFile.exists())
        assertTrue(partFile.length() > 0)
        assertTrue(partFile.length() < modelBytes.size)
        assertTrue(!destination().exists())
    }

    @Test
    fun `http error deletes the partial and returns failure with status`() = runTest {
        File(tempDir, "gemma.task.part").writeBytes(byteArrayOf(1, 2, 3))
        server.enqueue(MockResponse().setResponseCode(403))

        val result = downloader.download(url(), destination()) {}

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error as com.bangersoul.aivance.core.common.result.ProviderError
        assertEquals(403, error.statusCode)
        assertTrue(!File(tempDir, "gemma.task.part").exists())
    }
}

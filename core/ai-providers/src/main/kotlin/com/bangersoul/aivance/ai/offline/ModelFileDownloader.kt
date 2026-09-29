package com.bangersoul.aivance.ai.offline

import com.bangersoul.aivance.core.common.result.ProviderError
import com.bangersoul.aivance.core.common.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile

/**
 * Streams a model file from a URL to disk with progress reporting.
 * Abstracted so the provider can be unit-tested without network I/O.
 */
interface ModelFileDownloader {
    suspend fun download(
        url: String,
        destination: File,
        onProgress: (Float) -> Unit
    ): Result<Unit>
}

/**
 * OkHttp-backed [ModelFileDownloader] with **resumable downloads**.
 *
 * Writes into `<destination>.part` and atomically moves it into place only on
 * success, so an interrupted download (app backgrounded, worker retried) never
 * leaves a corrupt model file. On retry it sends an HTTP `Range` request
 * resuming from the bytes already on disk — servers that honor ranges reply
 * `206 Partial Content` and only the missing tail is fetched.
 *
 * Servers that ignore ranges reply `200` and the download restarts cleanly;
 * a `416` (range not satisfiable) means the partial file already covers the
 * whole resource, so it is promoted directly.
 */
class OkHttpModelFileDownloader(
    private val client: OkHttpClient = OkHttpClient()
) : ModelFileDownloader {

    override suspend fun download(
        url: String,
        destination: File,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val partFile = File(destination.parentFile, "${destination.name}.part")
        try {
            val resumeFrom = if (partFile.exists()) partFile.length() else 0L

            val requestBuilder = Request.Builder().url(url)
            if (resumeFrom > 0) {
                requestBuilder.header("Range", "bytes=$resumeFrom-")
            }
            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                when {
                    response.code == 206 && resumeFrom > 0 ->
                        resumeFromPartial(response, partFile, destination, resumeFrom, onProgress)

                    response.code == 416 ->
                        promoteCompletePart(partFile, destination, onProgress)

                    response.isSuccessful ->
                        downloadFull(response, partFile, destination, onProgress)

                    else -> {
                        partFile.delete()
                        Result.Failure(
                            ProviderError(
                                providerId = "gemma",
                                statusCode = response.code,
                                message = "Model download failed: HTTP ${response.code}"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Keep the partial file so a retry can resume from it. Only IO
            // errors that made it past a successful HTTP exchange matter here;
            // connection failures leave the part intact for the next attempt.
            Result.Failure(
                ProviderError(
                    providerId = "gemma",
                    message = e.message ?: "Model download failed",
                    cause = e
                )
            )
        }
    }

    private suspend fun resumeFromPartial(
        response: okhttp3.Response,
        partFile: File,
        destination: File,
        resumeFrom: Long,
        onProgress: (Float) -> Unit
    ): Result<Unit> {
        val body = response.body ?: return Result.Failure(
            ProviderError("gemma", message = "Model download failed: empty body")
        )
        // Content-Range is "bytes <start>-<end>/<total>".
        val contentRange = response.header("Content-Range")
        val total = parseTotalFromContentRange(contentRange)
        val expected = resumeFrom + body.contentLength().coerceAtLeast(0L)

        RandomAccessFile(partFile, "rw").use { raf ->
            raf.seek(resumeFrom)
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            body.byteStream().use { input ->
                var downloaded = resumeFrom
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    raf.write(buffer, 0, read)
                    downloaded += read
                    emitProgress(downloaded, total, expected, onProgress)
                }
            }
        }
        return finalize(partFile, destination, total, expected, onProgress)
    }

    private suspend fun downloadFull(
        response: okhttp3.Response,
        partFile: File,
        destination: File,
        onProgress: (Float) -> Unit
    ): Result<Unit> {
        val body = response.body ?: return Result.Failure(
            ProviderError("gemma", message = "Model download failed: empty body")
        )
        val total = body.contentLength().coerceAtLeast(0L)
        partFile.parentFile?.mkdirs()

        partFile.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            body.byteStream().use { input ->
                var downloaded = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    emitProgress(downloaded, total, total, onProgress)
                }
            }
        }
        return finalize(partFile, destination, total, total, onProgress)
    }

    /**
     * 416 Range Not Satisfiable: the partial file already spans the whole
     * resource (or the server refuses ranges). If the file is non-empty,
     * promote it; otherwise fail and clean up.
     */
    private suspend fun promoteCompletePart(
        partFile: File,
        destination: File,
        onProgress: (Float) -> Unit
    ): Result<Unit> {
        if (!partFile.exists() || partFile.length() <= 0) {
            partFile.delete()
            return Result.Failure(
                ProviderError("gemma", message = "Model download failed: server rejected range request")
            )
        }
        onProgress(1f)
        return moveIntoPlace(partFile, destination)
    }

    private suspend fun finalize(
        partFile: File,
        destination: File,
        total: Long,
        expected: Long,
        onProgress: (Float) -> Unit
    ): Result<Unit> {
        val actual = partFile.length()
        if (total > 0 && actual < total) {
            // Interrupted (e.g. server closed the connection): keep the partial
            // file so a WorkManager retry resumes from it.
            return Result.Failure(
                ProviderError("gemma", message = "Model download interrupted")
            )
        }
        if (expected > 0 && actual < expected) {
            return Result.Failure(
                ProviderError("gemma", message = "Model download interrupted")
            )
        }
        onProgress(1f)
        return moveIntoPlace(partFile, destination)
    }

    private fun moveIntoPlace(partFile: File, destination: File): Result<Unit> {
        destination.parentFile?.mkdirs()
        if (destination.exists()) destination.delete()
        if (!partFile.renameTo(destination)) {
            partFile.copyTo(destination, overwrite = true)
            partFile.delete()
        }
        return Result.Success(Unit)
    }

    private fun emitProgress(downloaded: Long, total: Long, expected: Long, onProgress: (Float) -> Unit) {
        val denominator = maxOf(total, expected, 1L)
        onProgress((downloaded.toFloat() / denominator).coerceIn(0f, 1f))
    }

    private fun parseTotalFromContentRange(contentRange: String?): Long {
        if (contentRange == null) return 0L
        // Format: "bytes <start>-<end>/<total>" — the total may be "*".
        val slash = contentRange.lastIndexOf('/')
        if (slash == -1) return 0L
        return contentRange.substring(slash + 1).toLongOrNull() ?: 0L
    }

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 64 * 1024
    }
}

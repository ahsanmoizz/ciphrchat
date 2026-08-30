package org.ciphrchat.app.files

import android.content.Context
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates chunked streaming 5 GiB file uploads and downloads to the CiphrChat relay.
 * Streams data directly from storage to network with bounded RAM buffers, supporting resume and cancellation.
 */
@Singleton
class LargeFileTransferManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val activeTransfers = ConcurrentHashMap<String, Boolean>()
    private val _progress = MutableStateFlow<Map<String, FileTransferProgress>>(emptyMap())
    val progress: StateFlow<Map<String, FileTransferProgress>> = _progress.asStateFlow()

    /**
     * Prepares a file descriptor for streaming upload, generating a per-file AES-256 random key.
     */
    suspend fun prepareDescriptor(
        uri: Uri,
        fileName: String,
        mimeType: String,
        senderId: String,
        recipientId: String
    ): Result<Pair<FileTransferDescriptor, ByteArray>> = withContext(Dispatchers.IO) {
        runCatching {
            val fileId = UUID.randomUUID().toString()
            val fileSize = getFileSize(uri)
            require(fileSize > 0 && fileSize <= FileTransferDescriptor.MAX_FILE_SIZE_BYTES) {
                "File size exceeds 5 GiB limit or is 0"
            }

            val chunkSize = FileTransferDescriptor.DEFAULT_CHUNK_SIZE_BYTES
            val totalChunks = ((fileSize + chunkSize - 1) / chunkSize).toInt()

            val fileKey = ByteArray(32).apply { SecureRandom().nextBytes(this) }
            val sha256 = calculateSha256(uri)

            val descriptor = FileTransferDescriptor(
                fileId = fileId,
                fileName = fileName,
                fileSize = fileSize,
                mimeType = mimeType,
                sha256 = sha256,
                chunkSize = chunkSize,
                totalChunks = totalChunks,
                fileKeyBase64 = Base64.encodeToString(fileKey, Base64.NO_WRAP),
                senderId = senderId,
                recipientId = recipientId
            )
            Pair(descriptor, fileKey)
        }
    }

    /**
     * Uploads file chunks in a streaming manner to the relay with resume support.
     */
    suspend fun uploadFile(
        relayBaseHttpUrl: String,
        uri: Uri,
        descriptor: FileTransferDescriptor,
        fileKey: ByteArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val fileId = descriptor.fileId
            activeTransfers[fileId] = true

            // 1. Init file on relay
            initFileOnRelay(relayBaseHttpUrl, descriptor)

            // 2. Query already uploaded chunks for resume
            val uploadedSet = queryUploadedChunks(relayBaseHttpUrl, fileId)

            val buffer = ByteArray(descriptor.chunkSize)
            var bytesReadTotal = 0L

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                for (chunkIndex in 0 until descriptor.totalChunks) {
                    if (activeTransfers[fileId] != true) {
                        throw CancellationException("Upload cancelled for $fileId")
                    }

                    val expectedBytes = if (chunkIndex == descriptor.totalChunks - 1) {
                        (descriptor.fileSize - (chunkIndex.toLong() * descriptor.chunkSize)).toInt()
                    } else {
                        descriptor.chunkSize
                    }

                    if (uploadedSet.contains(chunkIndex)) {
                        // Skip already uploaded chunk for resume
                        skipFully(inputStream, expectedBytes.toLong())
                        bytesReadTotal += expectedBytes
                        continue
                    }

                    val read = readExact(inputStream, buffer, expectedBytes)
                    val plaintextChunk = buffer.copyOfRange(0, read)

                    val encryptedChunk = FileChunkCipher.encryptChunk(fileKey, fileId, chunkIndex, plaintextChunk)
                    uploadChunkToRelay(relayBaseHttpUrl, fileId, chunkIndex, encryptedChunk)

                    bytesReadTotal += read
                    updateProgress(
                        FileTransferProgress.Uploading(
                            fileId = fileId,
                            uploadedBytes = bytesReadTotal,
                            totalBytes = descriptor.fileSize,
                            currentChunk = chunkIndex + 1,
                            totalChunks = descriptor.totalChunks
                        )
                    )
                }
            } ?: error("Could not open input file")

            // 3. Mark complete on relay
            completeFileOnRelay(relayBaseHttpUrl, fileId)
            updateProgress(FileTransferProgress.Completed(fileId, uri.toString(), descriptor.fileSize))
        }.also {
            activeTransfers.remove(descriptor.fileId)
        }
    }

    /**
     * Downloads file chunks from the relay, decrypts them, and streams them to a local destination file.
     */
    suspend fun downloadFile(
        relayBaseHttpUrl: String,
        descriptor: FileTransferDescriptor,
        destinationFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val fileId = descriptor.fileId
            activeTransfers[fileId] = true
            val fileKey = Base64.decode(descriptor.fileKeyBase64, Base64.NO_WRAP)

            if (!destinationFile.parentFile.exists()) destinationFile.parentFile.mkdirs()
            val tempFile = File(destinationFile.parentFile, ".download_${fileId}.tmp")

            var downloadedBytes = 0L
            FileOutputStream(tempFile, false).use { outputStream ->
                for (chunkIndex in 0 until descriptor.totalChunks) {
                    if (activeTransfers[fileId] != true) {
                        throw CancellationException("Download cancelled for $fileId")
                    }

                    val encryptedBytes = downloadChunkFromRelay(relayBaseHttpUrl, fileId, chunkIndex)
                    val decryptedBytes = FileChunkCipher.decryptChunk(fileKey, fileId, chunkIndex, encryptedBytes)

                    outputStream.write(decryptedBytes)
                    downloadedBytes += decryptedBytes.size

                    updateProgress(
                        FileTransferProgress.Downloading(
                            fileId = fileId,
                            downloadedBytes = downloadedBytes,
                            totalBytes = descriptor.fileSize,
                            currentChunk = chunkIndex + 1,
                            totalChunks = descriptor.totalChunks
                        )
                    )
                }
            }

            if (tempFile.renameTo(destinationFile)) {
                updateProgress(FileTransferProgress.Completed(fileId, destinationFile.absolutePath, descriptor.fileSize))
                // Acknowledge & delete from relay
                runCatching { deleteFileFromRelay(relayBaseHttpUrl, fileId) }
                destinationFile
            } else {
                error("Failed to finalize downloaded file")
            }
        }.also {
            activeTransfers.remove(descriptor.fileId)
        }
    }

    fun cancel(fileId: String) {
        activeTransfers[fileId] = false
        updateProgress(FileTransferProgress.Cancelled(fileId))
    }

    private fun updateProgress(progressItem: FileTransferProgress) {
        val current = _progress.value.toMutableMap()
        when (progressItem) {
            is FileTransferProgress.Uploading -> current[progressItem.fileId] = progressItem
            is FileTransferProgress.Downloading -> current[progressItem.fileId] = progressItem
            is FileTransferProgress.Completed -> current[progressItem.fileId] = progressItem
            is FileTransferProgress.Failed -> current[progressItem.fileId] = progressItem
            is FileTransferProgress.Cancelled -> current[progressItem.fileId] = progressItem
        }
        _progress.value = current
    }

    private fun getFileSize(uri: Uri): Long {
        return context.contentResolver.openFileDescriptor(uri, "r")?.use {
            it.statSize
        } ?: 0L
    }

    private fun calculateSha256(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val buf = ByteArray(64 * 1024)
            var r: Int
            while (stream.read(buf).also { r = it } > 0) {
                digest.update(buf, 0, r)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun skipFully(inputStream: InputStream, toSkip: Long) {
        var remaining = toSkip
        while (remaining > 0) {
            val skipped = inputStream.skip(remaining)
            if (skipped <= 0) break
            remaining -= skipped
        }
    }

    private fun readExact(inputStream: InputStream, buffer: ByteArray, length: Int): Int {
        var total = 0
        while (total < length) {
            val r = inputStream.read(buffer, total, length - total)
            if (r < 0) break
            total += r
        }
        return total
    }

    private fun initFileOnRelay(relayUrl: String, descriptor: FileTransferDescriptor) {
        val json = JSONObject()
            .put("file_id", descriptor.fileId)
            .put("sender_peer_id", descriptor.senderId)
            .put("recipient_peer_id", descriptor.recipientId)
            .put("file_size", descriptor.fileSize)
            .put("chunk_size", descriptor.chunkSize)
            .put("total_chunks", descriptor.totalChunks)
            .put("sha256", descriptor.sha256)
            .put("expires_at_epoch_ms", System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000)

        val url = URL("$relayUrl/files/init")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        require(code in 200..299) { "Failed to init file on relay: HTTP $code" }
    }

    private fun queryUploadedChunks(relayUrl: String, fileId: String): Set<Int> {
        return runCatching {
            val url = URL("$relayUrl/files/status/$fileId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            if (conn.responseCode != 200) return emptySet()
            val text = conn.inputStream.use { BufferedReader(InputStreamReader(it)).readText() }
            val json = JSONObject(text)
            val array = json.optJSONArray("uploaded_chunks") ?: JSONArray()
            val set = mutableSetOf<Int>()
            for (i in 0 until array.length()) set.add(array.getInt(i))
            set
        }.getOrDefault(emptySet())
    }

    private fun uploadChunkToRelay(relayUrl: String, fileId: String, chunkIndex: Int, chunkData: ByteArray) {
        val url = URL("$relayUrl/files/upload/$fileId/$chunkIndex")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/octet-stream")
            setFixedLengthStreamingMode(chunkData.size)
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 30_000
        }
        conn.outputStream.use { it.write(chunkData) }
        val code = conn.responseCode
        require(code in 200..299) { "Failed to upload chunk $chunkIndex: HTTP $code" }
    }

    private fun downloadChunkFromRelay(relayUrl: String, fileId: String, chunkIndex: Int): ByteArray {
        val url = URL("$relayUrl/files/download/$fileId/$chunkIndex")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 30_000
        }
        val code = conn.responseCode
        require(code == 200) { "Failed to download chunk $chunkIndex: HTTP $code" }
        return conn.inputStream.use { it.readBytes() }
    }

    private fun completeFileOnRelay(relayUrl: String, fileId: String) {
        val url = URL("$relayUrl/files/complete/$fileId")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        val code = conn.responseCode
        require(code in 200..299) { "Failed to mark file complete: HTTP $code" }
    }

    private fun deleteFileFromRelay(relayUrl: String, fileId: String) {
        val url = URL("$relayUrl/files/$fileId")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        conn.responseCode
    }
}

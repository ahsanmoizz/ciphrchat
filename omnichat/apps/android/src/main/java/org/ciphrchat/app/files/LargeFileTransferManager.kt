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
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates chunked streaming 5 GiB file uploads and downloads through the zero-retention CiphrChat relay.
 * The relay acts as an in-memory transit pipe with zero disk storage; all chunk tracking, partial assembly,
 * and resumption state is managed end-to-end on local device storage.
 */
@Singleton
class LargeFileTransferManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val activeTransfers = ConcurrentHashMap<String, Boolean>()
    private val _progress = MutableStateFlow<Map<String, FileTransferProgress>>(emptyMap())
    val progress: StateFlow<Map<String, FileTransferProgress>> = _progress.asStateFlow()

    private val descriptorsBySha256 = ConcurrentHashMap<String, FileTransferDescriptor>()
    private val descriptorsByFileId = ConcurrentHashMap<String, FileTransferDescriptor>()

    fun registerDescriptor(descriptor: FileTransferDescriptor) {
        descriptorsBySha256[descriptor.sha256] = descriptor
        descriptorsByFileId[descriptor.fileId] = descriptor
        saveDescriptorToDisk(descriptor)
    }

    fun findDescriptorBySha256(sha256: String): FileTransferDescriptor? {
        descriptorsBySha256[sha256]?.let { return it }
        return loadDescriptorFromDiskBySha(sha256)
    }

    fun findDescriptorByFileId(fileId: String): FileTransferDescriptor? {
        descriptorsByFileId[fileId]?.let { return it }
        return loadDescriptorFromDiskByFileId(fileId)
    }

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
            registerDescriptor(descriptor)
            Pair(descriptor, fileKey)
        }
    }

    /**
     * Streams file chunks from local storage through the transit relay, reading missing chunks from the original local file.
     */
    suspend fun uploadFile(
        relayBaseHttpUrl: String,
        uri: Uri,
        descriptor: FileTransferDescriptor,
        fileKey: ByteArray,
        missingChunkIndexes: Set<Int>? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val fileId = descriptor.fileId
            activeTransfers[fileId] = true
            registerDescriptor(descriptor)

            // 1. Initialize transient stream session on relay
            initFileOnRelay(relayBaseHttpUrl, descriptor)

            val chunksToSend = missingChunkIndexes ?: (0 until descriptor.totalChunks).toSet()
            var bytesReadTotal = 0L

            for (chunkIndex in 0 until descriptor.totalChunks) {
                if (activeTransfers[fileId] != true) {
                    throw CancellationException("Upload cancelled for $fileId")
                }

                val expectedBytes = if (chunkIndex == descriptor.totalChunks - 1) {
                    (descriptor.fileSize - (chunkIndex.toLong() * descriptor.chunkSize)).toInt()
                } else {
                    descriptor.chunkSize
                }

                if (!chunksToSend.contains(chunkIndex)) {
                    bytesReadTotal += expectedBytes
                    continue
                }

                // Read chunk directly from local source file
                val plaintextChunk = readChunkFromUri(uri, chunkIndex.toLong() * descriptor.chunkSize, expectedBytes)
                val encryptedChunk = FileChunkCipher.encryptChunk(fileKey, fileId, chunkIndex, plaintextChunk)

                uploadChunkToRelay(relayBaseHttpUrl, fileId, chunkIndex, encryptedChunk)

                bytesReadTotal += plaintextChunk.size
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

            // 2. Mark transit session complete on relay
            completeFileOnRelay(relayBaseHttpUrl, fileId)
            updateProgress(FileTransferProgress.Completed(fileId, uri.toString(), descriptor.fileSize))
        }.also {
            activeTransfers.remove(descriptor.fileId)
        }
    }

    /**
     * Streams file chunks from the relay directly into a local .partial file, tracking received chunks in .part_meta.
     * Performs final SHA-256 validation before atomically renaming to the destination file.
     */
    suspend fun downloadFile(
        relayBaseHttpUrl: String,
        descriptor: FileTransferDescriptor,
        destinationFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val fileId = descriptor.fileId
            activeTransfers[fileId] = true
            registerDescriptor(descriptor)
            val fileKey = Base64.decode(descriptor.fileKeyBase64, Base64.NO_WRAP)

            if (!destinationFile.parentFile.exists()) destinationFile.parentFile.mkdirs()
            val partialFile = File(destinationFile.parentFile, "${destinationFile.name}.partial")
            val metaFile = File(destinationFile.parentFile, ".${destinationFile.name}.part_meta")

            // Load locally received chunk indexes for end-to-end resume
            val receivedChunks = loadLocalReceivedChunks(metaFile)

            var downloadedBytes = receivedChunks.size.toLong() * descriptor.chunkSize
            if (downloadedBytes > descriptor.fileSize) downloadedBytes = descriptor.fileSize

            RandomAccessFile(partialFile, "rw").use { raf ->
                raf.setLength(descriptor.fileSize)

                for (chunkIndex in 0 until descriptor.totalChunks) {
                    if (activeTransfers[fileId] != true) {
                        throw CancellationException("Download cancelled for $fileId")
                    }

                    if (receivedChunks.contains(chunkIndex)) {
                        continue
                    }

                    val encryptedBytes = downloadChunkFromRelay(relayBaseHttpUrl, fileId, chunkIndex)
                    val decryptedBytes = FileChunkCipher.decryptChunk(fileKey, fileId, chunkIndex, encryptedBytes)

                    raf.seek(chunkIndex.toLong() * descriptor.chunkSize)
                    raf.write(decryptedBytes)

                    receivedChunks.add(chunkIndex)
                    saveLocalReceivedChunks(metaFile, receivedChunks)

                    downloadedBytes = (downloadedBytes + decryptedBytes.size).coerceAtMost(descriptor.fileSize)

                    updateProgress(
                        FileTransferProgress.Downloading(
                            fileId = fileId,
                            downloadedBytes = downloadedBytes,
                            totalBytes = descriptor.fileSize,
                            currentChunk = receivedChunks.size,
                            totalChunks = descriptor.totalChunks
                        )
                    )
                }
            }

            // Verify final SHA-256 integrity check on the complete local .partial file
            val actualSha256 = calculateFileSha256(partialFile)
            require(actualSha256.equals(descriptor.sha256, ignoreCase = true)) {
                partialFile.delete()
                metaFile.delete()
                "File integrity verification failed (SHA-256 mismatch)"
            }

            if (partialFile.renameTo(destinationFile)) {
                metaFile.delete()
                updateProgress(FileTransferProgress.Completed(fileId, destinationFile.absolutePath, descriptor.fileSize))
                // Acknowledge & clear transient session on relay
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

    private fun loadLocalReceivedChunks(metaFile: File): MutableSet<Int> {
        if (!metaFile.exists()) return mutableSetOf()
        return runCatching {
            metaFile.readLines()
                .mapNotNull { it.trim().toIntOrNull() }
                .toMutableSet()
        }.getOrDefault(mutableSetOf())
    }

    private fun saveLocalReceivedChunks(metaFile: File, chunks: Set<Int>) {
        runCatching {
            metaFile.writeText(chunks.joinToString("\n"))
        }
    }

    private fun calculateFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buf = ByteArray(64 * 1024)
            var r: Int
            while (stream.read(buf).also { r = it } > 0) {
                digest.update(buf, 0, r)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readChunkFromUri(uri: Uri, offset: Long, length: Int): ByteArray {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            skipFully(stream, offset)
            val buf = ByteArray(length)
            val read = readExact(stream, buf, length)
            return buf.copyOfRange(0, read)
        } ?: error("Could not open source file URI")
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

    private fun uploadChunkToRelay(relayUrl: String, fileId: String, chunkIndex: Int, chunkData: ByteArray) {
        var attempts = 0
        val maxAttempts = 6
        while (attempts < maxAttempts) {
            attempts++
            val url = URL("$relayUrl/files/upload/$fileId/$chunkIndex")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/octet-stream")
                setFixedLengthStreamingMode(chunkData.size)
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 30_000
            }
            try {
                conn.outputStream.use { it.write(chunkData) }
                val code = conn.responseCode
                if (code in 200..299) {
                    return
                } else if (code == 429 || code in 500..599) {
                    Thread.sleep((attempts * 250L).coerceAtMost(2000L))
                    continue
                } else {
                    error("Failed to upload chunk $chunkIndex: HTTP $code")
                }
            } catch (e: Exception) {
                if (attempts >= maxAttempts) throw e
                Thread.sleep((attempts * 250L).coerceAtMost(2000L))
            }
        }
        error("Exceeded maximum retries uploading chunk $chunkIndex")
    }

    private fun downloadChunkFromRelay(relayUrl: String, fileId: String, chunkIndex: Int): ByteArray {
        var attempts = 0
        val maxAttempts = 20
        while (attempts < maxAttempts) {
            attempts++
            val url = URL("$relayUrl/files/download/$fileId/$chunkIndex")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30_000
                readTimeout = 30_000
            }
            try {
                val code = conn.responseCode
                if (code == 200) {
                    return conn.inputStream.use { it.readBytes() }
                } else if (code == 404 || code == 429 || code in 500..599) {
                    Thread.sleep((attempts * 200L).coerceAtMost(1500L))
                    continue
                } else {
                    error("Failed to download chunk $chunkIndex: HTTP $code")
                }
            } catch (e: Exception) {
                if (attempts >= maxAttempts) throw e
                Thread.sleep((attempts * 200L).coerceAtMost(1500L))
            }
        }
        error("Exceeded maximum retries downloading chunk $chunkIndex")
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

    private fun saveDescriptorToDisk(descriptor: FileTransferDescriptor) {
        runCatching {
            val dir = File(context.filesDir, "CiphrChat/descriptors").apply { mkdirs() }
            val file = File(dir, "${descriptor.fileId}.json")
            val json = JSONObject()
                .put("fileId", descriptor.fileId)
                .put("fileName", descriptor.fileName)
                .put("fileSize", descriptor.fileSize)
                .put("mimeType", descriptor.mimeType)
                .put("sha256", descriptor.sha256)
                .put("chunkSize", descriptor.chunkSize)
                .put("totalChunks", descriptor.totalChunks)
                .put("fileKeyBase64", descriptor.fileKeyBase64)
                .put("senderId", descriptor.senderId)
                .put("recipientId", descriptor.recipientId)
                .put("createdAtEpochMs", descriptor.createdAtEpochMs)
            file.writeText(json.toString())
        }
    }

    private fun loadDescriptorFromDiskByFileId(fileId: String): FileTransferDescriptor? = runCatching {
        val file = File(context.filesDir, "CiphrChat/descriptors/$fileId.json")
        if (!file.exists()) return null
        parseDescriptorJson(JSONObject(file.readText()))
    }.getOrNull()

    private fun loadDescriptorFromDiskBySha(sha256: String): FileTransferDescriptor? = runCatching {
        val dir = File(context.filesDir, "CiphrChat/descriptors")
        if (!dir.exists()) return null
        dir.listFiles()?.forEach { file ->
            val desc = runCatching { parseDescriptorJson(JSONObject(file.readText())) }.getOrNull()
            if (desc?.sha256 == sha256) {
                descriptorsBySha256[sha256] = desc
                descriptorsByFileId[desc.fileId] = desc
                return desc
            }
        }
        null
    }.getOrNull()

    private fun parseDescriptorJson(json: JSONObject): FileTransferDescriptor {
        return FileTransferDescriptor(
            fileId = json.getString("fileId"),
            fileName = json.getString("fileName"),
            fileSize = json.getLong("fileSize"),
            mimeType = json.getString("mimeType"),
            sha256 = json.getString("sha256"),
            chunkSize = json.getInt("chunkSize"),
            totalChunks = json.getInt("totalChunks"),
            fileKeyBase64 = json.getString("fileKeyBase64"),
            senderId = json.getString("senderId"),
            recipientId = json.getString("recipientId"),
            createdAtEpochMs = json.optLong("createdAtEpochMs", System.currentTimeMillis())
        )
    }
}

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
    private val senderTransfersByFileId = ConcurrentHashMap<String, SenderTransferState>()

    init {
        loadPersistedSenderTransfers()
    }

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

    fun saveSenderTransfer(state: SenderTransferState) {
        senderTransfersByFileId[state.fileId] = state
        registerDescriptor(state.descriptor)
        saveSenderTransferToDisk(state)
    }

    fun getSenderTransfer(fileId: String): SenderTransferState? {
        senderTransfersByFileId[fileId]?.let { return it }
        return loadSenderTransferFromDisk(fileId)
    }

    fun updateSenderTransferStatus(fileId: String, status: String) {
        getSenderTransfer(fileId)?.let { existing ->
            val updated = existing.copy(status = status)
            saveSenderTransfer(updated)
        }
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

            val senderState = SenderTransferState(
                fileId = fileId,
                sourceUriString = uri.toString(),
                recipientId = recipientId,
                descriptor = descriptor,
                fileKeyBase64 = descriptor.fileKeyBase64,
                status = "WAITING_FOR_RECEIVER"
            )
            saveSenderTransfer(senderState)
            updateProgress(
                FileTransferProgress.WaitingForReceiver(
                    fileId = fileId,
                    fileName = fileName,
                    totalBytes = fileSize
                )
            )

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
            require(relayBaseHttpUrl.isNotBlank()) {
                "File relay URL is not configured"
            }
            val fileId = descriptor.fileId
            activeTransfers[fileId] = true
            updateSenderTransferStatus(fileId, "UPLOADING")

            // 1. Initialize transient stream session on relay
            initFileOnRelay(relayBaseHttpUrl, descriptor)

            val chunksToSend = missingChunkIndexes ?: (0 until descriptor.totalChunks).toSet()
            var bytesTransferredTotal = 0L

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
                    bytesTransferredTotal += expectedBytes
                    continue
                }

                // Read chunk directly from original local source file
                val plaintextChunk = readChunkFromUri(uri, chunkIndex.toLong() * descriptor.chunkSize, expectedBytes)
                val encryptedChunk = FileChunkCipher.encryptChunk(fileKey, fileId, chunkIndex, plaintextChunk)

                uploadChunkToRelay(relayBaseHttpUrl, fileId, chunkIndex, encryptedChunk)

                bytesTransferredTotal += plaintextChunk.size
                updateProgress(
                    FileTransferProgress.Uploading(
                        fileId = fileId,
                        uploadedBytes = bytesTransferredTotal,
                        totalBytes = descriptor.fileSize,
                        currentChunk = chunkIndex + 1,
                        totalChunks = descriptor.totalChunks
                    )
                )
            }

            // 2. Mark transit session complete on relay
            completeFileOnRelay(relayBaseHttpUrl, fileId)
            updateSenderTransferStatus(fileId, "COMPLETED")
            updateProgress(FileTransferProgress.Completed(fileId, uri.toString(), descriptor.fileSize))
        }.onFailure { err ->
            if (err is CancellationException) {
                updateProgress(FileTransferProgress.Cancelled(descriptor.fileId))
            } else {
                updateProgress(FileTransferProgress.Failed(descriptor.fileId, err.message ?: "Upload failed"))
            }
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
            require(relayBaseHttpUrl.isNotBlank()) {
                "File relay URL is not configured"
            }
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
        }.onFailure { err ->
            if (err is CancellationException) {
                updateProgress(FileTransferProgress.Cancelled(descriptor.fileId))
            } else {
                updateProgress(FileTransferProgress.Failed(descriptor.fileId, err.message ?: "Download failed"))
            }
        }.also {
            activeTransfers.remove(descriptor.fileId)
        }
    }

    fun cancel(fileId: String) {
        activeTransfers[fileId] = false
        updateProgress(FileTransferProgress.Cancelled(fileId))
    }

    fun pause(fileId: String) {
        activeTransfers[fileId] = false
        val currentProg = _progress.value[fileId]
        val transferred = when (currentProg) {
            is FileTransferProgress.Uploading -> currentProg.uploadedBytes
            is FileTransferProgress.Downloading -> currentProg.downloadedBytes
            else -> 0L
        }
        val total = when (currentProg) {
            is FileTransferProgress.Uploading -> currentProg.totalBytes
            is FileTransferProgress.Downloading -> currentProg.totalBytes
            is FileTransferProgress.WaitingForReceiver -> currentProg.totalBytes
            else -> 0L
        }
        updateProgress(FileTransferProgress.Paused(fileId, transferred, total))
    }

    fun getLocalMissingChunks(destinationFile: File, totalChunks: Int): List<Int> {
        val metaFile = File(destinationFile.parentFile, ".${destinationFile.name}.part_meta")
        val received = loadLocalReceivedChunks(metaFile)
        return (0 until totalChunks).filter { !received.contains(it) }
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
        } ?: error("Original source file is no longer accessible; please select the file again.")
    }

    fun updateProgress(progressItem: FileTransferProgress) {
        val current = _progress.value.toMutableMap()
        current[progressItem.fileId] = progressItem
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
        val maxAttempts = 120 // Support up to several minutes of receiver backpressure
        while (attempts < maxAttempts && activeTransfers[fileId] == true) {
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
                    // Backpressure: receiver has not consumed buffered chunks yet; wait with exponential backoff
                    Thread.sleep((attempts * 250L).coerceAtMost(3000L))
                    continue
                } else {
                    error("Failed to upload chunk $chunkIndex: HTTP $code")
                }
            } catch (e: Exception) {
                if (attempts >= maxAttempts || activeTransfers[fileId] != true) throw e
                Thread.sleep((attempts * 250L).coerceAtMost(3000L))
            }
        }
        if (activeTransfers[fileId] != true) throw CancellationException("Upload cancelled")
        error("Exceeded maximum retries uploading chunk $chunkIndex")
    }

    private fun downloadChunkFromRelay(relayUrl: String, fileId: String, chunkIndex: Int): ByteArray {
        var attempts = 0
        val maxAttempts = 120 // Support receiver waiting for sender live stream
        while (attempts < maxAttempts && activeTransfers[fileId] == true) {
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
                    // Chunk not yet buffered by sender into transit relay: wait for live concurrent streaming
                    Thread.sleep((attempts * 200L).coerceAtMost(2000L))
                    continue
                } else {
                    error("Failed to download chunk $chunkIndex: HTTP $code")
                }
            } catch (e: Exception) {
                if (attempts >= maxAttempts || activeTransfers[fileId] != true) throw e
                Thread.sleep((attempts * 200L).coerceAtMost(2000L))
            }
        }
        if (activeTransfers[fileId] != true) throw CancellationException("Download cancelled")
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

    private fun saveSenderTransferToDisk(state: SenderTransferState) {
        runCatching {
            val dir = File(context.filesDir, "CiphrChat/sender_transfers").apply { mkdirs() }
            val file = File(dir, "${state.fileId}.json")
            val json = JSONObject()
                .put("fileId", state.fileId)
                .put("sourceUriString", state.sourceUriString)
                .put("recipientId", state.recipientId)
                .put("fileKeyBase64", state.fileKeyBase64)
                .put("status", state.status)
                .put("createdAtEpochMs", state.createdAtEpochMs)
                .put("descriptor", JSONObject()
                    .put("fileId", state.descriptor.fileId)
                    .put("fileName", state.descriptor.fileName)
                    .put("fileSize", state.descriptor.fileSize)
                    .put("mimeType", state.descriptor.mimeType)
                    .put("sha256", state.descriptor.sha256)
                    .put("chunkSize", state.descriptor.chunkSize)
                    .put("totalChunks", state.descriptor.totalChunks)
                    .put("fileKeyBase64", state.descriptor.fileKeyBase64)
                    .put("senderId", state.descriptor.senderId)
                    .put("recipientId", state.descriptor.recipientId)
                    .put("createdAtEpochMs", state.descriptor.createdAtEpochMs)
                )
            file.writeText(json.toString())
        }
    }

    private fun loadSenderTransferFromDisk(fileId: String): SenderTransferState? = runCatching {
        val file = File(context.filesDir, "CiphrChat/sender_transfers/$fileId.json")
        if (!file.exists()) return null
        parseSenderTransferJson(JSONObject(file.readText()))
    }.getOrNull()

    private fun loadPersistedSenderTransfers() {
        runCatching {
            val dir = File(context.filesDir, "CiphrChat/sender_transfers")
            if (!dir.exists()) return
            dir.listFiles()?.forEach { file ->
                val state = runCatching { parseSenderTransferJson(JSONObject(file.readText())) }.getOrNull()
                if (state != null) {
                    senderTransfersByFileId[state.fileId] = state
                    registerDescriptor(state.descriptor)
                }
            }
        }
    }

    private fun parseSenderTransferJson(json: JSONObject): SenderTransferState {
        val descJson = json.getJSONObject("descriptor")
        val descriptor = parseDescriptorJson(descJson)
        return SenderTransferState(
            fileId = json.getString("fileId"),
            sourceUriString = json.getString("sourceUriString"),
            recipientId = json.getString("recipientId"),
            descriptor = descriptor,
            fileKeyBase64 = json.getString("fileKeyBase64"),
            status = json.optString("status", "WAITING_FOR_RECEIVER"),
            createdAtEpochMs = json.optLong("createdAtEpochMs", System.currentTimeMillis())
        )
    }

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

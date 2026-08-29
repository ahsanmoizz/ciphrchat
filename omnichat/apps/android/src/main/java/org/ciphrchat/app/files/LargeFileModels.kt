package org.ciphrchat.app.files

/**
 * Data models for chunked streaming 5 GiB file transfer.
 */
data class FileTransferDescriptor(
    val fileId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val sha256: String,
    val chunkSize: Int,
    val totalChunks: Int,
    val fileKeyBase64: String,
    val senderId: String,
    val recipientId: String,
    val createdAtEpochMs: Long = System.currentTimeMillis()
) {
    companion object {
        const val MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024 * 1024 // 5 GiB
        const val DEFAULT_CHUNK_SIZE_BYTES = 1024 * 1024 // 1 MiB
    }

    init {
        require(fileSize > 0 && fileSize <= MAX_FILE_SIZE_BYTES) {
            "File size must be between 1 byte and 5 GiB (got $fileSize bytes)"
        }
        require(chunkSize in 1024..(4 * 1024 * 1024)) {
            "Chunk size must be between 1 KiB and 4 MiB"
        }
        val expectedChunks = ((fileSize + chunkSize - 1) / chunkSize).toInt()
        require(totalChunks == expectedChunks) {
            "Total chunks mismatch for file size: expected $expectedChunks, got $totalChunks"
        }
        require(fileId.length in 1..64 && fileId.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            "File ID contains invalid characters"
        }
    }
}

sealed interface FileTransferProgress {
    data class Uploading(
        val fileId: String,
        val uploadedBytes: Long,
        val totalBytes: Long,
        val currentChunk: Int,
        val totalChunks: Int
    ) : FileTransferProgress

    data class Downloading(
        val fileId: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val currentChunk: Int,
        val totalChunks: Int
    ) : FileTransferProgress

    data class Completed(
        val fileId: String,
        val localFilePath: String,
        val totalBytes: Long
    ) : FileTransferProgress

    data class Failed(
        val fileId: String,
        val error: String
    ) : FileTransferProgress

    data class Cancelled(
        val fileId: String
    ) : FileTransferProgress
}

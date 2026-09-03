package org.ciphrchat.app.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ensures media files are never loaded into Compose memory at full unconstrained resolution.
 * Calculates optimal inSampleSize and decodes safely on background thread.
 */
object ThumbnailUtils {
    const val DEFAULT_MAX_DIMENSION = 300

    fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    suspend fun decodeSampledBitmap(
        file: File,
        reqWidth: Int = DEFAULT_MAX_DIMENSION,
        reqHeight: Int = DEFAULT_MAX_DIMENSION
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile || file.length() == 0L) return@withContext null
        runCatching {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) return@runCatching null

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565

            BitmapFactory.decodeFile(file.absolutePath, options)
        }.getOrNull()
    }

    suspend fun decodeSampledBitmap(
        bytes: ByteArray,
        reqWidth: Int = DEFAULT_MAX_DIMENSION,
        reqHeight: Int = DEFAULT_MAX_DIMENSION
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (bytes.isEmpty()) return@withContext null
        runCatching {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) return@runCatching null

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565

            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull()
    }
}

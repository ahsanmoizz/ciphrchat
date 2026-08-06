package org.ciphrchat.app.sharing

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppShareManager @Inject constructor(
    private val context: Context
) {
    fun shareApk(apkFile: File): Result<Unit> = runCatching {
        require(apkFile.exists()) { "APK file does not exist" }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            apkFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share CiphrChat").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun shareDownloadLink(url: String = OFFICIAL_DOWNLOAD_URL): Result<Unit> = runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Check out CiphrChat — private messaging through any connection: $url")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share CiphrChat").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    companion object {
        const val OFFICIAL_DOWNLOAD_URL = "https://github.com/AhsanDevHub/CiphrChat/releases"
    }
}

package dev.labstk.homeburrow.files

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

class AndroidDownloadedFileOpener(
    private val context: Context,
) : DownloadedFileOpener {

    override fun openFile(file: LocalFileData): Result<Unit> {
        return try {
            val outputDir = File(context.cacheDir, "downloads")
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                return Result.failure(IllegalStateException("Could not prepare local file storage."))
            }

            val safeName = sanitizeFileName(file.fileName)
            val outputFile = File(outputDir, "${System.currentTimeMillis()}-$safeName")
            outputFile.writeBytes(file.bytes)

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile,
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, file.mimeType.ifBlank { "application/octet-stream" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(context.packageManager) == null) {
                Result.failure(IllegalStateException("No app is available to open this file type."))
            } else {
                context.startActivity(intent)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sanitizeFileName(value: String): String {
        return value
            .substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "download.bin" }
    }
}

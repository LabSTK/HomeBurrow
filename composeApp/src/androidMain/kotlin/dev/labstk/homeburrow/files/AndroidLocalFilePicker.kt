package dev.labstk.homeburrow.files

import android.provider.OpenableColumns
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class AndroidLocalFilePicker(
    private val activity: ComponentActivity,
) : LocalFilePicker {

    private var pendingPickContinuation: kotlinx.coroutines.CancellableContinuation<Result<LocalFileData>>? = null

    private val pickerLauncher =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val continuation = pendingPickContinuation ?: return@registerForActivityResult
            pendingPickContinuation = null

            val result = if (uri == null) {
                Result.failure(IllegalStateException("File selection was canceled."))
            } else {
                readFile(uri)
            }
            continuation.resume(result)
        }

    override suspend fun pickFile(): Result<LocalFileData> = withContext(Dispatchers.Main.immediate) {
        if (pendingPickContinuation != null) {
            return@withContext Result.failure(IllegalStateException("A file selection is already in progress."))
        }

        suspendCancellableCoroutine { continuation ->
            pendingPickContinuation = continuation
            continuation.invokeOnCancellation {
                if (pendingPickContinuation === continuation) {
                    pendingPickContinuation = null
                }
            }

            try {
                pickerLauncher.launch(arrayOf("*/*"))
            } catch (e: Exception) {
                if (pendingPickContinuation === continuation) {
                    pendingPickContinuation = null
                }
                continuation.resume(Result.failure(e))
            }
        }
    }

    private fun readFile(uri: Uri): Result<LocalFileData> {
        return try {
            val contentResolver = activity.contentResolver
            val bytes = contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            } ?: return Result.failure(IllegalStateException("Unable to read the selected file."))

            val fileName = readFileName(uri).ifBlank { "upload.bin" }
            val mimeType = contentResolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" }
            Result.success(LocalFileData(fileName = fileName, mimeType = mimeType, bytes = bytes))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readFileName(uri: Uri): String {
        return activity.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index == -1 || !cursor.moveToFirst()) {
                return@use null
            }
            cursor.getString(index)
        }.orEmpty()
    }
}

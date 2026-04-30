package dev.labstk.homeburrow.files

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
class IosLocalFilePicker(
    private val presentingViewControllerProvider: () -> UIViewController? = { defaultPresentingViewController() },
) : LocalFilePicker {
    private var pendingContinuation: kotlinx.coroutines.CancellableContinuation<Result<LocalFileData>>? = null
    private var activePicker: UIDocumentPickerViewController? = null
    private var activeDelegate: UIDocumentPickerDelegateProtocol? = null

    @Suppress("DEPRECATION")
    override suspend fun pickFile(): Result<LocalFileData> = withContext(Dispatchers.Main.immediate) {
        if (pendingContinuation != null) {
            return@withContext Result.failure(IllegalStateException("A file selection is already in progress."))
        }

        val presenter = presentingViewControllerProvider()
            ?: return@withContext Result.failure(IllegalStateException("Could not present file picker."))

        suspendCancellableCoroutine { continuation ->
            pendingContinuation = continuation

            val pickerDelegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                private fun finish(result: Result<LocalFileData>) {
                    if (pendingContinuation === continuation) {
                        pendingContinuation = null
                    }
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                    cleanup()
                }

                override fun documentPicker(
                    controller: UIDocumentPickerViewController,
                    didPickDocumentsAtURLs: List<*>,
                ) {
                    val selectedUrl = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                        ?: return finish(Result.failure(IllegalStateException("No file was selected.")))
                    finish(readFile(selectedUrl))
                }

                override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                    finish(Result.failure(IllegalStateException("File selection was canceled.")))
                }
            }

            val picker = UIDocumentPickerViewController(
                documentTypes = listOf("public.data"),
                inMode = UIDocumentPickerMode.UIDocumentPickerModeImport,
            ).apply {
                this.delegate = pickerDelegate
                allowsMultipleSelection = false
            }

            activePicker = picker
            activeDelegate = pickerDelegate

            continuation.invokeOnCancellation {
                if (pendingContinuation === continuation) {
                    pendingContinuation = null
                }
                cleanup()
            }

            presenter.presentViewController(picker, animated = true, completion = null)
        }
    }

    private fun readFile(url: NSURL): Result<LocalFileData> {
        return try {
            val data = NSData.dataWithContentsOfURL(url)
                ?: return Result.failure(IllegalStateException("Unable to read the selected file."))

            val fileName = url.lastPathComponent?.ifBlank { null } ?: "upload.bin"
            val mimeType = mimeTypeFromExtension(url.pathExtension)

            Result.success(
                LocalFileData(
                    fileName = fileName,
                    mimeType = mimeType,
                    bytes = data.toByteArray(),
                ),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun cleanup() {
        activePicker?.delegate = null
        activePicker = null
        activeDelegate = null
    }

    private fun mimeTypeFromExtension(pathExtension: String?): String {
        return when (pathExtension?.lowercase()) {
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "json" -> "application/json"
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            else -> "application/octet-stream"
        }
    }
}

private fun defaultPresentingViewController(): UIViewController? =
    UIApplication.sharedApplication.keyWindow?.rootViewController?.topMostPresented()

private fun UIViewController.topMostPresented(): UIViewController =
    presentedViewController?.topMostPresented() ?: this

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) {
        return ByteArray(0)
    }

    val out = ByteArray(length)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, this.length)
    }
    return out
}

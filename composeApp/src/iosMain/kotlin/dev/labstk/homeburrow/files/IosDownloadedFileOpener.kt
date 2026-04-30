package dev.labstk.homeburrow.files

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosDownloadedFileOpener(
    private val presentingViewControllerProvider: () -> UIViewController? = { defaultPresentingViewController() },
) : DownloadedFileOpener {

    override fun openFile(file: LocalFileData): Result<Unit> {
        return try {
            val presenter = presentingViewControllerProvider()
                ?: return Result.failure(IllegalStateException("Could not present file opener."))

            val fileUrl = buildOutputUrl(file.fileName)
            val written = file.bytes.toNSData().writeToURL(fileUrl, atomically = true)
            if (!written) {
                return Result.failure(IllegalStateException("Could not persist downloaded file."))
            }

            val activityController = UIActivityViewController(
                activityItems = listOf<Any>(fileUrl),
                applicationActivities = null,
            )
            presenter.presentViewController(activityController, animated = true, completion = null)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildOutputUrl(fileName: String): NSURL {
        val safeName = sanitizeFileName(fileName)
        val tempPath = NSTemporaryDirectory().ifBlank { "./" }
        val fullPath = "$tempPath${NSUUID().UUIDString}-$safeName"
        return NSURL.fileURLWithPath(fullPath)
    }

    private fun sanitizeFileName(value: String): String {
        return value
            .substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "download.bin" }
    }

    private fun ByteArray.toNSData(): NSData {
        if (isEmpty()) {
            return NSData()
        }
        return usePinned { pinned ->
            NSData.create(
                bytes = pinned.addressOf(0),
                length = size.toULong(),
            )
        }
    }
}

private fun defaultPresentingViewController(): UIViewController? =
    UIApplication.sharedApplication.keyWindow?.rootViewController?.topMostPresented()

private fun UIViewController.topMostPresented(): UIViewController =
    presentedViewController?.topMostPresented() ?: this

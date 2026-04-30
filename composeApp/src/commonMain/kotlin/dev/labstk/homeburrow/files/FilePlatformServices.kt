package dev.labstk.homeburrow.files

data class LocalFileData(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LocalFileData) return false

        return fileName == other.fileName &&
            mimeType == other.mimeType &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

interface LocalFilePicker {
    suspend fun pickFile(): Result<LocalFileData>
}

interface DownloadedFileOpener {
    fun openFile(file: LocalFileData): Result<Unit>
}

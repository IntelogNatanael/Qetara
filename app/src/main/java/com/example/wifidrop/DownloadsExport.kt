package com.example.wifidrop

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File

object DownloadsExport {

    private const val FOLDER_NAME = "Qetara"

    fun exportToDownloads(context: Context, source: File, checkActive: () -> Unit = {}): Result<Uri> {
        return runCatching {
            require(source.exists() && source.isFile) {
                context.getString(R.string.rt_invalid_source_file)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportWithMediaStore(context, source, checkActive)
            } else {
                exportLegacy(source, checkActive)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportWithMediaStore(context: Context, source: File, checkActive: () -> Unit): Uri {
        val resolver = context.contentResolver
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER_NAME/"
        val displayName = uniqueDisplayName(resolver, source.name, relativePath)
        val mime = ReceivedFileMimeTypes.fromName(displayName) ?: "application/octet-stream"

        val values = ContentValues().apply {
            put(MediaStore.DownloadColumns.DISPLAY_NAME, displayName)
            put(MediaStore.DownloadColumns.MIME_TYPE, mime)
            put(MediaStore.DownloadColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.DownloadColumns.IS_PENDING, 1)
        }

        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values)
            ?: error(context.getString(R.string.rt_create_download_failed))

        try {
            resolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        checkActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count > 0) out.write(buffer, 0, count)
                    }
                }
            } ?: error(context.getString(R.string.rt_open_download_failed))

            checkActive()
            val updated = resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.DownloadColumns.IS_PENDING, 0)
                },
                null,
                null
            )
            check(updated > 0) { context.getString(R.string.rt_publish_download_failed) }

            return uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun uniqueDisplayName(
        resolver: android.content.ContentResolver,
        desiredName: String,
        relativePath: String
    ): String {
        val base = desiredName.substringBeforeLast('.', desiredName)
        val ext = desiredName.substringAfterLast('.', "")

        var candidate = desiredName
        var index = 1

        while (downloadNameExists(resolver, candidate, relativePath)) {
            candidate = if (ext.isBlank()) {
                "$base ($index)"
            } else {
                "$base ($index).$ext"
            }
            index++
        }
        return candidate
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun downloadNameExists(
        resolver: android.content.ContentResolver,
        name: String,
        relativePath: String
    ): Boolean {
        val projection = arrayOf(MediaStore.DownloadColumns._ID)
        val selection = "${MediaStore.DownloadColumns.DISPLAY_NAME} = ? AND ${MediaStore.DownloadColumns.RELATIVE_PATH} = ?"
        val args = arrayOf(name, relativePath)

        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            null
        )?.use { cursor ->
            return cursor.moveToFirst()
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun exportLegacy(source: File, checkActive: () -> Unit): Uri {
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(root, FOLDER_NAME).apply { mkdirs() }

        val target = com.example.wifidrop.protocol.copyReceivedFile(source, dir, checkActive = checkActive)
        return Uri.fromFile(target)
    }

}

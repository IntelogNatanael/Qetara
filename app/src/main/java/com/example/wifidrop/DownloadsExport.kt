package com.example.wifidrop

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.net.URLConnection

object DownloadsExport {

    private const val FOLDER_NAME = "WifiDrop"

    fun exportToDownloads(context: Context, source: File): Result<Uri> {
        return runCatching {
            require(source.exists() && source.isFile) {
                "Archivo origen invalido"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportWithMediaStore(context, source)
            } else {
                exportLegacy(source)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportWithMediaStore(context: Context, source: File): Uri {
        val resolver = context.contentResolver
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER_NAME/"
        val displayName = uniqueDisplayName(resolver, source.name, relativePath)
        val mime = URLConnection.guessContentTypeFromName(displayName) ?: "application/octet-stream"

        val values = ContentValues().apply {
            put(MediaStore.DownloadColumns.DISPLAY_NAME, displayName)
            put(MediaStore.DownloadColumns.MIME_TYPE, mime)
            put(MediaStore.DownloadColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.DownloadColumns.IS_PENDING, 1)
        }

        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values)
            ?: error("No pude crear entrada en Descargas")

        try {
            resolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { input ->
                    input.copyTo(out)
                }
            } ?: error("No pude abrir stream de salida en Descargas")

            resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.DownloadColumns.IS_PENDING, 0)
                },
                null,
                null
            )

            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
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
    private fun exportLegacy(source: File): Uri {
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(root, FOLDER_NAME).apply { mkdirs() }

        val target = uniqueFileOnDisk(dir, source.name)
        source.inputStream().use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return Uri.fromFile(target)
    }

    private fun uniqueFileOnDisk(dir: File, desiredName: String): File {
        val base = desiredName.substringBeforeLast('.', desiredName)
        val ext = desiredName.substringAfterLast('.', "")
        var candidate = File(dir, desiredName)
        var i = 1
        while (candidate.exists()) {
            val next = if (ext.isBlank()) "$base ($i)" else "$base ($i).$ext"
            candidate = File(dir, next)
            i++
        }
        return candidate
    }
}

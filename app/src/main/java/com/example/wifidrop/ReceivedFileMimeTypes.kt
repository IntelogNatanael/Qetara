package com.example.wifidrop

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.net.URLConnection
import java.util.Locale

/** MIME metadata controls which apps Android offers; unknown types must not masquerade as PDF. */
internal object ReceivedFileMimeTypes {
    fun fromName(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension.isBlank()) return null
        return chooseReceivedFileMimeType(MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)) {
            URLConnection.guessContentTypeFromName(fileName.lowercase(Locale.ROOT))
        }
    }

    fun resolve(context: Context, uri: Uri, fileName: String? = null): String? {
        val resolver = context.contentResolver
        val reported = runCatching { resolver.getType(uri) }.getOrNull()
        return chooseReceivedFileMimeType(reported) {
            val name = fileName ?: runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
            }.getOrNull() ?: uri.lastPathSegment
            name?.let(::fromName)
        }
    }
}

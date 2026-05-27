package com.example.wifidrop

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.net.URLConnection

object ExternalOpenUtils {

    fun openDownloads(context: Context) {
        val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openRoute(context: Context, route: String?) {
        if (route.isNullOrBlank()) {
            openDownloads(context)
            return
        }

        val uri = runCatching { route.toUri() }.getOrNull()
        if (uri != null && !uri.scheme.isNullOrBlank()) {
            openUri(context, uri)
            return
        }

        val file = File(route)
        if (file.exists() && file.isFile) {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            openUri(context, contentUri, URLConnection.guessContentTypeFromName(file.name) ?: "*/*")
            return
        }

        openDownloads(context)
    }

    private fun openUri(context: Context, uri: Uri, mime: String = "*/*") {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            openDownloads(context)
        }
    }
}

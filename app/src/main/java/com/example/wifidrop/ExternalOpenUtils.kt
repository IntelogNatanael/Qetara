package com.example.wifidrop

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

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
        if (uri != null && !uri.scheme.isNullOrBlank() && uri.scheme != "file") {
            openUri(context, uri)
            return
        }

        // Older Downloads exports store file:// routes. Android 7+ requires a content URI.
        val file = File(if (uri?.scheme == "file") uri.path.orEmpty() else route)
        if (file.exists() && file.isFile) {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            openUri(context, contentUri, file.name)
            return
        }

        openDownloads(context)
    }

    private fun openUri(context: Context, uri: Uri, fileName: String? = null) {
        val mime = ReceivedFileMimeTypes.resolve(context, uri, fileName) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            clipData = ClipData.newRawUri("Archivo de Qetara", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // A chooser keeps the decision with the user even when another app is the default.
        val chooser = Intent.createChooser(intent, "Abrir con").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            openDownloads(context)
        }
    }
}

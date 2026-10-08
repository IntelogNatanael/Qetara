package com.example.wifidrop

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object FileShareUtils {
    fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val mime = ReceivedFileMimeTypes.fromName(file.name) ?: "*/*"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, context.getString(R.string.rt_share_file)))
    }
}

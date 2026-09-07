package com.example.wifidrop

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

/** Keep the saved document's type consistent with the selected received file. */
internal class CreateReceivedDocument : ActivityResultContracts.CreateDocument("application/octet-stream") {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).apply {
            type = ReceivedFileMimeTypes.fromName(input) ?: "application/octet-stream"
        }
}

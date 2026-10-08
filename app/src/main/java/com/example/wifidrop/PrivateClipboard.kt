package com.example.wifidrop

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Parcel
import android.os.PersistableBundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.ClipboardManager as ComposeClipboardManager

/** Marks a copy without changing its items, MIME types, styles, or existing extras. */
// This String key is inlined. PersistableBundle and ClipDescription.setExtras exist at minSdk 24;
// older systems may ignore the preview hint, but do not call an API 33 method to store it.
@SuppressLint("InlinedApi")
private fun sensitiveCopy(clip: ClipData): ClipData {
    // ClipData's copy constructor shares its description. Parcel round-trip preserves the complete
    // transferable clip while keeping this preview hint out of the caller's original metadata.
    val parcel = Parcel.obtain()
    val copy = try {
        clip.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        ClipData.CREATOR.createFromParcel(parcel)
    } finally {
        parcel.recycle()
    }
    copy.description.extras = (copy.description.extras?.let(::PersistableBundle) ?: PersistableBundle()).apply {
        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
    }
    return copy
}

/** Covers Compose selection actions as well as explicit copy buttons within Qetara. */
@Suppress("DEPRECATION")
@Composable
internal fun ProvidePrivateClipboard(content: @Composable () -> Unit) {
    val clipboard = LocalClipboard.current
    val legacyClipboard = LocalClipboardManager.current
    val privateClipboard = remember(clipboard) {
        object : Clipboard by clipboard {
            override suspend fun setClipEntry(clipEntry: ClipEntry?) {
                clipboard.setClipEntry(clipEntry?.let { ClipEntry(sensitiveCopy(it.clipData)) })
            }
        }
    }
    val privateLegacyClipboard = remember(legacyClipboard) {
        object : ComposeClipboardManager by legacyClipboard {
            override fun setClip(clipEntry: ClipEntry?) {
                legacyClipboard.setClip(clipEntry?.let { ClipEntry(sensitiveCopy(it.clipData)) })
            }

            override fun setText(annotatedString: AnnotatedString) {
                // Qetara's legacy text copies are plain text; rich ClipEntry copies stay intact.
                setClip(ClipEntry(ClipData.newPlainText(appString(R.string.rt_qetara_text), annotatedString.text)))
            }
        }
    }
    CompositionLocalProvider(
        LocalClipboard provides privateClipboard,
        LocalClipboardManager provides privateLegacyClipboard,
        content = content
    )
}

/** Requests a private system preview; the receiving app can still read pasted text. */
internal fun copySensitiveText(context: Context, label: String, text: String) {
    val clip = sensitiveCopy(ClipData.newPlainText(label, text))
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(clip)
}

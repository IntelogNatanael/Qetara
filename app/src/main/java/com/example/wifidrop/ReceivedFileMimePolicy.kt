package com.example.wifidrop

import java.util.Locale

/** Preserve concrete provider metadata and use the display name only when it is unavailable. */
internal fun chooseReceivedFileMimeType(reported: String?, fromName: () -> String?): String? =
    concreteReceivedMimeType(reported) ?: concreteReceivedMimeType(fromName())

private fun concreteReceivedMimeType(value: String?): String? {
    val mime = value?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) ?: return null
    val token = "[a-z0-9!#$&^_.+\\-]+"
    if (!mime.matches(Regex("$token/$token"))) return null
    return mime.takeUnless {
        it == "application/octet-stream" || it == "binary/octet-stream" ||
            it == "application/x-unknown-content-type"
    }
}

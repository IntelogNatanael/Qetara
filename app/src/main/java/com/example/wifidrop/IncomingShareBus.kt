package com.example.wifidrop

import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

data class IncomingSharePayload(
    val eventId: Long,
    val uris: List<Uri>
)

object IncomingShareBus {
    private val nextId = AtomicLong(1)

    private val _payload = MutableStateFlow<IncomingSharePayload?>(null)
    val payload: StateFlow<IncomingSharePayload?> = _payload.asStateFlow()

    fun publishFromIntent(intent: Intent?) {
        if (intent == null) return

        val uris = extractUris(intent)
        if (uris.isEmpty()) return

        _payload.value = IncomingSharePayload(
            eventId = nextId.getAndIncrement(),
            uris = uris.distinct()
        )
    }

    fun consume(eventId: Long) {
        if (_payload.value?.eventId == eventId) {
            _payload.value = null
        }
    }

    private fun extractUris(intent: Intent): List<Uri> {
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                listOfNotNull(uri)
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
                }
            }

            else -> emptyList()
        }
    }
}

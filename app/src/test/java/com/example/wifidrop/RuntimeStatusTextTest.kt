package com.example.wifidrop

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeStatusTextTest : LocalizedResourcesTest() {
    @Test fun retainedChannelRecipientCountsAndChannelNamesFollowBothLanguageChanges() {
        for ((source, target) in listOf("es" to "en", "en" to "es")) {
            for (quantity in listOf(1, 2)) {
                useAppLocale(source)
                val retained = TransferRuntimeState(
                    messageStatus = appQuantityString(
                        R.plurals.rt_channel_message_published_for, quantity,
                        appString(R.string.rt_direct_channel), quantity
                    )
                )

                useAppLocale(target)
                val expected = if (target == "en") {
                    "Message posted to Wi‑Fi Direct channel for $quantity ${if (quantity == 1) "device" else "devices"}."
                } else {
                    "Mensaje publicado en Canal Wi‑Fi Direct para $quantity ${if (quantity == 1) "equipo" else "equipos"}."
                }
                assertEquals("$source → $target, quantity=$quantity", expected, localizeRuntimeStatus(retained.messageStatus))
            }
        }
    }

    @Test fun genericChannelPublicationStillTranslatesItsNestedChannelName() {
        for ((source, target) in listOf("es" to "en", "en" to "es")) {
            useAppLocale(source)
            val retained = appString(R.string.rt_channel_message_published, appString(R.string.rt_direct_channel))
            useAppLocale(target)

            assertEquals(
                appString(R.string.rt_channel_message_published, appString(R.string.rt_direct_channel)),
                localizeRuntimeStatus(retained)
            )
        }
    }

    @Test fun retainedPendingCountsKeepRetryDelayAndSingularOrPlural() {
        for ((source, target) in listOf("es" to "en", "en" to "es")) {
            for (quantity in listOf(1, 2)) {
                useAppLocale(source)
                val pending = appQuantityString(R.plurals.rt_pending_messages, quantity, quantity)
                val retrying = appQuantityString(R.plurals.rt_pending_messages_retry, quantity, quantity, 7L)
                useAppLocale(target)

                assertEquals(appQuantityString(R.plurals.rt_pending_messages, quantity, quantity), localizeRuntimeStatus(pending))
                assertEquals(
                    appQuantityString(R.plurals.rt_pending_messages_retry, quantity, quantity, 7L),
                    localizeRuntimeStatus(retrying)
                )
            }
        }
    }

    @Test fun retainedRuntimeErrorsTranslateNestedCopyWithoutTranslatingFileNames() {
        val fileName = "Canal Wi‑Fi Direct.pdf"
        for ((source, target) in listOf("es" to "en", "en" to "es")) {
            useAppLocale(source)
            val retained = TransferRuntimeState(
                sendStatus = appString(R.string.rt_send_file_error, fileName, runtimeFailureText("noise_key_mismatch")),
                receiverStatus = appString(R.string.rt_file_received, fileName, appString(R.string.rt_saved_downloads)),
                messageStatus = appString(
                    R.string.rt_message_send_error,
                    runtimeFailureText("Receptor rechazo mensaje: grupo_direct_renovado")
                )
            )
            useAppLocale(target)

            assertEquals(
                appString(R.string.rt_send_file_error, fileName, appString(R.string.rt_error_noise_key_changed)),
                localizeRuntimeStatus(retained.sendStatus)
            )
            assertEquals(
                appString(R.string.rt_file_received, fileName, appString(R.string.rt_saved_downloads)),
                localizeRuntimeStatus(retained.receiverStatus)
            )
            assertEquals(
                appString(
                    R.string.rt_message_send_error,
                    appString(R.string.rt_error_message_rejected, appString(R.string.rt_error_direct_group_changed))
                ),
                localizeRuntimeStatus(retained.messageStatus)
            )
        }
    }

    @Test fun specificStatusesRemainDistinctFromOverlappingSharedFileAndChannelTemplates() {
        val credentials = R.string.rt_credentials_shared
        val exactErrors = listOf(
            R.string.rt_invalid_session_token, R.string.rt_invalid_session_pin,
            R.string.rt_invalid_message_token, R.string.rt_invalid_message_pin,
            R.string.rt_invalid_send_token, R.string.rt_invalid_send_pin
        )
        for ((source, target) in listOf("es" to "en", "en" to "es")) {
            useAppLocale(source)
            val shared = appString(credentials, "Alice", "192.168.49.2")
            val retainedErrors = exactErrors.associateWith { appString(it) }
            useAppLocale(target)

            assertEquals(appString(credentials, "Alice", "192.168.49.2"), localizeRuntimeStatus(shared))
            for ((id, retained) in retainedErrors) assertEquals(appString(id), localizeRuntimeStatus(retained))
        }
    }

    @Test fun deviceNamesAndUnknownTextArePreservedAcrossBothLanguageChanges() {
        val deviceName = "Mensaje enviado"
        val unknown = "User-defined status: Mensaje enviado"
        for ((source, target) in listOf("es" to "en", "en" to "es")) {
            useAppLocale(source)
            val retained = appString(R.string.rt_sending_message_to, deviceName)
            useAppLocale(target)

            assertEquals(appString(R.string.rt_sending_message_to, deviceName), localizeRuntimeStatus(retained))
            assertEquals(unknown, localizeRuntimeStatus(unknown))
        }
    }

    @Test fun numericCountersAndDelaysRerenderWhilePortsAndCodesKeepTheirDigits() {
        val host = "192.168.49.2"
        val port = 8988.toString()
        val reason = (-1).toString()
        for ((source, target) in listOf("es" to "en", "en" to "es")) {
            useAppLocale(source)
            val batch = appString(R.string.rt_batch_finished, 3, 2, 1)
            val retry = appString(R.string.rt_connection_retry_in, "Alice", 7L)
            val fileSent = appString(R.string.rt_file_sent_address, host, port)
            val messageSent = appString(R.string.rt_message_sent_address, host, port)
            val listening = appString(R.string.rt_waiting_files_port, port)
            val wifiFailure = appString(R.string.rt_wifi_search_failed, appString(R.string.rt_wifi_unknown_error, reason))
            useAppLocale(target)

            assertEquals(appString(R.string.rt_batch_finished, 3, 2, 1), localizeRuntimeStatus(batch))
            assertEquals(appString(R.string.rt_connection_retry_in, "Alice", 7L), localizeRuntimeStatus(retry))
            assertEquals(appString(R.string.rt_file_sent_address, host, port), localizeRuntimeStatus(fileSent))
            assertEquals(appString(R.string.rt_message_sent_address, host, port), localizeRuntimeStatus(messageSent))
            assertEquals(appString(R.string.rt_waiting_files_port, port), localizeRuntimeStatus(listening))
            assertEquals(
                appString(R.string.rt_wifi_search_failed, appString(R.string.rt_wifi_unknown_error, reason)),
                localizeRuntimeStatus(wifiFailure)
            )
        }
    }
}

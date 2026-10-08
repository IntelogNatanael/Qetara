package com.example.wifidrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferNotificationTextTest : com.example.wifidrop.LocalizedResourcesTest() {
    @Test fun listeningWithoutFileDoesNotClaimAnActiveReceive() {
        val state = TransferRuntimeState(serviceRunning = true, receiverListening = true)
        assertEquals("Esperando archivos.", buildTransferNotificationText(state))
    }

    @Test fun actualFileReceiveReportsItsProgress() {
        val state = TransferRuntimeState(
            serviceRunning = true, receiverListening = true, receiving = true,
            receiverFileName = "document.pdf", receiverProgress = 0.5f
        )
        assertTrue(buildTransferNotificationText(state).startsWith("Recibiendo 50%"))
    }

    @Test fun existingTransferNotificationsFollowTheCurrentLanguage() {
        val waiting = TransferRuntimeState(serviceRunning = true, receiverListening = true)
        val receiving = waiting.copy(receiving = true, receiverProgress = 0.5f)

        useAppLocale("en")
        assertEquals("Waiting for files.", buildTransferNotificationText(waiting))
        assertEquals("Receiving 50% · 0 B/s", buildTransferNotificationText(receiving))

        useAppLocale("es")
        assertEquals("Esperando archivos.", buildTransferNotificationText(waiting))
        assertEquals("Recibiendo 50% · 0 B/s", buildTransferNotificationText(receiving))

        useAppLocale("en")
        assertEquals("Waiting for files.", buildTransferNotificationText(waiting))
    }
}

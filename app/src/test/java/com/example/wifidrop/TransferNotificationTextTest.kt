package com.example.wifidrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferNotificationTextTest {
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
}

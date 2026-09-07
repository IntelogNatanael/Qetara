package com.example.wifidrop.pc

import com.example.wifidrop.protocol.flash.FlashProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopFlashProgressTest {
    @Test
    fun thousandsOfChunksProduceOneLatestUpdatePerOperation() {
        val progress = DesktopFlashProgressBuffer()
        repeat(10_000) { progress.offer(1, FlashProgress("file-a", it.toLong(), 10_000, true)) }
        progress.offer(1, FlashProgress("file-b", 500, 1_000, false))
        val tick = progress.drain()
        assertEquals(2, tick.size)
        assertEquals(9_999L, tick.first { it.second.operationId == "file-a" }.second.transferredBytes)
        assertEquals(500L, tick.first { it.second.operationId == "file-b" }.second.transferredBytes)
        assertTrue(progress.drain().isEmpty())
    }

    @Test
    fun lateProgressFromAnOldActivationCannotReplaceTheNewActivation() {
        val progress = DesktopFlashProgressBuffer()
        progress.offer(2, FlashProgress("same-id", 50, 100, true))
        progress.offer(1, FlashProgress("same-id", 99, 100, true))
        assertEquals(50L, progress.drain().single { it.first == 2L }.second.transferredBytes)
        progress.offer(2, FlashProgress("same-id", 60, 100, true))
        progress.clear()
        assertTrue(progress.drain().isEmpty())
    }
}

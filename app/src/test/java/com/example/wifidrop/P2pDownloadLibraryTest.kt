package com.example.wifidrop

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pDownloadLibraryTest {
    @Test
    fun searchFindsSpanishNamesWithoutRequiringAccentsOrCase() {
        val files = listOf(file("Revisión final.pdf", 80, 10), file("foto.png", 30, 20))
        assertEquals(listOf("Revisión final.pdf"), filterDownloadFiles(files, "  REVISION  ", DownloadFileSort.NEWEST).map { it.file.name })
    }

    @Test
    fun sortAppliesAfterSearchAndIncludesAllMatchingFiles() {
        val files = (1..30).map { file("Informe $it.pdf", it.toLong(), (31 - it).toLong()) }
        assertEquals(30, filterDownloadFiles(files, "informe", DownloadFileSort.NEWEST).size)
        assertEquals("Informe 1.pdf", filterDownloadFiles(files, "informe", DownloadFileSort.NEWEST).first().file.name)
        assertEquals("Informe 30.pdf", filterDownloadFiles(files, "informe", DownloadFileSort.LARGEST).first().file.name)
    }

    @Test
    fun attentionFilterCombinesWithPeerSearchAndExcludesSuccessfulTransfers() {
        val history = listOf(
            entry("success", TransferDirection.RECEIVED, TransferOutcome.SUCCESS, "Portátil José", 30),
            entry("failed", TransferDirection.RECEIVED, TransferOutcome.FAILED, "Portátil José", 20),
            entry("canceled", TransferDirection.SENT, TransferOutcome.CANCELED, "Portátil José", 10),
            entry("other", TransferDirection.SENT, TransferOutcome.FAILED, "Otro equipo", 40)
        )
        assertEquals(listOf("failed", "canceled"), filterDownloadHistory(history, "JOSE", DownloadHistoryFilter.ATTENTION).map { it.id })
    }

    @Test
    fun directionFiltersKeepFailureStatesAndOrderNewestFirst() {
        val history = listOf(
            entry("old", TransferDirection.RECEIVED, TransferOutcome.SUCCESS, "Equipo", 10),
            entry("sent", TransferDirection.SENT, TransferOutcome.FAILED, "Equipo", 30),
            entry("recent", TransferDirection.RECEIVED, TransferOutcome.FAILED, "Equipo", 20)
        )
        assertEquals(listOf("recent", "old"), filterDownloadHistory(history, "", DownloadHistoryFilter.RECEIVED).map { it.id })
        assertEquals(listOf("sent"), filterDownloadHistory(history, "192.168.1.4", DownloadHistoryFilter.SENT).map { it.id })
    }

    @Test
    fun emptySearchCanBeClearedWithoutLosingOriginalLibrary() {
        val files = listOf(file("Documento.txt", 12, 5))
        assertTrue(filterDownloadFiles(files, "missing", DownloadFileSort.NAME).isEmpty())
        assertEquals(files, filterDownloadFiles(files, "  ", DownloadFileSort.NAME))
    }

    private fun file(name: String, size: Long, date: Long) = DownloadFileSnapshot(File(name), size, date)

    private fun entry(id: String, direction: TransferDirection, outcome: TransferOutcome, peer: String, time: Long) = TransferHistoryEntry(
        id = id,
        direction = direction,
        fileName = "Documento.txt",
        bytes = 1024,
        outcome = outcome,
        timestampMs = time,
        peerLabel = peer,
        peerIp = "192.168.1.4",
        route = null,
        errorCause = if (outcome == TransferOutcome.FAILED) "timeout" else null
    )
}

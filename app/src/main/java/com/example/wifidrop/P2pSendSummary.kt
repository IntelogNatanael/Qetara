package com.example.wifidrop

internal enum class P2pSendSummaryKind { ACTIVE, SUCCESS, ATTENTION }

internal data class P2pSendSummary(
    val kind: P2pSendSummaryKind,
    val title: String,
    val detail: String
)

/** Completed counters are incremented only after the receiver acknowledges the verified file. */
internal fun buildP2pSendSummary(
    total: Int,
    completed: Int,
    failed: Int,
    canceled: Int,
    sending: Boolean,
    paused: Boolean
): P2pSendSummary? {
    if (total <= 0) return null
    val finished = !sending && completed + failed + canceled >= total
    if (!finished) return P2pSendSummary(
        P2pSendSummaryKind.ACTIVE,
        if (paused) "Envío en pausa" else "Envío en curso",
        "$completed de $total archivos entregados"
    )
    if (failed == 0 && canceled == 0 && completed == total) return P2pSendSummary(
        P2pSendSummaryKind.SUCCESS,
        "Último envío completado",
        if (completed == 1) "1 archivo entregado" else "$completed archivos entregados"
    )
    return P2pSendSummary(
        P2pSendSummaryKind.ATTENTION,
        "Último envío incompleto",
        buildList {
            if (completed > 0) add("$completed entregados")
            if (failed > 0) add("$failed fallidos")
            if (canceled > 0) add("$canceled cancelados")
        }.joinToString(" · ")
    )
}

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
        if (paused) appString(R.string.msg_send_paused) else appString(R.string.msg_send_in_progress),
        appQuantityString(R.plurals.msg_delivered_progress, total, completed, total)
    )
    if (failed == 0 && canceled == 0 && completed == total) return P2pSendSummary(
        P2pSendSummaryKind.SUCCESS,
        appString(R.string.msg_last_send_complete),
        appQuantityString(R.plurals.msg_files_delivered_count, completed, completed)
    )
    return P2pSendSummary(
        P2pSendSummaryKind.ATTENTION,
        appString(R.string.msg_last_send_incomplete),
        buildList {
            if (completed > 0) add(appQuantityString(R.plurals.msg_delivered_count, completed, completed))
            if (failed > 0) add(appQuantityString(R.plurals.msg_failed_count, failed, failed))
            if (canceled > 0) add(appQuantityString(R.plurals.msg_canceled_count, canceled, canceled))
        }.joinToString(" · ")
    )
}

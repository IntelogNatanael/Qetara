package com.example.wifidrop

import com.example.wifidrop.presentation.localizeKnownStatus
import com.example.wifidrop.presentation.matchesLocalizedStatus

/** Re-render only complete application-owned status templates, preserving names and file content. */
internal fun localizeRuntimeStatus(status: String): String =
    localizeReceivedFileStatus(status) ?:
    localizeKnownStatus(status, runtimeStatusStrings, runtimeStatusPlurals) { resource, index, argument ->
        when {
            index == 0 && resource in runtimeFailureArgumentFirst -> runtimeFailureText(argument)
            index == 1 && resource in runtimeFailureArgumentSecond -> runtimeFailureText(argument)
            resource == R.string.rt_file_received && index == 1 -> localizeRuntimeStatus(argument)
            index == 0 && resource in runtimeWifiReasonArgument ->
                localizeKnownStatus(argument, runtimeWifiReasonStrings)
            index == 0 && resource in runtimeChannelArgument ->
                localizeKnownStatus(argument, runtimeChannelStrings)
            else -> argument
        }
    }

/**
 * A filename can contain the ". " separating it from the export result. Locate that boundary
 * using the complete known export copy; splitting at the first punctuation mark loses the name.
 * Ambiguous legacy text stays intact instead of guessing which part belongs to the file.
 */
private fun localizeReceivedFileStatus(status: String): String? {
    if (status.isBlank()) return null
    val candidates = linkedSetOf<Pair<String, String>>()
    var receivedFileStatus = false
    for (template in appStringVariants(R.string.rt_file_received)) {
        val filePlaceholder = "%1\$s"
        val exportPlaceholder = "%2\$s"
        val fileIndex = template.indexOf(filePlaceholder)
        val exportIndex = template.indexOf(exportPlaceholder)
        if (fileIndex < 0 || exportIndex <= fileIndex) continue
        val prefix = template.substring(0, fileIndex)
        val separator = template.substring(fileIndex + filePlaceholder.length, exportIndex)
        val suffix = template.substring(exportIndex + exportPlaceholder.length)
        if (separator.isEmpty() || !status.startsWith(prefix) || !status.endsWith(suffix)) continue
        receivedFileStatus = true
        val body = status.removePrefix(prefix).removeSuffix(suffix)
        var boundary = body.indexOf(separator)
        while (boundary >= 0) {
            val exportStatus = body.substring(boundary + separator.length)
            if (matchesLocalizedStatus(exportStatus, R.string.rt_saved_downloads) ||
                matchesLocalizedStatus(exportStatus, R.string.rt_copy_downloads_failed)) {
                candidates += body.substring(0, boundary) to exportStatus
            }
            boundary = body.indexOf(separator, boundary + separator.length)
        }
    }
    if (!receivedFileStatus) return null
    val (fileName, exportStatus) = candidates.singleOrNull() ?: return status
    return appString(R.string.rt_file_received, fileName, localizeRuntimeStatus(exportStatus))
}

private val runtimeFailureArgumentFirst = setOf(
    R.string.rt_copy_downloads_failed, R.string.rt_receiver_error_retrying,
    R.string.rt_message_send_error, R.string.rt_connection_closed
)
private val runtimeFailureArgumentSecond = setOf(
    R.string.rt_send_file_error, R.string.rt_connection_rejected_from,
    R.string.rt_client_processing_error
)
private val runtimeWifiReasonArgument = setOf(
    R.string.rt_wifi_search_failed, R.string.rt_wifi_join_failed, R.string.rt_wifi_cancel_failed,
    R.string.rt_wifi_create_failed, R.string.rt_wifi_close_failed
)
private val runtimeWifiReasonStrings = intArrayOf(
    R.string.rt_wifi_unsupported, R.string.rt_wifi_busy, R.string.rt_wifi_error, R.string.rt_wifi_unknown_error
)
private val runtimeChannelArgument = setOf(
    R.string.rt_invalid_channel_token, R.string.rt_invalid_channel_pin,
    R.string.rt_channel_message_published, R.plurals.rt_channel_message_published_for
)
private val runtimeChannelStrings = intArrayOf(R.string.rt_channel, R.string.rt_direct_channel, R.string.rt_global_lan)

private val runtimeStatusStrings = intArrayOf(
    R.string.rt_service_ready,
    R.string.rt_invalid_session_token,
    R.string.rt_invalid_token,
    R.string.rt_invalid_session_pin,
    R.string.rt_invalid_pin,
    R.string.rt_session_expired_renew,
    R.string.rt_session_expired,
    R.string.rt_invalid_receive_directory,
    R.string.rt_invalid_directory,
    R.string.rt_preparing_session,
    R.string.rt_preparing_receive,
    R.string.rt_preparing_peer_connection,
    R.string.rt_connection_request_verify,
    R.string.rt_confirm_connection,
    R.string.rt_credentials_shared,
    R.string.rt_untrusted_device,
    R.string.rt_confirm_device,
    R.string.rt_receiving_file,
    R.string.rt_saved_downloads,
    R.string.rt_copy_downloads_failed,
    R.string.rt_file_received,
    R.string.rt_received_file,
    R.string.rt_receiver_error_retrying,
    R.string.rt_receiver_retry,
    R.string.rt_receiver_stopped,
    R.string.rt_missing_file_uri,
    R.string.rt_empty_destination_ip,
    R.string.rt_invalid_send_token,
    R.string.rt_invalid_send_pin,
    R.string.rt_file_queued,
    R.string.rt_sending_files,
    R.string.rt_empty_message_destination_ip,
    R.string.rt_invalid_message_token,
    R.string.rt_invalid_message_pin,
    R.string.rt_empty_message,
    R.string.rt_message_queued_for,
    R.string.rt_global_lan,
    R.string.rt_direct_channel,
    R.string.rt_channel,
    R.string.rt_invalid_channel_token,
    R.string.rt_invalid_channel_pin,
    R.string.rt_channel_message_published,
    R.string.rt_channel_message_sending,
    R.string.rt_retry_scheduled,
    R.string.rt_retry_message_missing,
    R.string.rt_message_missing_ip,
    R.string.rt_retry_session_invalid,
    R.string.rt_message_requeued,
    R.string.rt_message_canceled,
    R.string.rt_message_not_queued,
    R.string.rt_message_deleted,
    R.string.rt_direct_chat_cleared,
    R.string.rt_global_lan_cleared,
    R.string.rt_direct_channel_cleared,
    R.string.rt_messages_deleted,
    R.string.rt_send_success,
    R.string.rt_send_file_canceled,
    R.string.rt_send_canceled,
    R.string.rt_retrying_file,
    R.string.rt_send_retry,
    R.string.rt_send_file_error,
    R.string.rt_send_error,
    R.string.rt_batch_finished,
    R.string.rt_global_peer,
    R.string.rt_global_ready_for,
    R.string.rt_message_received_from,
    R.string.rt_relayed_message_received_from,
    R.string.rt_device,
    R.string.rt_file_shared,
    R.string.rt_channel_peer,
    R.string.rt_device_trusted_name,
    R.string.rt_device_trusted,
    R.string.rt_device_forgotten,
    R.string.rt_request_changed,
    R.string.rt_identity_changed,
    R.string.rt_connection_approved_for,
    R.string.rt_connection_authorized,
    R.string.rt_connection_request_rejected_for,
    R.string.rt_connection_request_rejected,
    R.string.rt_peer_favorited,
    R.string.rt_peer_unfavorited,
    R.string.rt_alias_unchanged,
    R.string.rt_alias_updated,
    R.string.rt_queue_paused,
    R.string.rt_queue_resumed,
    R.string.rt_queue_item_canceled,
    R.string.rt_queue_reordered,
    R.string.rt_send_paused,
    R.string.rt_receiver_paused,
    R.string.rt_canceling_sends,
    R.string.rt_canceling_receive,
    R.string.rt_canceling_transfer,
    R.string.rt_sending_message_to,
    R.string.rt_message_sent_to,
    R.string.rt_message_sent,
    R.string.rt_connection_retry_in,
    R.string.rt_message_queued,
    R.string.rt_message_send_error,
    R.string.rt_message_error,
    R.string.rt_closing_session,
    R.string.rt_session_stopped,
    R.string.rt_session_stopped_notification,
    R.string.rt_resume,
    R.string.rt_pause,
    R.string.rt_cancel,
    R.string.rt_downloads,
    R.string.rt_active_title,
    R.string.rt_transfer_channel_name,
    R.string.rt_transfer_channel_description,
    R.string.rt_wifi_unavailable_device,
    R.string.rt_wifi_ready,
    R.string.rt_wifi_permission_missing,
    R.string.rt_wifi_enabled,
    R.string.rt_wifi_disabled,
    R.string.rt_unnamed,
    R.string.rt_wifi_searching,
    R.string.rt_wifi_search_failed,
    R.string.rt_wifi_no_peers,
    R.string.rt_wifi_invalid_peer_address,
    R.string.rt_wifi_joining,
    R.string.rt_wifi_join_failed,
    R.string.rt_wifi_search_canceled,
    R.string.rt_wifi_cancel_failed,
    R.string.rt_wifi_group_created,
    R.string.rt_wifi_create_failed,
    R.string.rt_wifi_group_closed,
    R.string.rt_wifi_close_failed,
    R.string.rt_wifi_unavailable,
    R.string.rt_wifi_permission_denied,
    R.string.rt_wifi_unsupported,
    R.string.rt_wifi_busy,
    R.string.rt_wifi_error,
    R.string.rt_wifi_unknown_error,
    R.string.rt_ready,
    R.string.rt_receiver_inactive,
    R.string.rt_direct_host,
    R.string.rt_qetara_file,
    R.string.rt_open_with,
    R.string.rt_share_file,
    R.string.rt_qetara_text,
    R.string.rt_invalid_source_file,
    R.string.rt_create_download_failed,
    R.string.rt_open_download_failed,
    R.string.rt_publish_download_failed,
    R.string.rt_file_sent_address,
    R.string.rt_message_sent_address,
    R.string.rt_waiting_files_port,
    R.string.rt_transfer_canceled_by_user,
    R.string.rt_connection_rejected_from,
    R.string.rt_client_processing_error,
    R.string.rt_connection_closed,
    R.string.rt_file_requested,
    R.string.rt_transfers_paused,
    R.string.rt_waiting_files,
    R.string.rt_qetara_ready
)

private val runtimeStatusPlurals = intArrayOf(
    R.plurals.rt_sending_files,
    R.plurals.rt_pending_files,
    R.plurals.rt_queued_messages,
    R.plurals.rt_pending_messages,
    R.plurals.rt_pending_messages_retry,
    R.plurals.rt_channel_message_published_for,
    R.plurals.rt_wifi_peers_ready
)

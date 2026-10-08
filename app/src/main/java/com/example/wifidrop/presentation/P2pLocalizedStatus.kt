package com.example.wifidrop.presentation

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.example.wifidrop.R
import com.example.wifidrop.appQuantityString
import com.example.wifidrop.appQuantityStringVariants
import com.example.wifidrop.appString
import com.example.wifidrop.appStringVariants
import java.util.concurrent.ConcurrentHashMap

private data class StatusTemplate(
    val expression: Regex,
    val arguments: List<Pair<Int, Char>>,
    val specificity: Int
)

private data class StatusMatch(
    val resourceId: Int,
    val arguments: Array<Any>,
    val quantity: Int?,
    val specificity: Int
)

private val stringStatusTemplates = ConcurrentHashMap<Int, List<StatusTemplate>>()
private val quantityStatusTemplates = ConcurrentHashMap<Int, List<StatusTemplate>>()

/** Matches complete application-owned copy, never substrings of device names or messages. */
private fun statusTemplate(template: String): StatusTemplate {
    val placeholders = Regex("%(?:(\\d+)\\$)?([sd])")
    val pattern = StringBuilder("^")
    val arguments = mutableListOf<Pair<Int, Char>>()
    var cursor = 0
    placeholders.findAll(template).forEachIndexed { index, match ->
        pattern.append(Regex.escape(template.substring(cursor, match.range.first).replace("%%", "%")))
        val kind = match.groupValues[2].single()
        pattern.append(if (kind == 'd') "([0-9]+)" else "(.*?)")
        arguments += (match.groupValues[1].toIntOrNull()?.minus(1) ?: index) to kind
        cursor = match.range.last + 1
    }
    pattern.append(Regex.escape(template.substring(cursor).replace("%%", "%"))).append("$")
    val specificity = if (arguments.isEmpty()) Int.MAX_VALUE else placeholders.replace(template, "").length
    return StatusTemplate(Regex(pattern.toString(), RegexOption.DOT_MATCHES_ALL), arguments, specificity)
}

private fun stringTemplates(@StringRes resourceId: Int): List<StatusTemplate> =
    stringStatusTemplates.getOrPut(resourceId) { appStringVariants(resourceId).map(::statusTemplate) }

private fun quantityTemplates(@PluralsRes resourceId: Int): List<StatusTemplate> =
    quantityStatusTemplates.getOrPut(resourceId) {
        (appQuantityStringVariants(resourceId, 1) + appQuantityStringVariants(resourceId, 2))
            .distinct().map(::statusTemplate)
    }

private fun StatusTemplate.argumentsFor(status: String): Array<Any>? {
    val result = expression.matchEntire(status) ?: return null
    val output = arrayOfNulls<Any>((arguments.maxOfOrNull { it.first } ?: -1) + 1)
    arguments.forEachIndexed { index, (position, kind) ->
        val value = result.groupValues[index + 1]
        output[position] = if (kind == 'd') value.toLongOrNull() ?: return null else value
    }
    return output.map { it ?: return null }.toTypedArray()
}

fun matchesLocalizedStatus(status: String, @StringRes resourceId: Int): Boolean =
    status.isNotBlank() && stringTemplates(resourceId).any { it.expression.matches(status.trim()) }

fun matchesLocalizedQuantityStatus(status: String, @PluralsRes resourceId: Int): Boolean =
    status.isNotBlank() && quantityTemplates(resourceId).any { it.expression.matches(status.trim()) }

/**
 * Re-renders only the supplied, application-owned status templates after a language change.
 * Inputs not in the catalog (including user content and protocol codes) pass through untouched.
 * Callers explicitly identify which arguments contain nested copy, rather than translating names.
 */
fun localizeKnownStatus(
    status: String,
    stringIds: IntArray,
    pluralIds: IntArray = intArrayOf(),
    localizeArgument: (resourceId: Int, argumentIndex: Int, value: String) -> String = { _, _, value -> value }
): String {
    if (status.isBlank()) return status
    fun localizedArguments(id: Int, args: Array<Any>): Array<Any> =
        args.mapIndexed { index, argument ->
            if (argument is String) localizeArgument(id, index, argument) else argument
        }.toTypedArray()
    // Compare both catalogs before rendering. For example, "Posted to %s." also matches
    // "Posted to %s for %d devices."; its unconstrained argument must not swallow the count.
    var selected: StatusMatch? = null
    for (id in stringIds) {
        for (template in stringTemplates(id)) {
            val args = template.argumentsFor(status) ?: continue
            if (template.specificity > (selected?.specificity ?: -1)) {
                selected = StatusMatch(id, args, null, template.specificity)
            }
        }
    }
    for (id in pluralIds) {
        for (template in quantityTemplates(id)) {
            val args = template.argumentsFor(status) ?: continue
            val quantity = args.filterIsInstance<Number>().firstOrNull()?.toInt() ?: continue
            if (template.specificity > (selected?.specificity ?: -1)) {
                selected = StatusMatch(id, args, quantity, template.specificity)
            }
        }
    }
    val match = selected ?: return status
    val args = localizedArguments(match.resourceId, match.arguments)
    return match.quantity?.let { appQuantityString(match.resourceId, it, *args) }
        ?: appString(match.resourceId, *args)
}

private val sessionFailureCopy = intArrayOf(
    R.string.pr_sync_auto_failed_manual,
    R.string.pr_sync_auto_failed,
    R.string.pr_sync_host_missing,
    R.string.pr_sync_invalid_peer,
    R.string.pr_session_review,
    R.string.pr_error_session_closed,
    R.string.pr_error_direct_unconfirmed,
    R.string.pr_error_direct_changed,
    R.string.pr_error_direct_outside,
    R.string.pr_error_manual_pairing,
    R.string.pr_error_approval_required,
    R.string.pr_error_untrusted,
    R.string.pr_error_identity_changed,
    R.string.pr_error_credentials,
    R.string.pr_error_expired,
    R.string.pr_error_permissions,
    R.string.pr_error_refused,
    R.string.pr_error_no_route,
    R.string.pr_error_timeout,
    R.string.pr_error_interrupted,
    R.string.pr_error_integrity,
    R.string.pr_error_canceled,
    R.string.pr_error_connection,
    R.string.pr_error_sync
)

private val otherFailureCopy = intArrayOf(
    R.string.pr_file_required,
    R.string.pr_message_required,
    R.string.pr_message_or_file_required,
    R.string.pr_channel_wifi_required,
    R.string.pr_channel_join_required,
    R.string.pr_channel_no_recipients,
    R.string.pr_invalid_self_target,
    R.string.pr_session_invalid,
    R.string.pr_pin_six_digits,
    R.string.pr_last_target_missing,
    R.string.pr_direct_host_file_limit,
    R.string.pr_direct_client_file_limit,
    R.string.pr_clipboard_empty,
    R.string.pr_scan_range_missing,
    R.string.pr_scan_wifi_required,
    R.string.pr_scan_canceled,
    R.string.pr_target_missing_action,
    R.string.pr_permissions_action,
    R.string.pr_connect_action,
    R.string.pr_target_missing_hint,
    R.string.pr_expired_action,
    R.string.pr_wifi_settings_failed,
    R.string.pr_file_details_missing,
    R.string.pr_sender_ip_missing,
    R.string.pr_download_renew_session,
    R.string.pr_enable_session_hint
)

fun isSessionSyncManualPairingRequired(status: String): Boolean =
    matchesLocalizedStatus(status, R.string.pr_error_manual_pairing) ||
        status.trim() in setOf("secure_credentials_required", "emparejamiento_manual_requerido")

fun isSessionSyncApprovalRequired(status: String): Boolean =
    matchesLocalizedStatus(status, R.string.pr_error_approval_required) ||
        status.trim() == "confirmacion_host_requerida"

fun isSessionSyncRetrying(status: String): Boolean =
    matchesLocalizedStatus(status, R.string.pr_sync_retry)

fun isSessionSyncProgress(status: String): Boolean =
    isSessionSyncRetrying(status) || intArrayOf(
        R.string.pr_sync_start, R.string.pr_sync_auto_start,
        R.string.pr_sync_busy, R.string.pr_session_connecting
    ).any { matchesLocalizedStatus(status, it) }

fun isSessionSyncFailure(status: String): Boolean =
    sessionFailureCopy.any { matchesLocalizedStatus(status, it) }

fun isSessionSyncConfirmed(status: String): Boolean = intArrayOf(
    R.string.pr_sync_success, R.string.pr_sync_already_success,
    R.string.pr_session_confirmed, R.string.pr_session_prepared
).any { matchesLocalizedStatus(status, it) }

fun isMessageStatusFailure(status: String): Boolean = intArrayOf(
    R.string.rt_empty_message_destination_ip, R.string.rt_invalid_message_token,
    R.string.rt_invalid_message_pin, R.string.rt_empty_message,
    R.string.rt_invalid_channel_token, R.string.rt_invalid_channel_pin,
    R.string.rt_retry_message_missing, R.string.rt_message_missing_ip,
    R.string.rt_retry_session_invalid, R.string.rt_message_send_error
).any { matchesLocalizedStatus(status, it) }

fun isMessageStatusSending(status: String): Boolean =
    matchesLocalizedStatus(status, R.string.rt_sending_message_to)

internal fun isKnownPresentationFailure(status: String): Boolean =
    isSessionSyncFailure(status) || isIncompleteAttachmentRecovery(status) ||
        otherFailureCopy.any { matchesLocalizedStatus(status, it) } ||
        isMessageStatusFailure(status)

private val presentationStatusCopy = intArrayOf(
    R.string.pr_undo,
    R.string.pr_delete_prompt,
    R.string.pr_delete_canceled,
    R.string.pr_message_deleted,
    R.string.pr_direct_chat_scope,
    R.string.pr_channel_scope,
    R.string.pr_direct_chat_cleared,
    R.string.pr_channel_cleared,
    R.string.pr_clear_canceled,
    R.string.pr_favorite_removed,
    R.string.pr_favorite_restored,
    R.string.pr_alias_unchanged,
    R.string.pr_alias_removed,
    R.string.pr_alias_updated,
    R.string.pr_scan_canceled,
    R.string.pr_scan_wifi_required,
    R.string.pr_scanning,
    R.string.pr_scanning_auto,
    R.string.pr_scan_range_missing,
    R.string.pr_scan_empty,
    R.string.pr_client,
    R.string.pr_device,
    R.string.pr_file_required,
    R.string.pr_send_files_action,
    R.string.pr_send_messages_action,
    R.string.pr_send_message_files_action,
    R.string.pr_message_required,
    R.string.pr_message_queued,
    R.string.pr_message_or_file_required,
    R.string.pr_channel_wifi_required,
    R.string.pr_channel_join_required,
    R.string.pr_channel_no_recipients,
    R.string.pr_use_channel_action,
    R.string.pr_channel_message_posted,
    R.string.pr_message_files_queued,
    R.string.pr_files_queued,
    R.string.pr_invalid_self_target,
    R.string.pr_session_invalid,
    R.string.pr_pin_six_digits,
    R.string.pr_last_target_missing,
    R.string.pr_wifi_channel,
    R.string.pr_retry_scheduled,
    R.string.pr_message_cancel_requested,
    R.string.pr_direct_host_file_limit,
    R.string.pr_direct_client_file_limit,
    R.string.pr_peer_favorited,
    R.string.pr_peer_skipped,
    R.string.pr_peer_saved_favorites,
    R.string.pr_direct_host,
    R.string.pr_hint_permissions,
    R.string.pr_hint_renew,
    R.string.pr_hint_paused,
    R.string.pr_hint_sending,
    R.string.pr_hint_receiving,
    R.string.pr_hint_direct_ready,
    R.string.pr_hint_direct_waiting,
    R.string.pr_hint_direct_sync,
    R.string.pr_hint_lan_sending,
    R.string.pr_hint_lan_receiving,
    R.string.pr_hint_lan_ready,
    R.string.pr_hint_enable_wifi,
    R.string.pr_hint_quick_start,
    R.string.pr_sync_auto_failed_manual,
    R.string.pr_sync_auto_failed,
    R.string.pr_sync_start,
    R.string.pr_sync_auto_start,
    R.string.pr_sync_busy,
    R.string.pr_sync_host_missing,
    R.string.pr_sync_manual_hint,
    R.string.pr_sync_host_shares,
    R.string.pr_sync_invalid_peer,
    R.string.pr_sync_success,
    R.string.pr_sync_already_success,
    R.string.pr_session_review,
    R.string.pr_session_prepared,
    R.string.pr_session_code_label,
    R.string.pr_session_code_copied,
    R.string.pr_session_code_pasted,
    R.string.pr_clipboard_empty,
    R.string.pr_session_connecting,
    R.string.pr_attachments_restoring,
    R.string.pr_attachments_adding,
    R.string.pr_network_connected,
    R.string.pr_error_session_closed,
    R.string.pr_error_direct_unconfirmed,
    R.string.pr_error_direct_changed,
    R.string.pr_error_direct_outside,
    R.string.pr_error_manual_pairing,
    R.string.pr_error_approval_required,
    R.string.pr_error_untrusted,
    R.string.pr_error_identity_changed,
    R.string.pr_error_credentials,
    R.string.pr_error_expired,
    R.string.pr_error_permissions,
    R.string.pr_error_refused,
    R.string.pr_error_no_route,
    R.string.pr_error_timeout,
    R.string.pr_error_interrupted,
    R.string.pr_error_integrity,
    R.string.pr_error_canceled,
    R.string.pr_error_connection,
    R.string.pr_error_sync,
    R.string.pr_wifi_settings_failed,
    R.string.pr_android_permissions_hint,
    R.string.pr_about_tagline,
    R.string.pr_about_description,
    R.string.pr_about_credit,
    R.string.pr_about_license,
    R.string.pr_licenses,
    R.string.pr_about_storage,
    R.string.pr_done,
    R.string.pr_advanced_connection,
    R.string.pr_change_connection,
    R.string.pr_flash_pending,
    R.string.pr_flash_active,
    R.string.pr_flash_open,
    R.string.pr_request,
    R.string.pr_active,
    R.string.pr_qetara_options,
    R.string.pr_reading_notifications,
    R.string.pr_about_qetara,
    R.string.pr_connection_ended,
    R.string.pr_session_enabled,
    R.string.pr_session_disabled,
    R.string.pr_peer_forgotten,
    R.string.pr_channel_joined,
    R.string.pr_channel_left,
    R.string.pr_file_details_missing,
    R.string.pr_sender_ip_missing,
    R.string.pr_download_renew_session,
    R.string.pr_download_requested,
    R.string.pr_enable_session_hint,
    R.string.pr_clear_prompt,
    R.string.pr_target_missing_action,
    R.string.pr_permissions_action,
    R.string.pr_connect_action,
    R.string.pr_target_missing_hint,
    R.string.pr_expired_action,
    R.string.pr_session_renewed,
    R.string.pr_sync_retry,
    R.string.pr_session_confirmed,
    R.string.pr_trust_share_rejected,
    R.string.pr_error_prefix,
    R.string.pr_version,
    R.string.pr_fingerprint,
    R.string.pr_connecting_address
)

private val presentationQuantityCopy = intArrayOf(
    R.plurals.pr_devices_found,
    R.plurals.pr_files_sending,
    R.plurals.pr_files_recovered,
    R.plurals.pr_files_unavailable,
    R.plurals.pr_files_shared_imported,
    R.plurals.pr_message_files_targets,
    R.plurals.pr_message_targets,
    R.plurals.pr_files_targets,
    R.plurals.pr_files_queued_count,
    R.plurals.pr_channel_message_files,
    R.plurals.pr_channel_files,
    R.plurals.pr_channel_message_targets,
    R.plurals.pr_files_selected,
    R.plurals.pr_channel_files_posted,
    R.plurals.pr_files_partially_recovered,
    R.plurals.pr_files_unavailable_reselect
)

fun localizePresentationStatus(status: String): String = localizeKnownStatus(
    status, presentationStatusCopy, presentationQuantityCopy
) { resourceId, argumentIndex, argument ->
    when {
        resourceId == R.plurals.pr_files_partially_recovered && argumentIndex == 1 ->
            localizeKnownStatus(argument, intArrayOf(), intArrayOf(R.plurals.pr_files_unavailable_reselect))
        resourceId in intArrayOf(
            R.string.pr_target_missing_action, R.string.pr_permissions_action,
            R.string.pr_connect_action, R.string.pr_target_missing_hint, R.string.pr_expired_action
        ) && argumentIndex == 0 -> localizeKnownStatus(argument, intArrayOf(
            R.string.pr_send_files_action, R.string.pr_send_messages_action,
            R.string.pr_send_message_files_action, R.string.pr_use_channel_action
        ))
        resourceId == R.string.pr_clear_prompt && argumentIndex == 0 -> localizeKnownStatus(
            argument, intArrayOf(R.string.pr_direct_chat_scope, R.string.pr_channel_scope)
        )
        else -> argument
    }
}

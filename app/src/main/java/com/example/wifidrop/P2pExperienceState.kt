package com.example.wifidrop

import com.example.wifidrop.presentation.isSessionSyncFailure

internal enum class P2pExperienceCommand {
    REQUEST_PERMISSION,
    OPEN_WIFI_SETTINGS,
    START_CLIENT,
    CANCEL_DIRECT,
    SCAN_LAN,
    CANCEL_LAN_SCAN,
    USE_SUGGESTED_TARGET,
    REFRESH_STATE,
    REVIEW_TRUST,
    RENEW_SESSION,
    OPEN_DOWNLOADS,
    SYNC_TOKEN,
    OPEN_QUEUE,
    OPEN_MESSAGES,
    SEND_NOW,
    CONTINUE_FLOW
}

internal data class P2pExperienceAction(
    val label: String,
    val command: P2pExperienceCommand,
    val enabled: Boolean = true
)

internal data class P2pSupportBannerState(
    val title: String,
    val body: String,
    val action: P2pExperienceAction,
    val isError: Boolean = false
) {
    val key: String = "$title|$body|${action.label}|$isError"
}

internal data class P2pExperienceState(
    val activeConnectionMode: ConnectionMode,
    val alternateConnectionMode: ConnectionMode,
    val alternateModeEnabled: Boolean,
    val connectionLabel: String,
    val headerSummary: String,
    val headerHint: String,
    val connectionNarrative: String,
    val alternateModeHint: String?,
    val primaryAction: P2pExperienceAction,
    val supportBanner: P2pSupportBannerState?,
    val connectionReadyForFlow: Boolean,
    val lanReadyForExchange: Boolean,
    val nextStageAfterConnect: FocusStage,
    val showWifiDirectSection: Boolean,
    val showLanSection: Boolean
)

internal data class P2pChatExperienceState(
    val transportLabel: String,
    val transportStatus: String,
    val transportHint: String,
    val showSyncAction: Boolean
)

internal fun deriveP2pExperienceState(state: P2pScreenState): P2pExperienceState {
    val activeConnectionMode = state.activeConnectionMode
    val connected = state.connection?.groupFormed == true
    val isHost = state.connection?.isGroupOwner == true
    val directBusy = state.directDiscovering || state.directConnecting || state.directCreatingGroup
    val directReadyForExchange = state.sessionReady && !state.directTargetIp.isNullOrBlank()
    val directTargetLabel = state.directTargetLabel ?: state.directTargetIp
    val queueRunningCount = state.sendQueue.count { it.status == SendQueueStatus.RUNNING }
    val queueFailedCount = state.sendQueue.count { it.status == SendQueueStatus.FAILED }
    val messageFailedCount = state.chatMessages.count {
        it.direction == ChatMessageDirection.OUTGOING && it.status == ChatMessageStatus.FAILED
    }
    val hasAnyExchange = state.chatMessages.isNotEmpty() ||
        state.history.isNotEmpty() ||
        state.sendBatchTotal > 0
    val alternateConnectionMode = if (activeConnectionMode == ConnectionMode.WIFI_DIRECT) {
        ConnectionMode.LAN
    } else {
        ConnectionMode.WIFI_DIRECT
    }
    val alternateModeEnabled = when (alternateConnectionMode) {
        ConnectionMode.WIFI_DIRECT -> state.wifiDirectModeEnabled
        ConnectionMode.LAN -> state.lanModeEnabled
    }
    val lanReadyForExchange = state.sessionReady && state.lanConnected && (
        !state.resolvedTargetIp.isNullOrBlank() ||
            !state.suggestedTargetIp.isNullOrBlank() ||
            state.knownServicePeers.any { it.ip.isNotBlank() }
        )
    val connectionReadyForFlow = when (activeConnectionMode) {
        ConnectionMode.WIFI_DIRECT -> directReadyForExchange
        ConnectionMode.LAN -> lanReadyForExchange || directReadyForExchange
    }
    val nextStageAfterConnect = when {
        state.selectedFilesCount > 0 || state.sendQueue.isNotEmpty() -> FocusStage.SEND
        state.chatDraft.isNotBlank() || state.chatMessages.isNotEmpty() -> FocusStage.CHAT
        else -> FocusStage.SEND
    }
    val connectionLabel = activeConnectionMode.title
    val headerSummary = when (activeConnectionMode) {
        ConnectionMode.WIFI_DIRECT -> when {
            connected && directReadyForExchange && isHost -> appString(R.string.conn_wifi_direct_ready_with, directTargetLabel ?: appString(R.string.conn_your_device))
            connected && directReadyForExchange -> appString(R.string.conn_direct_ready_with, directTargetLabel ?: appString(R.string.conn_your_device))
            connected && isHost -> appString(R.string.conn_link_created_summary)
            connected -> appString(R.string.conn_preparing_direct_summary)
            state.directCreatingGroup -> appString(R.string.conn_creating_link_summary)
            state.directConnecting -> appString(R.string.conn_joining_direct_summary)
            state.directDiscovering && state.peers.isNotEmpty() -> appString(R.string.conn_choose_to_join)
            state.directDiscovering -> appString(R.string.conn_searching_direct_summary)
            !state.permissionGranted -> appString(R.string.conn_missing_direct_permission)
            !state.p2pEnabled -> appString(R.string.conn_open_system_wifi_direct)
            else -> appString(R.string.conn_direct_ready_create_search)
        }

        ConnectionMode.LAN -> when {
            state.lanScanning -> appString(R.string.conn_searching_same_wifi)
            state.lanConnected -> appString(R.string.conn_wifi_ready_at, state.lanLocalIp ?: appString(R.string.conn_this_network))
            else -> appString(R.string.conn_connect_both_same_wifi)
        }
    }
    val alternateModeHint = when {
        !alternateModeEnabled -> null
        alternateConnectionMode == ConnectionMode.WIFI_DIRECT && connected -> appString(R.string.conn_direct_alternative_available)
        alternateConnectionMode == ConnectionMode.WIFI_DIRECT && state.p2pEnabled -> appString(R.string.conn_direct_still_available)
        alternateConnectionMode == ConnectionMode.LAN && state.lanConnected -> appString(R.string.conn_wifi_alternative_available)
        alternateConnectionMode == ConnectionMode.LAN -> appString(R.string.conn_same_wifi_alternative)
        else -> null
    }
    val primaryAction = when {
        state.sessionExpired -> P2pExperienceAction(appString(R.string.conn_renew_session), P2pExperienceCommand.RENEW_SESSION)

        connectionReadyForFlow && isSessionSyncFailure(state.tokenSyncStatus) -> {
            P2pExperienceAction(appString(R.string.conn_sync_session), P2pExperienceCommand.SYNC_TOKEN)
        }

        connectionReadyForFlow && state.selectedFilesCount > 0 -> {
            P2pExperienceAction(appString(R.string.conn_continue_send), P2pExperienceCommand.CONTINUE_FLOW)
        }

        connectionReadyForFlow && (state.chatDraft.isNotBlank() || state.chatMessages.isNotEmpty()) -> {
            P2pExperienceAction(appString(R.string.conn_open_chat), P2pExperienceCommand.CONTINUE_FLOW)
        }

        connectionReadyForFlow -> {
            P2pExperienceAction(appString(R.string.conn_continue), P2pExperienceCommand.CONTINUE_FLOW)
        }

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && !state.permissionGranted -> {
            P2pExperienceAction(appString(R.string.conn_grant_permission), P2pExperienceCommand.REQUEST_PERMISSION)
        }

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && !state.p2pEnabled -> {
            P2pExperienceAction(appString(R.string.conn_open_wifi), P2pExperienceCommand.OPEN_WIFI_SETTINGS)
        }

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && connected && isHost && directReadyForExchange -> {
            P2pExperienceAction(appString(R.string.conn_continue), P2pExperienceCommand.CONTINUE_FLOW)
        }

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && connected && isHost -> {
            P2pExperienceAction(appString(R.string.conn_cancel_link), P2pExperienceCommand.CANCEL_DIRECT)
        }

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && connected && !isHost -> {
            P2pExperienceAction(appString(R.string.conn_preparing_direct), P2pExperienceCommand.REFRESH_STATE, enabled = false)
        }

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && directBusy -> {
            P2pExperienceAction(appString(R.string.conn_cancel), P2pExperienceCommand.CANCEL_DIRECT)
        }

        activeConnectionMode == ConnectionMode.WIFI_DIRECT -> {
            P2pExperienceAction(appString(R.string.conn_find_link), P2pExperienceCommand.START_CLIENT)
        }

        activeConnectionMode == ConnectionMode.LAN && state.lanScanning -> {
            P2pExperienceAction(appString(R.string.conn_cancel_search), P2pExperienceCommand.CANCEL_LAN_SCAN)
        }

        activeConnectionMode == ConnectionMode.LAN && !state.lanConnected -> {
            P2pExperienceAction(appString(R.string.conn_connect_wifi), P2pExperienceCommand.OPEN_WIFI_SETTINGS)
        }

        activeConnectionMode == ConnectionMode.LAN &&
            !state.suggestedTargetIp.isNullOrBlank() &&
            state.resolvedTargetIp.isNullOrBlank() -> {
            P2pExperienceAction(appString(R.string.conn_use_suggested_ip), P2pExperienceCommand.USE_SUGGESTED_TARGET)
        }

        activeConnectionMode == ConnectionMode.LAN &&
            state.selectedFilesCount > 0 &&
            !state.resolvedTargetIp.isNullOrBlank() -> {
            P2pExperienceAction(appString(R.string.conn_send_now), P2pExperienceCommand.SEND_NOW)
        }

        else -> P2pExperienceAction(appString(R.string.conn_search_devices), P2pExperienceCommand.SCAN_LAN)
    }
    val onboardingStep1Done = when (activeConnectionMode) {
        ConnectionMode.WIFI_DIRECT -> state.permissionGranted
        ConnectionMode.LAN -> state.lanConnected || directReadyForExchange
    }
    val onboardingStep2Done = when (activeConnectionMode) {
        ConnectionMode.WIFI_DIRECT -> directReadyForExchange
        ConnectionMode.LAN -> lanReadyForExchange || directReadyForExchange
    }
    val onboardingStep3Done = hasAnyExchange
    val onboardingAllDone = onboardingStep1Done && onboardingStep2Done && onboardingStep3Done
    val onboardingActiveStep = when {
        !onboardingStep1Done -> 1
        !onboardingStep2Done -> 2
        !onboardingStep3Done -> 3
        else -> 0
    }
    val supportBanner = when {
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && !state.permissionGranted -> P2pSupportBannerState(
            title = appString(R.string.conn_permissions_pending),
            body = appString(R.string.conn_permission_required_hint),
            action = P2pExperienceAction(appString(R.string.conn_grant_permission), P2pExperienceCommand.REQUEST_PERMISSION),
            isError = true
        )

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && !state.p2pEnabled -> P2pSupportBannerState(
            title = appString(R.string.conn_wifi_off),
            body = appString(R.string.conn_system_wifi_hint),
            action = P2pExperienceAction(appString(R.string.conn_open_wifi), P2pExperienceCommand.OPEN_WIFI_SETTINGS),
            isError = true
        )

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && state.directCreatingGroup -> P2pSupportBannerState(
            title = appString(R.string.conn_link_created),
            body = appString(R.string.conn_other_find_connect_hint),
            action = P2pExperienceAction(appString(R.string.conn_cancel_link), P2pExperienceCommand.CANCEL_DIRECT)
        )

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && state.directConnecting -> P2pSupportBannerState(
            title = appString(R.string.conn_joining_link),
            body = appString(R.string.conn_wait_link_hint),
            action = P2pExperienceAction(appString(R.string.conn_cancel), P2pExperienceCommand.CANCEL_DIRECT)
        )

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && state.directDiscovering && state.peers.isEmpty() -> P2pSupportBannerState(
            title = appString(R.string.conn_searching_devices),
            body = appString(R.string.conn_keep_link_open_hint),
            action = P2pExperienceAction(appString(R.string.conn_cancel), P2pExperienceCommand.CANCEL_DIRECT)
        )

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && !connected -> P2pSupportBannerState(
            title = appString(R.string.conn_choose_device_role),
            body = appString(R.string.conn_role_hint),
            action = P2pExperienceAction(appString(R.string.conn_find_link), P2pExperienceCommand.START_CLIENT)
        )

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && connected && isHost && !directReadyForExchange -> P2pSupportBannerState(
            title = appString(R.string.conn_link_created),
            body = appString(R.string.conn_other_find_finish_hint),
            action = P2pExperienceAction(appString(R.string.conn_refresh_state), P2pExperienceCommand.REFRESH_STATE)
        )

        activeConnectionMode == ConnectionMode.LAN && !state.lanConnected && !directReadyForExchange -> P2pSupportBannerState(
            title = appString(R.string.conn_shared_wifi_missing),
            body = appString(R.string.conn_shared_wifi_ip_hint),
            action = P2pExperienceAction(appString(R.string.conn_open_wifi_settings), P2pExperienceCommand.OPEN_WIFI_SETTINGS),
            isError = true
        )

        activeConnectionMode == ConnectionMode.LAN && !lanReadyForExchange && !directReadyForExchange -> P2pSupportBannerState(
            title = appString(R.string.conn_prepare_same_wifi),
            body = appString(R.string.conn_find_or_suggested_ip),
            action = if (state.suggestedTargetIp.isNullOrBlank()) {
                P2pExperienceAction(appString(R.string.conn_search_devices), P2pExperienceCommand.SCAN_LAN)
            } else {
                P2pExperienceAction(appString(R.string.conn_use_suggested_ip), P2pExperienceCommand.USE_SUGGESTED_TARGET)
            }
        )

        state.pendingCredentialShare != null -> P2pSupportBannerState(
            title = appString(R.string.conn_connection_request),
            body = appString(R.string.conn_share_session_request, state.pendingCredentialShare.label),
            action = P2pExperienceAction(appString(R.string.conn_review_request), P2pExperienceCommand.REVIEW_TRUST)
        )

        state.sessionExpired -> P2pSupportBannerState(
            title = appString(R.string.conn_session_expired),
            body = appString(R.string.conn_renew_chat_transfers),
            action = P2pExperienceAction(appString(R.string.conn_renew_session), P2pExperienceCommand.RENEW_SESSION),
            isError = true
        )

        !state.sendFailureCause.isNullOrBlank() || queueFailedCount > 0 -> P2pSupportBannerState(
            title = appString(R.string.conn_queue_failures),
            body = appString(R.string.conn_retry_queue_hint),
            action = P2pExperienceAction(appString(R.string.conn_open_queue), P2pExperienceCommand.OPEN_QUEUE),
            isError = true
        )

        state.selectedFilesCount > 0 && state.resolvedTargetIp.isNullOrBlank() -> P2pSupportBannerState(
            title = appString(R.string.conn_unresolved_destination),
            body = appString(R.string.conn_files_no_destination_hint),
            action = if (state.suggestedTargetIp.isNullOrBlank()) {
                P2pExperienceAction(appString(R.string.conn_refresh_state), P2pExperienceCommand.REFRESH_STATE)
            } else {
                P2pExperienceAction(appString(R.string.conn_use_suggested_destination), P2pExperienceCommand.USE_SUGGESTED_TARGET)
            },
            isError = true
        )

        state.selectedFilesCount > 0 && !state.sessionExpired -> P2pSupportBannerState(
            title = appString(R.string.conn_ready_send),
            body = appString(R.string.conn_start_sending_hint),
            action = P2pExperienceAction(appString(R.string.conn_send_now), P2pExperienceCommand.SEND_NOW)
        )

        messageFailedCount > 0 -> P2pSupportBannerState(
            title = appString(R.string.conn_messages_retry),
            body = appString(R.string.conn_failed_messages_hint),
            action = P2pExperienceAction(appString(R.string.conn_open_messages), P2pExperienceCommand.OPEN_MESSAGES),
            isError = true
        )

        !onboardingAllDone -> {
            val action = when (onboardingActiveStep) {
                1 -> if (activeConnectionMode == ConnectionMode.WIFI_DIRECT) {
                    P2pExperienceAction(appString(R.string.conn_grant_permission), P2pExperienceCommand.REQUEST_PERMISSION)
                } else {
                    P2pExperienceAction(appString(R.string.conn_open_wifi), P2pExperienceCommand.OPEN_WIFI_SETTINGS)
                }

                2 -> when (activeConnectionMode) {
                    ConnectionMode.WIFI_DIRECT -> if (state.p2pEnabled) {
                        P2pExperienceAction(appString(R.string.conn_find_link), P2pExperienceCommand.START_CLIENT)
                    } else {
                        P2pExperienceAction(appString(R.string.conn_open_wifi), P2pExperienceCommand.OPEN_WIFI_SETTINGS)
                    }

                    ConnectionMode.LAN -> if (state.suggestedTargetIp.isNullOrBlank()) {
                        P2pExperienceAction(
                            if (state.lanScanning) appString(R.string.conn_cancel_search) else appString(R.string.conn_search_devices),
                            if (state.lanScanning) {
                                P2pExperienceCommand.CANCEL_LAN_SCAN
                            } else {
                                P2pExperienceCommand.SCAN_LAN
                            }
                        )
                    } else {
                        P2pExperienceAction(appString(R.string.conn_use_suggested_ip), P2pExperienceCommand.USE_SUGGESTED_TARGET)
                    }
                }

                3 -> when {
                    state.sessionExpired -> P2pExperienceAction(appString(R.string.conn_renew_session), P2pExperienceCommand.RENEW_SESSION)
                    !isHost && isSessionSyncFailure(state.tokenSyncStatus) -> {
                        P2pExperienceAction(appString(R.string.conn_sync_session), P2pExperienceCommand.SYNC_TOKEN)
                    }

                    else -> P2pExperienceAction(appString(R.string.conn_go_messages), P2pExperienceCommand.OPEN_MESSAGES)
                }

                else -> P2pExperienceAction(appString(R.string.conn_refresh_state), P2pExperienceCommand.REFRESH_STATE)
            }
            P2pSupportBannerState(
                title = when (onboardingActiveStep) {
                    1 -> if (activeConnectionMode == ConnectionMode.WIFI_DIRECT) appString(R.string.conn_permissions_pending) else appString(R.string.conn_connect_same_network)
                    2 -> if (activeConnectionMode == ConnectionMode.WIFI_DIRECT) appString(R.string.conn_connect_direct_step) else appString(R.string.conn_prepare_same_wifi)
                    3 -> appString(R.string.conn_ready_try)
                    else -> appString(R.string.conn_next_step)
                },
                body = when (onboardingActiveStep) {
                    1 -> if (activeConnectionMode == ConnectionMode.WIFI_DIRECT) {
                        appString(R.string.conn_permission_discovery_required)
                    } else {
                        appString(R.string.conn_shared_wifi_discovery_hint)
                    }

                    2 -> if (activeConnectionMode == ConnectionMode.WIFI_DIRECT) {
                        appString(R.string.conn_system_wifi_then_link)
                    } else {
                        appString(R.string.conn_wifi_channel_discovery_hint)
                    }

                    3 -> appString(R.string.conn_try_chat_send_history)
                    else -> ""
                },
                action = action,
                isError = onboardingActiveStep == 1
            )
        }

        else -> null
    }
    val headerHint = when {
        state.sessionExpired -> appString(R.string.conn_renew_continue_chat)
        state.sending -> appString(R.string.conn_sending_background)
        state.receiving -> appString(R.string.conn_receiving_background)
        state.pendingCredentialShare != null -> appString(R.string.conn_credentials_pending)
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && state.directCreatingGroup ->
            appString(R.string.conn_created_other_find_hint)
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && state.directConnecting ->
            appString(R.string.conn_wait_joining_hint)
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && state.directDiscovering && state.peers.isNotEmpty() ->
            appString(R.string.conn_choose_device_connect)
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && state.directDiscovering ->
            appString(R.string.conn_find_other_link)
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && !state.p2pEnabled ->
            appString(R.string.conn_system_manages_direct_hint)
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && !connected -> appString(R.string.conn_role_hint)
        activeConnectionMode == ConnectionMode.WIFI_DIRECT && connected && isHost && !directReadyForExchange ->
            appString(R.string.conn_created_wait_other)
        activeConnectionMode == ConnectionMode.LAN && !state.lanConnected && !directReadyForExchange ->
            appString(R.string.conn_connect_both_same_wifi)
        activeConnectionMode == ConnectionMode.LAN && state.resolvedTargetIp.isNullOrBlank() -> {
            appString(R.string.conn_find_or_known_ip)
        }

        else -> appString(R.string.conn_active_tab_hint)
    }
    val connectionNarrative = when (activeConnectionMode) {
        ConnectionMode.WIFI_DIRECT -> if (!state.p2pEnabled) {
            appString(R.string.conn_system_wifi_availability_hint)
        } else if (connected) {
            if (directReadyForExchange) {
                appString(R.string.conn_direct_prioritized)
            } else {
                appString(R.string.conn_other_finishing_join)
            }
        } else if (state.directDiscovering && state.peers.isNotEmpty()) {
            appString(R.string.conn_choose_connect_finish)
        } else if (state.directDiscovering) {
            appString(R.string.conn_find_other_direct_link)
        } else {
            appString(R.string.conn_create_or_find_role)
        }

        ConnectionMode.LAN -> if (directReadyForExchange) {
            appString(R.string.conn_direct_ready_while_wifi)
        } else if (state.lanConnected) {
            appString(R.string.conn_same_wifi_pc_discovery)
        } else {
            appString(R.string.conn_same_wifi_when_shared)
        }
    }

    return P2pExperienceState(
        activeConnectionMode = activeConnectionMode,
        alternateConnectionMode = alternateConnectionMode,
        alternateModeEnabled = alternateModeEnabled,
        connectionLabel = connectionLabel,
        headerSummary = headerSummary,
        headerHint = headerHint,
        connectionNarrative = connectionNarrative,
        alternateModeHint = alternateModeHint,
        primaryAction = primaryAction,
        supportBanner = supportBanner,
        connectionReadyForFlow = connectionReadyForFlow,
        lanReadyForExchange = lanReadyForExchange,
        nextStageAfterConnect = nextStageAfterConnect,
        showWifiDirectSection = activeConnectionMode == ConnectionMode.WIFI_DIRECT,
        showLanSection = activeConnectionMode == ConnectionMode.LAN
    )
}

internal fun deriveP2pChatExperienceState(state: P2pScreenState): P2pChatExperienceState {
    val p2pLinked = state.connection?.groupFormed == true
    val isP2pHost = state.connection?.isGroupOwner == true
    val activeConnectionMode = state.activeConnectionMode
    val effectiveChatChannel = state.chatChannel
    val globalReady = state.lanConnected && state.globalLanJoined
    val directTargetCount = state.chatDirectTargetIps.size
    val directReady = directTargetCount > 0
    val directTargetLabel = state.chatDirectTargetLabel ?: state.chatDirectTargetIp
    val directChatMode = state.chatDirectTargetMode ?: state.activeConnectionMode
    val transportLabel = when (effectiveChatChannel) {
        ChatChannel.DIRECT -> appString(R.string.conn_direct_chat)
        ChatChannel.GLOBAL -> appString(R.string.conn_wifi_channel)
    }
    val transportStatus = when {
        effectiveChatChannel == ChatChannel.GLOBAL && globalReady -> {
            if (state.globalChatPeerCount > 0) {
                appQuantityString(R.plurals.conn_chat_wifi_devices, state.globalChatPeerCount, state.globalChatPeerCount)
            } else {
                appString(R.string.conn_chat_wifi_alone)
            }
        }
        effectiveChatChannel == ChatChannel.GLOBAL && !state.lanConnected -> appString(R.string.conn_chat_no_wifi)
        effectiveChatChannel == ChatChannel.GLOBAL && !state.globalLanJoined -> appString(R.string.conn_chat_outside_wifi)
        effectiveChatChannel == ChatChannel.GLOBAL -> appString(R.string.conn_chat_wifi_alone)
        directReady && directChatMode == ConnectionMode.WIFI_DIRECT -> {
            when {
                directTargetCount > 1 -> appQuantityString(R.plurals.conn_chat_direct_devices, directTargetCount, directTargetCount)
                directTargetLabel != null -> appString(R.string.conn_chat_direct_device, directTargetLabel)
                else -> appString(R.string.conn_chat_direct)
            }
        }
        directReady && directChatMode == ConnectionMode.LAN -> {
            if (directTargetLabel != null) appString(R.string.conn_chat_lan_device, directTargetLabel) else appString(R.string.conn_chat_lan)
        }
        else -> appString(R.string.conn_chat_no_destination)
    }
    val transportHint = when {
        state.sessionExpired -> appString(R.string.conn_chat_expired_hint)
        effectiveChatChannel == ChatChannel.GLOBAL && !state.lanConnected -> {
            appString(R.string.conn_chat_connect_wifi_hint)
        }

        effectiveChatChannel == ChatChannel.GLOBAL && !state.globalLanJoined -> {
            appString(R.string.conn_chat_join_channel_hint)
        }

        effectiveChatChannel == ChatChannel.GLOBAL && state.globalChatPeerCount <= 0 -> {
            appString(R.string.conn_chat_write_alone_hint)
        }

        effectiveChatChannel == ChatChannel.GLOBAL -> {
            appString(R.string.conn_chat_send_channel_hint)
        }

        directReady && directChatMode == ConnectionMode.WIFI_DIRECT -> {
            when {
                directTargetCount > 1 -> appQuantityString(R.plurals.conn_chat_direct_ready_devices, directTargetCount, directTargetCount)
                directTargetLabel != null -> appString(R.string.conn_chat_direct_ready_device, directTargetLabel)
                else -> appString(R.string.conn_chat_direct_ready)
            }
        }

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && p2pLinked && isP2pHost -> {
            appString(R.string.conn_chat_wait_other_join)
        }

        activeConnectionMode == ConnectionMode.WIFI_DIRECT && p2pLinked -> {
            appString(R.string.conn_chat_direct_mode_ready)
        }

        activeConnectionMode == ConnectionMode.WIFI_DIRECT -> {
            appString(R.string.conn_chat_direct_mode_connect)
        }

        activeConnectionMode == ConnectionMode.LAN && !state.lanConnected -> {
            appString(R.string.conn_chat_wifi_mode_connect)
        }

        activeConnectionMode == ConnectionMode.LAN && !directReady -> {
            appString(R.string.conn_chat_wifi_needs_peer)
        }

        else -> appString(R.string.conn_chat_lan_ready)
    }
    val showSyncAction = !state.sessionExpired && (
        when (effectiveChatChannel) {
            ChatChannel.DIRECT -> {
                (activeConnectionMode == ConnectionMode.WIFI_DIRECT && directReady && !isP2pHost) ||
                    (activeConnectionMode == ConnectionMode.LAN && state.lanConnected && !state.chatDirectTargetIp.isNullOrBlank())
            }

            ChatChannel.GLOBAL -> state.lanConnected && state.globalLanJoined && state.globalChatPeerCount > 0
        }
    )
    return P2pChatExperienceState(
        transportLabel = transportLabel,
        transportStatus = transportStatus,
        transportHint = transportHint,
        showSyncAction = showSyncAction
    )
}

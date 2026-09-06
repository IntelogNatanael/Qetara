package com.example.wifidrop.presentation

import android.os.Build

data class P2pWifiPermissionPlan(
    val refreshWifiDirectState: Boolean = false,
    val markPermissionMissing: Boolean = false,
    val requestPermission: Boolean = false
)

/** Passive observation never opens a system permission dialog. */
fun resolveWifiPermissionPlan(
    permissionGranted: Boolean,
    lanConnected: Boolean
): P2pWifiPermissionPlan {
    return when {
        permissionGranted -> P2pWifiPermissionPlan(
            refreshWifiDirectState = true
        )

        lanConnected -> P2pWifiPermissionPlan()

        else -> P2pWifiPermissionPlan(
            markPermissionMissing = true
        )
    }
}

fun shouldRequestNotificationPermission(
    transferInProgress: Boolean,
    sdkInt: Int,
    notificationPermissionGranted: Boolean,
    permissionAlreadyRequested: Boolean
): Boolean {
    if (!transferInProgress) return false
    if (sdkInt < Build.VERSION_CODES.TIRAMISU) return false
    if (notificationPermissionGranted) return false
    return !permissionAlreadyRequested
}

/** A repeated request without a rationale cannot display another Android dialog. */
enum class WifiPermissionRequestAction { REFRESH_STATE, REQUEST_PERMISSION, OPEN_APP_SETTINGS }

fun resolveWifiPermissionRequestAction(
    permissionGranted: Boolean,
    permissionAlreadyRequested: Boolean,
    shouldShowRationale: Boolean
): WifiPermissionRequestAction = when {
    permissionGranted -> WifiPermissionRequestAction.REFRESH_STATE
    permissionAlreadyRequested && !shouldShowRationale -> WifiPermissionRequestAction.OPEN_APP_SETTINGS
    else -> WifiPermissionRequestAction.REQUEST_PERMISSION
}

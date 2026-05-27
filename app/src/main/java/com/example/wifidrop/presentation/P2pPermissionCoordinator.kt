package com.example.wifidrop.presentation

import android.os.Build

data class P2pWifiPermissionPlan(
    val refreshWifiDirectState: Boolean = false,
    val markPermissionMissing: Boolean = false,
    val requestPermission: Boolean = false
)

fun resolveWifiPermissionPlan(
    permissionGranted: Boolean,
    lanConnected: Boolean,
    permissionAlreadyRequested: Boolean
): P2pWifiPermissionPlan {
    return when {
        permissionGranted -> P2pWifiPermissionPlan(
            refreshWifiDirectState = true
        )

        lanConnected -> P2pWifiPermissionPlan()

        else -> P2pWifiPermissionPlan(
            markPermissionMissing = true,
            requestPermission = !permissionAlreadyRequested
        )
    }
}

fun shouldRequestNotificationPermission(
    sdkInt: Int,
    notificationPermissionGranted: Boolean,
    permissionAlreadyRequested: Boolean
): Boolean {
    if (sdkInt < Build.VERSION_CODES.TIRAMISU) return false
    if (notificationPermissionGranted) return false
    return !permissionAlreadyRequested
}

package com.example.wifidrop.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pPermissionCoordinatorTest {

    @Test
    fun wifiPlanRefreshesWhenPermissionIsGranted() {
        val plan = resolveWifiPermissionPlan(
            permissionGranted = true,
            lanConnected = false,
            permissionAlreadyRequested = false
        )

        assertTrue(plan.refreshWifiDirectState)
        assertFalse(plan.markPermissionMissing)
        assertFalse(plan.requestPermission)
    }

    @Test
    fun wifiPlanDoesNotInterruptLanModeWhenWifiDirectPermissionIsMissing() {
        val plan = resolveWifiPermissionPlan(
            permissionGranted = false,
            lanConnected = true,
            permissionAlreadyRequested = false
        )

        assertFalse(plan.refreshWifiDirectState)
        assertFalse(plan.markPermissionMissing)
        assertFalse(plan.requestPermission)
    }

    @Test
    fun notificationPermissionOnlyRequestsOnAndroidThirteenAndAbove() {
        assertFalse(
            shouldRequestNotificationPermission(
                sdkInt = 32,
                notificationPermissionGranted = false,
                permissionAlreadyRequested = false
            )
        )

        assertTrue(
            shouldRequestNotificationPermission(
                sdkInt = 33,
                notificationPermissionGranted = false,
                permissionAlreadyRequested = false
            )
        )

        assertFalse(
            shouldRequestNotificationPermission(
                sdkInt = 33,
                notificationPermissionGranted = false,
                permissionAlreadyRequested = true
            )
        )
    }
}

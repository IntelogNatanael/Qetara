package com.example.wifidrop.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pPermissionCoordinatorTest {

    @Test
    fun wifiPlanRefreshesWhenPermissionIsGranted() {
        val plan = resolveWifiPermissionPlan(
            permissionGranted = true,
            lanConnected = false
        )

        assertTrue(plan.refreshWifiDirectState)
        assertFalse(plan.markPermissionMissing)
        assertFalse(plan.requestPermission)
    }

    @Test
    fun wifiPlanDoesNotInterruptLanModeWhenWifiDirectPermissionIsMissing() {
        val plan = resolveWifiPermissionPlan(
            permissionGranted = false,
            lanConnected = true
        )

        assertFalse(plan.refreshWifiDirectState)
        assertFalse(plan.markPermissionMissing)
        assertFalse(plan.requestPermission)
    }

    @Test
    fun passiveWifiObservationNeverRequestsPermissionEvenOnFirstRun() {
        val plan = resolveWifiPermissionPlan(permissionGranted = false, lanConnected = false)
        assertTrue(plan.markPermissionMissing)
        assertFalse(plan.refreshWifiDirectState)
        assertFalse(plan.requestPermission)
    }

    @Test
    fun notificationPermissionOnlyRequestsOnAndroidThirteenAndAbove() {
        assertFalse(
            shouldRequestNotificationPermission(
                transferInProgress = true,
                sdkInt = 32,
                notificationPermissionGranted = false,
                permissionAlreadyRequested = false
            )
        )

        assertTrue(
            shouldRequestNotificationPermission(
                transferInProgress = true,
                sdkInt = 33,
                notificationPermissionGranted = false,
                permissionAlreadyRequested = false
            )
        )

        assertFalse(
            shouldRequestNotificationPermission(
                transferInProgress = true,
                sdkInt = 33,
                notificationPermissionGranted = false,
                permissionAlreadyRequested = true
            )
        )
    }
    @Test
    fun notificationPermissionDoesNotInterruptExploringTheAppBeforeATransfer() {
        assertFalse(
            shouldRequestNotificationPermission(
                transferInProgress = false,
                sdkInt = 36,
                notificationPermissionGranted = false,
                permissionAlreadyRequested = false
            )
        )
    }

    @Test
    fun notificationPermissionDoesNotRepeatAfterApproval() {
        assertFalse(
            shouldRequestNotificationPermission(
                transferInProgress = true,
                sdkInt = 36,
                notificationPermissionGranted = true,
                permissionAlreadyRequested = false
            )
        )
    }

    @Test
    fun permanentlyDeniedPermissionHasAnActionableSettingsRoute() {
        assertEquals(
            WifiPermissionRequestAction.OPEN_APP_SETTINGS,
            resolveWifiPermissionRequestAction(permissionGranted = false, permissionAlreadyRequested = true, shouldShowRationale = false)
        )
    }

    @Test
    fun firstRequestAndExplainableDenialCanUseAndroidPermissionDialog() {
        assertEquals(
            WifiPermissionRequestAction.REQUEST_PERMISSION,
            resolveWifiPermissionRequestAction(permissionGranted = false, permissionAlreadyRequested = false, shouldShowRationale = false)
        )
        assertEquals(
            WifiPermissionRequestAction.REQUEST_PERMISSION,
            resolveWifiPermissionRequestAction(permissionGranted = false, permissionAlreadyRequested = true, shouldShowRationale = true)
        )
    }

    @Test
    fun permissionGrantedInSettingsRefreshesConnectionWithoutAnotherPrompt() {
        assertEquals(
            WifiPermissionRequestAction.REFRESH_STATE,
            resolveWifiPermissionRequestAction(permissionGranted = true, permissionAlreadyRequested = true, shouldShowRationale = false)
        )
    }

}

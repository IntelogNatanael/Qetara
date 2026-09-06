package com.example.wifidrop

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID

/** Service-owned monitor: group changes remain visible while Compose is in the background. */
internal class WifiDirectGroupMonitor(
    context: Context,
    private val onChanged: (DirectGroupContext?) -> Unit
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel = runCatching { manager?.initialize(appContext, appContext.mainLooper, null) }.getOrNull()
    private val epoch = DirectGroupEpoch()
    private var active = false
    private var generation = 0L
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION &&
                intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1) != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                invalidate()
                return
            }
            val info = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO, WifiP2pInfo::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<WifiP2pInfo>(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
            }
            @Suppress("DEPRECATION")
            val network = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
            @Suppress("DEPRECATION")
            val disconnected = network?.isConnected == false
            if (info?.groupFormed == false || disconnected) invalidate()
            refresh()
        }
    }

    fun start() {
        if (active) return
        active = true
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    fun stop() {
        if (!active) return
        active = false
        generation++
        runCatching { appContext.unregisterReceiver(receiver) }
        if (Build.VERSION.SDK_INT >= 27) runCatching { channel?.close() }
        onChanged(epoch.clear())
    }

    private fun invalidate() {
        generation++
        onChanged(epoch.clear())
    }

    @SuppressLint("MissingPermission")
    fun refresh() {
        if (!active) return
        val requestGeneration = ++generation
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_FINE_LOCATION
        val mgr = manager
        val ch = channel
        if (mgr == null || ch == null || ContextCompat.checkSelfPermission(appContext, permission) != PackageManager.PERMISSION_GRANTED) {
            onChanged(epoch.clear())
            return
        }
        try {
            mgr.requestConnectionInfo(ch) { info ->
                if (!active || requestGeneration != generation) return@requestConnectionInfo
                if (info?.groupFormed != true || info.groupOwnerAddress == null) {
                    onChanged(epoch.clear())
                    return@requestConnectionInfo
                }
                try {
                    mgr.requestGroupInfo(ch) { group ->
                        if (!active || requestGeneration != generation) return@requestGroupInfo
                        val name = group?.`interface`.orEmpty()
                        val addresses = runCatching {
                            NetworkInterface.getByName(name)?.inetAddresses?.toList().orEmpty()
                                .filterIsInstance<Inet4Address>()
                                .filterNot { it.isLoopbackAddress || it.isAnyLocalAddress }
                                .mapNotNull { it.hostAddress }.toSet()
                        }.getOrDefault(emptySet())
                        if (group == null || group.isGroupOwner != info.isGroupOwner) {
                            onChanged(epoch.clear())
                        } else {
                            onChanged(epoch.update(
                                ownerIp = info.groupOwnerAddress.hostAddress.orEmpty(), isOwner = info.isGroupOwner,
                                interfaceName = name, localAddresses = addresses, networkName = group.networkName.orEmpty(),
                                ownerDeviceAddress = group.owner?.deviceAddress.orEmpty(),
                                nowElapsedMs = SystemClock.elapsedRealtime(), nextSessionId = { UUID.randomUUID().toString() }
                            ))
                        }
                    }
                } catch (_: RuntimeException) {
                    if (active && requestGeneration == generation) onChanged(epoch.clear())
                }
            }
        } catch (_: RuntimeException) {
            if (active && requestGeneration == generation) onChanged(epoch.clear())
        }
    }
}

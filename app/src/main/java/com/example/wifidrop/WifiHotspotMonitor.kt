package com.example.wifidrop

import android.content.Context
import android.net.TetheringInterface
import android.net.TetheringManager
import android.os.Build
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi

/** Observes an existing hotspot; never starts tethering or changes the user's network. */
internal interface WifiHotspotMonitor {
    val interfaceNames: Set<String>
    @MainThread
    fun start()
    @MainThread
    fun stop()

    companion object {
        fun create(context: Context): WifiHotspotMonitor =
            if (Build.VERSION.SDK_INT >= 36) Api36WifiHotspotMonitor(context.applicationContext)
            else UnsupportedWifiHotspotMonitor
    }
}

// Older Android versions do not expose this tethering interface API to ordinary apps.
// Do not guess from wlan/private-IP names: those can also belong to a Direct group.
private object UnsupportedWifiHotspotMonitor : WifiHotspotMonitor {
    override val interfaceNames: Set<String> = emptySet()
    override fun start() = Unit
    override fun stop() = Unit
}

@RequiresApi(36)
private class Api36WifiHotspotMonitor(private val context: Context) : WifiHotspotMonitor {
    private val manager = context.getSystemService(TetheringManager::class.java)
    private var callback: TetheringManager.TetheringEventCallback? = null

    @Volatile
    override var interfaceNames: Set<String> = emptySet()
        private set

    override fun start() {
        val tethering = manager ?: return
        if (callback != null) return
        val next = object : TetheringManager.TetheringEventCallback {
            override fun onTetheredInterfacesChanged(interfaces: Set<TetheringInterface>) {
                if (callback !== this) return
                interfaceNames = interfaces.asSequence()
                    .filter { it.type == TetheringManager.TETHERING_WIFI }
                    .map { it.`interface` }
                    .filter { it.isNotBlank() }
                    .toSet()
            }
        }
        callback = next
        try {
            // ACCESS_NETWORK_STATE is already declared; no nearby/location prompt is required.
            tethering.registerTetheringEventCallback(context.mainExecutor, next)
        } catch (_: RuntimeException) {
            callback = null
            interfaceNames = emptySet()
        }
    }

    override fun stop() {
        val previous = callback
        // Ignore queued callbacks from a refresh loop that has already been cancelled.
        callback = null
        interfaceNames = emptySet()
        if (previous != null) runCatching { manager?.unregisterTetheringEventCallback(previous) }
    }
}

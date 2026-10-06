package com.example.wifidrop.presentation

import android.content.Context
import com.example.wifidrop.FileTransfer
import com.example.wifidrop.NetworkUtils
import com.example.wifidrop.TransferSecurity
import com.example.wifidrop.WifiHotspotMonitor
import com.example.wifidrop.WifiLanSnapshot
import com.example.wifidrop.backend.P2pBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

data class P2pLanDiscoveryState(
    val connected: Boolean,
    val localIp: String?,
    val scanStatus: String = "",
    val scanning: Boolean = false,
    val localIpv4Addresses: Set<String> = emptySet()
)

class P2pLanDiscoveryPresenter(
    context: Context,
    private val backend: P2pBackend,
    private val localDeviceId: String
) {
    private val appContext = context.applicationContext
    private val hotspotMonitor = WifiHotspotMonitor.create(appContext)
    private val initialSnapshot = readSnapshot()
    private val _state = MutableStateFlow(initialSnapshot.toDiscoveryState())
    val state: StateFlow<P2pLanDiscoveryState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var lastLanScannedPrefix: String? = null
    // Effect restarts can overlap while the previous coroutine finishes cancellation.
    // Unregister its callback before starting the replacement, without locking manual scans.
    private val refreshLoopMutex = Mutex()

    suspend fun runAutoRefreshLoop(
        autoScanEnabled: Boolean,
        sessionToken: String,
        sessionPin: String,
        sessionExpired: Boolean,
        deviceLabelProvider: () -> String,
        onSuggestedTarget: (String) -> Unit
    ) = refreshLoopMutex.withLock {
        hotspotMonitor.start()
        try {
            while (currentCoroutineContext().isActive) {
                val snapshot = refreshSnapshot()
                val prefix = snapshot.ipv4?.substringBeforeLast(".", "")

                if (!snapshot.connected) {
                    lastLanScannedPrefix = null
                }

                val shouldAutoScan = snapshot.connected &&
                    autoScanEnabled &&
                    !prefix.isNullOrBlank() &&
                    prefix != lastLanScannedPrefix &&
                    FileTransfer.isValidToken(sessionToken) &&
                    TransferSecurity.isValidPin(sessionPin) &&
                    !sessionExpired

                if (shouldAutoScan) {
                    lastLanScannedPrefix = prefix
                    startScanInternal(
                        manual = false,
                        deviceLabel = deviceLabelProvider(),
                        onSuggestedTarget = onSuggestedTarget
                    )
                }

                delay(2_500)
            }
        } finally {
            hotspotMonitor.stop()
        }
    }

    fun startScan(
        scope: CoroutineScope,
        manual: Boolean,
        deviceLabel: String,
        onSuggestedTarget: (String) -> Unit
    ) {
        if (scanJob?.isActive == true) return
        scanJob = scope.launch {
            try {
                startScanInternal(
                    manual = manual,
                    deviceLabel = deviceLabel,
                    onSuggestedTarget = onSuggestedTarget
                )
            } finally {
                scanJob = null
            }
        }
    }

    fun cancelScan() {
        val currentJob = scanJob ?: return
        if (!currentJob.isActive) {
            scanJob = null
            return
        }
        _state.update {
            it.copy(
                scanStatus = "Búsqueda cancelada.",
                scanning = false
            )
        }
        scanJob = null
        currentJob.cancel(CancellationException("cancelado por usuario"))
    }

    private suspend fun startScanInternal(
        manual: Boolean,
        deviceLabel: String,
        onSuggestedTarget: (String) -> Unit
    ): Int {
        if (_state.value.scanning) return 0

        val snapshot = refreshSnapshot()
        val localIp = snapshot.ipv4
        if (!snapshot.connected || localIp.isNullOrBlank()) {
            _state.update {
                it.copy(scanStatus = "Conecta este equipo a una misma Wi-Fi para buscar equipos.")
            }
            return 0
        }

        _state.update {
            it.copy(
                scanning = true,
                scanStatus = if (manual) {
                    "Buscando equipos en esta Wi-Fi..."
                } else {
                    "Escaneo automático en red Wi-Fi..."
                }
            )
        }

        return try {
            val candidates = NetworkUtils.subnetCandidates(localIp).filter {
                isAutomaticConnectionAddress(it, localIp) && it !in _state.value.localIpv4Addresses
            }
            if (candidates.isEmpty()) {
                _state.update {
                    it.copy(scanStatus = "No pude resolver el rango de red local.")
                }
                return 0
            }

            val semaphore = Semaphore(24)
            val sanitizedLabel = deviceLabel.ifBlank { "cliente" }
            val foundIps = mutableListOf<String>()
            val foundLock = Any()
            val found = coroutineScope {
                candidates.map { ip ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val result = backend.probePeer(
                                hostAddress = ip,
                                clientId = localDeviceId,
                                deviceLabel = sanitizedLabel
                            )
                            result.getOrNull()?.takeIf { it.peerId != localDeviceId }?.let { payload ->
                                backend.reportDiscoveredPeer(
                                    peerId = payload.peerId,
                                    peerLabel = payload.peerLabel,
                                    peerIp = ip,
                                    trusted = payload.trustedByHost,
                                    globalLanJoined = payload.globalLanJoined
                                )
                                synchronized(foundLock) {
                                    foundIps.add(ip)
                                }
                                1
                            } ?: 0
                        }
                    }
                }.awaitAll().sum()
            }

            foundIps.firstOrNull()?.let(onSuggestedTarget)

            _state.update {
                it.copy(
                    scanStatus = if (found > 0) {
                        "Equipos encontrados en esta Wi-Fi: $found"
                    } else {
                        "No se encontraron equipos en la red Wi-Fi actual."
                    }
                )
            }
            found
        } catch (_: CancellationException) {
            _state.update {
                it.copy(scanStatus = "Búsqueda cancelada.")
            }
            0
        } finally {
            _state.update { it.copy(scanning = false) }
        }
    }

    private suspend fun refreshSnapshot(): WifiLanSnapshot {
        val snapshot = withContext(Dispatchers.IO) { readSnapshot() }
        val localAddresses = withContext(Dispatchers.IO) {
            runCatching {
                NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                    .flatMap { it.inetAddresses.toList() }
                    .filterIsInstance<Inet4Address>()
                    .mapNotNull { it.hostAddress }
                    .toSet()
            }.getOrDefault(emptySet())
        }
        _state.update { current ->
            current.copy(
                connected = snapshot.connected,
                localIp = snapshot.ipv4,
                localIpv4Addresses = localAddresses + listOfNotNull(snapshot.ipv4)
            )
        }
        return snapshot
    }

    private fun readSnapshot(): WifiLanSnapshot = NetworkUtils.currentWifiLanSnapshot(
        context = appContext,
        wifiHotspotInterfaceNames = hotspotMonitor.interfaceNames,
        excludedInterfaceNames = setOfNotNull(backend.transferState.value.directGroup?.interfaceName)
    )
}

private fun WifiLanSnapshot.toDiscoveryState(): P2pLanDiscoveryState {
    return P2pLanDiscoveryState(
        connected = connected,
        localIp = ipv4
    )
}

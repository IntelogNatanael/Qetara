package com.example.wifidrop

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PeerDevice(
    val name: String,
    val address: String,
    val status: Int
)

data class ConnectionSnapshot(
    val groupFormed: Boolean,
    val isGroupOwner: Boolean,
    val groupOwnerAddress: String?
)

data class GroupSnapshot(
    val networkName: String?,
    val passphrase: String?,
    val ownerAddress: String?,
    val clients: List<String>
)

data class WifiDirectState(
    val supported: Boolean = true,
    val p2pEnabled: Boolean = false,
    val thisDeviceName: String = "",
    val thisDeviceAddress: String = "",
    val peers: List<PeerDevice> = emptyList(),
    val connection: ConnectionSnapshot? = null,
    val group: GroupSnapshot? = null,
    val discoveringPeers: Boolean = false,
    val connectingToPeer: Boolean = false,
    val creatingGroup: Boolean = false,
    val statusMessage: String = "Listo"
)

class WifiDirectController(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel = manager?.initialize(appContext, appContext.mainLooper, null)

    private val _state = MutableStateFlow(
        WifiDirectState(
            supported = manager != null && channel != null,
            statusMessage = if (manager == null || channel == null) {
                "Wi-Fi Direct no disponible en este dispositivo."
            } else {
                "Listo para Wi-Fi Direct."
            }
        )
    )
    val state: StateFlow<WifiDirectState> = _state.asStateFlow()

    fun markPermissionMissing() {
        _state.update {
            it.copy(
                discoveringPeers = false,
                connectingToPeer = false,
                creatingGroup = false,
                statusMessage = "Falta permiso runtime para Wi-Fi Direct."
            )
        }
    }

    fun onP2pStateChanged(enabled: Boolean) {
        _state.update {
            it.copy(
                p2pEnabled = enabled,
                peers = if (enabled) it.peers else emptyList(),
                connection = if (enabled) it.connection else null,
                group = if (enabled) it.group else null,
                discoveringPeers = if (enabled) it.discoveringPeers else false,
                connectingToPeer = if (enabled) it.connectingToPeer else false,
                creatingGroup = if (enabled) it.creatingGroup else false,
                statusMessage = if (enabled) "Wi-Fi Direct activado." else "Wi-Fi Direct desactivado en sistema."
            )
        }
    }

    fun onThisDeviceChanged(device: WifiP2pDevice?) {
        if (device == null) return
        val name = device.deviceName?.ifBlank { "(sin nombre)" } ?: "(sin nombre)"
        _state.update {
            it.copy(
                thisDeviceName = name,
                thisDeviceAddress = device.deviceAddress ?: ""
            )
        }
    }

    fun onPeersChanged() {
        requestPeers()
    }

    fun onConnectionChanged() {
        requestConnectionInfo()
        requestGroupInfo()
    }

    fun refreshAll() {
        requestPeers()
        requestConnectionInfo()
        requestGroupInfo()
    }

    @SuppressLint("MissingPermission")
    fun discoverPeers() {
        withManagerAndPermission { mgr, ch ->
            mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    _state.update {
                        it.copy(
                            discoveringPeers = true,
                            connectingToPeer = false,
                            creatingGroup = false,
                            statusMessage = "Buscando equipos con Wi-Fi Direct..."
                        )
                    }
                }

                override fun onFailure(reason: Int) {
                    _state.update {
                        it.copy(
                            discoveringPeers = false,
                            statusMessage = "No se pudo buscar equipos: ${reasonToText(reason)}"
                        )
                    }
                }
            })
        }
    }

    @SuppressLint("MissingPermission")
    fun requestPeers() {
        withManagerAndPermission { mgr, ch ->
            mgr.requestPeers(ch) { list ->
                val mapped = list.deviceList
                    .map { d ->
                        PeerDevice(
                            name = d.deviceName?.ifBlank { "(sin nombre)" } ?: "(sin nombre)",
                            address = d.deviceAddress ?: "",
                            status = d.status
                        )
                    }
                    .sortedBy { it.name.lowercase() }

                _state.update {
                    it.copy(
                        peers = mapped,
                        discoveringPeers = false,
                        statusMessage = if (mapped.isEmpty()) {
                            "Aun no hay equipos cerca."
                        } else {
                            "Equipos listos para conectar: ${mapped.size}"
                        }
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(deviceAddress: String) {
        withManagerAndPermission { mgr, ch ->
            val normalizedAddress = deviceAddress.trim()
            if (normalizedAddress.isBlank()) {
                updateStatus("Direccion de peer invalida para conectar.")
                return@withManagerAndPermission
            }
            val config = WifiP2pConfig().apply {
                this.deviceAddress = normalizedAddress
                wps.setup = WpsInfo.PBC
            }

            mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    _state.update {
                        it.copy(
                            discoveringPeers = false,
                            connectingToPeer = true,
                            creatingGroup = false,
                            statusMessage = "Uniendote al enlace..."
                        )
                    }
                }

                override fun onFailure(reason: Int) {
                    _state.update {
                        it.copy(
                            connectingToPeer = false,
                            statusMessage = "No se pudo unir al enlace: ${reasonToText(reason)}"
                        )
                    }
                }
            })
        }
    }

    @SuppressLint("MissingPermission")
    fun cancelConnect() {
        withManagerAndPermission { mgr, ch ->
            mgr.cancelConnect(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    _state.update {
                        it.copy(
                            discoveringPeers = false,
                            connectingToPeer = false,
                            statusMessage = "Busqueda cancelada."
                        )
                    }
                }

                override fun onFailure(reason: Int) {
                    _state.update {
                        it.copy(
                            connectingToPeer = false,
                            statusMessage = "No se pudo cancelar: ${reasonToText(reason)}"
                        )
                    }
                }
            })
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscoveryAndNegotiation() {
        withManagerAndPermission { mgr, ch ->
            // These requests stop discovery/negotiation while leaving an established network intact.
            mgr.stopPeerDiscovery(ch, null)
            mgr.cancelConnect(ch, null)
            _state.update { it.copy(
                discoveringPeers = false,
                connectingToPeer = false,
                creatingGroup = false
            ) }
        }
    }

    fun cancelDirectAttempt() {
        val snapshot = state.value
        val hasGroupToUndo = snapshot.connection?.groupFormed == true ||
            snapshot.group != null ||
            snapshot.creatingGroup
        if (hasGroupToUndo) {
            removeGroup()
        } else {
            cancelConnect()
        }
    }

    @SuppressLint("MissingPermission")
    fun createGroup() {
        withManagerAndPermission { mgr, ch ->
            mgr.createGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    _state.update {
                        it.copy(
                            discoveringPeers = false,
                            connectingToPeer = false,
                            creatingGroup = true,
                            statusMessage = "Enlace creado en este equipo."
                        )
                    }
                }

                override fun onFailure(reason: Int) {
                    _state.update {
                        it.copy(
                            creatingGroup = false,
                            statusMessage = "No se pudo crear el enlace: ${reasonToText(reason)}"
                        )
                    }
                }
            })
        }
    }

    @SuppressLint("MissingPermission")
    fun removeGroup() {
        withManagerAndPermission { mgr, ch ->
            mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    _state.update {
                        it.copy(
                            connection = null,
                            group = null,
                            discoveringPeers = false,
                            connectingToPeer = false,
                            creatingGroup = false,
                            statusMessage = "Enlace cerrado."
                        )
                    }
                }

                override fun onFailure(reason: Int) {
                    _state.update {
                        it.copy(
                            creatingGroup = false,
                            connectingToPeer = false,
                            statusMessage = "No se pudo cerrar el enlace: ${reasonToText(reason)}"
                        )
                    }
                }
            })
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestConnectionInfo() {
        withManagerAndPermission { mgr, ch ->
            mgr.requestConnectionInfo(ch) { info: WifiP2pInfo? ->
                val snapshot = info?.let {
                    ConnectionSnapshot(
                        groupFormed = it.groupFormed,
                        isGroupOwner = it.isGroupOwner,
                        groupOwnerAddress = it.groupOwnerAddress?.hostAddress
                    )
                }
                _state.update {
                    it.copy(
                        connection = snapshot,
                        connectingToPeer = if (snapshot?.groupFormed == true) false else it.connectingToPeer,
                        creatingGroup = if (snapshot?.groupFormed == true) false else it.creatingGroup
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestGroupInfo() {
        withManagerAndPermission { mgr, ch ->
            mgr.requestGroupInfo(ch) { group: WifiP2pGroup? ->
                val snapshot = group?.let {
                    GroupSnapshot(
                        networkName = it.networkName,
                        passphrase = it.passphrase,
                        ownerAddress = it.owner?.deviceAddress,
                        clients = it.clientList.map { client ->
                            val n = client.deviceName?.ifBlank { "(sin nombre)" } ?: "(sin nombre)"
                            "$n (${client.deviceAddress})"
                        }
                    )
                }
                _state.update { it.copy(group = snapshot) }
            }
        }
    }

    private fun withManagerAndPermission(block: (WifiP2pManager, WifiP2pManager.Channel) -> Unit) {
        val mgr = manager
        val ch = channel
        if (mgr == null || ch == null) {
            updateStatus("Wi-Fi Direct no esta disponible.")
            return
        }

        if (!hasRequiredPermission()) {
            markPermissionMissing()
            return
        }

        try {
            block(mgr, ch)
        } catch (se: SecurityException) {
            _state.update {
                it.copy(
                    discoveringPeers = false,
                    connectingToPeer = false,
                    creatingGroup = false,
                    statusMessage = "Permiso denegado para operar Wi-Fi Direct."
                )
            }
        }
    }

    private fun hasRequiredPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateStatus(message: String) {
        _state.update { it.copy(statusMessage = message) }
    }

    private fun reasonToText(reason: Int): String {
        return when (reason) {
            WifiP2pManager.P2P_UNSUPPORTED -> "P2P_UNSUPPORTED"
            WifiP2pManager.BUSY -> "BUSY"
            WifiP2pManager.ERROR -> "ERROR"
            else -> "UNKNOWN($reason)"
        }
    }
}

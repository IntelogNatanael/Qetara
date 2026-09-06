package com.example.wifidrop.backend

import android.content.Context
import android.net.Uri
import com.example.wifidrop.ChatMessageScope
import com.example.wifidrop.ChannelFileOffer
import com.example.wifidrop.ChannelFileOfferStore
import com.example.wifidrop.FileTransfer
import com.example.wifidrop.PeerDiscoveryPayload
import com.example.wifidrop.SessionCredentialsPayload
import com.example.wifidrop.TransferForegroundService
import com.example.wifidrop.TransferRuntimeState
import com.example.wifidrop.WifiDirectBroadcastReceiver
import com.example.wifidrop.WifiDirectController
import com.example.wifidrop.WifiDirectState
import com.example.wifidrop.UxPreferencesStore
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract consumed by the Compose layer.
 *
 * UI code should talk to this interface instead of calling transport/runtime
 * services directly. This keeps frontend code focused on presentation and
 * makes backend responsibilities easier to evolve independently.
 */
interface P2pBackend {
    val wifiState: StateFlow<WifiDirectState>
    val transferState: StateFlow<TransferRuntimeState>

    fun createWifiDirectBroadcastReceiver(): WifiDirectBroadcastReceiver
    fun refreshWifiDirectState()
    fun markWifiDirectPermissionMissing()
    fun discoverPeers()
    fun createGroup()
    fun connect(deviceAddress: String)
    fun cancelConnect()
    fun cancelDirectAttempt()
    fun removeGroup()
    fun disconnectAll()
    suspend fun requestSessionCredentials(
        hostAddress: String,
        clientId: String,
        deviceLabel: String
    ): Result<SessionCredentialsPayload>
    suspend fun announcePresence(
        hostAddress: String,
        token: String,
        pin: String,
        clientId: String,
        deviceLabel: String
    ): Result<Unit>
    suspend fun probePeer(
        hostAddress: String,
        clientId: String,
        deviceLabel: String
    ): Result<PeerDiscoveryPayload>

    fun startSession(
        token: String,
        pin: String,
        sessionExpiresAtMs: Long,
        receiveDirPath: String
    )

    fun stopSession()
    fun setSessionEnabled(enabled: Boolean)
    fun sendFile(
        fileUri: Uri,
        fileName: String,
        targetIp: String,
        token: String,
        pin: String,
        deviceLabel: String
    )

    fun sendMessage(
        targetIp: String,
        token: String,
        pin: String,
        deviceLabel: String,
        message: String,
        peerLabelOverride: String? = null,
        scope: ChatMessageScope = ChatMessageScope.DIRECT
    )

    fun sendBroadcastMessage(
        token: String,
        pin: String,
        deviceLabel: String,
        message: String,
        scope: ChatMessageScope,
        channelLabel: String,
        targetPeerIds: List<String>,
        targetIps: List<String>,
        targetLabels: List<String>
    )

    fun registerChannelFileOffer(
        fileUri: Uri,
        fileName: String,
        deviceLabel: String,
        senderIp: String? = null
    ): ChannelFileOffer

    fun requestChannelFileOffer(
        offer: ChannelFileOffer,
        targetIp: String,
        token: String,
        pin: String,
        deviceLabel: String
    )

    fun sendSilentMessage(
        targetIp: String,
        token: String,
        pin: String,
        deviceLabel: String,
        message: String,
        peerLabelOverride: String? = null,
        scope: ChatMessageScope = ChatMessageScope.DIRECT
    )

    fun retryMessage(messageId: String, deviceLabel: String)
    fun cancelPendingMessage(messageId: String)
    fun deleteChatMessage(messageId: String)
    fun clearChatMessages(scope: ChatMessageScope? = null)
    fun pauseTransfers()
    fun resumeTransfers()
    fun cancelActiveTransfer()
    fun openDownloads()
    fun trustPeer(peerId: String, peerLabel: String)
    fun forgetPeer(peerId: String)
    fun reportDiscoveredPeer(
        peerId: String,
        peerLabel: String,
        peerIp: String,
        trusted: Boolean = false,
        globalLanJoined: Boolean = false
    )

    fun approveCredentialShare(peerId: String, peerLabel: String, expectedNoiseStaticKey: String, requestedAtMs: Long)
    fun rejectCredentialShare(peerId: String)
    fun setPeerFavorite(peerId: String, favorite: Boolean)
    fun setPeerAlias(peerId: String, alias: String)
    fun pauseQueueItem(transferId: String)
    fun resumeQueueItem(transferId: String)
    fun cancelQueueItem(transferId: String)
    fun moveQueueItemUp(transferId: String)
    fun moveQueueItemDown(transferId: String)
}

class AndroidP2pBackend(
    context: Context,
    private val wifiDirectController: WifiDirectController = WifiDirectController(context.applicationContext)
) : P2pBackend {

    private val appContext = context.applicationContext
    private val networkGate = SessionNetworkGate(
        isEnabled = { UxPreferencesStore.load(appContext).sessionEnabled },
        persistEnabled = { UxPreferencesStore.setSessionEnabled(appContext, it) }
    )

    private fun sessionEnabled(): Boolean = UxPreferencesStore.load(appContext).sessionEnabled

    override fun setSessionEnabled(enabled: Boolean) {
        networkGate.setEnabled(enabled)
        if (!enabled) {
            TransferForegroundService.stopSession(appContext)
            wifiDirectController.stopDiscoveryAndNegotiation()
        }
    }

    override val wifiState: StateFlow<WifiDirectState> = wifiDirectController.state
    override val transferState: StateFlow<TransferRuntimeState> = TransferForegroundService.state

    override fun createWifiDirectBroadcastReceiver(): WifiDirectBroadcastReceiver {
        return WifiDirectBroadcastReceiver(wifiDirectController)
    }

    override fun refreshWifiDirectState() {
        wifiDirectController.refreshAll()
    }

    override fun markWifiDirectPermissionMissing() {
        wifiDirectController.markPermissionMissing()
    }

    override fun discoverPeers() {
        if (!sessionEnabled()) return
        wifiDirectController.discoverPeers()
    }

    override fun createGroup() {
        if (!sessionEnabled()) return
        wifiDirectController.createGroup()
    }

    override fun connect(deviceAddress: String) {
        if (!sessionEnabled()) return
        wifiDirectController.connect(deviceAddress)
    }

    override fun cancelConnect() {
        wifiDirectController.cancelConnect()
    }

    override fun cancelDirectAttempt() {
        wifiDirectController.cancelDirectAttempt()
    }

    override fun removeGroup() {
        wifiDirectController.removeGroup()
    }

    override fun disconnectAll() {
        wifiDirectController.removeGroup()
        wifiDirectController.cancelConnect()
        TransferForegroundService.stopSession(appContext)
    }

    override suspend fun requestSessionCredentials(
        hostAddress: String,
        clientId: String,
        deviceLabel: String
    ): Result<SessionCredentialsPayload> {
        return networkGate.run {
            FileTransfer.requestSessionCredentials(
                context = appContext,
                hostAddress = hostAddress,
                clientIdRaw = clientId,
                deviceLabelRaw = deviceLabel
            )
        }.onSuccess { credentials ->
            val peerId = credentials.peerId ?: return@onSuccess
            val noiseKey = credentials.noiseStaticKey ?: return@onSuccess
            TransferForegroundService.reportAuthenticatedPeer(
                appContext, peerId, credentials.peerLabel.orEmpty(), hostAddress, noiseKey
            )
        }
    }

    override suspend fun announcePresence(
        hostAddress: String,
        token: String,
        pin: String,
        clientId: String,
        deviceLabel: String
    ): Result<Unit> {
        return networkGate.run { FileTransfer.announcePresence(
            context = appContext,
            hostAddress = hostAddress,
            expectedPeerId = transferState.value.knownPeers.firstOrNull { it.ip == hostAddress }?.id,
            tokenRaw = token,
            pinRaw = pin,
            clientIdRaw = clientId,
            deviceLabelRaw = deviceLabel
        ) }
    }

    override suspend fun probePeer(
        hostAddress: String,
        clientId: String,
        deviceLabel: String
    ): Result<PeerDiscoveryPayload> {
        return networkGate.run { FileTransfer.probePeer(
            hostAddress = hostAddress,
            clientIdRaw = clientId,
            deviceLabelRaw = deviceLabel
        ) }
    }

    override fun startSession(
        token: String,
        pin: String,
        sessionExpiresAtMs: Long,
        receiveDirPath: String
    ) {
        if (!sessionEnabled()) return
        TransferForegroundService.startSession(
            context = appContext,
            token = token,
            pin = pin,
            sessionExpiresAtMs = sessionExpiresAtMs,
            receiveDirPath = receiveDirPath
        )
    }

    override fun stopSession() {
        if (transferState.value.serviceRunning) TransferForegroundService.stopSession(appContext)
    }

    override fun sendFile(
        fileUri: Uri,
        fileName: String,
        targetIp: String,
        token: String,
        pin: String,
        deviceLabel: String
    ) {
        if (!sessionEnabled()) return
        TransferForegroundService.sendFile(
            context = appContext,
            fileUri = fileUri,
            fileName = fileName,
            targetIp = targetIp,
            token = token,
            pin = pin,
            deviceLabel = deviceLabel
        )
    }

    override fun sendMessage(
        targetIp: String,
        token: String,
        pin: String,
        deviceLabel: String,
        message: String,
        peerLabelOverride: String?,
        scope: ChatMessageScope
    ) {
        if (!sessionEnabled()) return
        TransferForegroundService.sendMessage(
            context = appContext,
            targetIp = targetIp,
            token = token,
            pin = pin,
            deviceLabel = deviceLabel,
            message = message,
            peerLabelOverride = peerLabelOverride,
            scope = scope
        )
    }

    override fun sendBroadcastMessage(
        token: String,
        pin: String,
        deviceLabel: String,
        message: String,
        scope: ChatMessageScope,
        channelLabel: String,
        targetPeerIds: List<String>,
        targetIps: List<String>,
        targetLabels: List<String>
    ) {
        if (!sessionEnabled()) return
        TransferForegroundService.sendBroadcastMessage(
            context = appContext,
            token = token,
            pin = pin,
            deviceLabel = deviceLabel,
            message = message,
            scope = scope,
            channelLabel = channelLabel,
            targetPeerIds = targetPeerIds,
            targetIps = targetIps,
            targetLabels = targetLabels
        )
    }

    override fun registerChannelFileOffer(
        fileUri: Uri,
        fileName: String,
        deviceLabel: String,
        senderIp: String?
    ): ChannelFileOffer {
        check(sessionEnabled()) { "sesion_cerrada" }
        return ChannelFileOfferStore.register(
            context = appContext,
            uri = fileUri,
            fileName = fileName,
            fileSizeBytes = queryUriSize(fileUri),
            senderId = com.example.wifidrop.LocalDeviceIdentity.getOrCreate(appContext),
            senderLabel = deviceLabel,
            senderIp = senderIp
        )
    }

    override fun requestChannelFileOffer(
        offer: ChannelFileOffer,
        targetIp: String,
        token: String,
        pin: String,
        deviceLabel: String
    ) {
        if (!sessionEnabled()) return
        TransferForegroundService.requestChannelFileOffer(
            context = appContext,
            offer = offer,
            targetIp = targetIp,
            token = token,
            pin = pin,
            deviceLabel = deviceLabel
        )
    }

    override fun sendSilentMessage(
        targetIp: String,
        token: String,
        pin: String,
        deviceLabel: String,
        message: String,
        peerLabelOverride: String?,
        scope: ChatMessageScope
    ) {
        if (!sessionEnabled()) return
        TransferForegroundService.sendSilentMessage(
            context = appContext,
            targetIp = targetIp,
            token = token,
            pin = pin,
            deviceLabel = deviceLabel,
            message = message,
            peerLabelOverride = peerLabelOverride,
            scope = scope
        )
    }

    override fun retryMessage(messageId: String, deviceLabel: String) {
        if (!sessionEnabled()) return
        TransferForegroundService.retryMessage(
            context = appContext,
            messageId = messageId,
            deviceLabel = deviceLabel
        )
    }

    override fun cancelPendingMessage(messageId: String) {
        TransferForegroundService.cancelPendingMessage(
            context = appContext,
            messageId = messageId
        )
    }

    override fun deleteChatMessage(messageId: String) {
        TransferForegroundService.deleteChatMessage(
            context = appContext,
            messageId = messageId
        )
    }

    override fun clearChatMessages(scope: ChatMessageScope?) {
        TransferForegroundService.clearChatMessages(
            context = appContext,
            scope = scope
        )
    }

    override fun pauseTransfers() {
        TransferForegroundService.pauseTransfers(appContext)
    }

    override fun resumeTransfers() {
        if (!sessionEnabled()) return
        TransferForegroundService.resumeTransfers(appContext)
    }

    override fun cancelActiveTransfer() {
        TransferForegroundService.cancelActive(appContext)
    }

    override fun openDownloads() {
        TransferForegroundService.openDownloads(appContext)
    }

    override fun trustPeer(peerId: String, peerLabel: String) {
        TransferForegroundService.trustPeer(
            context = appContext,
            peerId = peerId,
            peerLabel = peerLabel
        )
    }

    override fun forgetPeer(peerId: String) {
        TransferForegroundService.forgetPeer(appContext, peerId)
    }

    override fun reportDiscoveredPeer(
        peerId: String,
        peerLabel: String,
        peerIp: String,
        trusted: Boolean,
        globalLanJoined: Boolean
    ) {
        if (!sessionEnabled()) return
        TransferForegroundService.reportDiscoveredPeer(
            context = appContext,
            peerId = peerId,
            peerLabel = peerLabel,
            peerIp = peerIp,
            trusted = trusted,
            globalLanJoined = globalLanJoined
        )
    }

    override fun approveCredentialShare(peerId: String, peerLabel: String, expectedNoiseStaticKey: String, requestedAtMs: Long) {
        if (!sessionEnabled()) return
        TransferForegroundService.approveCredentialShare(
            context = appContext,
            peerId = peerId,
            peerLabel = peerLabel,
            expectedNoiseStaticKey = expectedNoiseStaticKey,
            requestedAtMs = requestedAtMs
        )
    }

    override fun rejectCredentialShare(peerId: String) {
        TransferForegroundService.rejectCredentialShare(
            context = appContext,
            peerId = peerId
        )
    }

    override fun setPeerFavorite(peerId: String, favorite: Boolean) {
        TransferForegroundService.setPeerFavorite(
            context = appContext,
            peerId = peerId,
            favorite = favorite
        )
    }

    override fun setPeerAlias(peerId: String, alias: String) {
        TransferForegroundService.setPeerAlias(
            context = appContext,
            peerId = peerId,
            alias = alias
        )
    }

    override fun pauseQueueItem(transferId: String) {
        TransferForegroundService.pauseQueueItem(appContext, transferId)
    }

    override fun resumeQueueItem(transferId: String) {
        if (!sessionEnabled()) return
        TransferForegroundService.resumeQueueItem(appContext, transferId)
    }

    override fun cancelQueueItem(transferId: String) {
        TransferForegroundService.cancelQueueItem(appContext, transferId)
    }

    override fun moveQueueItemUp(transferId: String) {
        TransferForegroundService.moveQueueItemUp(appContext, transferId)
    }

    override fun moveQueueItemDown(transferId: String) {
        TransferForegroundService.moveQueueItemDown(appContext, transferId)
    }

    private fun queryUriSize(uri: Uri): Long {
        return try {
            appContext.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                pfd.statSize.takeIf { it >= 0L } ?: -1L
            } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }
}

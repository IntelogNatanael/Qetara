package com.example.wifidrop.backend

import android.content.Context
import android.net.Uri
import com.example.wifidrop.ChatMessageScope
import com.example.wifidrop.FileTransfer
import com.example.wifidrop.PeerDiscoveryPayload
import com.example.wifidrop.SessionCredentialsPayload
import com.example.wifidrop.TransferForegroundService
import com.example.wifidrop.TransferRuntimeState
import com.example.wifidrop.WifiDirectBroadcastReceiver
import com.example.wifidrop.WifiDirectController
import com.example.wifidrop.WifiDirectState
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
    fun reportDiscoveredPeer(
        peerId: String,
        peerLabel: String,
        peerIp: String,
        trusted: Boolean = false,
        globalLanJoined: Boolean = false
    )

    fun approveCredentialShare(peerId: String, peerLabel: String)
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
        wifiDirectController.discoverPeers()
    }

    override fun createGroup() {
        wifiDirectController.createGroup()
    }

    override fun connect(deviceAddress: String) {
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
        return FileTransfer.requestSessionCredentials(
            hostAddress = hostAddress,
            clientIdRaw = clientId,
            deviceLabelRaw = deviceLabel
        )
    }

    override suspend fun announcePresence(
        hostAddress: String,
        token: String,
        pin: String,
        clientId: String,
        deviceLabel: String
    ): Result<Unit> {
        return FileTransfer.announcePresence(
            context = appContext,
            hostAddress = hostAddress,
            tokenRaw = token,
            pinRaw = pin,
            clientIdRaw = clientId,
            deviceLabelRaw = deviceLabel
        )
    }

    override suspend fun probePeer(
        hostAddress: String,
        clientId: String,
        deviceLabel: String
    ): Result<PeerDiscoveryPayload> {
        return FileTransfer.probePeer(
            hostAddress = hostAddress,
            clientIdRaw = clientId,
            deviceLabelRaw = deviceLabel
        )
    }

    override fun startSession(
        token: String,
        pin: String,
        sessionExpiresAtMs: Long,
        receiveDirPath: String
    ) {
        TransferForegroundService.startSession(
            context = appContext,
            token = token,
            pin = pin,
            sessionExpiresAtMs = sessionExpiresAtMs,
            receiveDirPath = receiveDirPath
        )
    }

    override fun stopSession() {
        TransferForegroundService.stopSession(appContext)
    }

    override fun sendFile(
        fileUri: Uri,
        fileName: String,
        targetIp: String,
        token: String,
        pin: String,
        deviceLabel: String
    ) {
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

    override fun sendSilentMessage(
        targetIp: String,
        token: String,
        pin: String,
        deviceLabel: String,
        message: String,
        peerLabelOverride: String?,
        scope: ChatMessageScope
    ) {
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

    override fun reportDiscoveredPeer(
        peerId: String,
        peerLabel: String,
        peerIp: String,
        trusted: Boolean,
        globalLanJoined: Boolean
    ) {
        TransferForegroundService.reportDiscoveredPeer(
            context = appContext,
            peerId = peerId,
            peerLabel = peerLabel,
            peerIp = peerIp,
            trusted = trusted,
            globalLanJoined = globalLanJoined
        )
    }

    override fun approveCredentialShare(peerId: String, peerLabel: String) {
        TransferForegroundService.approveCredentialShare(
            context = appContext,
            peerId = peerId,
            peerLabel = peerLabel
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
}

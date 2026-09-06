package com.example.wifidrop

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.wifidrop.backend.AndroidP2pBackend
import com.example.wifidrop.presentation.P2pConnectionHintsPresenter
import com.example.wifidrop.presentation.P2pLanDiscoveryPresenter
import com.example.wifidrop.presentation.P2pOutboundPresenter
import com.example.wifidrop.presentation.P2pPeerActionsPresenter
import com.example.wifidrop.presentation.P2pSessionPresenter
import com.example.wifidrop.presentation.P2pShareImportPresenter
import com.example.wifidrop.presentation.P2pTrustPresenter
import com.example.wifidrop.presentation.P2pUiEffectsPresenter
import com.example.wifidrop.presentation.restoreSavedAttachments
import com.example.wifidrop.presentation.restoreSessionAfterProcessDeath
import com.example.wifidrop.presentation.saveAttachmentsForRecreation
import com.example.wifidrop.presentation.saveSessionForRecreation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect

data class P2pRoutePresenters(
    val backend: AndroidP2pBackend,
    val lanDiscoveryPresenter: P2pLanDiscoveryPresenter,
    val sessionPresenter: P2pSessionPresenter,
    val peerActionsPresenter: P2pPeerActionsPresenter,
    val connectionHintsPresenter: P2pConnectionHintsPresenter,
    val shareImportPresenter: P2pShareImportPresenter,
    val trustPresenter: P2pTrustPresenter,
    val outboundPresenter: P2pOutboundPresenter,
    val uiEffectsPresenter: P2pUiEffectsPresenter,
    val importPickedFiles: (List<android.net.Uri>, com.example.wifidrop.presentation.P2pAttachmentContext) -> Unit,
    val consumeIncomingShare: (IncomingSharePayload?) -> Unit
)

/** Keeps the active connection and prepared content when Android recreates the Activity. */
class P2pRouteViewModel(
    appContext: Context,
    localDeviceId: String,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val stateScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var incomingShareJob: Job? = null
    private var importingShareEventId: Long? = null
    private val restoredAttachments = restoreSavedAttachments(savedState.get<ArrayList<String>>(ATTACHMENTS_KEY))
    private val backend = AndroidP2pBackend(appContext.applicationContext)
    private val sessionPresenter = P2pSessionPresenter(
        appContext.applicationContext, backend, localDeviceId,
        restoreSessionAfterProcessDeath(savedState.get<ArrayList<String>>(SESSION_KEY), System.currentTimeMillis())
    )
    private val peerActionsPresenter = P2pPeerActionsPresenter(backend)
    private val shareImportPresenter = P2pShareImportPresenter(appContext.applicationContext, restoredAttachments)

    val presenters = P2pRoutePresenters(
        backend = backend,
        lanDiscoveryPresenter = P2pLanDiscoveryPresenter(appContext.applicationContext, backend, localDeviceId),
        sessionPresenter = sessionPresenter,
        peerActionsPresenter = peerActionsPresenter,
        connectionHintsPresenter = P2pConnectionHintsPresenter(peerActionsPresenter),
        shareImportPresenter = shareImportPresenter,
        trustPresenter = P2pTrustPresenter(backend),
        outboundPresenter = P2pOutboundPresenter(backend, shareImportPresenter, localDeviceId),
        uiEffectsPresenter = P2pUiEffectsPresenter(),
        importPickedFiles = { uris, context -> stateScope.launch { shareImportPresenter.importPickedUris(uris, context) }; Unit },
        consumeIncomingShare = ::consumeIncomingShare
    )

    init {
        stateScope.launch(start = CoroutineStart.UNDISPATCHED) {
            sessionPresenter.state.collect { savedState[SESSION_KEY] = saveSessionForRecreation(it) }
        }
        stateScope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Preserve the old snapshot until access checks finish, then save the current banks.
            shareImportPresenter.restoreSelections(restoredAttachments)
            shareImportPresenter.state.collect { state ->
                savedState[ATTACHMENTS_KEY] = saveAttachmentsForRecreation(shareImportPresenter.savedAttachments(state))
            }
        }
    }

    private fun consumeIncomingShare(payload: IncomingSharePayload?) {
        payload ?: return
        if (importingShareEventId == payload.eventId && incomingShareJob?.isActive == true) return
        incomingShareJob?.cancel()
        importingShareEventId = payload.eventId
        incomingShareJob = stateScope.launch { shareImportPresenter.consumeIncomingShare(payload) }
    }

    override fun onCleared() {
        presenters.lanDiscoveryPresenter.cancelScan()
        stateScope.cancel()
        super.onCleared()
    }

    private companion object {
        const val SESSION_KEY = "qetara.route.session.v1"
        const val ATTACHMENTS_KEY = "qetara.route.attachments.v1"
    }
}

private class P2pRouteViewModelFactory(
    private val appContext: Context,
    private val localDeviceId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass == P2pRouteViewModel::class.java)
        val savedStateExtras = MutableCreationExtras(extras).apply {
            // External share intents are input, not initial session or attachment state.
            this[DEFAULT_ARGS_KEY] = Bundle.EMPTY
        }
        return P2pRouteViewModel(appContext.applicationContext, localDeviceId, savedStateExtras.createSavedStateHandle()) as T
    }
}

@Composable
fun rememberP2pRoutePresenters(appContext: Context, localDeviceId: String): P2pRoutePresenters {
    val owner = LocalContext.current.findActivity() as? ComponentActivity
        ?: error("Qetara requires an Activity that owns saved state")
    return remember(owner, appContext, localDeviceId) {
        ViewModelProvider(
            owner.viewModelStore,
            P2pRouteViewModelFactory(appContext.applicationContext, localDeviceId),
            owner.defaultViewModelCreationExtras
        )[P2pRouteViewModel::class.java].presenters
    }
}

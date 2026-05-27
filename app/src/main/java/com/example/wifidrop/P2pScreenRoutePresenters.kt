package com.example.wifidrop

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.wifidrop.backend.AndroidP2pBackend
import com.example.wifidrop.presentation.P2pConnectionHintsPresenter
import com.example.wifidrop.presentation.P2pLanDiscoveryPresenter
import com.example.wifidrop.presentation.P2pOutboundPresenter
import com.example.wifidrop.presentation.P2pPeerActionsPresenter
import com.example.wifidrop.presentation.P2pSessionPresenter
import com.example.wifidrop.presentation.P2pShareImportPresenter
import com.example.wifidrop.presentation.P2pTrustPresenter
import com.example.wifidrop.presentation.P2pUiEffectsPresenter

data class P2pRoutePresenters(
    val backend: AndroidP2pBackend,
    val lanDiscoveryPresenter: P2pLanDiscoveryPresenter,
    val sessionPresenter: P2pSessionPresenter,
    val peerActionsPresenter: P2pPeerActionsPresenter,
    val connectionHintsPresenter: P2pConnectionHintsPresenter,
    val shareImportPresenter: P2pShareImportPresenter,
    val trustPresenter: P2pTrustPresenter,
    val outboundPresenter: P2pOutboundPresenter,
    val uiEffectsPresenter: P2pUiEffectsPresenter
)

@Composable
fun rememberP2pRoutePresenters(
    appContext: Context,
    localDeviceId: String
): P2pRoutePresenters {
    val backend = remember { AndroidP2pBackend(appContext) }
    val lanDiscoveryPresenter = remember {
        P2pLanDiscoveryPresenter(
            context = appContext,
            backend = backend,
            localDeviceId = localDeviceId
        )
    }
    val sessionPresenter = remember {
        P2pSessionPresenter(
            context = appContext,
            backend = backend,
            localDeviceId = localDeviceId
        )
    }
    val peerActionsPresenter = remember { P2pPeerActionsPresenter(backend) }
    val connectionHintsPresenter = remember { P2pConnectionHintsPresenter(peerActionsPresenter) }
    val shareImportPresenter = remember { P2pShareImportPresenter(appContext) }
    val trustPresenter = remember { P2pTrustPresenter(backend) }
    val outboundPresenter = remember {
        P2pOutboundPresenter(
            backend = backend,
            shareImportPresenter = shareImportPresenter,
            localDeviceId = localDeviceId
        )
    }
    val uiEffectsPresenter = remember { P2pUiEffectsPresenter() }

    return P2pRoutePresenters(
        backend = backend,
        lanDiscoveryPresenter = lanDiscoveryPresenter,
        sessionPresenter = sessionPresenter,
        peerActionsPresenter = peerActionsPresenter,
        connectionHintsPresenter = connectionHintsPresenter,
        shareImportPresenter = shareImportPresenter,
        trustPresenter = trustPresenter,
        outboundPresenter = outboundPresenter,
        uiEffectsPresenter = uiEffectsPresenter
    )
}

package com.example.wifidrop.presentation

import com.example.wifidrop.R
import com.example.wifidrop.appString

import com.example.wifidrop.PendingCredentialShareRequest
import com.example.wifidrop.PendingTrustRequest
import com.example.wifidrop.backend.P2pBackend

class P2pTrustPresenter(
    private val backend: P2pBackend
) {
    fun trustPeer(request: PendingTrustRequest): P2pFeedbackMessage? {
        backend.trustPeer(request.id, request.label)
        return null
    }

    fun approveCredentialShare(request: PendingCredentialShareRequest): P2pFeedbackMessage? {
        backend.approveCredentialShare(
            peerId = request.id,
            peerLabel = request.label,
            expectedNoiseStaticKey = request.noiseStaticKey.orEmpty(),
            requestedAtMs = request.requestedAtMs
        )
        // Service acceptance removes this exact pending request; a replaced request stays visible.
        return null
    }

    fun rejectCredentialShare(request: PendingCredentialShareRequest): P2pFeedbackMessage {
        backend.rejectCredentialShare(request.id)
        return P2pFeedbackMessage(appString(R.string.pr_trust_share_rejected, request.label))
    }
}

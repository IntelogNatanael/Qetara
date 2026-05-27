package com.example.wifidrop.presentation

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

    fun approveCredentialShare(request: PendingCredentialShareRequest): P2pFeedbackMessage {
        backend.approveCredentialShare(request.id, request.label)
        return P2pFeedbackMessage("Aprobaste compartir la sesión con ${request.label}.")
    }

    fun rejectCredentialShare(request: PendingCredentialShareRequest): P2pFeedbackMessage {
        backend.rejectCredentialShare(request.id)
        return P2pFeedbackMessage("Rechazaste compartir la sesión con ${request.label}.")
    }
}

package com.example.wifidrop.presentation

import com.example.wifidrop.R
import com.example.wifidrop.appString

import java.util.Locale

fun actionableTransferIssue(cause: String?, fallback: String): String? {
    val normalized = cause?.trim()?.lowercase(Locale.ROOT).orEmpty()
    if (normalized.isBlank()) return null

    return when {
        normalized.contains("sesion_cerrada") -> {
            appString(R.string.pr_error_session_closed)
        }

        normalized.contains("grupo_direct_no_acreditado") -> {
            appString(R.string.pr_error_direct_unconfirmed)
        }

        normalized.contains("grupo_direct_renovado") -> {
            appString(R.string.pr_error_direct_changed)
        }

        normalized.contains("destino_fuera_grupo_direct") -> {
            appString(R.string.pr_error_direct_outside)
        }

        normalized.contains("secure_credentials_required") ||
            normalized.contains("emparejamiento_manual_requerido") -> {
            appString(R.string.pr_error_manual_pairing)
        }

        normalized.contains("confirmacion_host_requerida") -> {
            appString(R.string.pr_error_approval_required)
        }

        normalized.contains("dispositivo_no_confiable") ||
            normalized.contains("no confiable") -> {
            appString(R.string.pr_error_untrusted)
        }

        normalized.contains("noise_key_mismatch") ||
            normalized.contains("clave noise") -> {
            appString(R.string.pr_error_identity_changed)
        }

        normalized.contains("auth_invalida") ||
            normalized.contains("autenticacion") ||
            normalized.contains("mac noise") ||
            normalized.contains("token") ||
            normalized.contains("pin") -> {
            appString(R.string.pr_error_credentials)
        }

        normalized.contains("sesion_expirada") ||
            normalized.contains("sesion expirada") ||
            normalized.contains("sesión expirada") ||
            normalized.contains("expired") -> {
            appString(R.string.pr_error_expired)
        }

        normalized.contains("permission") ||
            normalized.contains("permiso") -> {
            appString(R.string.pr_error_permissions)
        }

        normalized.contains("connection refused") ||
            normalized.contains("refused") ||
            normalized.contains("rechaz") -> {
            appString(R.string.pr_error_refused)
        }

        normalized.contains("unknownhost") ||
            normalized.contains("no route") ||
            normalized.contains("unresolved") -> {
            appString(R.string.pr_error_no_route)
        }

        normalized.contains("timeout") ||
            normalized.contains("timed out") ||
            normalized.contains("tiempo") -> {
            appString(R.string.pr_error_timeout)
        }

        normalized.contains("broken pipe") ||
            normalized.contains("connection reset") ||
            normalized.contains("eof") ||
            normalized.contains("interrump") -> {
            appString(R.string.pr_error_interrupted)
        }

        normalized.contains("hash") ||
            normalized.contains("sha-256") ||
            normalized.contains("integridad") -> {
            appString(R.string.pr_error_integrity)
        }

        normalized.contains("cancel") -> {
            appString(R.string.pr_error_canceled)
        }

        normalized.contains("wifi") ||
            normalized.contains("network") ||
            normalized.contains("socket") ||
            normalized.contains("host") ||
            normalized.contains("ip") -> {
            appString(R.string.pr_error_connection)
        }

        else -> fallback
    }
}

fun actionableSessionSyncFailure(errorMessage: String?): String {
    return actionableTransferIssue(
        cause = errorMessage,
        fallback = appString(R.string.pr_error_sync)
    ) ?: appString(R.string.pr_error_sync)
}

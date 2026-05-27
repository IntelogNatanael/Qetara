package com.example.wifidrop.presentation

import java.util.Locale

fun actionableTransferIssue(cause: String?, fallback: String): String? {
    val normalized = cause?.trim()?.lowercase(Locale.ROOT).orEmpty()
    if (normalized.isBlank()) return null

    return when {
        normalized.contains("confirmacion_host_requerida") -> {
            "El otro equipo debe aprobar compartir la sesión. Espera la aprobación y vuelve a intentar."
        }

        normalized.contains("dispositivo_no_confiable") ||
            normalized.contains("no confiable") -> {
            "El otro equipo aún no está confiado. Acepta la solicitud de confianza en Qetara y reintenta."
        }

        normalized.contains("auth_invalida") ||
            normalized.contains("autenticacion") ||
            normalized.contains("mac noise") ||
            normalized.contains("token") ||
            normalized.contains("pin") -> {
            "Token o PIN no coinciden. Sincroniza la sesión con el otro equipo y vuelve a enviar."
        }

        normalized.contains("noise_key_mismatch") ||
            normalized.contains("clave noise") -> {
            "La identidad segura del otro equipo cambió. Verifica que sea el equipo correcto y vuelve a confiarlo."
        }

        normalized.contains("sesion_expirada") ||
            normalized.contains("sesion expirada") ||
            normalized.contains("sesión expirada") ||
            normalized.contains("expired") -> {
            "La sesión expiró. Renuévala en Qetara antes de continuar."
        }

        normalized.contains("permission") ||
            normalized.contains("permiso") -> {
            "Falta un permiso necesario. Revisa permisos de Wi-Fi cercano/notificaciones y vuelve a intentar."
        }

        normalized.contains("connection refused") ||
            normalized.contains("refused") ||
            normalized.contains("rechaz") -> {
            "El receptor del otro equipo no está activo o rechazó la conexión. Abre Qetara allí y activa la sesión."
        }

        normalized.contains("unknownhost") ||
            normalized.contains("no route") ||
            normalized.contains("unresolved") -> {
            "No encuentro esa IP en la red. Verifica que ambos equipos estén en la misma Wi-Fi o usa Wi-Fi Direct."
        }

        normalized.contains("timeout") ||
            normalized.contains("timed out") ||
            normalized.contains("tiempo") -> {
            "El otro equipo no respondió a tiempo. Acércalos, revisa la red y vuelve a intentar."
        }

        normalized.contains("broken pipe") ||
            normalized.contains("connection reset") ||
            normalized.contains("eof") ||
            normalized.contains("interrump") -> {
            "La conexión se interrumpió durante la transferencia. Mantén ambas apps abiertas y reintenta."
        }

        normalized.contains("hash") ||
            normalized.contains("sha-256") ||
            normalized.contains("integridad") -> {
            "La verificación del archivo falló. Qetara descartó la copia incompleta; vuelve a enviarlo."
        }

        normalized.contains("cancelad") -> {
            "Operación cancelada."
        }

        normalized.contains("wifi") ||
            normalized.contains("network") ||
            normalized.contains("socket") ||
            normalized.contains("host") ||
            normalized.contains("ip") -> {
            "No se pudo completar la conexión. Revisa la IP, la red y que Qetara esté activo en el otro equipo."
        }

        else -> fallback
    }
}

fun actionableSessionSyncFailure(errorMessage: String?): String {
    return actionableTransferIssue(
        cause = errorMessage,
        fallback = "No se pudieron sincronizar las credenciales. Verifica la conexión y vuelve a intentar."
    ) ?: "No se pudieron sincronizar las credenciales. Verifica la conexión y vuelve a intentar."
}

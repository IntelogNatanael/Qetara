package com.example.wifidrop.presentation

import java.util.Locale

fun actionableTransferIssue(cause: String?, fallback: String): String? {
    val normalized = cause?.trim()?.lowercase(Locale.ROOT).orEmpty()
    if (normalized.isBlank()) return null

    return when {
        normalized.contains("sesion_cerrada") -> {
            "La sesión está cerrada. Toca Activar sesión para conectar o enviar otra vez."
        }

        normalized.contains("grupo_direct_no_acreditado") -> {
            "Todavía no se ha confirmado el equipo en este enlace Wi-Fi Direct. Mantén Qetara abierto en ambos; si no avanza, desconecta y vuelve a unirlos."
        }

        normalized.contains("grupo_direct_renovado") -> {
            "El enlace Wi-Fi Direct cambió. Vuelve a Conectar, revisa el equipo de destino y repite el envío."
        }

        normalized.contains("destino_fuera_grupo_direct") -> {
            "Ese equipo no forma parte del enlace Wi-Fi Direct actual. Elige un equipo del grupo o cambia a Misma Wi-Fi para conectarlo por la red local."
        }

        normalized.contains("secure_credentials_required") ||
            normalized.contains("emparejamiento_manual_requerido") -> {
            "Este equipo necesita emparejamiento manual. En Conectar, abre Credenciales e introduce el código de sesión y el PIN que muestra el otro equipo."
        }

        normalized.contains("confirmacion_host_requerida") -> {
            "El otro equipo debe aprobar la conexión. Compara la huella, espera la aprobación y vuelve a intentar."
        }

        normalized.contains("dispositivo_no_confiable") ||
            normalized.contains("no confiable") -> {
            "El otro equipo aún no está confiado. Acepta la solicitud de confianza en Qetara y reintenta."
        }

        normalized.contains("noise_key_mismatch") ||
            normalized.contains("clave noise") -> {
            "La identidad de este equipo cambió. Comprueba su huella en Acerca de Qetara. Si es tu equipo, ve a Conectar, Gestionar equipos recordados y olvídalo antes de volver a conectar."
        }

        normalized.contains("auth_invalida") ||
            normalized.contains("autenticacion") ||
            normalized.contains("mac noise") ||
            normalized.contains("token") ||
            normalized.contains("pin") -> {
            "Token o PIN no coinciden. Sincroniza la sesión con el otro equipo y vuelve a enviar."
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

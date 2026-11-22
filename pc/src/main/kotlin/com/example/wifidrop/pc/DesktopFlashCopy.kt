package com.example.wifidrop.pc

import java.util.Locale

/** Keep controller copy unresolved so retained errors/results follow the current desktop language. */
internal data class DesktopFlashText(val spanish: String, val english: String) {
    fun resolve(locale: Locale = Locale.getDefault()): String =
        if (locale.language == "es") spanish else english
}

internal fun flashText(es: String, en: String) = DesktopFlashText(es, en)
internal fun flashCopy(es: String, en: String): String = flashText(es, en).resolve()

internal fun flashFileCount(count: Int): String =
    if (count == 1) flashCopy("1 archivo", "1 file") else flashCopy("$count archivos", "$count files")

internal fun flashValidationText(code: String): DesktopFlashText = when (code) {
    "text_too_large" -> flashText("El texto no puede superar 16 KiB (16 384 bytes).", "Text cannot exceed 16 KiB (16,384 bytes).")
    else -> flashText("El texto contiene caracteres Unicode no válidos.", "The text contains invalid Unicode characters.")
}

/** Transport codes are stable; never expose the protocol's Spanish diagnostic or remote text as UI. */
internal fun flashFailureText(code: String): DesktopFlashText = when (code) {
    "discovery_failed" -> flashText("No se pudo buscar esa dirección. Comprueba la IP, la red y que Flash esté activo en el otro equipo.", "Could not search that address. Check the IP, network and that Flash is active on the other device.")
    "cancelled" -> flashText("Transferencia cancelada.", "Transfer cancelled.")
    "rejected" -> flashText("La transferencia no fue aprobada en ambos equipos.", "The transfer was not approved on both devices.")
    "expired" -> flashText("Flash terminó. Actívalo de nuevo para compartir.", "Flash expired. Turn it on again to share.")
    "approval_expired" -> flashText("La confirmación caducó. Vuelve a enviar y compara el código en ambos equipos.", "Approval expired. Send again and compare the code on both devices.")
    "busy" -> flashText("Flash está ocupado. Termina o cancela la operación actual.", "Flash is busy. Finish or cancel the current operation.")
    "invalid_file" -> flashText("El archivo no está disponible o supera el tamaño permitido.", "The file is unavailable or exceeds the size limit.")
    "invalid_batch" -> flashText("El lote supera el límite de 128 archivos o el tamaño permitido para sus nombres y datos. Reduce la selección.", "The batch exceeds 128 files or the allowed size for names and data. Select fewer files.")
    "batch_incompatible" -> flashText("El otro equipo no admite una confirmación por lote. Actualiza Qetara en ambos equipos.", "The other device does not support batch approval. Update Qetara on both devices.")
    "invalid_address" -> flashText("Usa una dirección IPv4 de tu red local.", "Use an IPv4 address on your local network.")
    "peer_changed" -> flashText("La activación del otro equipo cambió. Vuelve a buscarlo.", "The other device's session changed. Search for it again.")
    "integrity_failed", "invalid_frame" -> flashText("El archivo no superó la verificación y no se guardó.", "The file failed verification and was not saved.")
    "storage_unavailable" -> flashText("No se pudo guardar el archivo en la carpeta elegida.", "Could not save the file in the selected folder.")
    "incompatible" -> flashText("El otro equipo no usa una versión compatible de Flash.", "The other device is using an incompatible Flash version.")
    else -> flashText("La conexión se interrumpió. Comprueba en el receptor si llegó el archivo antes de volver a enviarlo.", "The connection was interrupted. Check whether the receiver got the file before sending again.")
}

internal enum class DesktopFlashSendActions { NONE, MESSAGE, FILES, BOTH }

/** Presentation only: the two operations still have separate approval and transport paths. */
internal fun desktopFlashSendActions(draft: String, fileCount: Int): DesktopFlashSendActions = when {
    draft.isNotBlank() && fileCount > 0 -> DesktopFlashSendActions.BOTH
    draft.isNotBlank() -> DesktopFlashSendActions.MESSAGE
    fileCount > 0 -> DesktopFlashSendActions.FILES
    else -> DesktopFlashSendActions.NONE
}

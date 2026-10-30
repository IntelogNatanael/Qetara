package com.example.wifidrop.pc

import java.io.FileNotFoundException
import java.io.IOException
import java.net.BindException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.nio.file.AccessDeniedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopTransferErrorTest {
    @Test fun deniedSocketAccessExplainsLocalNetworkPolicyInsteadOfFolderPermissions() {
        for (error in listOf(
            SocketException("Permission denied: connect"),
            SocketException("Access denied"),
            SocketException("Operation not permitted"),
            SocketException("Acceso denegado"),
            ConnectException("Permission denied: connect"),
            BindException("Permission denied: bind")
        )) {
            val copy = actionableDesktopError(error)
            assertTrue(copy.contains("red local"), "$error: $copy")
            assertTrue(copy.contains("VPN") && copy.contains("firewall"), "$error: $copy")
            assertFalse(copy.contains("carpeta") || copy.contains("puerto está ocupado"), "$error: $copy")
            assertFalse(copy.contains("desactiva", ignoreCase = true), "$error: $copy")
        }
    }

    @Test fun unreachableSocketRoutesDoNotSuggestFolderAccessOrAnInactiveReceiver() {
        for (error in listOf(
            SocketException("Network is unreachable: connect"),
            ConnectException("Network unreachable: connect"),
            SocketException("A socket operation was attempted to an unreachable network"),
            SocketException("Red no accesible"),
            NoRouteToHostException(),
            SocketException("No route to host")
        )) {
            val copy = actionableDesktopError(error)
            assertTrue(copy.contains("ruta disponible"), "$error: $copy")
            assertTrue(copy.contains("misma red"), "$error: $copy")
            assertFalse(copy.contains("carpeta") || copy.contains("Activa Recibir"), "$error: $copy")
        }
    }

    @Test fun realFileDenialsKeepTheStorageRecoveryMessage() {
        val expected = "No se puede acceder a esa carpeta. Elige una carpeta donde tengas permiso para guardar archivos."
        for (error in listOf(
            AccessDeniedException("received-files"),
            AccessDeniedException("received-files", null, "Permission denied"),
            FileNotFoundException("received-files/example.txt (Permission denied)"),
            IOException("Acceso denegado")
        )) assertEquals(expected, actionableDesktopError(error))
    }

    @Test fun authenticationAndHostApprovalMessagesRemainUnchanged() {
        val authentication = "El código o PIN no coincide. Copia la sesión del equipo receptor y vuelve a intentar."
        for (error in listOf(
            IllegalStateException("auth_invalida"),
            IOException("Autenticación fallida"),
            SocketException("auth_invalida")
        )) assertEquals(authentication, actionableDesktopError(error))
        assertEquals("Aprueba este equipo en el teléfono y vuelve a enviar. Compara antes la huella con Ajustes → Este equipo.",
            actionableDesktopError(IOException("confirmacion_host_requerida")))
    }

    @Test fun ordinarySocketFailuresKeepTheirExistingRecoveryMessages() {
        assertEquals("Se interrumpió la conexión. Comprueba que el receptor siga activo y vuelve a intentar.",
            actionableDesktopError(SocketException("Connection reset")))
        assertEquals("El equipo no responde. Activa Recibir en el otro equipo y comprueba la misma red y puerto.",
            actionableDesktopError(ConnectException("Connection refused")))
        assertEquals("El puerto está ocupado. Cierra otra instancia de Qetara o cambia el puerto en Ajustes.",
            actionableDesktopError(BindException("Address already in use")))
    }
}

# Usar Qetara

Qetara comparte archivos y mensajes directamente entre tus equipos, sin crear una cuenta. Mantén abiertas las aplicaciones durante el primer intercambio.

1. [Guía para Android](ANDROID_GUIDE.md): conectar, enviar, conversar y encontrar descargas.
2. [Guía para PC](DESKTOP_GUIDE.md): preparar una sesión, elegir destino y recibir archivos.
3. [Flash opcional](FLASH.md): uno o varios archivos por la red local sin escribir credenciales.
4. [Privacidad](PRIVACY.md): qué información permanece en cada equipo y qué se anuncia en la red.

## Compartir puntualmente con Flash

Abre **Flash** en ambos equipos y actívalo. Elige uno o varios archivos y el destinatario y pulsa **Solicitar envío**. Qetara procesa la cola de uno en uno: compara los cuatro grupos de verificación y acepta cada archivo en los dos dispositivos. Flash está apagado al iniciar el proceso y termina a los 30 minutos o al desactivarlo. La sesión habitual se controla por separado. Consulta [Flash](FLASH.md) para recibir, cancelar y abrir lo recibido.

## Primer intercambio habitual Android ↔ PC

Conecta ambos a la misma Wi-Fi. En PC crea una sesión y activa la recepción. En Android elige **Misma Wi-Fi**, selecciona el PC o introduce su IP y copia su código de sesión y PIN. Confirma esos datos, elige uno o varios archivos y envíalos. Espera la confirmación antes de cerrar Qetara.

El código tiene ocho caracteres y el PIN seis cifras. Son temporales: usa los que aparecen en la sesión activa. El intercambio automático de credenciales con aprobación de identidad está disponible entre Android compatibles; el PC utiliza código y PIN manuales.

## Si no conecta

- Comprueba que el receptor sigue activo y que la sesión no caducó.
- Usa la dirección local del receptor si el descubrimiento no lo muestra.
- Una red de invitados puede impedir la comunicación entre equipos incluso si comparten Wi-Fi.
- Si Windows solicita acceso de red, revisa el aviso y limita el permiso a una red privada de confianza. No hace falta desactivar el firewall.
- Entre dos Android compatibles puedes usar Wi-Fi Direct. Sigue los permisos y las indicaciones del sistema.

## Instalar una actualización

Android exige que una actualización conserve el identificador de aplicación y la firma del paquete anterior. La entrega 1.3.0 distingue el APK de distribución del APK de depuración USB. Para conservar los datos usa el que tenga la misma firma que la instalación anterior; consulta el LEEME de la entrega. Cambiar de firma no es una actualización directa.

La validación de esta entrega distingue las pruebas realizadas en el emulador y en Windows de los recorridos que necesitan dos equipos físicos. Consulta el informe de validación incluido con la distribución.

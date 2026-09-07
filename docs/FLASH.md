# Flash en Android y PC

Flash permite compartir archivos puntuales por la red local sin preparar credenciales. Es una función que activas cuando la necesitas y está apagada al iniciar Qetara. No requiere cuenta ni internet. Ambos dispositivos deben usar una versión de Qetara compatible con Flash.

## Un envío completo

1. Conecta ambos equipos a la misma red local. Abre **Flash** en Qetara y actívalo en los dos.
2. En el equipo que envía, elige un archivo. Esta selección todavía no comparte su contenido.
3. Busca y selecciona el otro equipo. Si no aparece, usa su dirección IP local; Flash también debe estar activo allí. En redes con aislamiento de clientes, ni la búsqueda ni la dirección manual pueden atravesar ese aislamiento.
4. Inicia el envío. Comprueba el nombre y tamaño del archivo, el equipo y la verificación que muestran las dos pantallas. Los cuatro grupos deben coincidir exactamente. Acepta en ambos equipos únicamente después de compararlos; si no coinciden, rechaza.
5. Sigue el progreso. El resultado confirma si el receptor terminó de guardar y verificar el archivo. Puedes cancelar durante la espera o la transferencia y abrir el archivo recibido cuando termine.
6. Desactiva Flash para dejar de estar disponible. También termina automáticamente al cumplirse 30 minutos desde la activación.

El envío requiere una decisión expresa en ambos dispositivos. Compartir la Wi-Fi, ver un nombre familiar o haber usado Qetara anteriormente no acepta una transferencia por sí solo. No es necesario escribir la verificación: se compara visualmente.

## Qué permanece y qué termina

La activación y su identidad son temporales y no se restauran después de cerrar el proceso. Una nueva activación genera otra identidad. Desactivar o agotar el tiempo detiene las conexiones y las solicitudes pendientes; una aceptación tardía no reabre Flash. Los archivos ya recibidos permanecen guardados.

En Android, la notificación permite reconocer y detener la sesión activa mientras consultas otras pantallas. En PC, la sesión permanece activa al volver a otro espacio de Qetara. La recepción habitual y Flash se controlan por separado: cerrar uno no debe confundirse con cerrar el otro.

Flash muestra el resultado de la operación dentro de su pantalla. En Android se pueden abrir desde allí; en PC, **Ver en carpeta** abre su ubicación para que elijas qué hacer con el archivo. En Android se publican en Descargas cuando el sistema lo permite; en PC se guardan en la carpeta indicada en Flash.

## Privacidad y límites

La búsqueda local expone el nombre del equipo, una identidad temporal, su dirección y disponibilidad a otros equipos de la red. Los nombres son informativos y no demuestran identidad. Los metadatos del archivo y su contenido viajan por un canal cifrado Noise XX; la comparación en ambas pantallas es necesaria para verificar el canal sin credenciales previas. Consulta [Privacidad](PRIVACY.md) y [Protocolo](PROTOCOL.md).

Esta versión de Flash comparte un archivo por operación y requiere verificar cada envío. No reanuda automáticamente una transferencia interrumpida: elimina el parcial cuando detecta el corte o la cancelación y permite volver a enviarlo. Un cierre abrupto del proceso puede dejar temporales o una reserva vacía; no ofrece recuperación automática después de un fallo del proceso. Un archivo completo puede haber llegado aunque se pierda la confirmación final; comprueba el receptor antes de repetirlo. Los archivos con el mismo nombre no sustituyen los anteriores. La conexión habitual mantiene sus funciones de mensajes, lotes y recuperación.

Flash utiliza TCP y UDP 8989. Las redes invitadas, algunos routers y el firewall pueden impedir el acceso. Una dirección manual solo sustituye la búsqueda; no cambia las restricciones de la red. Esta implementación admite direcciones IPv4 locales, privadas o de enlace local; no es una función para publicar archivos en internet.

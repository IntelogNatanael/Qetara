# Qetara para Windows

Qetara permite compartir archivos y conversar entre equipos de una misma red local. Puedes conectar una PC con Android o con otra PC, sin crear una cuenta ni subir los archivos a un servicio de almacenamiento.

## Flash opcional

El botón **Flash** abre el envío temporal sin escribir código de sesión ni PIN. Actívalo en ambos equipos, elige un archivo y el destino y pulsa **Solicitar envío**. Compara los cuatro grupos en ambas pantallas antes de aceptar. **Flash activo** mantiene visible la disponibilidad aunque vuelvas a otro espacio. Cada activación caduca a los 30 minutos; **Desactivar** termina únicamente Flash. La carpeta de recepción se elige antes de activarlo y **Ver en carpeta** permite localizar lo recibido. La [guía de Flash](FLASH.md) incluye el recorrido completo y la alternativa por dirección local.

## Abrir Qetara

Extrae la carpeta completa de la distribución para Windows y abre **Qetara.exe**. Conserva las carpetas que acompañan al ejecutable: forman parte de la aplicación.

La ventana se adapta al área disponible del monitor. Si una vista tiene más contenido del que cabe, usa la rueda del ratón o la barra de desplazamiento del lateral derecho.

| Vista | Para qué sirve |
| --- | --- |
| **Compartir** | Elegir un archivo y el equipo que lo recibirá. |
| **Recibir** | Activar la recepción, elegir dónde guardar y revisar archivos recibidos. |
| **Mensajes** | Conversar directamente o participar en el Canal Wi-Fi. |
| **Actividad** | Revisar operaciones y errores de esta apertura de Qetara. |
| **Ajustes** | Cambiar el nombre del equipo, puerto, duración de sesión y reintentos. |

## Conectar dos equipos

Ambos equipos deben usar **el mismo código de sesión y el mismo PIN**.

1. En la PC que recibirá, abre **Compartir** o **Recibir** y pulsa **Crear una sesión**.
2. Qetara genera un código de ocho caracteres y un PIN de seis dígitos. Usa **Ver** para mostrar el PIN si necesitas leerlo.
3. Pulsa **Activar recepción aquí**, o ve a **Recibir → Activar recepción**.
4. En el otro equipo, introduce ese código y PIN. En Android, utiliza la conexión manual de su vista de conexión.
5. Indica la dirección de la PC receptora si no aparece al buscar equipos. Se muestra en **Recibir** cuando está activa y en **Ajustes → Tu red local**.

**Copiar datos para conectar** copia dirección, puerto, código y PIN al portapapeles. Compártelos con la persona que conectará el otro equipo.

Si la sesión ya se creó en Android o en otra PC, escribe sus datos en la tarjeta **Conecta los dos equipos**. No pulses **Crear otra sesión**: eso generaría datos distintos. Que el código y PIN sean válidos no confirma que coincidan con los del receptor; la comprobación ocurre al conectar.

Qetara PC usa emparejamiento manual. No entrega el código ni el PIN a quien los solicite por red.

Al conectar una PC nueva a un receptor Android, el teléfono puede pedir que apruebes ese equipo antes de recibir contenido. Compara la huella que muestra la solicitud con **Ajustes → Este equipo** en la PC. Si coincide, aprueba el equipo en el teléfono y vuelve a enviar desde la PC. La huella identifica la clave pública del dispositivo; no es el código ni el PIN de sesión.

## Enviar un archivo desde la PC

1. Abre **Compartir**.
2. Arrastra un archivo sobre la ventana o pulsa el área **Arrastra y suelta un archivo aquí**.
3. Pulsa **Buscar equipos** y selecciona el receptor. También puedes escribir su IP o nombre de equipo.
4. Comprueba que el receptor esté activo y que ambos equipos tengan la misma sesión.
5. Pulsa **Enviar archivo**.

El estado distingue la preparación, el envío y la verificación por el receptor. Qetara confirma el envío después de que el otro equipo haya aceptado y verificado el archivo.

- La PC envía **un archivo por operación**. Si arrastras varios, selecciona el primero y te lo indica.
- Para compartir una carpeta, comprímela primero en un archivo.
- **Quitar archivo** retira la selección; no elimina el original.
- **Cancelar envío** detiene el envío en curso. Si vuelves a enviar el mismo archivo al mismo destino, Qetara puede aprovechar los datos parciales que conserve el receptor.
- Los reintentos automáticos mantienen la identidad de la operación. Un intento ya registrado por el receptor puede confirmarse sin guardar otra copia.

## Recibir y encontrar lo recibido

En **Recibir**, elige la carpeta de destino antes de activar la recepción. La carpeta inicial es **Downloads/Qetara**, normalmente mostrada dentro de **Descargas** por el Explorador de Windows.

Cuando el receptor esté disponible, la cabecera indica **Disponible para recibir**. Durante una transferencia se muestra el progreso y, al terminar, aparece un aviso y una entrada en **Archivos recientes**.

- **Abrir carpeta** abre la carpeta de destino.
- **Ver en carpeta** abre la ubicación del archivo recibido.
- Si ya existe un archivo con el mismo nombre, Qetara guarda la nueva copia con otro nombre; conserva el anterior.
- Los archivos sólo se publican en la carpeta de destino después de verificar su integridad. Los datos de una recepción incompleta permanecen en una subcarpeta de trabajo para permitir la reanudación.
- **Detener recepción** cierra la recepción. Los archivos que ya se guardaron permanecen en su carpeta.

Los datos de sesión y la carpeta quedan fijos mientras el receptor está activo. Detén la recepción para cambiarlos.

## Mensajes, archivos adjuntos y conversaciones

### Chat directo

Abre **Mensajes → Chat directo**, elige un equipo y escribe el mensaje. Puedes adjuntar un archivo, enviar texto o enviar ambos.

Pulsa **Enviar** o utiliza **Ctrl + Enter**. **Enter** sin Ctrl añade un salto de línea. Cada mensaje admite hasta 2000 caracteres.

Para recibir respuestas en tu PC, activa también la recepción. Los dos equipos necesitan el mismo código y PIN.

Los borradores y adjuntos se mantienen separados por destinatario. Cambiar de conversación no coloca el borrador anterior en el nuevo chat. Si editas un texto mientras se está enviando, la confirmación del envío no elimina el nuevo contenido.

Durante el envío puedes preparar el siguiente texto o adjunto. La operación en curso conserva su propio contenido y puedes cancelarla; no empieza otra hasta que termine o se detenga. Si cambias de conversación, el resultado identifica al destinatario original.

Si un equipo deja de estar disponible, su conversación puede seguir leyéndose. Qetara no cambia silenciosamente el envío a otro destinatario.

### Canal Wi-Fi

Al abrir **Canal Wi-Fi**, participas en el canal de esta PC. Los mensajes se envían a los equipos detectados que también se hayan unido y utilicen la misma sesión. Usa **Salir del canal** para dejar de participar.

Un archivo compartido en el canal aparece como una oferta que los demás pueden descargar. Para descargarlo en tu PC, activa **Recibir**. La opción de descarga automática está desactivada inicialmente; puedes activarla en la vista del canal.

La oferta necesita que el equipo que conserva el archivo siga disponible. El archivo no se sube a un servidor central. Al salir del canal, detener la recepción, cambiar de sesión o cerrar Qetara se retiran las ofertas de esta PC y se cancelan sus envíos del canal. Se atiende una descarga del canal a la vez; si está ocupada, el otro equipo puede volver a solicitarla al terminar.

Si sólo parte del canal confirma la entrega, Qetara muestra los recuentos y conserva lo pendiente en el borrador. Un nuevo envío manual vuelve a dirigirse a todos los equipos del canal que estén disponibles; algunos pueden recibirlo otra vez. Revisa el resultado antes de repetir.

### Avisos y borradores

Cuando llega un mensaje fuera de la conversación visible, aparece un aviso y aumenta el contador de mensajes nuevos. Abre **Mensajes** o selecciona la conversación que tenga mensajes pendientes.

Los mensajes, avisos, borradores y adjuntos pendientes de la interfaz se mantienen durante esta apertura de Qetara. **Los borradores no se guardan al cerrar la aplicación.** Los archivos ya recibidos sí permanecen en su carpeta.

## Sesión y preferencias

En **Ajustes** puedes elegir:

| Ajuste | Comportamiento |
| --- | --- |
| **Nombre visible** | Es el nombre con el que reconocerás esta PC en el otro equipo. |
| **Puerto** | Debe coincidir en ambos equipos. El valor inicial es 8988. |
| **Reintentos** | Entre 1 y 10. El valor inicial es 3. |
| **Sesión (minutos)** | Entre 1 y 1440. El valor inicial es 120 minutos. |

Pulsa **Guardar preferencias** para conservar los ajustes. La carpeta elegida también se recuerda.

La recepción termina al cumplirse la duración de la sesión. Actívala de nuevo cuando necesites seguir recibiendo. Puedes conservar el código y PIN o crear otra sesión para usar datos nuevos.

Las preferencias guardan el nombre, carpeta y opciones de conexión. **No guardan el código ni el PIN.** Qetara conserva por separado una identidad local de dispositivo para su funcionamiento.

## Si algo no funciona

### No aparece el otro equipo

Comprueba que Qetara esté abierto, la recepción esté activa y ambos equipos estén en la misma red. Pulsa **Buscar equipos** de nuevo o introduce la IP manualmente. Una red de invitados puede impedir que sus equipos se comuniquen entre sí.

Si cambiaste de Wi-Fi o conectaste un cable de red, utiliza **Ajustes → Actualizar red**.

### Windows pregunta por el acceso a la red

Si el Firewall de Windows pregunta por Qetara o Java, permite el acceso **sólo en la red privada que reconoces**. No hace falta habilitar redes públicas para compartir dentro de tu red privada. Qetara no cambia las reglas del firewall automáticamente.

En un equipo administrado por una organización, sigue la configuración de red de esa organización.

### El código o PIN no coincide

Comprueba los datos en el receptor. Si generaste una nueva sesión en uno de los equipos, actualiza también el otro. Detén la recepción antes de editar sus datos en la PC.

### El puerto está ocupado

Cierra otra instancia de Qetara que esté usando el mismo puerto, o elige otro puerto en **Ajustes** y utiliza ese mismo valor en el otro equipo.

### Falta espacio o no se puede guardar

Libera espacio en el receptor o elige una carpeta donde puedas guardar archivos. Después vuelve a enviar el archivo. Los mensajes de estado y **Actividad** indican el motivo del fallo.

### Se interrumpió la transferencia

Comprueba que el receptor siga activo y que la sesión no haya terminado. Vuelve a intentar el envío. Qetara puede reanudar un archivo parcial compatible; conserva siempre el archivo original hasta verificar la recepción.

## Alcance de esta versión de PC

- La conexión de PC funciona por la red local. Las modalidades de retransmisión y canales Wi-Fi Direct propias de Android no se implementan en PC; utiliza **Chat directo** o **Canal Wi-Fi**.
- El código y PIN permiten acceder a la sesión. La PC no identifica por sí sola a la persona que los está usando.
- Las conversaciones y la actividad de la interfaz no son un archivo permanente de mensajes.

En **Ajustes → Hecho para compartir → Licencias de código abierto** puedes leer y seleccionar los avisos y textos de licencia incluidos en la instalación. El diálogo se consulta sin conexión y dispone de barra de desplazamiento.

# Qetara para Android

Qetara permite compartir archivos y mensajes entre equipos cercanos. No requiere una cuenta. Para empezar con un PC, conecta ambos equipos a la misma red Wi-Fi y abre Qetara en los dos.

La aplicación está configurada para Android 7.0 (API 24) o posterior. Consulta el [README del proyecto](../README.md) para identificar el APK de la entrega y su forma de instalación. Qetara 1.3.0 incorpora Flash opcional y las mejoras de privacidad evaluadas tras 1.2.0. La comprobación física permanece pausada; consulta [Validación](VALIDATION.md) para distinguir las pruebas de esta entrega de las realizadas anteriormente.

## Flash opcional

Abre **Flash** para un envío puntual por la misma Wi-Fi, sin escribir código de sesión ni PIN. Actívalo en los dos dispositivos, selecciona uno o varios archivos y el destino. Revisa todos los nombres, el número de archivos y el tamaño total; compara los cuatro grupos del código y acepta una vez en cada equipo para enviar ese lote. Qetara procesa los archivos en orden y muestra el resultado de cada uno. Otro lote requiere una nueva confirmación. La notificación permite detener la sesión activa; también caduca a los 30 minutos. Al reiniciar el proceso empieza apagado. La [guía de Flash](FLASH.md) explica recepción, cancelación, compatibilidad y qué hacer si la búsqueda no encuentra al otro equipo.

## Elegir cómo conectar

En **Conectar**, elige uno de estos modos:

| Modo | Para qué sirve | Qué preparar |
| --- | --- | --- |
| **Misma Wi-Fi** | Conectar Android con Android o con un PC. | Ambos equipos en la misma red local, con Qetara abierto. No hace falta que la red tenga internet. |
| **Wi-Fi Direct** | Crear un enlace cercano entre equipos Android. | Wi-Fi encendida, permiso de dispositivos cercanos o ubicación según Android y soporte Wi-Fi Direct en ambos equipos. |

El selector bajo el nombre Qetara también permite cambiar de modo. La vista **Completo** conserva los controles avanzados; los pasos siguientes usan la vista normal de Conectar.

## Primer uso con un PC

1. Conecta el Android y el PC a la misma red Wi-Fi. Abre Qetara en ambos.
2. En el PC, crea una sesión y deja visibles su **IP**, **código de sesión** y **PIN de 6 dígitos**.
3. En Android, entra en **Conectar → Misma Wi-Fi** y toca **Buscar equipos**. Selecciona el PC cuando aparezca.
4. Si no aparece, abre **Conectar con una dirección IP**, escribe la IP que muestra el PC y toca **Conectar por IP**. Escribe solo la dirección, como **192.168.1.8**, sin protocolo, puerto ni espacios.
5. La versión de PC usa emparejamiento manual. Cuando Android muestre **Sesión del otro equipo**, introduce el código y el PIN del PC. La IP selecciona el equipo; el código y el PIN preparan la sesión para compartir con él.
6. Toca **Usar esta sesión** para abrir Enviar. También puedes usar la pestaña Chat después de completar los datos.

Si el PC va a enviar por primera vez a Android, usa en el PC el código y el PIN que muestra Android. El teléfono puede pedirte aprobar la conexión: compara la huella con **Ajustes → Este equipo** del PC, aprueba y vuelve a intentar el envío desde el PC.

Si necesitas abrir el formulario de nuevo, usa **Ver o introducir credenciales** en Conectar. **Copiar código** copia el código de la sesión actual; **Pegar código** lo toma del portapapeles. El PIN se introduce por separado.

El PIN está oculto mientras lo introduces. **Mostrar** permite comprobarlo sin abandonar el campo; **Ocultar** vuelve a cubrirlo. Al pausar la aplicación, girar la pantalla o cambiar la sesión, vuelve a ocultarse. También se oculta al perder el foco de la ventana, aunque Qetara siga activa, y no se revela automáticamente al regresar. Para mostrarlo otra vez, la ventana debe estar activa y tener el foco. Este ajuste se comprobó en emulador Android 16; puedes tocar **Ocultar** antes de abrir Recientes cuando necesites cubrirlo de inmediato.

Qetara marca las copias como sensibles para pedir al sistema que oculte su vista previa. El contenido sigue disponible para pegarlo. En Android 13 o posterior también pide ocultar la miniatura guardada de aplicaciones recientes; la transición en directo depende del sistema. Ocultar el PIN no elimina un fotograma que Android ya haya capturado. Las capturas de pantalla voluntarias siguen disponibles. Consulta los alcances y las comprobaciones físicas en [Privacidad](PRIVACY.md).

Al enviar hacia un receptor, usa el código y el PIN que muestra ese receptor. Si cambias los datos o renuevas la sesión en uno de los equipos, vuelve a comprobar que ambos usan los mismos valores. Una sesión expirada se recupera con **Renovar sesión**; la renovación local no modifica por sí sola la sesión del otro equipo.

## Conectar dos Android

### En la misma red Wi-Fi

1. Abre Qetara en los dos y elige **Misma Wi-Fi**.
2. Busca equipos y selecciona el otro Android. La dirección IP manual sirve como alternativa cuando el descubrimiento de la red no lo encuentra.
3. Si aparece una solicitud de conexión en el otro Android, revisa el nombre, la IP y la **huella del equipo** antes de aprobarla.
4. Compara esa huella con la de **Acerca de Qetara** en el Android que inició la solicitud. Al aprobar, compartes la sesión para intercambiar archivos y mensajes.

### Con Wi-Fi Direct

1. En los dos Android, elige **Wi-Fi Direct**.
2. Si aparece **Permitir conexión cercana**, toca el botón y concede el permiso de Android.
3. En un equipo, toca **Crear un enlace**.
4. En el otro, toca **Buscar un equipo** y selecciona el Android que creó el enlace.
5. Acepta la solicitud de conexión del sistema si aparece. Después, revisa la solicitud de Qetara para compartir la sesión.
6. Mantén ambas aplicaciones abiertas hasta completar el enlace. Para terminar, usa **Desconectar equipo** o **Cerrar enlace**, según el estado mostrado.

Wi-Fi Direct es una función Android. La aplicación de PC usa la red local. Si eres el anfitrión y aún no hay un miembro acreditado del grupo, Qetara espera al otro Android antes de ofrecer un destino. El funcionamiento de Direct depende del hardware y de la implementación de Android; esta entrega no acredita una prueba de enlace Direct entre dos teléfonos físicos.

## Cerrar y volver a activar la sesión

En **Conectar**, toca **Cerrar sesión** cuando quieras dejar de compartir. Esta acción detiene la recepción y cancela las operaciones de transferencia en curso; no desconecta la red Wi-Fi del sistema. Los archivos recibidos, el historial y los borradores de archivos o mensajes se conservan.

La sesión permanece cerrada aunque vuelvas a abrir Qetara o cambie la red. Puedes seguir preparando archivos y consultando Descargas. Para conectar o enviar otra vez, toca **Activar sesión** en la banda visible y comprueba el destinatario y sus datos de sesión. Cambiar el tamaño del texto o el modo de conexión no reactiva una sesión cerrada.

## Permisos sin interrumpir el inicio

La pantalla de bienvenida permite explorar la aplicación sin pedir permisos automáticamente.

- **Conexión cercana:** se solicita al tocar la acción correspondiente de Wi-Fi Direct. Si Android ya no permite mostrar el diálogo, la misma acción abre los ajustes de Qetara para que puedas conceder el permiso. Al volver, la aplicación comprueba el estado otra vez.
- **Notificaciones:** Qetara las solicita en el contexto de la primera transferencia activa mientras la aplicación está visible. Permiten ver su actividad fuera de la pantalla de Qetara. Denegarlas no equivale a cancelar una transferencia.
- **Archivos:** el selector de documentos de Android permite elegir los archivos que quieres enviar. Seleccionar un archivo todavía no lo envía.

Si Wi-Fi está apagada, usa **Abrir ajustes Wi-Fi**, actívala y vuelve a Qetara. El permiso de Wi-Fi Direct y la conexión a una red Wi-Fi son pasos diferentes.

## Enviar archivos

1. Abre **Enviar** y elige uno o varios archivos en el selector de Android.
2. Revisa el equipo de destino y la selección. Si hay muchos archivos, usa **Revisar archivos** para ver la lista completa.
3. Toca el botón de envío cuando el destino y la sesión estén preparados.
4. Sigue el progreso en la misma pantalla. **Ver cola** permite revisar los elementos pendientes y actuar sobre ellos. Al terminar, una banda visible mantiene el resultado del último lote: sólo muestra **Último envío completado** cuando el receptor confirmó todos los archivos. Su botón **Actividad** abre el historial directamente; un lote fallido o cancelado aparece como incompleto.

Puedes preparar una selección antes de conectar otro equipo. Si falta el destino, vuelve a **Conectar un equipo**; la selección permanece en la aplicación.

La cola ofrece controles para pausar, reanudar, cancelar y cambiar el orden de los elementos pendientes cuando corresponde a su estado. Un archivo cancelado o fallido no debe interpretarse como entregado. Revisa el resultado en **Descargas → Actividad** antes de repetir un envío.

## Conservar lo preparado

Al girar el dispositivo o recrearse la pantalla, Qetara conserva los adjuntos de Enviar, Chat y Canal y mantiene el código, el PIN y el vencimiento de la sesión. Girar no renueva una sesión caducada ni reactiva una sesión que cerraste.

Si Android recupera una tarea después de terminar su proceso, Qetara intenta recuperar el texto y los archivos a los que aún tenga acceso. Un archivo borrado o cuyo permiso ya no sea válido se omite. El aviso queda visible junto a la selección hasta que elijas de nuevo o limpies los adjuntos; el texto del borrador se conserva. Revisa el equipo y confirma la sesión antes de enviar. Esta recuperación está limitada para selecciones muy grandes, no sustituye una copia guardada y no cubre borrar los datos, desinstalar o eliminar voluntariamente la tarea.

Cuando un destinatario elegido deja de estar disponible, Qetara conserva esa intención y espera: no sustituye silenciosamente el equipo por otro miembro. En un grupo Direct con varios miembros, el anfitrión debe elegir a quién enviará.

## Chat directo

En **Chat**, comprueba el destinatario antes de escribir. La dirección IP que elegiste manualmente en Conectar también sirve para Chat, aunque el descubrimiento no encuentre ese equipo. La sesión debe estar sincronizada o confirmada para ese destinatario. Los equipos disponibles aparecen como opciones desplazables horizontalmente. En una red Wi-Fi normal, el chat directo elige un destinatario. En Wi-Fi Direct, un cliente se dirige al anfitrión oficial; el anfitrión sólo ofrece equipos que ya han establecido una conexión verificada dentro del grupo actual. Los equipos conocidos únicamente por LAN no aparecen como participantes Direct.

El cuadro permite escribir hasta **2.000 caracteres** y adjuntar archivos. Si una acción no está disponible, el mensaje de la pantalla explica si falta un equipo, una sesión válida o una ruta compatible para los adjuntos. Los borradores de texto de Chat y Canal se mantienen separados al cambiar de pestaña. Enviar, Chat y Canal también guardan selecciones de archivos independientes: al volver a una pestaña recuperas sus adjuntos. Quitar adjuntos retira solo la selección de esa pestaña, sin borrar los archivos originales. Compartir hacia Qetara desde otra aplicación abre una selección en Enviar.

Usa el buscador para localizar texto o nombres de equipos en los mensajes guardados. El menú de cada mensaje permite copiar, compartir o eliminar su copia local; un mensaje con error puede ofrecer **Reintentar envío**. Si estás leyendo mensajes anteriores, el botón de mensajes nuevos permite volver al final.

**Historial de chats directos** reúne los mensajes directos guardados en este Android; seleccionar un destinatario cambia a quién envías, pero no convierte ese historial en una conversación filtrada exclusiva de ese equipo.

Eliminar un mensaje actúa sobre el historial de este Android. **Limpiar chat** elimina todos los mensajes locales del canal elegido, incluidos los que no aparecen en una búsqueda. No borra la copia que pueda conservar el otro equipo.

## Canal de la red Wi-Fi

**Canal** permite conversar con otros participantes de Qetara que hayan entrado al canal de la misma red local. Su contenido se dirige al canal, por lo que debes comprobar esta pestaña antes de compartir algo destinado a una sola persona.

1. Conecta el Android a la red Wi-Fi y abre **Canal**.
2. Toca **Entrar al canal Wi-Fi**. Los demás participantes también deben entrar.
3. Escribe o adjunta archivos. Si aparece **Solo tú**, todavía no hay otros participantes detectados.
4. Para dejar de participar, usa **Salir**.

Los archivos anunciados en el canal ofrecen **Descargar archivo**. En **Opciones del canal** puedes activar la descarga automática; está desactivada de forma predeterminada.

## Descargas y actividad

**Descargas** tiene dos vistas:

- **Recibidos:** biblioteca de archivos disponibles en este Android. Puedes buscar por nombre y ordenar por fecha, nombre o tamaño. Toca un archivo para abrirlo, o usa su menú para **Compartir** con una aplicación de Android.
- **Actividad:** resultados de envíos y recepciones. Puedes buscar por archivo o equipo y filtrar por dirección o por operaciones incompletas. Los resultados completados, cancelados y fallidos aparecen diferenciados.

La biblioteca recibida se puede consultar sin estar conectado a otro equipo. El botón Actualizar vuelve a leer los archivos y la actividad. El icono de carpeta abre la vista de descargas del sistema; su presentación depende de Android.

Para abrir un formato concreto, Android necesita una aplicación que lo reconozca. Si no puedes abrirlo, prueba **Compartir** y elige una aplicación adecuada. Guarda una copia mediante Compartir si necesitas conservarla fuera del almacenamiento de Qetara antes de desinstalar o borrar sus datos.

## Recordar, verificar y olvidar equipos

Una solicitud válida muestra una huella que puedes comparar en **Acerca de Qetara** del otro equipo. Qetara no permite aprobar desde ese diálogo una identidad cuya huella no se pueda obtener. En ese caso, actualiza el otro Qetara y vuelve a conectar.

Cuando se ofrezca, **Guardar favorito** facilita volver a seleccionar un equipo conocido. La dirección guardada puede cambiar al conectarse a otra red; si un favorito no responde, busca el equipo de nuevo o usa su IP actual.

Para dejar de recordar un equipo, abre **Conectar → Gestionar equipos recordados → Olvidar equipo** y revisa la confirmación. Se elimina su identidad recordada, apodo y favorito, y se cancelan los envíos pendientes o activos dirigidos a él. Los archivos ya recibidos permanecen. Para compartir de nuevo, vuelve a conectar y revisa la identidad.

Si Qetara informa de que la identidad cambió, compara primero la huella del equipo. Si confirmas que es tu equipo, puedes olvidarlo y emparejarlo otra vez.

## Ajustar la experiencia

El engranaje abre **A tu manera**. Permite cambiar la escala del texto de la aplicación, activar el modo compacto y ajustar las vibraciones y las confirmaciones discretas. Los cambios se guardan automáticamente. **Normal** restaura la escala de texto al 100 %.

El botón de información abre **Acerca de Qetara**, con la versión, licencia y huella de este dispositivo. Su botón **Licencias** permite leer los avisos y textos completos de las dependencias sin conexión ni aplicaciones externas. El código de esta entrega se proporciona con licencia MIT; esta guía no presupone que un repositorio remoto sea público.

## Si algo no funciona

| Lo que ocurre | Qué hacer |
| --- | --- |
| El otro equipo no aparece | Comprueba la misma red Wi-Fi, abre Qetara en ambos y toca Actualizar. Prueba la IP que muestra el receptor. Algunas redes de invitados aíslan sus dispositivos. |
| El PC pide emparejamiento manual | Introduce en Android el código y el PIN visibles en el PC. Repetir Sincronizar sesión no sustituye este paso. |
| El código o el PIN no coinciden | Revisa ambos datos en el receptor y corrígelos en Conectar. Después vuelve a intentar el envío. |
| La sesión expiró | Renueva la sesión y vuelve a comprobar los datos entre los equipos. |
| Falta permiso cercano | Toca Permitir conexión cercana. Si se abren los ajustes de Qetara, concede allí el permiso y vuelve. |
| La conexión se interrumpe | Mantén ambas aplicaciones abiertas, comprueba la red y revisa Actividad antes de reintentar. |
| Un archivo no se abre | Usa Compartir o instala una aplicación compatible con ese formato. |

La entrega se valida por compilación, pruebas automatizadas y comprobaciones en el entorno disponible. La compatibilidad de todos los teléfonos, redes y restricciones de batería debe verificarse en los equipos concretos de uso. Para una primera prueba, envía un archivo pequeño y comprueba que puedes abrirlo en el receptor.

Al cambiar entre Misma Wi-Fi y Wi-Fi Direct, Qetara descarta el destino y la confirmación de sesión anteriores. Los archivos elegidos se conservan. Revisa el equipo mostrado antes de continuar. En Misma Wi-Fi, la tarjeta principal permite **Cambiar equipo** o abrir directamente **Conectar por dirección IP**.


## Abrir archivos recibidos (1.3.1)

**Abrir** solicita el selector **Abrir con** de Android con el tipo del archivo. Si sólo hay una aplicación compatible, Android puede abrirla directamente. Puedes elegir Fotos, una galería u otra aplicación que declare compatibilidad. Qetara no fija Drive ni ninguna aplicación. La lista concreta depende de las aplicaciones instaladas; Drive puede aparecer si también admite ese tipo de imagen. Compartir y Guardar una copia conservan el tipo cuando el nombre o el proveedor permiten identificarlo.

El permiso temporal permite leer únicamente el archivo elegido; no concede escritura ni acceso general a tus archivos. Si faltan el tipo y una extensión reconocible, Qetara deja la elección al selector genérico sin afirmar que el archivo sea PDF o imagen.

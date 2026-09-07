# Cambios en Qetara

## 1.3.0 — 7 de septiembre de 2026

- Flash se activa expresamente en Android y PC para compartir archivos por la misma red local sin crear ni escribir credenciales. Empieza apagado y caduca a los 30 minutos.
- Ambas plataformas comparten descubrimiento, canal cifrado, verificación y aceptación por archivo, progreso, cancelación y resultado. Las solicitudes vencen y una aceptación tardía no reactiva la función.
- Flash conserva la sesión habitual y sus controles avanzados. La dirección IP local sirve como alternativa a la búsqueda cuando la red permite conexiones directas.
- Se distingue el estado cerrado de la sesión en la vista Completo de Android y se aclaran los estados de búsqueda y conexión Wi-Fi Direct.

### Privacidad de Android integrada en esta entrega

- El PIN empieza oculto en las vistas normal y Completo, con controles Mostrar y Ocultar. Cambiar de sesión, recrear la pantalla o pausar la aplicación vuelve a ocultarlo; editarlo conserva el foco. También lo oculta al perder el foco de la ventana, aunque la actividad siga activa. Mostrarlo requiere que Qetara esté activa y tenga el foco; recuperarlo no revela el PIN automáticamente.
- Los botones de copia y la selección de texto marcan el portapapeles como sensible. El contenido se conserva para pegarlo, y los sistemas compatibles pueden ocultar su vista previa.
- Android 13 y posteriores reciben la indicación de ocultar la miniatura guardada de Qetara en aplicaciones recientes. La transición en directo depende del sistema: el nuevo manejo del foco no elimina un fotograma que Android ya haya capturado. Las capturas voluntarias en primer plano siguen permitidas.
- El descubrimiento público deja de indicar si el anfitrión confía en quien consulta. La disposición del protocolo y la autenticación se mantienen compatibles.
- Las reglas de copia de seguridad excluyen explícitamente los dominios de datos privados, incluida la transferencia entre dispositivos y el almacenamiento protegido del dispositivo.

Estas mejoras se evaluaron previamente como candidatas Android 1.2.1. La compilación 05 superó seis recorridos de privacidad en emulador Android 16, con dos comprobaciones de Recientes. La validación física comenzó con una actualización autorizada de 1.0 a la compilación 04 en un CPH2743 con Android 16, conservando instalación y permisos sin borrar datos; después se instaló 05. El teléfono quedó pausado antes de verificar allí el nuevo manejo del foco. Los resultados y límites de 1.3.0 se registran en [Validación](docs/VALIDATION.md).

## 1.2.0 — 6 de septiembre de 2026

Esta actualización se centra en conservar el trabajo preparado y recuperarse de interrupciones.

- Android conserva los presentadores al recrear la pantalla y guarda el estado recuperable de la sesión y de los borradores. Girar el dispositivo no debe cambiar las credenciales ni descartar adjuntos.
- Una selección restaurada con archivos inaccesibles muestra un aviso junto al selector. La importación desde Compartir se consume una sola vez y no reaparece tras borrar la selección y girar la pantalla.
- Las preferencias heredadas alinean el modo LAN/Direct visible con la ruta elegida, conservando la vista Avanzada.
- Las selecciones de destinatarios expresas se conservan aunque un equipo deje de estar disponible; el envío espera una selección válida y no cambia silenciosamente a otro miembro.
- El emparejamiento manual con PC conserva el foco al editar código y PIN. Un destino que requiere acción del usuario deja de provocar reintentos automáticos al actualizarse el descubrimiento.
- Los avisos de Android ocupan espacio encima de la navegación. Cerrar una sesión ya no bloquea el primer toque en Chat mientras aparece la confirmación. En pantallas pequeñas, el editor conserva espacio cuando coinciden el teclado y un aviso; la descripción accesible identifica al destinatario.
- Windows permite cancelar el envío de mensajes y corta las conexiones y los lotes pendientes. Preparar otro adjunto no habilita un segundo envío simultáneo.
- Los resultados del chat se atribuyen a su conversación. Un envío parcial al canal conserva los componentes pendientes del borrador y explica el alcance de un nuevo envío manual.
- El receptor conserva un recibo del destino antes de publicar los bytes verificados. Tras un cierre de proceso en esa frontera, el mismo intento reconoce el archivo completo sin generar una segunda copia. Los recibos anteriores mantienen su formato compatible.
- Los selectores de archivos de Windows pertenecen a la ventana de Qetara y los estados tardíos del receptor no reactivan visualmente una sesión caducada.
- El empaquetado verifica los fuentes correspondientes del runtime, su inventario y versión, y los incluye en la distribución Windows.

Los resultados y los límites de esta actualización se documentan en [Validación](docs/VALIDATION.md). La evidencia anterior se conserva en [Validación de 1.1.0](docs/VALIDATION-1.1.0.md).

## 1.1.0 — 6 de septiembre de 2026

Esta versión convierte la base Android y PC en una entrega local instalable, con una experiencia de conexión, envío y recuperación más clara.

- Android organiza Conectar, Enviar, Chat, Canal y Descargas, adapta la navegación al tamaño del texto y pide permisos cuando una acción los necesita.
- La sesión se confirma antes de ofrecer el envío. La dirección manual, el transporte y el equipo seleccionado se mantienen coherentes.
- Cerrar sesión detiene la recepción y los envíos, conserva los archivos y mantiene la sesión cerrada al volver a abrir la aplicación. La reactivación espera el cierre de los trabajos anteriores.
- Los adjuntos y borradores conservan su contexto de envío, conversación o canal. Descargas incorpora búsqueda, orden y apertura o compartición.
- El chat mantiene la escritura y el envío accesibles con el teclado abierto en pantallas pequeñas y con texto ampliado. Los envíos completados conservan una confirmación visible.
- PC incorpora espacios de compartir, recibir, mensajes, actividad y ajustes, con destino visible, progreso y cancelación.
- El intercambio automático de credenciales entre Android usa un canal Noise autenticado y aprobación por huella. El mecanismo heredado en texto plano se rechaza. El PC utiliza código y PIN manuales.
- Los mensajes de grupos Wi-Fi Direct se restringen al grupo activo y a participantes observados en su interfaz; la confianza histórica de la LAN no acredita pertenencia al grupo.
- Los receptores limitan conexiones simultáneas, validan tamaños y nombres, verifican contenido antes de publicar y conservan archivos anteriores al repetir nombres.
- Se añaden licencia MIT, avisos de terceros accesibles sin conexión, guías de uso y contribución, scripts de verificación y empaquetado, y configuración de CI.
- Android dispone de configuración de firma de distribución mediante variables de entorno. Windows incluye un runtime Java actualizado, con sus avisos y fuentes correspondientes en la entrega.

El detalle de comprobaciones y sus límites se registra en `docs/VALIDATION.md`. La configuración para otros sistemas operativos no equivale a haber ejecutado sus instaladores. El repositorio remoto permanece con su visibilidad original.

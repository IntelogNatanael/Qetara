# Arquitectura de Qetara

Qetara contiene dos aplicaciones y un módulo JVM compartido. Android gestiona LAN y Wi-Fi Direct; PC usa LAN. El protocolo WDRP v4 conserva su identificador para interoperar en archivos y mensajes.

## Módulos

| Módulo | Responsabilidad | Puntos de entrada |
| --- | --- | --- |
| app | Aplicación Android y servicio de transferencia | MainActivity, P2pScreenRoute, TransferForegroundService |
| pc | Aplicación Compose Desktop y CLI | MainKt, DesktopWorkspace |
| protocol | Contrato de paquetes, validaciones y almacenamiento de recepciones | WifiDropProtocol, TransferValidation, ReceivedFileStorage |

## Android

La UI Compose renderiza estado y emite acciones. Las pantallas de conexión, envío, mensajes, descargas, preferencias y confianza están separadas en archivos propios. `presentation/` contiene coordinación de permisos, importación de archivos, selección de destino, disponibilidad de sesión y política de envíos.

`P2pBackend` es el contrato entre presentación y los servicios Android. `WifiDirectController` administra el enlace del sistema; `TransferForegroundService` mantiene sesión, cola, receptor y progreso mediante estado observable. `FileTransfer` implementa conexiones, autenticación, mensajes y transferencia. Los stores locales conservan historial, mensajes y decisiones de confianza.

La selección de archivos y el texto de un borrador pertenecen a su contexto de uso. Cambiar una pestaña no debe convertir un borrador de conversación en un envío a otro canal. La disponibilidad de un equipo requiere una sesión confirmada; ver un anuncio de descubrimiento no basta.

## PC

`Main.kt` conserva el motor de red, el receptor, la CLI y la conexión de callbacks. `DesktopWorkspace.kt` organiza Compartir, Recibir, Mensajes, Actividad y Ajustes. `DesktopPreferences.kt` guarda preferencias de interfaz sin persistir código ni PIN. `DesktopConversations.kt` separa borradores y adjuntos por destinatario y contexto, conserva conversaciones de equipos desconectados y calcula avisos de mensajes nuevos.

La UI y la CLI comparten el mismo motor. Un error debe describir la fase que falló: preparar, enviar, recibir o verificar. La ventana empaquetada usa el runtime de Java incluido en su carpeta.

## Flash opcional

`protocol/flash` implementa `FlashEngine`: descubrimiento temporal, Noise XX, comparación y aceptación por archivo, transferencia y publicación verificada. Tiene puerto y activación propios, separados de WDRP v4, y no conserva identidad ni confianza después de su cierre. El contrato detallado está en [protocol/FLASH.md](protocol/FLASH.md).

Android utiliza `FlashActivity`, `FlashForegroundService` y estado observable del proceso. PC utiliza `DesktopFlashController`, conservado al cambiar de espacio, y un diálogo global de solicitudes. Los adaptadores consumen callbacks en orden y descartan comandos de activaciones anteriores. Los resultados de archivos ya publicados se conservan aunque la interfaz haya empezado a cerrar la sesión; un resultado tardío no reactiva Flash.

## Transferencias y almacenamiento

Antes de escribir se validan nombres, tamaños, offsets, hashes y límites de frames. Las recepciones usan archivos temporales; la publicación del resultado ocurre después de verificar el contenido. La selección de nombres evita sobrescribir un archivo existente. En la sesión habitual, los recibos de transferencias y mensajes ayudan a evitar duplicados al repetir una operación cuya confirmación se perdió.

Cada receptor limita sus clientes simultáneos. En Android, la publicación de archivos se serializa para conservar coherencia de estado y exportación. Cancelar o cambiar de generación cierra sockets bloqueados y libera trabajos; el estado distingue receptor disponible de una transferencia activa.

La deduplicación no equivale a una transacción distribuida frente a un corte de energía en cualquier instrucción. El contrato y sus límites se detallan en [PROTOCOL.md](docs/PROTOCOL.md).

## Identidad y sesión

El descubrimiento anuncia información local y no prueba identidad. Android compatible puede solicitar credenciales por el paquete 12 y un canal Noise cifrado. La aprobación se vincula a la clave estática observada; una clave distinta para una identidad conocida se rechaza. El mecanismo antiguo de credenciales en claro se rechaza. PC requiere el mismo código y PIN introducidos manualmente.

La confianza recordada de un equipo y la disponibilidad de una sesión concreta son estados diferentes. Los mensajes reenviados conservan metadatos útiles de origen, pero esos metadatos no acreditan por sí mismos una firma criptográfica del remitente original.

## Mantener y verificar

- Mantén reglas de navegación, permisos y borradores fuera del transporte.
- Añade al contrato backend una operación nueva que deba iniciar la UI.
- Prueba invariantes de seguridad y datos con casos de error, cancelación y reintento.
- Comprueba visualmente las pantallas y valida un intercambio real después de cambios de sesión o destino.
- Conserva pruebas unitarias, pruebas de socket y evidencias de instalación como niveles distintos.

Las tareas reproducibles están en [CONTRIBUTING.md](CONTRIBUTING.md) y [RELEASING.md](docs/RELEASING.md). La matriz CI está configurada para Android y para PC en Windows, Linux y macOS; sus resultados se deben revisar en cada publicación.

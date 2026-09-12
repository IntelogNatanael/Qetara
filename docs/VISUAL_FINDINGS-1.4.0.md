# Hallazgos del recorrido de Qetara 1.4.0

Estado: hallazgos abiertos del recorrido parcial del 11 de septiembre de 2026. Este documento registra observaciones comunicadas por quien operó las aplicaciones y su contraste con el código; no declara terminado el recorrido, corregidos los problemas ni creadas incidencias externas. La matriz del recorrido se completará por separado con sus evidencias.

La aplicación corresponde a la candidata de código `588a92f2617815b5744eeb91a1da463c5c685f90`. Las referencias siguientes se comprobaron en el árbol de validación basado en `12b5f55c28f2444f43134ff5a104f67b51c24541`. No se modificó código de producción para esta revisión.

No se dispuso de un archivo Penpot activo: la alineación con el diseño no está validada. Aquí se documentan estados, mensajes y comportamiento observado; no una auditoría de composición, estilo o diseño de componentes.

## Observación común

En el envío normal PC → Android, se introdujeron los mismos datos de sesión y Android mostró la sesión preparada tras pulsar «Usar esta sesión». El primer envío requirió aprobar la identidad del PC en Android. Durante la comprobación humana de la huella, PC terminó mostrando el mensaje genérico de conexión interrumpida. Tras aprobar la huella coincidente y volver a enviar desde PC, se completó el lote de dos archivos.

El operador confirmó dos recibidos y coincidencia de sus SHA-256 con los originales. Posteriormente, Conectar en Android mostró «La sesión aún no está confirmada» y Descargas conservó los dos recibidos, junto al banner «Prepara esta misma Wi-Fi». Esos resultados de integridad corresponden a los dos archivos comprobados; este documento no inventa valores de hash ni los sustituye por una afirmación sobre todos los flujos.

## F-01: la confirmación manual puede perderse al conocer la identidad del mismo destino

**Prioridad propuesta: P2, resolver o reproducir de forma controlada antes de la publicación general.** La contradicción entre un intercambio completado y una sesión presentada como pendiente puede llevar a repetir la preparación innecesariamente. La observación no acredita pérdida de archivos ni una modificación de token/PIN.

**Comprobado en código.** El destino de una IP escrita manualmente obtiene su `peerId` de `knownPeers`; mientras no exista allí, puede ser `null`. La confirmación manual guarda esa IP, identidad, red, token y PIN. Una recepción autenticada registra al PC mediante `onPeerSeen`. Si ello cambia el destino de `(misma IP, peerId=null)` a `(misma IP, peerId=PC)`, `observeConnectionContext()` detecta cambio de destino y borra `confirmation` y `lastAutoSyncedPeerIp`. La comprobación de disponibilidad exige que la identidad coincida exactamente. Este recorrido no cambia por sí mismo token o PIN.

Referencias:

- [P2pRoutingPolicy.kt](../app/src/main/java/com/example/wifidrop/presentation/P2pRoutingPolicy.kt), líneas 227–253: resolución de la IP manual y su identidad conocida.
- [P2pSessionPresenter.kt](../app/src/main/java/com/example/wifidrop/presentation/P2pSessionPresenter.kt), líneas 97–108 y 122–135: invalidación por cambio de contexto y confirmación manual.
- [FileTransfer.kt](../app/src/main/java/com/example/wifidrop/FileTransfer.kt), línea 843, y [TransferForegroundService.kt](../app/src/main/java/com/example/wifidrop/TransferForegroundService.kt), función `onPeerSeen`: incorporación del par autenticado a los destinos conocidos.
- [P2pSessionReadiness.kt](../app/src/main/java/com/example/wifidrop/presentation/P2pSessionReadiness.kt), líneas 23–35: igualdad de identidad y evidencia de sesión.
- [P2pConnectTab.kt](../app/src/main/java/com/example/wifidrop/P2pConnectTab.kt), línea 227, y [P2pExperienceState.kt](../app/src/main/java/com/example/wifidrop/P2pExperienceState.kt), líneas 267–277: mensajes asociados al estado pendiente.

**Atribución pendiente.** El código ofrece una explicación concreta compatible con la observación, pero no se registró el `peerId` del destino inmediatamente antes y después de ese envío. No se presenta como causa demostrada de esa ejecución particular. Tampoco se interpreta el banner como prueba de que la red Wi-Fi se haya desconectado: esa rama también depende de que la sesión esté preparada.

**Cierre pendiente.** Reproducir la transición con una IP manual inicialmente sin identidad y observar los cambios de confirmación. Cualquier corrección debe conservar la invalidación ante una identidad o red realmente distinta; no basta con eliminar la comparación de `peerId`. Volver a confirmar después de identificar al PC es una recuperación prevista por el código, todavía no una corrección de este hallazgo.

## F-02: el primer rechazo por confianza puede acabar presentado como interrupción genérica

**Prioridad propuesta: P2, aclarar el diagnóstico antes de publicación.** El mensaje genérico orienta hacia receptor o red y puede ocultar la acción necesaria de aprobar y reenviar. El reintento observado completó los dos archivos; no hay evidencia aquí de un fallo persistente de transferencia.

**Observado.** Durante la verificación humana apareció el mensaje de conexión interrumpida. No se registró en esta revisión la excepción interna ni un intervalo medido que pruebe un timeout. La demora del operador y la terminación del intento son hechos distintos; no se afirma que la primera agotara una espera de aprobación.

**Comprobado en código.** El envío normal no mantiene la conexión esperando la decisión humana. La solicitud de confianza devuelve `false`; Android envía `confirmacion_host_requerida` y termina esa conexión. Aprobar fija la identidad para un envío posterior y muestra «Vuelve a enviar desde ese equipo». PC tiene una traducción específica para ese rechazo. «Se interrumpió la conexión» se obtiene para `EOFException` o `SocketException`; `SocketTimeoutException` tiene otro mensaje. Los límites de lectura —120 segundos en el envío PC y 10 segundos en la fase inicial Android— no son un temporizador de aprobación humana.

Referencias:

- [AuthenticatedPeerAuthorization.kt](../app/src/main/java/com/example/wifidrop/AuthenticatedPeerAuthorization.kt), líneas 4–17: la autorización consulta y devuelve, sin esperar una decisión futura.
- [FileTransfer.kt](../app/src/main/java/com/example/wifidrop/FileTransfer.kt), líneas 565 y 818–835: límite inicial, solicitud y rechazo.
- [TransferForegroundService.kt](../app/src/main/java/com/example/wifidrop/TransferForegroundService.kt), líneas 396–443 y 2138–2170: creación de la solicitud y aprobación para reenviar.
- [DesktopTransferUi.kt](../pc/src/main/kotlin/com/example/wifidrop/pc/DesktopTransferUi.kt), líneas 46–69: traducción diferenciada de rechazo, timeout e interrupción.
- [Main.kt](../pc/src/main/kotlin/com/example/wifidrop/pc/Main.kt), líneas 764, 797–805 y 5008–5015: límite PC, envío de metadatos y lectura de la respuesta del receptor.

**Hipótesis pendiente.** PC escribe `FILE_META` antes de leer la respuesta, mientras Android puede rechazar por falta de confianza y cerrar sin consumir esos metadatos. Un cierre con datos pendientes podría producir un reset y ocultar el resultado explicativo. Es una posibilidad derivada del orden de operaciones, no una causa demostrada sin registrar la excepción o el intercambio. No se ha aplicado un cambio de protocolo ni una corrección del mensaje.

**Cierre pendiente.** Capturar el tipo de excepción y la fase del primer intento en una reproducción de aprobación inicial, sin guardar token, PIN u otros datos privados. Comprobar que el rechazo conocido conserva sus instrucciones de recuperación y que una interrupción de red real sigue diferenciada.

## F-03: el área de selección de escritorio anuncia un archivo aunque admite lotes

**Prioridad propuesta: P3, corregir el texto antes de publicación.** Es una limitación aparente de capacidad; la transferencia por lotes funcionó en el recorrido observado y sus dos archivos coincidieron por hash.

**Comprobado.** [Main.kt](../pc/src/main/kotlin/com/example/wifidrop/pc/Main.kt), línea 4000, muestra «Arrastra y suelta un archivo aquí» y, en la línea 4004, «O pulsa aquí para elegirlo · un archivo por envío». El operador seleccionó y envió un lote de dos archivos desde la GUI, con dos recibidos confirmados en Android. Aunque cada transferencia interna procesa un archivo, la indicación de la pantalla presenta la acción de envío como limitada a uno.

**Cierre pendiente.** Ajustar la indicación al comportamiento por lotes y revisar el texto resultante dentro del recorrido correspondiente. No se modificaron cadenas ni componentes en esta revisión; tampoco se declara validada su alineación con Penpot.

## Alcance del cierre

En la candidata `588a92f`, los tres hallazgos quedaron abiertos. La [revisión de escritorio posterior](DESKTOP_DESIGN_REVIEW-2026-09-11.md) resolvió F-03: la nueva zona de selección y la acción de envío describen varios archivos, como muestran sus renders con un lote seleccionado. F-01 y F-02 siguen abiertos; esa revisión visual no aporta nueva evidencia sobre sus causas ni su resolución. Las prioridades anteriores expresan impacto funcional y claridad de uso; no son una declaración de vulnerabilidad ni una aprobación integral de publicación. La [validación UDP](UDP_VALIDATION-1.4.0.md) conserva por separado su intento fallido y su repetición aprobada: esos resultados no cierran los hallazgos del flujo normal descritos aquí.

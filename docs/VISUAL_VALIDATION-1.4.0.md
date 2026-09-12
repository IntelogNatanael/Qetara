# Recorrido visual de Qetara 1.4.0

**Estado: borrador del recorrido parcial del 11 de septiembre de 2026.** Registra los resultados comunicados por quien operó las aplicaciones y su contraste con capturas, jerarquías XML y el manifiesto local de archivos de prueba. No declara completado el recorrido ni aprobada incondicionalmente la publicación. Las últimas acciones y la limpieza siguen pendientes de registrar.

## Aplicaciones y entorno

El código de producción comprobado corresponde a `588a92f2617815b5744eeb91a1da463c5c685f90`. La versión es 1.4.0; Android usa código 7 e identificador `io.github.intelognatanael.qetara`.

| Artefacto | SHA-256 |
| --- | --- |
| APK release instalado | `5d9e28651ffdaf0216d0a82c049b287851906065bbc713eefa77d793670e22f6` |
| MSI del ejecutable instalado | `05245a66781441811e22ec2572508f194492e90a31f8e7230e483d9d99ee5fe5` |

Se usó el teléfono físico Android 16/API 36 y el ejecutable Windows instalado por el MSI, cuya instalación y autoprueba constan en [Validación MSI](MSI_VALIDATION-1.4.0.md). PC y teléfono compartían la Wi-Fi. Este recorrido utiliza las pantallas y selectores reales; sus resultados se conservan separados de los [lotes instrumentados](FLASH_SOCKET_QA.md) y del [arnés UDP físico](UDP_VALIDATION-1.4.0.md).

La GUI Windows se lanzó con `--gui --out <área-QA>/received` y `JAVA_TOOL_OPTIONS=-Duser.home=<área-QA>/pc-home`. Esa área está bajo `.local/publication-audit/visual-qa/`. Se utilizó una identidad PC de prueba y una carpeta de recepción dedicada; no se publican token, PIN, huellas de sesión, direcciones privadas ni seriales.

Al comenzar se consultó la documentación `high_level_overview` mediante Penpot MCP. No hubo un plugin conectado a un archivo activo, por lo que no se acreditó alineación con el diseño Penpot. No se modificó código de producción, texto de interfaz ni componentes para este recorrido.

## Pantallas y controles recorridos

| Recorrido | Resultado y alcance |
| --- | --- |
| Windows: Compartir, Recibir, Mensajes y Actividad | Se abrieron las vistas. Abrir una vista no acredita todavía todos sus flujos de datos. |
| Windows: Ajustes y licencias | Se abrieron y revisaron sus contenidos visibles. La huella de identidad de Ajustes se comparó durante la solicitud de confianza Android. |
| Android: inicio LAN y estado inicial Wi-Fi Direct | Se revisaron los estados iniciales. No se estableció una conexión Wi-Fi Direct física en este recorrido. |
| Android: nombre Flash | Se abrió el editor y se canceló; no se acredita un cambio persistente de nombre. |
| Android: Descargas | Se visualizaron los dos textos recibidos en el envío normal y sus acciones. |
| Android: selector de compartir | Se abrió y canceló el selector del sistema. No se enviaron archivos a terceros. |

Las capturas y XML Android `01-home` a `05-flash-name-done` registran los estados iniciales. `21-downloads-normal` a `29-share-cancelled` registran los recibidos, apertura y selector de compartir. Las observaciones de escritorio proceden del recorrido directo de su GUI; no se atribuyen a las capturas XML Android.

## Envío normal PC → Android

Desde el selector de archivos PC se eligieron conjuntamente dos textos de prueba. Se prepararon los mismos datos de sesión mediante la interfaz de las dos aplicaciones. Android mostró una solicitud de confianza y se comparó su huella con la identidad visible en Ajustes de PC.

El primer envío terminó en PC con un mensaje de conexión interrumpida mientras la aprobación seguía pendiente. Se conservó este resultado. Tras aprobar la huella coincidente, se volvió a enviar el lote desde PC: se recibieron **2 de 2 archivos**, con SHA-256 iguales a los originales del manifiesto. No se presenta el primer error como un timeout de aprobación demostrado; el flujo y sus posibles causas se analizan en [F-02](VISUAL_FINDINGS-1.4.0.md#f-02-el-primer-rechazo-por-confianza-puede-acabar-presentado-como-interrupción-genérica).

Android mostró ambos archivos en Descargas. Al abrir el primer texto, el lector Documentos mostró su contenido de prueba de forma visible, además del nombre correcto. La captura `25-external-text-reader.png` y su XML identifican el visor externo; no se sustituye esa observación por una comprobación únicamente de MIME o Intent. Después se abrió el selector de compartir y se canceló.

La sesión presentada como pendiente después del intercambio y el banner de preparación se conservan como [F-01](VISUAL_FINDINGS-1.4.0.md#f-01-la-confirmación-manual-puede-perderse-al-conocer-la-identidad-del-mismo-destino). La integridad del lote aprobado no cierra ese hallazgo de estado de sesión.

## Flash: descubrimiento y lotes

PC activó Flash antes que Android. Android encontró automáticamente al PC al activarse. La lista PC permaneció vacía hasta accionar su búsqueda manual, que encontró Android. Se pulsó también la búsqueda manual Android y el PC permaneció visible. Esta última acción acredita el control y la permanencia del par, pero no demuestra por sí sola descubrimiento de una identidad nueva; esa comprobación pertenece al arnés UDP independiente.

La observación de la lista PC inicial vacía se conserva. No se atribuye a una causa concreta ni se transforma en un resultado aprobado de descubrimiento automático PC. El [informe UDP](UDP_VALIDATION-1.4.0.md) registra por separado su primer intento fallido y su repetición aprobada.

| Dirección y selección | Aprobaciones | Resultado |
| --- | --- | --- |
| Android → PC: dos textos, elegidos conjuntamente en el selector Android | Se comparó la verificación de cada archivo y se aprobó por separado | 2 de 2 recibidos en la carpeta PC dedicada, con SHA-256 iguales al manifiesto |
| PC → Android: PDF de 609 bytes y PNG de 144 bytes, elegidos conjuntamente en el selector PC | Se comparó la verificación de cada archivo y se aprobó por separado | 2 de 2 recibidos, con SHA-256 iguales al manifiesto |

El proveedor Descargas del selector Android no mostraba las muestras colocadas mediante ADB. Se accedió a ellas por almacenamiento interno → Download y se seleccionaron los dos textos desde allí. No se sustituyó la selección visual por rutas inyectadas al motor. Este resultado depende de esos archivos preparados externamente; no demuestra un fallo general de acceso a Descargas.

Las evidencias `33-flash-pick-files` a `48-flash-batch-completed` cubren el selector Android, la selección múltiple y el primer lote. `49-flash-pdf-incoming` a `52-flash-received-list` cubren la recepción posterior de PDF y PNG. Los códigos se comprobaron individualmente; no se afirma que dos códigos cortos deban ser únicos.

## Abrir y guardar archivos recibidos

El PNG recibido por Flash se abrió en Fotos local y mostró correctamente las dos zonas de color de la muestra. El PDF se abrió en Documentos local y mostró el texto «Qetara: documento de prueba visual». Las capturas `54-image-viewer.png` y `64-pdf-viewer.png`, junto a sus XML, conservan la vista renderizada y la identidad de los visores. No se activaron acciones de envío o servicios externos de esos visores.

Desde la acción «Guardar una copia» del PNG se completó el selector SAF hacia `Qetara-Visual-QA-20260911/qetara-qa-copia-visual.png`. La copia conservó el SHA-256 del PNG original. Las evidencias `56-save-copy` a `60-copy-saved` registran la elección y confirmación del destino. Esta prueba acredita ese archivo y destino; no todos los proveedores SAF o visores Android.

## Integridad de las muestras

Estos valores proceden de `fixture-manifest.json`. El operador contrastó los recibidos de los tres lotes completados y la copia SAF con sus originales.

| Archivo original | Bytes | SHA-256 |
| --- | ---: | --- |
| `qetara-qa-pc-uno.txt` | 56 | `00a1316ce00bb78eeeb6e329179efac530c2a583ba578cce30d840a5f9a76151` |
| `qetara-qa-pc-dos.txt` | 140000 | `ae0c9d928513c902ec58eb93064e395f1dd468f754ca8398e844452e881ecb11` |
| `qetara-qa-android-uno.txt` | 61 | `307a9ce3abf0da01590c52fc3e4b9532fd2cda311cc1e2637157764fd3a071b8` |
| `qetara-qa-android-dos.txt` | 160000 | `59a879e09a03efaec3df8e62f21fa6043bb2950ff40840cf1f5dd3dfb9b5bdbc` |
| `qetara-qa-documento.pdf` | 609 | `1c6ffe9d1ba536428e2f21d110891c27a0374d2db6eaab82e64f2c142960bdc7` |
| `qetara-qa-imagen.png` | 144 | `17467ad48f80d0990bb69fc2f4bb4baf3f272f0c99c51a622b6bb9ffe36d6ee3` |

## Cancelación

La primera prueba prevista de cancelación usó un archivo de **128 MiB**. El envío terminó antes de que se accionara cancelar. Se contabiliza como una transferencia completada, **no como cancelación acreditada**. Las capturas `66-cancel-request` a `68-cancel-status` no permiten concluir que el botón haya interrumpido un envío en curso; ese archivo quedó recibido completo.

Una segunda prueba usó un archivo de **1 GiB**. Con PC en la fase visible «Transfiriendo», se pulsó «Cancelar transferencia». PC mostró «Transferencia cancelada.», rehabilitó «Solicitar envío» y conservó el archivo seleccionado. Android no anunció éxito: mostró «No se pudo completar la transferencia. Revisa Flash y la conexión del otro equipo.», registrado en `73-cancel-top.xml`.

La comprobación posterior de `WifiDropReceived` encontró únicamente los cinco archivos completos anteriores, sin el archivo de 1 GiB ni un parcial suyo; el temporal de esa operación se eliminó. **La cancelación funcional del archivo activo aprobó.** El mensaje genérico del receptor queda como observación de claridad abierta: no identifica que el emisor canceló y sugiere revisar la conexión. No se atribuye aquí una causa interna demostrada ni se declara corregido el mensaje. Esta ejecución no añade una prueba manual de cancelación con otros archivos pendientes en el mismo lote.

## Acciones aún pendientes

- [x] Cancelar visualmente una transferencia activa, conservar la selección PC y comprobar la ausencia de un recibido o parcial de esa operación.
- [ ] Completar el envío normal Android → PC.
- [ ] Completar Mensajes y el recorrido de canal.
- [ ] Completar Ajustes y Acerca de Android.
- [ ] Registrar la limpieza y el estado final de servicios, aplicaciones y archivos exclusivos de prueba.
- [ ] Cerrar la matriz del recorrido y preparar el nuevo paquete de entrega con su procedencia y hashes.

## Evidencias, hallazgos y límites

Las capturas PNG, jerarquías XML, logs, manifiesto de muestras y carpetas de prueba permanecen bajo `.local/publication-audit/visual-qa/`, excluidas de Git. Algunas contienen datos de sesión, direcciones o información del dispositivo: **no se empaquetan ni publican como un conjunto**. Este documento conserva los resultados y los identificadores de evidencia necesarios sin copiar esos datos.

Los tres [hallazgos visuales y funcionales](VISUAL_FINDINGS-1.4.0.md) siguen abiertos: estado de confirmación de sesión, diagnóstico del primer rechazo por confianza y texto de selección PC que anuncia un solo archivo aunque permite lotes. Los dos primeros requieren la reproducción y atribución descritas en ese informe; no se presentan hipótesis como causas ya demostradas.

Las transferencias, aperturas y cancelación aprobadas corresponden a los artefactos, visores y red de esta ejecución. No acreditan funcionamiento en otros routers, Wi-Fi Direct físico, todos los proveedores de archivos, accesibilidad completa o alineación con Penpot. La [preparación de publicación](PUBLICATION_READINESS.md) mantiene el recorrido y el nuevo paquete pendientes hasta cerrar sus comprobaciones.

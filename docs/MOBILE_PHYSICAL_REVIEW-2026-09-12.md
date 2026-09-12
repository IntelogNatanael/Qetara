# Revisión móvil en teléfono físico · 12 de septiembre de 2026

Se revisaron el diseño y la navegación de Qetara en un teléfono físico con Android 16/API 36. La actualización conservó los cinco archivos de muestra que ya aparecían en Descargas. Este informe distingue la compilación de main de los ajustes de GUI posteriores; no declara terminado el recorrido funcional de transferencias ni anuncia una publicación.

## Dispositivo y compilaciones

Pantalla de 1080 × 2372 píxeles, densidad lógica de 480 dpi y escala de texto 1,0 (100 %). El ancho lógico es 360 dp. La altura útil de la aplicación depende de las barras del sistema y del teclado; no se equipara toda la resolución física con el área de contenido.

Se instaló la compilación release firmada de main mediante `adb install -r`, reemplazando la candidata anterior. Se comprobó que Descargas conservaba sus cinco archivos de muestra. Después se instalaron los ajustes de GUI del árbol de trabajo, limitados a [P2pDownloadsTab.kt](../app/src/main/java/com/example/wifidrop/P2pDownloadsTab.kt) y [P2pPreferencesDialog.kt](../app/src/main/java/com/example/wifidrop/P2pPreferencesDialog.kt).

| APK instalado | Procedencia | Tamaño |
| --- | --- | ---: |
| Release de main | Commit `642503c4ee752458fe9827cc503c1b087906cf54` | 14.843.941 bytes |
| Release para revisión de GUI | Mismo commit base y cambios en los dos archivos anteriores | 14.860.325 bytes |

SHA-256 del APK firmado de main:

```text
d095cc007791a03285dcfc4208fb3a6ecb335f2d7d3cd5b2202f1d60fbd82aa1
```

SHA-256 del APK firmado con los ajustes de GUI:

```text
021afa4b2446c8591a49196250ff735bfbf3411ec4eb02c7243f82bf813761c5
```

Los tamaños y hashes se contrastaron con los archivos locales. Al terminar se extrajo nuevamente el APK instalado del teléfono y su SHA-256 coincidió con el APK de GUI anterior. El registro privado `.local/physical-qa-2026-09-12/gui-qa-provenance.json` conserva el commit base, los hashes de las dos fuentes y el hash del APK sin firma; las copias de los APK se conservan en esa misma carpeta ignorada por Git. La compilación de GUI procede del árbol de trabajo y no se presenta como una reconstrucción reproducible del commit original.

## Recorrido observado al 100 %

Se recorrieron las pantallas principales en los temas claro y oscuro. Los menús, modos de conexión y diálogos se comprobaron con el alcance indicado, sin una matriz exhaustiva de cada combinación. Los archivos y nombres utilizados son fixtures sintéticos.

| Vista o control | Alcance observado |
| --- | --- |
| Conectar | Cabecera, controles y estados de preparación o falta de permiso. |
| Enviar | Presentación de la pantalla y sus acciones; sin envío real. |
| Chat | Estado vacío; sin conversación poblada ni mensajes enviados. |
| Canal | Pantalla previa a entrar; no se entró al canal. |
| Descargas | Biblioteca con cinco archivos de muestra ya existentes. |
| Flash | Pantalla con Flash desactivado; sin activar recepción o descubrimiento. |
| Menú de conexión | Recorrido por Direct, LAN y Completo. |
| Opciones | Apertura de preferencias; lectura de la jerarquía de Acerca de Qetara, sin revisar su render. |

El recorrido permite revisar jerarquía, texto, color y acceso a las opciones en los estados indicados. No acredita por sí solo que exista una conexión válida con otro equipo.

## Ajustes comprobados en el teléfono

- En `updated-downloads-light.png`, el nombre completo `qetara-qa-cancelar.bin` aparece en una línea en la biblioteca. El tamaño y la fecha disponen de una fila separada del nombre.
- En `updated-search-results-light.png`, la búsqueda `qetara-qa-imagen` muestra el teclado y un resultado. Al cerrar el teclado se restauran la cabecera y los controles.
- En `updated-preferences-light.png`, los botones A−, Normal y A+ caben en una fila. Se pulsó A+ y la escala pasó al 105 %; Normal la devolvió al 100 %.

Estas observaciones corresponden al APK firmado con los dos ajustes de GUI, identificado arriba. La [galería seleccionada](../design/figma/mobile-physical-review/README.md) contiene capturas originales del teléfono y su manifiesto de procedencia. El símbolo, su geometría y los colores de marca no cambiaron en esta iteración.

## Texto al 200 % y estado final

Con la escala del sistema al 200 % y la escala de la app al 100 %, se revisaron Descargas y su búsqueda en tema oscuro. La cabecera se adaptó a una acción Flash con icono; la navegación mostró sus cinco iconos y el nombre de la sección seleccionada. Al desplazar la biblioteca, los nombres completos ocuparon varias líneas y la fecha pasó a una línea separada del tamaño.

La búsqueda con teclado conservó el foco y mostró el único resultado, su nombre completo repartido en dos líneas, tamaño, fecha y menú. El texto largo del campo de una sola línea se desplazó horizontalmente. Al borrar la consulta y cerrar el teclado volvieron los cinco resultados y los controles de biblioteca. Este pase no extiende la comprobación al 200 % a todas las pantallas ni a todos sus estados.

Al terminar se restauraron la escala del sistema a `1.0` y el modo nocturno a `2` (oscuro), comprobados mediante lectura de ajustes. La escala de la app quedó al 100 %. Se dejó Conectar en modo LAN, con los cinco archivos existentes conservados y sin consulta de búsqueda pendiente.

## Compilación y análisis automatizado

La compilación release, `lintRelease` y `testDebugUnitTest` terminaron correctamente en esta iteración. La lectura de los informes XML confirma:

- **199 pruebas Android aprobadas** en 36 informes: 0 fallos, 0 errores y 0 omitidas.
- **Lint release: 0 errores y 2 advertencias preexistentes**: `UsableSpace` en `FileTransfer.kt` y `UseKtx` en `TransferForegroundService.kt`.

Las cifras proceden de `app/build/test-results/testDebugUnitTest/TEST-*.xml` y `app/build/reports/lint-results-release.xml`. Son comprobaciones unitarias y estáticas; el recorrido físico descrito se realizó por separado. No se atribuye a este pase una nueva ejecución de las suites PC o protocolo.

## Observación pendiente

En el modo Completo se observó simultáneamente el chip «Direct listo», un aviso de falta de permiso y los botones de crear o buscar deshabilitados. La presentación resulta contradictoria y queda pendiente de revisión; no se corrigió en estos dos ajustes. No demuestra que Wi-Fi Direct esté conectado o preparado para transferir. Este informe no atribuye una causa interna sin evidencia adicional.

F-01 y F-02 del [recorrido anterior](VISUAL_FINDINGS-1.4.0.md) siguen pendientes: pérdida aparente de confirmación de sesión y mensaje genérico del primer intento de confianza. Se priorizó el diseño y la navegación física; no se reprodujeron ni se dieron por resueltos esos comportamientos.

## Referencia Figma y límites

Una consulta inicial a Figma devolvió el documento con una página vacía. Las consultas posteriores de inspección y bibliotecas fueron rechazadas por el límite MCP del plan Starter. No se crearon marcos ni se obtuvo una captura o revisión de diseño en Figma. Las referencias locales desde código se mantienen separadas de la evidencia del teléfono; no equivalen a un render de Android.

No se validaron en este pase descubrimiento UDP, envíos de archivos o mensajes, aprobación entre dos equipos, progreso ni cancelación real de una transferencia. Tampoco se declara completo el recorrido de abrir y guardar archivos, todas las orientaciones o una auditoría integral de accesibilidad. No se publican número de serie, direcciones de red ni identificadores privados del teléfono.

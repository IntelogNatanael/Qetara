# Revisión del flujo Flash móvil

Iteración de presentación y mensajes de Flash del 12 de septiembre de 2026. El APK final se compiló, firmó e instaló en un CPH2743 con Android 16. Conserva el protocolo y las tres apariencias de Qetara presentes en el árbol de trabajo. Las comprobaciones que siguen tienen un alcance de GUI; no acreditan un nuevo recorrido de transferencia entre equipos.

## Referencia

El conector de Figma volvió a responder que la cuota Starter está agotada. No se guardaron marcos ni se completó una revisión en Figma. La referencia local está en el código Compose, este informe y el [comparador de capturas](comparison.html).

## Decisiones

- Inicio con explicación breve, nombre del equipo y activación explícita por 30 minutos.
- Sesión activa compacta; receptor antes de archivos. La búsqueda automática existente se conserva, junto con una acción para volver a buscar y la dirección manual como alternativa.
- Receptor elegido resumido y modificable. La selección no cambia automáticamente si el equipo desaparece o caduca.
- Archivos agrupados y revisables, con selección múltiple incluso sin receptor. «Ver los N archivos» despliega la selección completa y «Mostrar menos» vuelve al resumen; al expandir se muestran los nombres completos, también visibles para selecciones de hasta tres archivos.
- Un único botón al pie ofrece «Elegir receptor», «Elegir archivos» o «Solicitar envío» según el siguiente paso. El pie se oculta al abrir el teclado y el cambio de fase devuelve el contenido al inicio. Cada archivo sigue requiriendo su código y aceptación en ambos equipos.
- Operaciones y resultados distinguen transferencia, confirmación de entrega y recepción. Ayuda, permisos opcionales e historial quedan después del flujo principal.
- La desactivación con trabajo preparado o en curso requiere una confirmación contextual.
- Flash aplica tanto la escala del sistema como la preferencia de lectura de Qetara. Los controles permiten envolver el texto y mantienen una altura táctil mínima de 48 dp.
- Se concretaron los mensajes de seis códigos existentes: confirmación caducada, sesión del receptor cambiada, dirección inválida, almacenamiento no disponible, respuesta incompatible y entrega sin confirmar. «Quitar todos» actualiza también el texto de estado; estos cambios no alteran el protocolo.

## Validación realizada

La compilación final 3 completó `assembleRelease`, `lintRelease` y `testDebugUnitTest` en modo offline en 2 min 8 s. Se reconfirmaron los informes XML: **202 pruebas Android, 0 fallos, 0 errores y 0 omitidas**. Lint terminó con **0 errores y 3 advertencias**, iguales al pase anterior: una `UsableSpace` y dos `UseKtx`. Se firmó e instaló el APK final conservando la instalación existente.

La revisión principal se realizó con la candidata 2: Flash apagado/activo, regreso al inicio al activar, acción contextual para elegir receptor, selección de cuatro archivos sintéticos y lista expandida. Al 200 % se inspeccionaron la sesión y el diálogo de apagado completo, y «Seguir en Flash» mantuvo la sesión. La entrada manual conserva el campo visible con teclado y el pie oculto.

Sobre el APK final 3 se seleccionó `qetara-flash-qa-01.txt`, de 66 B. «Quitar todos» vació la selección y actualizó el estado a **«Selección vacía. Elige archivos para compartir.»**. La [captura final](final-cleared-top.png) muestra ese resultado. No se atribuye al APK final una segunda ejecución completa de todas las comprobaciones de la candidata 2.

También se inspeccionó Flash **apagado** en [Marfil](flash-final-ivory.png) y [Oscuro](flash-final-dark.png) sobre el APK final 3. Esta comprobación adicional no cubre receptores ni transferencias en las tres paletas. La escala propia de lectura de Qetara se comprobó en preferencias al **105 %** y se conservó sin cambios; el 200 % indicado arriba corresponde al ajuste del sistema.

Al terminar se dejó **Flash apagado y Gris azulado seleccionado**, con la escala de Qetara al 105 %, la del sistema al 100 % y el modo oscuro original de Android restaurado. Se retiraron del teléfono los cuatro archivos sintéticos creados para este pase y su carpeta de pruebas; se conservaron las copias locales.

## Capturas y procedencia

El [comparador](comparison.html) conserva las vistas anteriores y posteriores y añade las paletas finales de Flash apagado. La captura final de «Quitar todos» se enlaza por separado. Son capturas de Android, no maquetas. Se comprobó en el navegador que las imágenes de Paletas cargan, que los controles cambian de vista y que el símbolo móvil aparece sin placa; se dejó seleccionada la vista Inicio. El [manifiesto](manifest.json) registra dimensiones, bytes y SHA-256 de los once PNG, los tres APK y las fuentes indicadas.

| Captura | Artefacto | Estado |
| --- | --- | --- |
| [Antes: apagado](before-flash-off.png) | Baseline | Flash anterior apagado |
| [Antes: activo](before-flash-active.png) | Baseline | Flash anterior activo |
| [Inicio](after-flash-off.png) | Candidata 2 | Activación explícita |
| [Sesión activa](after-flash-active.png) | Candidata 2 | Sesión y receptor |
| [Archivos](after-flash-files.png) | Candidata 2 | Cuatro archivos resumidos |
| [Lista completa](after-flash-files-expanded.png) | Candidata 2 | Nombres completos |
| [Dirección y teclado](after-flash-large-keyboard-filled.png) | Candidata 2 | Entrada manual con texto grande |
| [Confirmar apagado](stop-confirm-large.png) | Candidata 2 | Diálogo al 200 % |
| [Selección vacía final](final-cleared-top.png) | Final 3 | Estado después de «Quitar todos» |
| [Marfil final](flash-final-ivory.png) | Final 3 | Flash apagado |
| [Oscuro final](flash-final-dark.png) | Final 3 | Flash apagado |

| APK | Bytes | SHA-256 |
| --- | ---: | --- |
| Baseline de tres temas | 14.856.229 | `786b3395c9f2ba9ac13f2c869971bdd01ed94b2eede4230a365a5d927856c115` |
| Candidata 2 | 14.872.613 | `3afb079424bfa388bad920ea73436b660af60bcf7064b0861996834cbc8d7b19` |
| Final 3 instalado | 14.872.613 | `16f5ab6d3ceffbf5ac14428075b94c61c9fa4ac7343d8f2b00437de5f4e0900a` |

Los hashes se comprobaron contra los APK locales. [FlashActivity.kt](../../../app/src/main/java/com/example/wifidrop/FlashActivity.kt) y [FlashComponents.kt](../../../app/src/main/java/com/example/wifidrop/FlashComponents.kt) son idénticos entre la candidata 2 y la final 3. Después de la candidata 2 sólo cambió el texto de estado de `clearFiles` en [FlashForegroundService.kt](../../../app/src/main/java/com/example/wifidrop/FlashForegroundService.kt). El manifiesto registra los hashes de esas tres fuentes en ambas instantáneas; no representa por sí solo un inventario del árbol completo.

La evidencia auxiliar, los APK y cuatro archivos sintéticos permanecen en `.local/flash-ux-2026-09-12/` (no versionado). Esta galería no contiene binarios instalables.

## Límites

No había conexión Wi-Fi durante el recorrido. No se revalidaron UDP, envío, cancelación durante el contenido ni comparación real de códigos entre equipos. La aprobación explícita por archivo se conserva en el código, pero este pase no añade una prueba física de ella. Los mensajes de los seis errores no se presentan como seis fallos de red reproducidos en el teléfono.

La comprobación al 200 % cubre los estados Flash descritos; no completa la revisión del selector de apariencias ni una auditoría integral de accesibilidad. Los [hallazgos F-01/F-02](../../../docs/VISUAL_FINDINGS-1.4.0.md) conservan su estado pendiente. No se completó una revisión Figma ni se publicó una nueva release.

Para continuar quedan la validación de red entre equipos y la revisión al 200 % del selector de apariencias del pase anterior. La comprobación de instalación y de «Quitar todos» del APK final está cerrada.

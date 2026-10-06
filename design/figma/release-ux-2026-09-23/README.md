# Qetara · referencia UX de publicación · 2026-09-23

## Estado de Figma

Archivo autorizado: [Qetara en Figma](https://www.figma.com/design/FZhnKBdDPJWpPrbxn3AGQQ). La lectura remota confirmó una sola página, `Page 1`, ID `0:1`, vacía y sin variables ni estilos de texto locales. Inter está disponible, incluidos Regular, Medium, Semi Bold y Bold.

La reconexión permitió inspeccionar el archivo y las bibliotecas. **No se creó ni modificó ningún nodo. No se realizó una revisión visual de Figma.** La escritura de un marco de referencia mediante `use_figma` fue rechazada por el mismo límite que las últimas lecturas:

> You've reached the Figma MCP tool call limit on the Starter plan. Upgrade your plan for more tool calls: https://www.figma.com/files/team/1653569315983842121/all-projects?upgrade=mcp_rate_limit_paywall

La respuesta no proporcionó fecha de restablecimiento. Un segundo enlace de cuenta exigió reautenticación desde la tarea coordinadora; no fue posible confirmar su identidad desde el subagente. No se cambió de cuenta para eludir el límite.

## Referencia local conservada

[Referencia visual editable](reference.html) · [Especificación estructurada de estados](states.json)

`reference.html` documenta propuestas de los estados concretos; **no es una captura de la aplicación, ni un diseño importado o revisado en Figma**. Los datos y nombres de ejemplo son sintéticos. Las capturas de base enlazadas abajo pertenecen a sus revisiones originales y no verifican los cambios de esta fecha.

Fuentes de diseño: [tokens desktop](../desktop-tokens.json), [tokens móvil](../mobile-tokens.json), [referencia móvil](../MOBILE.md), [símbolo original de escritorio](../../../pc/src/main/resources/qetara-brand.svg). Se conservan Inter, la paleta y los radios existentes. El símbolo se referencia desde su SVG original con proporción 1:1 del viewport, sin reescribir el path ni deformarlo.

## Estados acordados para implementación

| Estado | Comportamiento / referencia | Fuente de código |
| --- | --- | --- |
| Android · Chat / búsqueda + teclado | Campo «Buscar texto o equipo» y cierre permanecen visibles con IME. El contador pluraliza «N mensajes encontrados». Los resultados ocupan el espacio disponible. La cabecera informativa y el editor se ocultan mientras se busca; cerrar restaura el editor con su borrador. | `P2pMessagesTab.kt` |
| Android · Chat / vacío | Badge «Sin equipo», una sola tarjeta «Elige un equipo» con «Primero elige el equipo con el que vas a chatear.» y CTA «Ir a Conectar». Suprimir la ayuda y el aviso repetidos. | `P2pMessagesTab.kt` |
| Android · Preferencias / tamaño | Conservar A− / Normal / A+ visuales. Añadir semántica accesible reducir, restablecer y aumentar tamaño de texto. | `P2pPreferencesDialog.kt` |
| PC · Selección de archivos | Mantener resumen de hasta 3 archivos. En la fila existente, sustituir contador por «Revisar N archivos», junto a «Quitar todos», sin aumentar la altura. Con un archivo: «Revisar archivo y ubicación». | `DesktopSelectedFiles.kt`, `DesktopWorkspace.kt`, `DesktopFlash.kt` |
| PC · Diálogo de revisión | Lista desplazable de todos los archivos con nombre y ruta completos, sin truncar el contenido que identifica el archivo. Cierre explícito. Aplica a Compartir y Flash. | `DesktopSelectedFiles.kt` |
| PC · Equipos | Hasta 8 equipos inicialmente, conservar visible el seleccionado, permitir «Mostrar todos los equipos (N)» / «Mostrar menos equipos». Desactivar cambios de selección durante envío. | `DesktopWorkspace.kt` |
| PC · Recepción | Cabecera y sesión muestran STARTING «Activando recepción…», STOPPING «Deteniendo recepción…», ERROR con estado visible. Mantener el motivo de caducidad cuando termina en IDLE. | `DesktopWorkspace.kt` |
| PC · Reintento bloqueado | Mostrar primero el requisito actual que impide enviar; un error antiguo no debe ocultar una condición presente, como falta de equipo o archivos. | `DesktopWorkspace.kt` |

Los nombres de código son referencias de ubicación, no pruebas de ejecución. La verificación de builds, pruebas y capturas posteriores corresponde a la tarea coordinadora y sus agentes de implementación.

## Evidencia previa inspeccionada

- [Android Chat claro](../mobile-surface-review/chat-light.png): captura previa con repetición «Elige un equipo». Su [manifest](../mobile-surface-review/manifest.json) registra APK y dispositivo de aquella revisión.
- [Desktop selección 1160 × 800](../previews/desktop-selected-1160x800.png): render Compose/Skia con datos sintéticos y sin red; procedencia en su [manifest](../previews/manifest.json).
- [Preferencias móvil](../mobile-physical-review/updated-preferences-light.png): referencia para mantener aspecto de los controles de tamaño.

## Descubrimiento de componentes

No se encontraron archivos Code Connect locales mediante búsqueda de `*.figma.*`, `@FigmaConnect` o `FigmaConnect`. No había pantallas existentes que inspeccionar en el archivo remoto.

`get_libraries` devolvió Material 3 Design Kit y otros kits comunitarios. Las búsquedas remotas devolvieron Button, Search y Dialog de Material 3; por tanto **no se afirma que no haya componentes disponibles**. El servidor redujo la primera consulta múltiple de cinco búsquedas a una. Las búsquedas de variables `surface` y estilo `body` quedaron bloqueadas por cuota, así que su descubrimiento está pendiente.

## Reanudación en el mismo archivo

1. Confirmar acceso/cuota en el enlace autorizado. Leer el lienzo antes de escribir para no duplicar contenido ajeno añadido después.
2. Completar la búsqueda pendiente de variables/estilos. Contrastar los componentes del kit con Qetara; conservar los tokens y la geometría del producto como fuente de verdad.
3. Crear referencias editables de los estados de `states.json`, con auto-layout, Inter y componentes/tokens reutilizables. Mantener las capturas anteriores identificadas como evidencia previa y las propuestas como propuestas.
4. Devolver todos los IDs creados, enlazar cada marco y capturar el resultado. Revisar textos, wraps, rutas largas, fuente y geometría del símbolo. Solo entonces registrar una revisión visual Figma como completada.

No se ha creado otro archivo de Figma, cambiado permisos, publicado componentes ni modificado código de Android/PC desde esta tarea de referencia.

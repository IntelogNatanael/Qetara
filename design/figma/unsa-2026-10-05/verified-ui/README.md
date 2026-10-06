# Capturas de la aplicación

Estas imágenes proceden de los diálogos Compose de producción con datos
sintéticos. Se conservan por separado de los paneles editables de Figma.

- Escritorio: `DesktopDesignPreview`, escenario `flash-approvals`; envío
  individual, envío/recepción de tres archivos y lote de 128, en ventanas de
  1160 × 800 y 800 × 620. La casilla y las acciones permanecen en el pie fijo.
- Android: `FlashApprovalPreviewScenario`, actividad `FlashActivity` real en
  emulador Pixel 8 API 36; envío/recepción de tres y 128 archivos, incluyendo el
  final de ambas listas extensas. No se envían archivos ni se aprueban solicitudes
  desde este escenario visual; se restaura el estado original al terminar.

El texto, los archivos, la dirección y el código de comparación son ejemplos.
La lista y el cuerpo pueden desplazarse; una captura muestra sólo su posición
en ese instante. Los cuatro grupos del código se ajustan al ancho móvil.

`manifest.json` registra los hashes de las capturas. El
[informe de validación](../../../../docs/FLASH_BATCH_REVIEW-2026-10-05.md)
documenta por separado las pruebas de transporte y los límites de esta revisión.

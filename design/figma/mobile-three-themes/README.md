# Tres apariencias de Qetara

Estado al pausar el 12 de septiembre de 2026: implementado e instalado en el teléfono; revisión visual final pendiente. Cambios locales sobre `d7be5c5`, sin publicar en GitHub.

Abre [la comparación](comparison.html) para ver capturas reales de Descargas con Marfil, Gris azulado y Oscuro. Las tres conservan la geometría del símbolo y la estructura de la interfaz. Marfil recupera el fondo cálido `#FFF7ED`; Gris azulado usa `#E7EFF2`; Oscuro usa `#101B22`.

El selector está en **⋮ → Ajustes de lectura y avisos → Apariencia**. Las opciones manuales se guardan inmediatamente en preferencias independientes de la sesión. **Seguir sistema** elige Gris azulado en modo claro y Oscuro en modo oscuro. La apariencia se comparte con Flash. El icono del launcher mantiene su fondo fijo; el splash nativo previo al contenido sigue el modo del sistema.

## Validación realizada

- `:app:assembleRelease :app:lintRelease :app:testDebugUnitTest --offline --no-daemon --max-workers=2`: completados correctamente; 202 pruebas sin fallos, errores ni omisiones.
- Lint: cero errores y tres advertencias (`UsableSpace` en FileTransfer; `UseKtx` en TransferForegroundService y P2pRouteTheme). La advertencia de P2pRouteTheme corresponde al nuevo fondo de ventana.
- APK firmado e instalado conservando datos en CPH2743, Android 16, 360 dp de ancho. Las cinco entradas de prueba de Descargas se conservaron.
- Marfil persistió después de detener el proceso y reabrir la app con el sistema en oscuro. Flash apagado también mostró Marfil.
- Oscuro manual se mantuvo al poner el sistema en claro. Seguir sistema cambió entre Gris azulado y Oscuro al cambiar el modo del sistema.
- [Contraste de pares opacos](contrast.json): mínimos de texto evaluados 4.572 (Gris azulado), 4.8085 (Marfil) y 5.5767 (Oscuro). No constituye una auditoría integral de accesibilidad.

## Para retomar

- Revisar visualmente el comparador en navegador y la captura de Gris azulado a tamaño completo.
- Terminar la revisión del selector al 200%: captura inicial disponible, desplazamiento y cierre aún pendientes de validación visual.
- Resolver la nueva advertencia `UseKtx`, actualizar evidencia si cambia el código, y registrar manifiesto de hashes antes del commit.
- Sincronizar Figma cuando permita continuar: la cuota del conector bloqueó la edición. Este HTML es una referencia local.

Al pausar se restauraron los ajustes originales del teléfono: escala del sistema 1.0 y modo oscuro. Qetara queda en Marfil, con el diálogo cerrado.

La evidencia auxiliar y el APK firmado están en `.local/three-themes-2026-09-12/` (no versionado). APK SHA-256: `786b3395c9f2ba9ac13f2c869971bdd01ed94b2eede4230a365a5d927856c115`.

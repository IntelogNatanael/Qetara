# Revisión cromática móvil · 11 de septiembre de 2026

Este informe conserva el APK y las capturas anteriores a la integración del
aleph con el nombre. La [revisión de marca posterior](../design/figma/mobile-brand-review/README.md)
documenta la cabecera actual y su nuevo APK; la ruta de salida se reutiliza.

## Resultado

Colores unificados en la GUI Android. Las tarjetas principales usan `surface`, los paneles interiores `surfaceContainerLow` y las acciones secundarias una misma escala gris. Los filtros de Conectar, Chat y Descargas comparten el verde petróleo de la navegación. Se sustituyeron mezclas y transparencias de superficies por roles opacos con su correspondiente color de contenido.

Actividad normal y «Listo» usan el acento primario; aprobación, pausa o reintento pendiente usan ámbar; los fallos usan rojo y las filas de cancelación son neutras. El indicador compacto de Flash activo dejó de usar el rojo predeterminado. Los errores conservan texto e iconos legibles sobre su propio fondo.

El símbolo reutiliza `ic_launcher_foreground.xml` **sin cambios respecto a HEAD**, incluido su azul `#102A43`, viewport y escala. Sólo cambia el fondo de la baldosa de la cabecera: `#EAF0EF` en claro y `#DDF1F0` en oscuro. El launcher y el splash originales permanecen intactos.

[Comparación de las diez capturas](../design/figma/mobile-color-review/README.md) · [Tokens y reglas](../design/figma/MOBILE.md).

## Validación de este APK

- `:app:assembleDebug :app:lintDebug --offline --console=plain`: correctos en la compilación final. Lint sin errores; conserva dos advertencias anteriores (`UsableSpace`, `UseKtx`).
- `git diff --check`: correcto. Revisión estática independiente de colores, callbacks y condiciones de las acciones; no se cambiaron protocolos ni reglas de transferencia.
- Diez capturas inspeccionadas en emulador Android 16/API 36, 360 × 800 dp, fuente 100 %: Conectar sin permiso, Enviar sin archivos, Chat sin equipo, Descargas vacías y Flash desactivado, en claro y oscuro.
- No se repitieron pruebas JVM para esta modificación visual. Las 199 pruebas y escenarios con texto al 200 % pertenecen a la [iteración previa](MOBILE_DESIGN_REVIEW-2026-09-11.md).

APK debug local: `app/build/outputs/apk/debug/app-debug.apk`, 22,982,982 bytes.

```text
SHA-256 bb53b8c2cda51b62f9b947f90d278684e2d5b62b6f85198bef221576464a3889
```

## Contraste

Cálculo sRGB sobre los roles opacos declarados; [valores y hash del tema](../design/figma/mobile-color-review/contrast.json).

| Par | Claro | Oscuro |
| --- | ---: | ---: |
| Texto principal / tarjeta | 14,64:1 | 13,00:1 |
| Texto / botón primario | 6,21:1 | 7,73:1 |
| Texto / acción secundaria | 5,19:1 | 7,00:1 |
| Texto / selección | 7,32:1 | 7,56:1 |
| Texto / aviso | 8,24:1 | 7,92:1 |
| Texto / error | 8,58:1 | 9,35:1 |
| Símbolo / baldosa | 12,70:1 | 12,49:1 |

Los 22 pares comprobados superan 4,5:1. Esta comprobación de colores no certifica toda la accesibilidad de la aplicación.

## Alcance y observaciones

Figma MCP sigue limitado por la cuota del plan Starter. La referencia es local; no se declara revisión ni sincronización del archivo remoto.

En este emulador con SwiftShader algunas navegaciones dejaron la cabecera o parte de la navegación sin dibujar, aunque seguían presentes en el árbol de interfaz. Volver al inicio de Android y reexponer la misma tarea restauró el dibujo sin cambiar el estado ni el código; también lo restauró el cambio de tema. Se repitieron las capturas afectadas y se inspeccionó su contenido completo. La revisión estática no encontró sobrepintado fuera del área de contenido. Es compatible con un problema de invalidación gráfica del emulador, pero no se ha confirmado la causa ni contrastado en hardware físico.

No se validaron en ejecución conversaciones pobladas, progreso o resultados reales, aprobaciones entre equipos ni todos los diálogos. Los pares de color de esos estados se revisaron en código. Esta iteración no publica una nueva versión ni modifica MSI, firma de distribución o F-Droid.

# Revisión de la GUI móvil · 11 de septiembre de 2026

Este informe conserva la evidencia del rediseño previo. La revisión posterior
de colores y el nuevo APK debug se documentan en
[MOBILE_COLOR_REVIEW-2026-09-11.md](MOBILE_COLOR_REVIEW-2026-09-11.md).
La ruta de salida del APK se reutiliza; el hash de cada informe identifica su compilación.

## Resultado

Interfaz Android renovada en el árbol de trabajo: tipografía Inter, paleta clara y oscura, cabecera con el símbolo original, navegación adaptable, Conectar, selección de archivos, Chat, Descargas y Flash. Se conservaron callbacks y condiciones de envío, recepción, aprobación y guardado. Esta iteración no es una nueva publicación ni modifica los instaladores de la candidata anterior.

La [galería de cinco capturas](../design/figma/mobile-previews/README.md) contiene imágenes reales del APK final de revisión y archivos de muestra sintéticos. El [manifiesto](../design/figma/mobile-previews/manifest.json) registra dimensiones y SHA-256. No son marcos editables de Figma.

## Comprobaciones

- Compilación Android debug y lint: correctos. Lint: **0 errores y 2 advertencias preexistentes** (`UsableSpace` en `FileTransfer.kt`, `UseKtx` en `TransferForegroundService.kt`).
- Pruebas Android JVM: **199 correctas**, 0 fallos, 0 errores, 0 omitidas, en 36 informes. No son pruebas de interfaz.
- Revisión independiente de los cambios y `git diff --check`: correctos; sin callbacks eliminados ni cambios en las reglas de aprobación.
- Los cuatro TTF dentro del APK coinciden byte a byte con sus fuentes locales y los originales de escritorio. OFL completa incluida una vez en el agregado `NOTICES.txt` que abre el diálogo de licencias.
- Vector del launcher, icono adaptativo y contorno del logo sin cambios respecto a HEAD. La cabecera usa directamente `ic_launcher_foreground` con su viewport y proporción originales.

APK **debug**, para revisión local, disponible en `app/build/outputs/apk/debug/app-debug.apk`: 22.982.982 bytes.

```text
SHA-256 62f8690289ee6e98a8b590e9f9f977fe954c9be7a5488559c738eaeb824287da
```

No se ha firmado una nueva distribución pública, generado MSI ni actualizado una receta F-Droid en esta iteración.

## Recorrido visual

Emulador Pixel 8 con Android 16/API 36, sin teléfono físico, iniciado en modo `-read-only`. Las dimensiones siguientes son dp; los PNG indican sus dimensiones reales en el manifiesto.

| Configuración | Comprobación |
| --- | --- |
| 411 × 914, fuente 100 %, claro | Conectar sin permiso, Enviar vacío, Chat sin equipo, Canal con solo este equipo, editor con teclado y Descargas vacías. |
| 360 × 800, fuente 130 %, claro | Conectar, biblioteca con archivo de muestra, Flash desactivado y activo. |
| 320 × 640, fuente 200 %, oscuro | Flash: temporizador, desplazamiento hasta archivos y búsqueda de receptor, pie de envío. |
| 360 × 800, fuente 100 %, claro, APK final | Conectar; selección de **dos archivos** mediante el selector Android; nombres y botón deshabilitado hasta conectar; Chat sin equipo; biblioteca con archivo sintético; Flash desactivado. |
| 320 × 640, fuente 200 %, oscuro, APK final | Cabecera completa, etiqueta «Descargas» completa, desplazamiento de biblioteca y Conectar hasta alcanzar «Permitir conexión cercana». Barras del sistema legibles. |

Las capturas auxiliares y los XML permanecen en `.local/design-review/mobile-qa/`, ignorado por Git. Entre las pruebas finales están `checked-send-two-files-stable.png`, `verified-small-library-final.png` y `verified-small-connect-action.png`.

Los archivos `Guia-Qetara.txt` y `Notas-de-diseno.md` se crearon para esta revisión y se copiaron al emulador. El archivo de la biblioteca **no llegó mediante una transferencia Qetara**. No se enviaron archivos ni mensajes a otros equipos. Activar Flash o entrar al canal en un emulador no acredita descubrimiento UDP ni una entrega real.

## Correcciones encontradas durante la revisión

- La acción Flash superior apilaba más contenido del que cabía en la barra. Ahora icono y nombre comparten fila; con texto grande se usa el icono con indicador de actividad y descripción accesible. La altura de la cabecera sigue la tipografía.
- El temporizador de Flash podía quitarle todo el ancho al título. Ambos pueden pasar a líneas separadas. El encabezado del receptor también se adapta al ancho y la escala.
- El panel de sesión comprimía su título al aumentar el texto. Usa un título breve y distribuye la acción en otra línea cuando hace falta.
- Las etiquetas de navegación podían recortarse. Cuando no caben, los cinco iconos conservan el mismo ancho y el nombre completo de la pestaña activa aparece debajo; las etiquetas accesibles de cada pestaña permanecen.
- Las barras de Android se actualizan con el tema Compose para conservar contraste al cambiar de modo.

## Contraste y recursos

Cálculo sRGB sobre los colores opacos declarados, independiente de los PNG:

| Par de colores | Claro | Oscuro |
| --- | ---: | ---: |
| Texto principal / tarjeta | 14,64:1 | 13,00:1 |
| Texto secundario / tarjeta | 5,98:1 | 8,22:1 |
| Texto / botón primario | 6,21:1 | 7,73:1 |

Estos pares no constituyen una certificación global de accesibilidad. [Procedencia de Inter](../licenses/inter-mobile-PROVENANCE.md) y [tokens](../design/figma/mobile-tokens.json). El informe de comprobación del APK se conserva en `.local/design-review/mobile-artifact-check.json`.

## Límites de la revisión

No se recorrieron conversaciones pobladas con mensajes recibidos y enviados, el diálogo de aprobación Flash entre dos equipos, progreso real, reintentos, cancelación de transferencias ni el ciclo completo de abrir o guardar un archivo recibido. No se declara validación en hardware físico, todas las orientaciones o TalkBack.

El emulador inicial presentó fallos gráficos de SystemUI (`EGL_BAD_CONFIG`) y capturas negras después de cambiar resoluciones. Se descartaron esas capturas como evidencia del resultado. Se repitió el cierre con arranque en frío, SwiftShader, 4 GB de RAM y renderizado sin actualizaciones parciales; las cinco capturas seleccionadas se inspeccionaron individualmente. También apareció un aviso de la aplicación de sistema Digital Wellbeing, cerrado en el emulador; no se atribuye ese fallo a Qetara.

Figma MCP volvió a responder con la cuota agotada del plan Starter. El archivo remoto continúa vacío; no se declara sincronización ni revisión en Figma. La [referencia móvil local](../design/figma/MOBILE.md) conserva los tokens y las capturas de esta iteración.

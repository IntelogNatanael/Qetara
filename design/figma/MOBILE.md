# Qetara · Referencia móvil

Referencia implementada en el árbol de trabajo el 11 de septiembre de 2026.
La variante actual integra el aleph y el nombre sin fondo propio en ambos
temas y adapta la tinta del símbolo al texto. La compilación, lint y las capturas
en ambos temas están en la [revisión de marca adaptable](mobile-brand-adaptive/README.md).
La [revisión de la marca anterior](mobile-brand-review/README.md) conserva las
pruebas y capturas de la variante con una base clara en oscuro.
La revisión de color anterior a ese ajuste está en la
[revisión cromática](../../docs/MOBILE_COLOR_REVIEW-2026-09-11.md).
Las pruebas del rediseño previo (199 pruebas JVM, maquetado, tipografía y
navegación con texto grande) se documentan por separado en la
[revisión móvil anterior](../../docs/MOBILE_DESIGN_REVIEW-2026-09-11.md).

## Referencia y alcance

Figma MCP volvió a responder con el límite de consultas del plan Starter.
El [archivo de referencia](https://www.figma.com/design/FZhnKBdDPJWpPrbxn3AGQQ)
no contiene marcos: no se declara una revisión ni sincronización con Figma.
La referencia de esta iteración se conserva localmente en
[mobile-tokens.json](mobile-tokens.json) y en el código Compose.

Los valores se extrajeron de
[P2pRouteTheme.kt](../../app/src/main/java/com/example/wifidrop/P2pRouteTheme.kt).
El JSON registra el SHA-256 de esa instantánea, los 35 colores explícitos de
cada modo, 15 estilos tipográficos y 5 radios Material 3. Los roles de color
que el código no redefine conservan sus valores predeterminados de Material 3;
no se inventan valores para ellos en este registro.

Este trabajo es posterior a la candidata compilada desde `588a92f`. Las
validaciones de sus APK anteriores no acreditan automáticamente los recursos
y pantallas de esta iteración móvil.

## Color y jerarquía

El tema sigue el modo claro u oscuro del sistema mediante
`isSystemInDarkTheme()`. Los componentes consumen `MaterialTheme.colorScheme`.

| Rol Material 3 | Claro | Oscuro |
| --- | --- | --- |
| `primary` | `#0A6B77` | `#88D3D7` |
| `onPrimary` | `#FFFFFF` | `#00363E` |
| `background` | `#F4F6F5` | `#101B22` |
| `surface` | `#FFFFFF` | `#182831` |
| `onSurface` | `#102A43` | `#E7EFF2` |
| `onSurfaceVariant` | `#536672` | `#AFC2CA` |
| `outlineVariant` | `#DDE5E5` | `#344A54` |
| `error` | `#B3261E` | `#FFB4AB` |

El fondo separa las tarjetas, el acento identifica acciones y selección y los
mensajes de error usan el rol correspondiente. Los textos de estado y las
etiquetas se conservan junto a los iconos; el color no sustituye su contenido.

| Uso compartido | Fondo | Texto e iconos |
| --- | --- | --- |
| Selección, listo y actividad normal | `primaryContainer` | `onPrimaryContainer` |
| Aprobación o atención pendiente | `tertiaryContainer` | `onTertiaryContainer` |
| Error | `errorContainer` | `onErrorContainer` |
| Tarjeta principal | `surface` | `onSurface` |
| Panel interior o información auxiliar | `surfaceContainerLow` | `onSurface` / `onSurfaceVariant` |
| Acción secundaria neutra | `surfaceContainerHigh` | `onSurfaceVariant` |

Los filtros de Conectar, Chat y Descargas comparten `qetaraFilterChipColors()`:
misma selección verde petróleo que la navegación. Las superficies usan roles
opacos en lugar de mezclas o transparencias que dependan del fondo. Recibir,
sincronizar y enviar comparten el acento de actividad; una cancelación es neutra.

## Símbolo y tinta adaptada

El recurso `ic_launcher_foreground` permanece idéntico a HEAD: conserva su
path, tinta original `#102A43`, viewport 108 × 108, escala uniforme `0.65625`
y proporción. No se modifican el launcher ni su fondo original.

La cabecera reutiliza ese recurso mediante `Image` y aplica únicamente en la
GUI `ColorFilter.tint(MaterialTheme.colorScheme.onBackground)`: `#102A43`
en claro y `#E7EFF2` en oscuro, igual que el nombre. Esta adaptación de tinta
no altera la geometría ni el archivo vectorial. En ambos temas el símbolo
aparece directamente sobre el fondo de la cabecera, sin base propia, baldosa,
borde ni sombra. La función anterior `qetaraBrandBackdrop()` se ha eliminado.

Aleph y nombre comparten una fila y centro vertical. Se mantiene el viewport
de 40 × 40 dp y se elimina el espacio adicional de 10 dp: el margen propio
del vector separa el trazo del nombre. El modo de conexión sigue debajo,
alineado con la palabra, y el conjunto conserva el acceso al selector de modo.
La altura de la cabecera se ajusta a la fila de marca y al tamaño del texto.
La inspección de código y del recurso está completa; todavía no se atribuyen
compilaciones ni capturas nuevas a esta variante de tinta adaptada.

## Tipografía y forma

Inter 4.1 se incluye localmente en cuatro TTF estáticos: Regular 400, Medium
500, SemiBold 600 y Bold 700. `FontFamily` los carga desde `res/font`; esa
declaración no descarga fuentes durante la ejecución. Los archivos móviles
son idénticos a los de escritorio, con nombres externos adaptados a recursos
Android. Véase la [procedencia y licencia](../../licenses/inter-mobile-PROVENANCE.md).

| Uso | Rol | Tamaño / interlínea, sp | Peso |
| --- | --- | ---: | ---: |
| Encabezado de Conectar | `headlineSmall` | 24 / 32 | 600 |
| Título del paso | `titleLarge` | 22 / 28 | 600 |
| Sección | `titleMedium` | 16 / 24 | 600 |
| Texto principal de las tarjetas de conexión | `bodyMedium` | 14 / 22 | 400 |
| Ayuda y metadatos | `bodySmall` | 12 / 18 | 400 |
| Etiqueta de acción | `labelLarge` | 14 / 20 | 600 |

Todos los estilos del tema usan espaciado de letras de 0 sp. Los tamaños en sp
permiten el escalado de texto del sistema; su legibilidad y composición con
escalas 1, 1,3 y 2 se inspeccionaron en los estados enumerados en el informe.
Esto no acredita todas las pantallas ni todas sus combinaciones de contenido.

Los radios de `MaterialTheme.shapes` son 6, 10, 16, 20 y 24 dp para
`extraSmall`, `small`, `medium`, `large` y `extraLarge`. Algunos controles fijan
un radio propio: en Conectar las tarjetas usan 16 dp y los botones principales,
secundarios y tonales usan 12 dp.

## Composición de Conectar

[P2pConnectTab.kt](../../app/src/main/java/com/example/wifidrop/P2pConnectTab.kt)
mantiene el orden de sus elementos, estados y callbacks:

- Encabezado breve y elección entre la misma Wi-Fi y Wi-Fi Direct.
- Tarjeta de identificación del equipo y un paso de conexión acorde al estado.
- Datos de sesión y dirección IP desplegables, nombrados mediante «código y PIN».
- Equipos, favoritos, confianza y ayuda con sus condiciones originales.

Las tarjetas de equipo tienen 16 dp de padding; los pasos usan 20 dp, con
12 dp entre contenidos y 16 dp entre tarjetas. Los botones y selectores de
modo tienen una altura mínima de 48 dp, sin fijar una altura máxima para los
textos. Los grupos de acciones usan `FlowRow` para pasar a otra línea cuando
no caben. Los títulos de paso exponen la semántica de encabezado.

Los iconos proceden de los Material Icons ya incluidos, en su variante Rounded;
la acción de envío usa su variante AutoMirrored. No se ha incorporado una
biblioteca de iconos adicional para este archivo.

## Mensajes, biblioteca y Flash

[Mensajes](../../app/src/main/java/com/example/wifidrop/P2pMessagesTab.kt) usa el
fondo del tema, tarjetas de estado vacío y burbujas de 16 dp que distinguen
recibidos, enviados y fallidos mediante roles Material. El editor conserva sus
restricciones, recuperación de adjuntos y acciones de copiar, compartir,
reenviar, cancelar y descargar. Sus controles táctiles se dimensionan a 48 dp;
la cabecera permite envolver las herramientas cuando falta ancho.

[Descargas](../../app/src/main/java/com/example/wifidrop/P2pDownloadsTab.kt)
mantiene separadas la biblioteca y la actividad. Las filas usan iconos de tipo,
nombre, tamaño y fecha, con superficies y bordes del tema. Acciones y filtros
pueden pasar a otra línea. Los espacios principales son de 16/20 dp y los
estados vacíos explican la acción disponible sin requerir conexión para leer
archivos ya guardados.

[Flash](../../app/src/main/java/com/example/wifidrop/FlashActivity.kt) conserva
la selección, las verificaciones y la acción de envío en el pie. El contenido
es desplazable y el temporizador puede pasar a otra línea. El recorrido en
320 × 640 dp, fuente 2 y modo oscuro inspeccionó el temporizador, los bloques de
archivos/receptor y el pie. No incluyó una transferencia real entre equipos.

La [cabecera](../../app/src/main/java/com/example/wifidrop/P2pScreenRoute.kt)
reutiliza el vector del launcher sin deformar su silueta, conserva el selector
de conexión y agrupa Ajustes/Acerca de en un menú. Con texto grande, Flash usa
una acción de icono con descripción accesible. La
[navegación](../../app/src/main/java/com/example/wifidrop/P2pUiChrome.kt)
reserva el mismo ancho para cada icono y muestra debajo la etiqueta completa
activa cuando no cabe en la fila. La variante compacta se comprobó a 320 dp
con texto al 200 %; las capturas seleccionadas están en la galería local.

## Validación y límites

Se verificaron los hashes de los recursos y se contrastaron estos tokens con
el código fuente. `assembleDebug`, `testDebugUnitTest` y
`lintDebug` del rediseño anterior fueron correctos: **199 pruebas, 0 fallos, 0 errores, 0 omitidas**;
el lint inspeccionado contiene 0 errores y dos advertencias preexistentes
(`UsableSpace` y `UseKtx`). El informe identifica el APK debug exacto mediante
su SHA-256; no se presenta como una distribución pública. Esos resultados no
validan por sí solos el cambio actual de tinta del aleph: su compilación y
recorrido visual se registrarán en la nueva revisión de marca adaptable.

El recorrido usó un emulador Pixel 8 API 36 iniciado sin ventana, en modo
`-read-only`, con configuraciones 411 × 914 dp/fuente 1/claro,
360 × 800 dp/fuente 1,3/claro y 320 × 640 dp/fuente 2/oscuro. No se usó el
teléfono físico. Las capturas y árboles auxiliares permanecen privados en
`.local/design-review/mobile-qa/`; sus nombres y estados inspeccionados figuran
en la [matriz de revisión](../../docs/MOBILE_DESIGN_REVIEW-2026-09-11.md).

La biblioteca poblada usa un archivo sintético copiado al emulador. No se
realizaron envíos de red, emparejamiento real ni revalidación de UDP. No se
declaran todos los estados poblados, una auditoría completa de accesibilidad,
una revisión Figma completada ni una candidata de publicación aprobada.

## Capturas y resultado de la iteración anterior

[Galería de cinco vistas](mobile-previews/README.md) · [Validación anterior y límites](../../docs/MOBILE_DESIGN_REVIEW-2026-09-11.md). El APK de aquella revisión tiene SHA-256
62f8690289ee6e98a8b590e9f9f977fe954c9be7a5488559c738eaeb824287da.

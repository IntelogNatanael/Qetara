# Qetara · Referencia móvil

Desde el 5 de octubre de 2026, usar [Qetara · Diseño y experiencia](https://www.figma.com/design/KRV4r4m1p1fSOFZOHOMV0B)
en la cuenta `cchoquenairat@unsa.edu.pe`, equipo `dev-UNSA`. La [revisión actual](unsa-2026-10-05/README.md)
cubre el logo marfil y la confirmación por lote Flash; los temas completos y su evidencia anterior siguen descritos abajo.

Referencia actualizada el 12 de septiembre de 2026. Qetara permite elegir
**Marfil, Gris azulado u Oscuro**, o **Seguir sistema**. El aleph y el nombre
siguen sin fondo propio en la cabecera, con tinta adaptada al tema. El alcance
de esta iteración se documenta en la
[revisión de los tres temas](mobile-three-themes/README.md).
La [revisión de superficies](mobile-surface-review/README.md) conserva la
compilación, contrastes y capturas del ajuste gris azulado anterior.
La [revisión de marca adaptable](mobile-brand-adaptive/README.md) conserva
la evidencia del ajuste anterior de la cabecera.
La [revisión de la marca anterior](mobile-brand-review/README.md) conserva las
pruebas y capturas de la variante con una base clara en oscuro.
La revisión de color anterior a ese ajuste está en la
[revisión cromática](../../docs/MOBILE_COLOR_REVIEW-2026-09-11.md).
Las pruebas del rediseño previo (199 pruebas JVM, maquetado, tipografía y
navegación con texto grande) se documentan por separado en la
[revisión móvil anterior](../../docs/MOBILE_DESIGN_REVIEW-2026-09-11.md).

## Referencia y alcance

En la revisión histórica del 12 de septiembre, Figma MCP volvió a responder con el límite de consultas del plan Starter.
El [archivo de referencia](https://www.figma.com/design/FZhnKBdDPJWpPrbxn3AGQQ)
no contiene marcos: no se declara una revisión ni sincronización con Figma.
La referencia de esta iteración se conserva localmente en
[mobile-tokens.json](mobile-tokens.json) y en el código Compose.

Los valores se extrajeron de
[P2pRouteTheme.kt](../../app/src/main/java/com/example/wifidrop/P2pRouteTheme.kt).
El JSON registra el SHA-256 de esa instantánea, los roles de color,
15 estilos tipográficos y 5 radios Material 3. Marfil parte de la paleta clara
y sustituye sus superficies y neutros; los roles no sustituidos conservan
los de la paleta base. Los valores que ninguna paleta redefine conservan
los valores predeterminados de Material 3.

Este trabajo es posterior a la candidata compilada desde `588a92f`. Las
validaciones de sus APK anteriores no acreditan automáticamente los recursos
y pantallas de esta iteración móvil.

## Elegir apariencia

Ruta: **⋮ → Ajustes de lectura y avisos → Apariencia**. El
[selector](../../app/src/main/java/com/example/wifidrop/QetaraAppearanceControls.kt)
presenta «Seguir sistema» y tres filas manuales con nombre, muestra de color
y selección exclusiva. Las filas son pulsables completas, tienen un mínimo
de 48 dp y permiten que el texto pase a otra línea.

La elección se guarda inmediatamente mediante
[QetaraAppearanceStore](../../app/src/main/java/com/example/wifidrop/QetaraAppearanceStore.kt),
independiente de la sesión de transferencia, y se comparte con Flash.
Elegir un tema manual sustituye a Seguir sistema. Volver a Seguir sistema
usa Gris azulado cuando Android está en modo claro y Oscuro cuando está en
modo oscuro. Una selección manual se mantiene aunque cambie el modo del sistema.

## Color y jerarquía

El tema resuelve la preferencia guardada y consulta `isSystemInDarkTheme()`
sólo para la opción Seguir sistema. Los componentes consumen
`MaterialTheme.colorScheme`.

| Rol Material 3 | Marfil | Gris azulado | Oscuro |
| --- | --- | --- | --- |
| `primary` | `#0A6B77` | `#0A6B77` | `#88D3D7` |
| `onPrimary` | `#FFFFFF` | `#FFFFFF` | `#00363E` |
| `background` | `#FFF7ED` | `#E7EFF2` | `#101B22` |
| `surface` | `#FFFBF5` | `#F1F5F7` | `#182831` |
| `onSurface` | `#102A43` | `#102A43` | `#E7EFF2` |
| `onSurfaceVariant` | `#666158` | `#536672` | `#AFC2CA` |
| `outlineVariant` | `#DDCEBC` | `#C3D2DC` | `#344A54` |
| `error` | `#B3261E` | `#B3261E` | `#FFB4AB` |

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
y proporción. El launcher recupera el fondo marfil clásico `#FFF7ED`;
la apariencia elegida no cambia el icono instalado. Los diez WEBP de
compatibilidad y el fondo vectorial se recuperaron byte a byte de `d7be5c5^`,
antes del cambio a gris azulado. El splash nativo sigue el modo
claro u oscuro de Android, no la preferencia manual de Qetara: antes de
cargar Compose puede diferir del tema elegido dentro de la aplicación.
Conserva su geometría propia y adapta la tinta al modo oscuro del sistema.

La cabecera reutiliza ese recurso mediante `Image` y aplica únicamente en la
GUI `ColorFilter.tint(MaterialTheme.colorScheme.onBackground)`: `#102A43`
en Marfil y Gris azulado, y `#E7EFF2` en Oscuro, igual que el nombre. Esta adaptación de tinta
no altera la geometría ni el archivo vectorial. En los tres temas el símbolo
aparece directamente sobre el fondo de la cabecera, sin base propia, baldosa,
borde ni sombra. La función anterior `qetaraBrandBackdrop()` se ha eliminado.

Aleph y nombre comparten una fila y centro vertical. Se mantiene el viewport
de 40 × 40 dp y se elimina el espacio adicional de 10 dp: el margen propio
del vector separa el trazo del nombre. El modo de conexión sigue debajo,
alineado con la palabra, y el conjunto conserva el acceso al selector de modo.
La altura de la cabecera se ajusta a la fila de marca y al tamaño del texto.
La revisión actual incluye compilación, lint y comprobaciones físicas de la
selección de apariencia. Los informes distinguen las capturas de Android de
las comprobaciones estáticas del splash y los vectores de los comparadores.

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
permiten el escalado de texto del sistema. Las inspecciones con escalas 1,
1,3 y 2 pertenecen a los estados e iteraciones enumerados en los informes
anteriores. La revisión al 200 % del nuevo selector y los tres temas sigue
pendiente; no se extrapolan aquellas comprobaciones a este cambio.

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

La [cabecera](../../app/src/main/java/com/example/wifidrop/P2pScreenRoute.kt)
reutiliza el vector del launcher sin deformar su silueta, conserva el selector
de conexión y agrupa Ajustes/Acerca de en un menú. Con texto grande, Flash usa
una acción de icono con descripción accesible. La
[navegación](../../app/src/main/java/com/example/wifidrop/P2pUiChrome.kt)
reserva el mismo ancho para cada icono y muestra debajo la etiqueta completa
activa cuando no cabe en la fila. La variante compacta se comprobó a 320 dp
con texto al 200 %; las capturas seleccionadas están en la galería local.

## Flash: receptor, archivos y acción siguiente

[FlashActivity.kt](../../app/src/main/java/com/example/wifidrop/FlashActivity.kt)
y [FlashComponents.kt](../../app/src/main/java/com/example/wifidrop/FlashComponents.kt)
organizan la pantalla alrededor del siguiente paso. Apagado, Flash presenta
«Comparte en un momento», tres instrucciones breves, el nombre editable del
equipo y una única activación explícita por 30 minutos. Abrir la pantalla no
activa el servicio.

La sesión activa muestra estado y tiempo, con dirección y apagado como
acciones secundarias. El flujo sigue **receptor → archivos → acción contextual**:

- La búsqueda automática existente se conserva. «Volver a buscar» permite
  repetirla y la dirección IP se despliega como alternativa, sin un indicador
  de búsqueda inventado. El receptor elegido se resume y «Cambiar equipo»
  vuelve a mostrar el selector. Se conserva la selección exacta por identidad,
  dirección y puerto; al vencer no se sustituye automáticamente por otro equipo.
- Es posible elegir archivos aunque todavía no haya receptor. Se conservan
  agregar, cancelar la preparación y quitar todos. Con más de tres archivos,
  «Ver los N archivos» despliega la selección entera con nombres completos;
  «Mostrar menos» recupera el resumen. Las selecciones de hasta tres muestran
  también el nombre completo.
- El pie ofrece «Elegir receptor», «Elegir archivos» o «Solicitar envío» según
  lo que falte. Se oculta cuando aparece el teclado para dejar espacio al
  campo de dirección. Cambiar de fase devuelve el contenido al inicio. Las
  operaciones aparecen antes del flujo y conservan su cancelación; actividad
  y avisos opcionales quedan después.

Apagar con preparación, solicitudes, operaciones o archivos seleccionados
requiere confirmación. «Seguir en Flash» conserva la sesión. Los archivos
recibidos siguen disponibles. Los controles respetan la escala de lectura
de Qetara además de la escala del sistema, usan alturas mínimas de 48 dp
y superficies opacas con sus colores de contenido correspondientes.

Se añadieron mensajes específicos para seis errores ya existentes:
confirmación caducada, sesión del receptor cambiada, dirección inválida,
almacenamiento no disponible, respuesta incompatible y entrega sin confirmar.
«Quitar todos» actualiza el estado a «Selección vacía. Elige archivos para
compartir.». Estos ajustes de presentación no cambian el protocolo ni la
comparación y aprobación explícita de cada archivo en ambos equipos.

La [revisión Flash](mobile-flash-review/README.md) separa las capturas del APK
comprobado y la compilación final. En un CPH2743 con Android 16 se inspeccionó
la candidata 2: apagado/activo, reinicio del desplazamiento al activar, acción
para elegir receptor, cuatro archivos sintéticos con lista expandida y, al
200 %, sesión y diálogo de apagado completo con «Seguir en Flash».
Su SHA-256 es
`3afb079424bfa388bad920ea73436b660af60bcf7064b0861996834cbc8d7b19`.

La compilación final 3 completó `assembleRelease`, `lintRelease` y
`testDebugUnitTest` en modo offline: **202 pruebas, 0 fallos, 0 errores y
0 omitidas**, reconfirmadas en los XML. Lint mantiene **0 errores y
3 advertencias**, iguales al pase anterior: una `UsableSpace` y dos `UseKtx`.
El APK final tiene SHA-256
`16f5ab6d3ceffbf5ac14428075b94c61c9fa4ac7343d8f2b00437de5f4e0900a`.
Los hashes de `FlashActivity.kt` y `FlashComponents.kt` coinciden entre la
candidata 2 y la final 3; el único cambio posterior fue el mensaje al quitar
todos los archivos. La final 3 se instaló correctamente. Se seleccionó un
archivo sintético de 66 B y se comprobó que «Quitar todos» vaciaba la selección
y mostraba «Selección vacía. Elige archivos para compartir.», como registra
la [captura final](mobile-flash-review/final-cleared-top.png).
También se inspeccionó Flash apagado en Marfil y Oscuro sobre la final 3,
sin extender esa comprobación a receptores o transferencias en las tres
paletas. La escala propia de lectura de Qetara se mantuvo al 105 %; el
200 % del recorrido indicado corresponde al ajuste del sistema.
Al terminar, Flash quedó apagado y Qetara en Gris azulado, con su escala de
lectura al 105 %, la escala del sistema al 100 % y el modo oscuro original de
Android restaurado. Se retiraron los cuatro archivos sintéticos de este pase
del teléfono y su carpeta de pruebas; se conservaron las copias locales.

No había conexión Wi-Fi para este recorrido: no se añaden pruebas de UDP,
envío ni verificación real entre equipos. La comprobación al 200 % cubre los
estados Flash indicados, no el selector completo de apariencias. Figma sigue
bloqueado por la cuota Starter; no se declara una revisión allí ni publicación.

## Validación de los tres temas y límites

`assembleRelease`, `lintRelease` y `testDebugUnitTest` terminaron correctamente
en modo offline para la iteración anterior de apariencia: **202 pruebas Android aprobadas en
37 informes**, 0 fallos, 0 errores y 0 omitidas. Lint release terminó con
**0 errores y 3 advertencias**: `UsableSpace` en `FileTransfer.kt`, `UseKtx`
en `TransferForegroundService.kt` y una nueva `UseKtx` en `P2pRouteTheme.kt`.

El APK release firmado se instaló mediante `adb install -r` en un teléfono
CPH2743 con Android 16. Marfil se conservó tras forzar el cierre y volver a
abrir la aplicación; Oscuro manual permaneció activo al cambiar Android a
modo claro. La preferencia es compartida por la GUI habitual y Flash y no
depende de abrir una sesión. La revisión física del selector de apariencia al
**200 % está pendiente**. Al cerrar ese pase se dejó el trabajo pausado,
con la escala del sistema restaurada al 100 %,
Qetara en Marfil y el diálogo cerrado. La inspección visual del comparador
en el navegador también queda pendiente.

Estas comprobaciones no añaden validación de UDP, transferencias o cancelación
entre equipos. Los [hallazgos F-01 y F-02](../../docs/VISUAL_FINDINGS-1.4.0.md)
siguen pendientes. No se declara publicación ni revisión Figma completada.

### Antecedentes del rediseño en emulador

Se verificaron los hashes de los recursos y se contrastaron estos tokens con
el código fuente. `assembleDebug`, `testDebugUnitTest` y
`lintDebug` del rediseño anterior fueron correctos: **199 pruebas, 0 fallos, 0 errores, 0 omitidas**;
el lint inspeccionado contiene 0 errores y dos advertencias preexistentes
(`UsableSpace` y `UseKtx`). El informe identifica el APK debug exacto mediante
su SHA-256; no se presenta como una distribución pública. Esos resultados
conservan su alcance histórico y no sustituyen la verificación de los tres
temas documentada arriba.

Ese recorrido anterior usó un emulador Pixel 8 API 36 iniciado sin ventana, en modo
`-read-only`, con configuraciones 411 × 914 dp/fuente 1/claro,
360 × 800 dp/fuente 1,3/claro y 320 × 640 dp/fuente 2/oscuro. No se usó el
teléfono físico en aquel pase. Las capturas y árboles auxiliares permanecen privados en
`.local/design-review/mobile-qa/`; sus nombres y estados inspeccionados figuran
en la [matriz de revisión](../../docs/MOBILE_DESIGN_REVIEW-2026-09-11.md).

La biblioteca poblada usa un archivo sintético copiado al emulador. No se
realizaron envíos de red, emparejamiento real ni revalidación de UDP. No se
declaran todos los estados poblados, una auditoría completa de accesibilidad,
una revisión Figma completada ni una candidata de publicación aprobada.

## Capturas y resultado de la iteración anterior

[Galería de cinco vistas](mobile-previews/README.md) · [Validación anterior y límites](../../docs/MOBILE_DESIGN_REVIEW-2026-09-11.md). El APK de aquella revisión tiene SHA-256
62f8690289ee6e98a8b590e9f9f977fe954c9be7a5488559c738eaeb824287da.

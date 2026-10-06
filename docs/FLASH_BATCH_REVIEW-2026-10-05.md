# Qetara 1.4.1 · lotes Flash y marca clásica

Revisión del 5 de octubre de 2026 sobre cambios locales en
`codex/flash-batches-classic-brand`. Se conservaron los cambios previos a esta
tarea. No se creó commit, tag, release, MSI ni entrega Android firmada.

## Comportamiento

Flash proponía cada archivo en una conexión Noise independiente y pedía una
confirmación por archivo. Ahora una selección de hasta 128 archivos usa un
manifiesto cifrado y una sola conexión: una comparación y aprobación en cada
equipo autoriza exactamente ese lote. Cada archivo conserva su progreso,
verificación SHA-256, publicación y acuse de recibo propios.

Los nuevos lotes, reintentos o conexiones requieren otra aprobación. Cancelar,
rechazar o fallar detiene los archivos restantes; no elimina los archivos ya
publicados ni convierte entregas confirmadas en canceladas. Los archivos no
confirmados permanecen seleccionados para un reintento explícito.

Se mantiene el protocolo individual anterior. Los lotes usan un tipo de paquete
y prologue distintos; si el receptor no los admite, aparece un aviso para
actualizar ambos equipos. No se degrada silenciosamente a aprobaciones por archivo.
El [contrato](../protocol/FLASH.md) detalla límites y compatibilidad.

En Compartir se revisó la autorización existente: un equipo confiado cuya clave
coincide no vuelve a pedir autorización; una clave cambiada se rechaza. No se
encontró el mismo defecto y no se cambió esa política.

## Marca y Figma

Se restauraron el fondo Android `#FFF7ED` y diez WEBP clásicos, idénticos byte a
byte a `d7be5c5^`. El símbolo conserva `#102A43`, su geometría y proporción.
Escritorio ya usaba esos colores. La cabecera, los temas y el splash se conservan.

Figma se utiliza exclusivamente con `cchoquenairat@unsa.edu.pe`, equipo `dev-UNSA`.
El [archivo activo](https://www.figma.com/design/KRV4r4m1p1fSOFZOHOMV0B) contiene
referencias editables de marca y consentimiento por lote, revisadas visualmente.
La [referencia local](../design/figma/unsa-2026-10-05/README.md) conserva IDs,
procedencia y capturas. No es una migración de todas las pantallas históricas.

Los diálogos muestran cantidad, tamaño total y nombres sin elipsis, con
desplazamiento para listas extensas. La confirmación indica el número de archivos.
En escritorio, la casilla de comparación y los botones permanecen en el pie fijo.

## Validación del código y transporte

Comando integrado, con JDK local 21.0.12.1+1 y caché Gradle existente:

```powershell
.\gradlew.bat :protocol:test :pc:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :pc:designPreview -PpreviewScenario=flash-approvals --offline --no-daemon --max-workers=2 --console=plain
```

Resultado: **BUILD SUCCESSFUL**. Protocolo: 53 pruebas aprobadas. Android JVM:
204 aprobadas. PC: 64 aprobadas y 2 optativas omitidas. Total: **321 aprobadas**,
sin errores ni fallos. Lint: 0 errores y 3 advertencias fuera de los archivos de
este cambio (`FileTransfer.kt`, `P2pRouteTheme.kt`, `TransferForegroundService.kt`).

Las regresiones incluyen aprobación bilateral única, rechazo sin escritura,
cancelación tras el primer acuse, reintento con nuevo código, mutación de archivos,
manifiestos inválidos/truncados/desbordados, orden incorrecto y receptor antiguo.
La revisión independiente detectó y ayudó a corregir carreras de callbacks y
acuse de recibo tardío en Android; no quedaron hallazgos bloqueantes conocidos.

Se ejecutó además la prueba optativa Windows ↔ Android API 36 con sockets TCP
reales reenviados por ADB. **PASS** en JUnit e instrumentación: dos archivos por
dirección, una comparación de código por lote (dos en total), cuatro hashes
idénticos y retirada de los archivos de prueba. No valida Wi-Fi físico ni UDP.

| Archivo sintético | SHA-256 |
| --- | --- |
| windows-1.bin | `cd24d9c6f01d65e9d55600a3de4bf68896840849d03a90130a4eccdba143b3f9` |
| windows-2.bin | `391708c36e1a7d451d78c28d37407d6a3edece2c87415fb4c8f467bd15888e87` |
| android-1.bin | `101d389649bf4182f06492eccc65f6ef9efa2f84070c8708ebef881821a381de` |
| android-2.bin | `80834db5c7c817f5cfad1b0a22df30dd6745dcdec4891923911b0798f4bd3d38` |

Evidencia local en `.local/flash-batch-2026-10-05/`: `integrated-build.log`,
`unit-summary.json`, `socket-results.xml`, `pc-sockets.log`, `android-sockets.log`.
Los APK debug se instalaron únicamente en un emulador temporal sin persistencia.
No se actualizó el PC instalado ni un teléfono físico.

## Validación visual

La primera revisión de ocho renders Compose de escritorio detectó que la
casilla de comparación quedaba bajo el desplazamiento; se trasladó al pie fijo.
La primera captura automática Android quedó tapada por un aviso ADB del entorno.
Se preservó el fallo y se reinició sólo el emulador temporal con la clave ADB
existente; no se aceptaron permisos desde el código de prueba.

El segundo intento llegó visualmente al archivo 128, pero su comprobación de
accesibilidad falló por una actualización pendiente del árbol. Se corrigió el
fixture para refrescar nodos y esperar la animación. La revisión del tercer
intento detectó además que la visibilidad parcial del último nombre bastaba para
su aserción; se exigió agotar el desplazamiento antes de guardar esa captura.
Estas correcciones afectan al fixture visual, no al flujo de transferencia.

La repetición de las ocho capturas de escritorio pasó la revisión visual: casos
individual, envío de tres archivos, recepción de tres y lote de 128, en ventanas
de 1160 × 800 y 800 × 620. Títulos, casilla y acciones permanecen visibles;
los nombres largos se ajustan en varias líneas y las listas extensas se desplazan.
La compilación dirigida posterior del preview PC, APK debug, APK de pruebas y
lint terminó correctamente (`visual-build.log`, 2 min 48 s).

La instrumentación final Android pasó los cuatro escenarios: envío y recepción
de tres y de 128 archivos, con seis capturas y restauración del estado original.
La revisión visual confirma títulos, botones y cuatro grupos del código completos;
el último archivo y su tamaño son legibles al terminar ambas listas de 128.
El cuerpo se desplaza cuando falta espacio. El fixture utiliza la actividad y
el diálogo de producción con estado sintético, sin red ni envío de aprobaciones.
Su compilación final terminó correctamente en 1 min 12 s. Evidencia local:
`android-preview-fixture-final-build.log`, `android-preview-final.log` y
`android-preview-final/` dentro del directorio local indicado anteriormente.

Las [14 capturas verificadas](../design/figma/unsa-2026-10-05/verified-ui/README.md)
se conservan con un manifiesto SHA-256. Estos resultados no equivalen a una
auditoría integral de accesibilidad ni a validación de una candidata distribuible
firmada. El emulador temporal utilizado para esta revisión se cerró al terminar.

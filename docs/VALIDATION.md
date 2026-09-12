# Validación de Qetara

## Revisión de GUI en teléfono físico — 12 de septiembre de 2026

El [informe físico](MOBILE_PHYSICAL_REVIEW-2026-09-12.md) registra el recorrido de la GUI de `642503c` y los ajustes posteriores de biblioteca, búsqueda con teclado y controles de tamaño de texto. La actualización conservó los cinco archivos existentes. Se comprobaron los temas claro y oscuro, además de Descargas con texto del sistema al 200 %; se restauraron los ajustes originales al terminar.

La compilación release, `lintRelease` y `testDebugUnitTest` terminaron correctamente: **199 pruebas Android aprobadas**, cero errores de lint y dos advertencias preexistentes. La [galería](../design/figma/mobile-physical-review/README.md) identifica el APK instalado y los hashes de sus fuentes. Este pase no añade pruebas de transferencia ni constituye una nueva candidata de publicación.

## Integración de la GUI para main — 11 de septiembre de 2026

Árbol de trabajo de `codex/prepare-public-release`, basado en `12b5f55c28f2444f43134ff5a104f67b51c24541`, versión 1.4.0/código 7. Incluye el diseño Android y escritorio, Inter local y el aleph de cabecera sin fondo en ambos temas.

`scripts/verify.ps1 -Offline` terminó correctamente con JDK 21 y SDK 36: compilación Android debug y clases PC, lint Android y suites JVM. Los informes contienen **301 pruebas aprobadas** (199 Android, 57 PC y 45 de protocolo), cero fallos y dos escenarios PC opt-in omitidos: sockets con emulador y UDP con teléfono. Las suites Android y PC se ejecutaron en este pase; Gradle reutilizó los resultados vigentes de protocolo y las tareas de compilación y lint sin cambios. La autoprueba CLI aprobó envío completo y reanudación, con comprobación de hash en ambos casos.

`:app:assembleDebugAndroidTest --offline --no-daemon --max-workers=2 --console=plain` también terminó correctamente. Se compiló el runner y sus escenarios; no se conectó un teléfono ni se ejecutó instrumentación en esta integración.

`:pc:createDistributable` terminó correctamente con `-PqetaraDesktopOnly=true --offline --no-daemon --max-workers=2 --console=plain`. Se comprobó que el JAR empaqueta los cuatro TTF Inter, sus dos archivos OFL, ambos inventarios de avisos y el SVG de marca con bytes idénticos a los recursos fuente. El inventario embebido de terceros incluye ahora Inter y coincide con el documento raíz, normalizando los finales de línea.

La evidencia visual está en los informes de [escritorio](DESKTOP_DESIGN_REVIEW-2026-09-11.md), [móvil](MOBILE_DESIGN_REVIEW-2026-09-11.md), [color](MOBILE_COLOR_REVIEW-2026-09-11.md) y [marca final](../design/figma/mobile-brand-adaptive/README.md). Cada galería conserva los hashes y el alcance de su iteración. Este pase no genera una nueva release, MSI ni validación oficial F-Droid.

## Candidata 1.4.0 — 11 de septiembre de 2026

La [validación de la candidata](VALIDATION-1.4.0.md) reúne la evidencia del código `588a92f2617815b5744eeb91a1da463c5c685f90`: 301 pruebas automatizadas iniciales, lint sin errores, nueve comprobaciones instrumentadas por dispositivo y lotes Flash TCP en emulador y teléfono físico. Las builds limpias Windows y Linux y la copia de firma produjeron APKs idénticos.

Se añadieron la [instalación MSI y autoprueba del ejecutable instalado](MSI_VALIDATION-1.4.0.md), la [validación local completa de la receta con fdroidserver](FDROID_VALIDATION-1.4.0.md) y el [descubrimiento UDP físico](UDP_VALIDATION-1.4.0.md). UDP aprobó las cuatro comprobaciones en el segundo intento sin cambiar el arnés; el primero falló por timeout de una búsqueda automática adicional y se conserva por separado. No se determinó la causa de ese fallo.

Siguen pendientes el recorrido visual completo de envío y cancelación y el nuevo paquete de entrega, según [Preparación de publicación](PUBLICATION_READINESS.md). Las revisiones de diseño posteriores figuran en la sección de integración anterior. La prueba local con fdroidserver no equivale a la ejecución del servidor oficial, aceptación o publicación en F-Droid. Las secciones siguientes conservan el alcance histórico de cada comprobación anterior.

## Búsqueda inicial de Flash — 11 de septiembre de 2026

PC inicia la búsqueda de receptores inmediatamente después de activar Flash, igual que Android. La búsqueda manual sigue disponible. Si se cancela la activación durante el arranque, no se solicita la búsqueda automática; activar una sesión ya activa tampoco la repite.

Se ejecutó `gradlew.bat -PqetaraDesktopOnly=true :pc:test --offline --no-daemon --max-workers=2 --console=plain` con el entorno preparado del proyecto: compilación correcta y **57 pruebas PC**, sin fallos, errores ni omisiones. Las cuatro regresiones nuevas verifican el descubrimiento inicial, la búsqueda manual posterior, la activación repetida y la cancelación durante el arranque mediante un transporte simulado. No se volvió a probar descubrimiento UDP entre equipos físicos ni se cambió la interfaz.

## Robustez de selección múltiple — 10 de septiembre de 2026

Revisión de `fix/multiple-file-selection` (`00e4a5d`) frente a `main` (`6b2bf61`), con mejoras preparadas en `codex/improve-multiple-file-selection`. La rama de origen añade un commit y modifica 19 archivos; esta revisión refuerza la coordinación de importaciones y lotes descrita en [Cambios](../CHANGELOG.md).

En Windows x64, con el entorno de `Preparar-entorno.ps1` (Temurin 21.0.12.1+1 y Android SDK 36), se ejecutó `scripts/verify.ps1 -Offline`. Compilación PC, APK debug Android, pruebas y lint finalizaron correctamente. Los informes contabilizan **297 pruebas: 199 Android, 53 PC y 45 del protocolo**, sin fallos, errores ni omisiones. Se añadieron 24 regresiones respecto a la rama de origen. Gradle reutilizó los resultados del protocolo sin cambios; volvió a ejecutar las pruebas Android y PC.

Las nuevas pruebas cubren resultados de Flash anteriores al retorno de `send`, callbacks ajenos o tardíos, cancelación frente a una confirmación ya en camino, conservación de pendientes, selección de archivos ausentes, importaciones superpuestas, invalidación de cargas lentas, limpieza al cancelar y deduplicación de URI/rutas Windows. Los escenarios del controlador PC usan transporte simulado para forzar el orden de callbacks; los helpers Android se prueban como lógica JVM.

Lint terminó con cero errores y tres advertencias ya documentadas: `ModifierParameter`, `UsableSpace` y `UseKtx`. La autoprueba CLI completó una transferencia local de 2 MiB y su reanudación desde un parcial; ambas verificaron el hash recibido. El APK de prueba está en `app/build/outputs/apk/debug/app-debug.apk`.

Esta comprobación no incluye recorridos visuales, instalación en un teléfono ni una nueva prueba Flash entre dispositivos físicos. Penpot MCP no estuvo disponible; las mejoras se limitaron a coordinación, selección y lógica de envío. Las evidencias de entrega y recorridos gráficos que siguen corresponden a las validaciones anteriores.

## Entrega 1.3.0 y validación anterior de selección múltiple

Entrega local para Android y Windows x64, verificada el 7 de septiembre de 2026. Los resultados anteriores se conservan en [1.2.0](VALIDATION-1.2.0.md) y [1.1.0](VALIDATION-1.1.0.md).

## Compilación y pruebas automatizadas

La verificación integrada posterior al cambio de selección múltiple completó 273 pruebas: 182 Android, 46 PC y 45 del protocolo compartido, sin fallos, errores ni pruebas omitidas. Incluye regresiones que comprueban la acumulación de selecciones sucesivas, el orden y la eliminación de duplicados en Android y PC. Android lint terminó sin errores; quedaron cinco advertencias (`GradleDependency` para dos actualizaciones disponibles, `ModifierParameter`, `UsableSpace` y `UseKtx`). Se generó el APK debug durante esta comprobación; los artefactos release, JAR Windows, aplicación portable con runtime e instalador MSI corresponden a la validación de la entrega descrita abajo.

Flash se comprueba con sockets reales en las pruebas del protocolo: aprobación de ambos extremos antes del contenido, código nuevo por archivo, rechazo, caducidad, cancelación, reinicio sin aprobación heredada, archivo vacío, colisiones de nombres, alteración del hash, corte durante el contenido, consulta TCP local, descubrimiento UDP con nonce, direcciones bloqueadas y tramas inválidas. También se prueba el orden publicación/recibo/desactivación y la liberación de puertos si falla la activación UDP. Estas pruebas no constituyen una auditoría criptográfica independiente.

## Interfaz Android

Se utilizó un emulador dedicado Android 16; solo archivos sintéticos. Se comprobó Flash apagado al entrar, activación explícita de su servicio `connectedDevice`, cuenta atrás, conservación durante la rotación y desactivación real sin servicios Qetara restantes. La sesión habitual permaneció cerrada. En 360 dp con fuente 1,3 fueron alcanzables la selección de archivo y la acción inferior. La revisión detectó iconos de estado con poco contraste; se corrigieron y se verificó el APK final con iconos oscuros sobre fondo claro. Los ajustes de pantalla del emulador se restauraron.

## Windows e interoperabilidad

La aplicación nativa Windows se abrió con la recepción habitual desactivada. Se comprobó la entrada opcional Flash, selección de carpeta antes de activar, selección de archivo, búsqueda por dirección local, elección explícita del receptor y comparación del código completo. Volver a la pantalla habitual mantuvo visible el estado Flash activo sin abrir la recepción habitual.

Una imagen PNG de 9.066 bytes se envió desde Windows al emulador después de comparar la verificación en ambos dispositivos. Android publicó una copia con nombre distinto sin sobrescribir imágenes previas. SHA-256: `ef566157414e5cb96480687e35b51c9c238c9529ab0494ff3515172785855e23`. Windows mostró la recepción confirmada. Antes, una solicitud con aprobación en un solo extremo caducó sin publicar el archivo; el reintento mostró un código nuevo.

Un TXT de 1.048.576 bytes llegó de Android a Windows con SHA-256 `4378f0a4062a69a1fe276559a26170ae9c6c31977759f87f00c946ce1ae7cb90`. La solicitud se mostró incluso en la vista habitual del PC. Android confirmó la entrega y limpió su selección. En PC, **Ver en carpeta** abrió el Explorador en la carpeta de recepción; en Android, **Abrir** mostró el PNG exportado en Photos. Una propuesta de 32 MiB se rechazó desde Android sin publicar archivo ni parcial en PC. Al finalizar, Android quedó apagado y el receptor TCP de PC dejó de escuchar al caducar la activación.

El último ajuste Windows sustituyó únicamente una frase de búsqueda que podía parecer una operación todavía en curso. Las 44 pruebas PC y sus artefactos nativos se recompilaron correctamente después del cambio. Las transferencias gráficas corresponden al binario anterior a esa frase; no se atribuye una segunda prueba gráfica completa al binario con el texto corregido. La cancelación durante el contenido se cubrió con sockets en el motor compartido; no se afirma una prueba gráfica de ese caso. En Android, el primer intento caducado mostró un error de conexión genérico, sin falso éxito ni archivo publicado.

## Correspondencia y licencias

Los nueve archivos de avisos y licencias incorporados a cada APK y al JAR de la aplicación PC se compararon byte por byte con sus fuentes. Las dependencias observadas (91 coordenadas Android y 33 Windows) están cubiertas por el inventario de 119 coordenadas distintas. La entrega conserva los textos completos de las licencias y acompaña el runtime Temurin 21.0.12.1+1 con sus fuentes correspondientes y scripts de construcción. El paquete incluye SHA-256, código fuente y un bundle Git.

Ambos APK verifican la firma v2. El certificado release tiene SHA-256 `5f5cdbfb03c1f62f7dc42aee12a5ea4a347b767ccfc268eb450ca69c98ba7a3b`; el debug, `0cffa446e917e8dd321b78e8f7eaca34b2113f2aead7c0da1e525b354556ca95`. Se entregan por separado porque Android exige el mismo certificado para actualizar una instalación existente conservando sus datos.

## Alcance y límites

El teléfono físico permanece pausado a petición del usuario y no se instaló esta actualización en él. La interoperabilidad usa el enlace local del emulador (reenvío ADB y pasarela del emulador); no demuestra descubrimiento broadcast entre equipos físicos a través de un router. El aviso de firewall de Windows se dejó al usuario; la automatización no cambió permisos de firewall. No se ensayaron nuevos escenarios físicos de Wi-Fi Direct, macOS o Linux, ni se publicó en una tienda.

Flash permite poner varios archivos en cola, pero comparte un archivo por operación y no reanuda ni reintenta automáticamente. Cada archivo requiere su propia comparación y aprobación. Un cierre abrupto puede dejar temporales privados o una reserva vacía; repetir una operación ya completada puede producir otra copia. La identidad pública de descubrimiento necesita comparación de la verificación en ambos extremos. Consulta [Flash](FLASH.md) y [Privacidad](PRIVACY.md).

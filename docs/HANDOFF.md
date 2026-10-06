# Traspaso de Qetara

Esta guía permite continuar el desarrollo sin depender de la conversación que originó la entrega. Los comandos se ejecutan desde la raíz del repositorio.

## Versión actual

Las fuentes de **1.4.2, código Android 9**, están publicadas en la etiqueta [`v1.4.2`](https://github.com/IntelogNatanael/Qetara/tree/v1.4.2), commit `c960afafbef5ad463e22127e979408e7a3c4d3af`. La [validación del 6 de octubre](VALIDATION-1.4.2.md) documenta las compilaciones limpias, escáneres, licencias y reproducibilidad nuevas: Windows, Linux y el ensayo local F-Droid produjeron el mismo APK; 337 pruebas pasaron y dos pruebas opcionales con dispositivos quedaron omitidas. La receta fija ese commit y versión. El [APK firmado limpio ya está publicado](https://github.com/IntelogNatanael/Qetara/releases/tag/v1.4.2). La solicitud a F-Droid se presentó el 6 de octubre de 2026 como [MR !51433](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51433); la revisión e inclusión oficiales siguen pendientes.

La build local anterior de **1.4.2/9** se instaló en Windows y en el teléfono físico durante la [revisión de UX del 5 de octubre](UX_REVIEW-2026-10-05.md); no se atribuyen esas pruebas físicas al nuevo APK limpio. Incluye detección de la zona Wi-Fi hospedada en Android 16 y un mensaje específico para errores de búsqueda Flash. Conserva la aprobación de todo el lote una vez por equipo y el icono marfil de la [fase 1.4.1](FLASH_BATCH_REVIEW-2026-10-05.md). Se preservaron la identidad y las preferencias de PC y la firma Android. El identificador sigue siendo `io.github.intelognatanael.qetara`; la decisión de identidad y migración está en [Identidad Android](ANDROID_IDENTITY.md). La versión se define en `gradle.properties` para todos los módulos.

La publicación de fuentes y del APK y la presentación de la solicitud ya están completadas. La MR está abierta, sin borrador ni conflictos; sólo añade el YAML validado desde `carlos5alentino/fdroiddata:codex/qetara-1.4.2`. La [pipeline de la MR](https://gitlab.com/carlos5alentino/fdroiddata/-/pipelines/2918632569) figura como `failed` porque GitLab exige verificación adicional de identidad antes de ejecutar CI. La descripción ya solicita a los mantenedores ejecutarla en el proyecto principal. No hay una build ni aceptación oficial de F-Droid acreditadas. Continúa en esa MR y consulta el [registro de envío](../fdroid/SUBMISSION-1.4.2.md) y [VALIDATION-1.4.2](VALIDATION-1.4.2.md) antes de actualizar su estado; no hay que abrir otra solicitud ni modificar la etiqueta o el APK publicado para resolver el bloqueo de CI.

La candidata histórica 1.4.0 comprobada procede de `588a92f2617815b5744eeb91a1da463c5c685f90`. Ya se validaron el APK final en emulador y teléfono, lotes Flash por Wi-Fi, la instalación MSI y la receta F-Droid con herramientas locales. UDP físico aprobó la repetición sin cambios del arnés; se conserva el timeout del primer intento. El [informe 1.4.0](VALIDATION-1.4.0.md) enlaza hashes y resultados completos. Después se revisaron la [interfaz de escritorio](DESKTOP_DESIGN_REVIEW-2026-09-11.md), la [interfaz móvil](MOBILE_DESIGN_REVIEW-2026-09-11.md), sus [colores](MOBILE_COLOR_REVIEW-2026-09-11.md) y la [marca adaptable](../design/figma/mobile-brand-adaptive/README.md). Estos cambios de diseño son posteriores a los binarios de la candidata. Siguen pendientes el recorrido visual completo de envío y cancelación y el nuevo paquete de entrega; no se ha publicado ni obtenido aceptación oficial de F-Droid.

## Entrega anterior de referencia

- **Android 1.3.1, código 6**, identificador `com.example.wifidrop`, Android 7/API 24 como mínimo y SDK objetivo 36.
- **PC 1.3.0 es compatible con Android 1.3.1.** La corrección 1.3.1 afecta a la apertura, exportación y copia de archivos Android; no cambia el protocolo de red.
- El APK 1.3.1 corresponde al commit `97e62099974d21659085f7e752ac49dc193acf01`. La referencia de distribución es [v1.3.1](https://github.com/IntelogNatanael/Qetara/releases/tag/v1.3.1); `main` incluye desarrollo posterior a esos binarios.
- El repositorio [IntelogNatanael/Qetara](https://github.com/IntelogNatanael/Qetara) es público desde el 6 de octubre de 2026. El código conserva su [licencia MIT](../LICENSE).
- Los instaladores anteriores mantienen sus versiones originales; no contienen el desarrollo posterior descrito a continuación.

Lee primero [CONTRIBUTING](../CONTRIBUTING.md), [arquitectura](../ARCHITECTURE.md), [principios de experiencia](EXPERIENCE.md) y [seguridad](../SECURITY.md). Trabaja en una rama propia y conserva los cambios del usuario. Antes de modificar interfaz, revisa [AGENTS.md](../AGENTS.md): usar exclusivamente Figma con `cchoquenairat@unsa.edu.pe`, equipo `dev-UNSA`. La [referencia activa](../design/figma/unsa-2026-10-05/README.md) ya contiene marca y confirmación de lotes editables y revisadas; el archivo antiguo vacío y sus límites pertenecen a las revisiones históricas.

## Desarrollo posterior a la entrega

`main` incorpora selección múltiple y envío por lotes en PC y Flash, con acumulación de selecciones Android, deduplicación y conservación de los archivos no confirmados. Flash detiene los pendientes al cancelar y espera el resultado de cada archivo antes de avanzar. Android y PC buscan receptores automáticamente al activar Flash; la búsqueda manual sigue disponible.

La validación histórica de estas mejoras ejecutó 199 pruebas Android, 57 PC y 45 del protocolo, además de compilación, lint y autopruebas locales. Sus comandos, fechas y límites están en [Validación](VALIDATION.md); no son los resultados de la versión actual. Los resultados nuevos están en [VALIDATION-1.4.2](VALIDATION-1.4.2.md). Los instaladores de la entrega anterior no incorporan el desarrollo posterior descrito en [Cambios](../CHANGELOG.md).

## Preparar y comprobar el entorno

Usa **JDK 21** para reproducir el entorno verificado; las fuentes tienen objetivo JVM 17. El wrapper incluido fija Gradle 9.1.0 y su SHA-256. Para Android instala SDK Platform 36 y Build Tools 36.0.0, como hace [CI](../.github/workflows/qetara-ci.yml). Configura `JAVA_HOME` y `ANDROID_HOME` en tu entorno. Como alternativa para el SDK, copia [local.properties.example](../local.properties.example) a `local.properties` y establece `sdk.dir`.

La primera compilación necesita descargar dependencias. Usa `--offline` o `-Offline` sólo cuando la caché esté completa. No subas `local.properties`, claves, archivos recibidos ni directorios `build/`.

En Windows, la comprobación de entrada es:

```powershell
.\scripts\verify.ps1
```

El script ejecuta pruebas de `protocol` y `pc`, compila PC, compila y comprueba Android y termina con una transferencia local de autoprueba de la CLI. Para trabajar sólo en PC, sin instalar el SDK Android:

```powershell
.\scripts\verify.ps1 -DesktopOnly
.\gradlew.bat -PqetaraDesktopOnly=true :pc:run
```

En macOS o Linux, las tareas equivalentes de compilación y pruebas son:

```sh
./gradlew :protocol:test :pc:test :pc:classes :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --max-workers=2
./gradlew -PqetaraDesktopOnly=true :pc:run --args="--no-gui --token ABCD1234 --pin 123456 --self-test --no-receiver --no-interactive --out build/qetara-selftest"
```

Los valores fijos del segundo comando son exclusivamente datos sintéticos de la autoprueba. Para una comprobación sólo de PC, añade `-PqetaraDesktopOnly=true` y omite todas las tareas `:app:*`.

## Continuar en Android

| Área | Punto de entrada |
| --- | --- |
| Pantalla, estado y restauración | [P2pScreenRoute.kt](../app/src/main/java/com/example/wifidrop/P2pScreenRoute.kt), [P2pScreenRoutePresenters.kt](../app/src/main/java/com/example/wifidrop/P2pScreenRoutePresenters.kt), `presentation/` |
| Contrato y sesión habitual | [backend/P2pBackend.kt](../app/src/main/java/com/example/wifidrop/backend/P2pBackend.kt), [TransferForegroundService.kt](../app/src/main/java/com/example/wifidrop/TransferForegroundService.kt), [FileTransfer.kt](../app/src/main/java/com/example/wifidrop/FileTransfer.kt) |
| Flash Android | [FlashActivity.kt](../app/src/main/java/com/example/wifidrop/FlashActivity.kt), [FlashForegroundService.kt](../app/src/main/java/com/example/wifidrop/FlashForegroundService.kt), [FlashAndroidState.kt](../app/src/main/java/com/example/wifidrop/FlashAndroidState.kt) |
| Corrección 1.3.1 | [ExternalOpenUtils.kt](../app/src/main/java/com/example/wifidrop/ExternalOpenUtils.kt), [ReceivedFileMimeTypes.kt](../app/src/main/java/com/example/wifidrop/ReceivedFileMimeTypes.kt), [DownloadsExport.kt](../app/src/main/java/com/example/wifidrop/DownloadsExport.kt), [CreateReceivedDocument.kt](../app/src/main/java/com/example/wifidrop/CreateReceivedDocument.kt) |

Para el ciclo de desarrollo Android:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`; los informes quedan en `app/build/reports/`. Para comprobar ambas variantes antes de distribuir, añade `:app:lintRelease :app:assembleRelease`.

La corrección MIME incluye un runner de instrumentación que usa proveedores reales, intercepta los Intents sin abrir aplicaciones y elimina sólo los archivos que él crea. Ejecútalo en un emulador de pruebas:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
adb devices -l
$serial = 'emulator-5554' # Sustituye por el emulador de pruebas observado.
adb -s $serial install -r app\build\outputs\apk\debug\app-debug.apk
adb -s $serial install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s $serial shell am instrument -w -r io.github.intelognatanael.qetara.test/com.example.wifidrop.ReceivedFileOpenInstrumentation
```

La aceptación requiere `result=PASS`, `checks_passed=9` y `created_fixtures_removed=true`. Además de los tests, comprueba visualmente las pantallas que modifiques. No sustituyas automáticamente una instalación del usuario por una de otra firma ni desinstales sus datos para resolver un conflicto.

## Continuar en PC y en el protocolo

La entrada PC es [Main.kt](../pc/src/main/kotlin/com/example/wifidrop/pc/Main.kt), con CLI y motor habitual. [DesktopWorkspace.kt](../pc/src/main/kotlin/com/example/wifidrop/pc/DesktopWorkspace.kt) organiza la interfaz. [DesktopFlashController.kt](../pc/src/main/kotlin/com/example/wifidrop/pc/DesktopFlashController.kt) adapta Flash. Las tareas `:pc:test`, `:pc:run` y `:pc:createDistributable` están configuradas en [pc/build.gradle.kts](../pc/build.gradle.kts).

El módulo `protocol` contiene reglas comunes, almacenamiento y el [motor Flash](../protocol/FLASH.md). Conserva la separación entre la sesión habitual WDRP v4 y Flash: Flash usa TCP/UDP 8989, empieza apagado y exige comparar y aceptar la verificación una vez en cada equipo para todo el lote, de hasta 128 archivos. Esa aprobación no cubre lotes posteriores. Una sesión cerrada, un error o un anuncio de descubrimiento no deben convertirse en autorización para enviar.

Después de cambiar lógica compartida, ejecuta `:protocol:test`, `:pc:test` y `:app:testDebugUnitTest`. No presentes progreso o entrega como exitosos antes de la confirmación correspondiente. Los archivos ya publicados deben conservarse si después falla el acuse o se cierra la sesión.

## Firma y distribución

La clave de distribución existente permite actualizar las instalaciones firmadas con ella. El acceso al almacén y sus contraseñas debe coordinarse por un canal privado; **no están ni deben estar en GitHub**. El certificado debug de otra máquina tampoco garantiza compatibilidad con una instalación debug anterior.

El build Android lee estas cuatro variables; configura todas mediante el entorno o el gestor de secretos del sistema de publicación:

| Variable | Contenido |
| --- | --- |
| `QETARA_SIGNING_STORE` | Ruta local al almacén PKCS12/JKS |
| `QETARA_SIGNING_STORE_PASSWORD` | Contraseña del almacén |
| `QETARA_SIGNING_ALIAS` | Alias de la clave existente |
| `QETARA_SIGNING_KEY_PASSWORD` | Contraseña de esa clave |

Con las cuatro variables disponibles:

```powershell
.\gradlew.bat :app:assembleRelease
```

Sin ninguna, el APK release queda **sin firmar**. Una configuración parcial se rechaza. Comprueba el resultado con `apksigner verify --verbose --print-certs` antes de distribuir; compara el certificado con el de la instalación que se actualizará. No generes otra clave para una actualización normal.

Para distribuir Windows con Java incluido, [scripts/package.ps1](../scripts/package.ps1) exige el paquete de fuentes y avisos correspondiente al JDK de empaquetado:

```powershell
.\scripts\package.ps1 -Installer -RuntimeSourceDirectory C:\Qetara\Qetara-third-party-source
```

Sustituye esa ruta por el paquete real. También se admite `QETARA_RUNTIME_SOURCE_DIR`. La entrega verificada utilizó Temurin 21.0.12.1+1. El script comprueba procedencia declarada, hashes y correspondencia del runtime; no descarga ese paquete por ti. Genera una carpeta nueva en `build/release/` y no necesita reemplazar entregas previas. Consulta [RELEASING](RELEASING.md) para los paquetes nativos, firma y fuentes de terceros. Los instaladores se construyen en su sistema operativo; la configuración CI no equivale a una ejecución aprobada.

## Qué está probado y qué queda por comprobar

- [Candidata 1.4.0](VALIDATION-1.4.0.md): incluye comprobaciones del APK final en teléfono físico y emulador, transferencias Flash, instalación MSI, descubrimiento UDP físico y receta F-Droid local. El fallo UDP inicial y los límites de cada prueba se conservan en sus informes; el recorrido visual completo de envío y cancelación y el nuevo paquete siguen pendientes.
- [Android 1.3.1](VALIDATION-1.3.1-ANDROID.md): 180 tests unitarios Android y nueve comprobaciones instrumentadas pasaron. PNG abrió en Fotos como `image/png`; PDF abrió en su visor como `application/pdf`. El selector no fija una aplicación. Puede abrir directamente si sólo hay un visor compatible.
- [Android/Windows 1.3.0](VALIDATION.md): 262 tests integrados y transferencias Flash gráficas en ambas direcciones con hashes coincidentes. Las pruebas del motor cubren sockets, aprobaciones, rechazo y cancelación; no son una auditoría criptográfica independiente.
- La QA de 1.3.1 usó un emulador Android 16/API 36. El teléfono físico quedó pausado en esa validación; no se atribuyen a 1.3.1 las pruebas físicas posteriores de 1.4.0. El enlace emulador–Windows no demuestra descubrimiento multicast en un router real. Wi-Fi Direct físico y los recorridos de uso en macOS y Linux requieren su propia validación.
- **Observación pendiente:** el historial de Android con sesión habitual cerrada mostró una lista vacía pese a conservar sus registros. No se alteró durante la corrección MIME; las URI de MediaStore se probaron directamente con instrumentación.
- No hay detección del formato por bytes: sin extensión ni MIME concreto del proveedor, el tipo permanece desconocido. HEIC/HEIF/AVIF se comprobaron como metadatos, no como decodificación de imágenes.
- Flash no reanuda automáticamente tras muerte del proceso. Un cierre abrupto puede dejar temporales privados; un envío completado con acuse perdido puede producir otra copia al repetirse. Lee [FLASH](FLASH.md) y [PRIVACY](PRIVACY.md) antes de cambiar recuperación o consentimiento.

Para cada cambio nuevo, registra commit, versión, plataforma, comandos ejecutados y límites. Conserva la distinción entre tests lógicos, pruebas de sockets y recorridos visuales reales.

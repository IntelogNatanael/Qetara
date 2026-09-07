# Traspaso de Qetara

Esta guía permite continuar el desarrollo sin depender de la conversación que originó la entrega. Los comandos se ejecutan desde la raíz del repositorio.

## Estado de referencia

- **Android 1.3.1, código 6**, identificador `com.example.wifidrop`, Android 7/API 24 como mínimo y SDK objetivo 36.
- **PC 1.3.0 es compatible con Android 1.3.1.** La corrección 1.3.1 afecta a la apertura, exportación y copia de archivos Android; no cambia el protocolo de red.
- El APK 1.3.1 corresponde al commit `97e62099974d21659085f7e752ac49dc193acf01`. La referencia de distribución es [v1.3.1](https://github.com/IntelogNatanael/Qetara/releases/tag/v1.3.1); `main` puede incluir documentación posterior.
- El repositorio [IntelogNatanael/Qetara](https://github.com/IntelogNatanael/Qetara) es privado: el compañero necesita permiso de acceso. El código conserva su [licencia MIT](../LICENSE).
- `build.gradle.kts` define ahora `1.3.1` para todos los módulos. Por tanto, recompilar PC desde este código le asigna ese número; no significa que el instalador PC 1.3.0 ya distribuido haya cambiado.

Lee primero [CONTRIBUTING](../CONTRIBUTING.md), [arquitectura](../ARCHITECTURE.md), [principios de experiencia](EXPERIENCE.md) y [seguridad](../SECURITY.md). Trabaja en una rama propia y conserva los cambios del usuario. Antes de modificar interfaz, revisa [AGENTS.md](../AGENTS.md): exige Penpot MCP. No estuvo disponible durante la entrega documentada; las verificaciones de interfaz realizadas se describen en los informes de validación.

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
adb -s $serial shell am instrument -w -r com.example.wifidrop.test/com.example.wifidrop.ReceivedFileOpenInstrumentation
```

La aceptación requiere `result=PASS`, `checks_passed=9` y `created_fixtures_removed=true`. Además de los tests, comprueba visualmente las pantallas que modifiques. No sustituyas automáticamente una instalación del usuario por una de otra firma ni desinstales sus datos para resolver un conflicto.

## Continuar en PC y en el protocolo

La entrada PC es [Main.kt](../pc/src/main/kotlin/com/example/wifidrop/pc/Main.kt), con CLI y motor habitual. [DesktopWorkspace.kt](../pc/src/main/kotlin/com/example/wifidrop/pc/DesktopWorkspace.kt) organiza la interfaz. [DesktopFlashController.kt](../pc/src/main/kotlin/com/example/wifidrop/pc/DesktopFlashController.kt) adapta Flash. Las tareas `:pc:test`, `:pc:run` y `:pc:createDistributable` están configuradas en [pc/build.gradle.kts](../pc/build.gradle.kts).

El módulo `protocol` contiene reglas comunes, almacenamiento y el [motor Flash](../protocol/FLASH.md). Conserva la separación entre la sesión habitual WDRP v4 y Flash: Flash usa TCP/UDP 8989, empieza apagado y exige comparar y aceptar la verificación en ambos equipos para cada archivo. Una sesión cerrada, un error o un anuncio de descubrimiento no deben convertirse en autorización para enviar.

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

- [Android 1.3.1](VALIDATION-1.3.1-ANDROID.md): 180 tests unitarios Android y nueve comprobaciones instrumentadas pasaron. PNG abrió en Fotos como `image/png`; PDF abrió en su visor como `application/pdf`. El selector no fija una aplicación. Puede abrir directamente si sólo hay un visor compatible.
- [Android/Windows 1.3.0](VALIDATION.md): 262 tests integrados y transferencias Flash gráficas en ambas direcciones con hashes coincidentes. Las pruebas del motor cubren sockets, aprobaciones, rechazo y cancelación; no son una auditoría criptográfica independiente.
- La QA móvil reciente usó un emulador Android 16/API 36. El teléfono físico quedó pausado; no se atribuye a 1.3.1 una prueba física nueva. El enlace emulador–Windows no demuestra descubrimiento multicast en un router real. Wi-Fi Direct físico, macOS y Linux requieren su propia validación.
- **Observación pendiente:** el historial de Android con sesión habitual cerrada mostró una lista vacía pese a conservar sus registros. No se alteró durante la corrección MIME; las URI de MediaStore se probaron directamente con instrumentación.
- No hay detección del formato por bytes: sin extensión ni MIME concreto del proveedor, el tipo permanece desconocido. HEIC/HEIF/AVIF se comprobaron como metadatos, no como decodificación de imágenes.
- Flash no reanuda automáticamente tras muerte del proceso. Un cierre abrupto puede dejar temporales privados; un envío completado con acuse perdido puede producir otra copia al repetirse. Lee [FLASH](FLASH.md) y [PRIVACY](PRIVACY.md) antes de cambiar recuperación o consentimiento.

Para cada cambio nuevo, registra commit, versión, plataforma, comandos ejecutados y límites. Conserva la distinción entre tests lógicos, pruebas de sockets y recorridos visuales reales.

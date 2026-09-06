# Preparar una distribución

La versión se define en `build.gradle.kts`. Para Android incrementa también `qetaraVersionCode`: una actualización debe superar el código instalado. Mantén el identificador de aplicación y la misma clave de firma para conservar la posibilidad de actualizar.

## Verificación

Usa JDK 17 o superior (esta entrega se verifica con JDK 21) y Android SDK 36. En Windows ejecuta `scripts/verify.ps1`. Para trabajar solamente en PC usa `scripts/verify.ps1 -DesktopOnly`; el SDK de Android no es necesario en ese modo.

En cualquier sistema puedes ejecutar:

```sh
./gradlew -PqetaraDesktopOnly=true :protocol:test :pc:test :pc:classes
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

La primera ejecución necesita conexión para descargar Gradle y dependencias. `--offline` o `-Offline` requieren una caché completa. La suma SHA-256 del wrapper está fijada en `gradle/wrapper/gradle-wrapper.properties`.

## Firma Android

El proyecto lee cuatro variables de entorno. Deben estar todas configuradas o todas ausentes:

- `QETARA_SIGNING_STORE`: ruta de la clave PKCS12/JKS.
- `QETARA_SIGNING_STORE_PASSWORD`: contraseña del almacén.
- `QETARA_SIGNING_ALIAS`: alias de la clave.
- `QETARA_SIGNING_KEY_PASSWORD`: contraseña de la clave.

No guardes contraseñas en el repositorio, en `gradle.properties` compartido ni en scripts de CI. Configúralas como secretos del entorno de distribución. Sin estas variables, `assembleRelease` genera un APK **sin firmar**; no debe presentarse como instalable. El APK debug es para desarrollo.

Esta entrega local incluye una clave propia en una carpeta privada separada del código. Conserva una copia segura del almacén y de sus datos de recuperación. Quien posea esa clave podrá firmar actualizaciones de Qetara. No los incluyas en un ZIP de fuentes ni los publiques como artefactos.

Una instalación anterior firmada con una clave diferente, incluida una clave debug, no puede actualizarse directamente con la nueva firma de distribución. Conserva sus archivos y trata la migración de forma explícita; nunca automatices la desinstalación de los datos de un usuario.

## Paquetes

En Windows:

```powershell
.\scripts\package.ps1 -Installer
```

El script verifica y construye los APK, JAR, aplicación Windows con Java incluido y ZIP portable. Con `-Installer` incluye también el MSI. Cada ejecución crea una carpeta nueva en `build/release/<fecha-UTC>-<identificador>/` y muestra su ruta al terminar; conserva las entregas anteriores.

Los APK se seleccionan por `output-metadata.json`, y el JAR y MSI por la misma versión. No se incorporan instaladores anteriores ni APK ajenos al metadata actual. La carpeta incluye `LICENSE`, los avisos y `licenses/` cuando están disponibles, y `SHA256SUMS.txt` cubre todos los archivos de esa ejecución con rutas relativas. `-SkipVerification` sirve únicamente cuando ya se ejecutó la verificación del mismo código.

Para crear paquetes nativos de otro sistema, compila en ese sistema:

```sh
./gradlew -PqetaraDesktopOnly=true :pc:createDistributable
./gradlew -PqetaraDesktopOnly=true :pc:packageDmg   # macOS
./gradlew -PqetaraDesktopOnly=true :pc:packageDeb   # Linux
```

Cada aplicación portable debe conservar toda su carpeta, incluidos runtime, recursos y avisos legales. El JAR necesita Java instalado y corresponde al sistema en que se empaquetó.

La firma Android local no equivale a la firma de código o notarización de Windows/macOS. Estos procesos necesitan las credenciales del editor y se realizan separadamente antes de una distribución pública que los requiera.

## Antes de compartir

Comprueba la firma del APK con `apksigner verify --verbose --print-certs`, las sumas SHA-256 y el arranque del paquete extraído. Registra versión, commit, sistema, pruebas realizadas y límites pendientes. Incluye LICENSE, avisos de terceros, las guías y las notas de versión.

El flujo CI incluido configura compilación y pruebas para Android y PC en los tres sistemas. Su presencia no significa que esos trabajos se hayan ejecutado: comprueba sus resultados en el repositorio antes de anunciar soporte verificado. La copia local está licenciada bajo MIT; publicar un repositorio o una release requiere una acción de publicación separada.

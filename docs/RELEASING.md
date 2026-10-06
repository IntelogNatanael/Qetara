# Preparar una distribución

La versión se define una sola vez en `gradle.properties`: `qetaraVersion` para todos los módulos y `qetaraVersionCode` para Android. La candidata en preparación es 1.4.2, código 9. Las actualizaciones posteriores deben incrementar el código y mantener el identificador y la clave de firma.

Los informes de 1.4.0/código 7 y las comprobaciones locales de 1.4.1/código 8 son antecedentes de sus respectivas revisiones. Sus hashes y resultados no acreditan una compilación nueva de 1.4.2. Registra el commit completo de la candidata y vincula cada comprobación y artefacto nuevo a ese commit. Publicar las fuentes, publicar un APK firmado y presentar la receta a F-Droid son acciones separadas.

Desde 1.4.0 el identificador Android es `io.github.intelognatanael.qetara`, basado en la cuenta de GitHub del proyecto. Es una aplicación nueva respecto de `com.example.wifidrop`; puede coexistir con la anterior y no importa sus datos privados. El `namespace` Kotlin conserva `com.example.wifidrop`: identifica las clases internas, no la aplicación instalada. Las autoridades de FileProvider se derivan del identificador de instalación.

## Verificación

Usa JDK 21 y Android SDK 36, con Build Tools 36.0.0 fijado en el proyecto. En Windows ejecuta `scripts/verify.ps1`. Para trabajar solamente en PC usa `scripts/verify.ps1 -DesktopOnly`; el SDK de Android no es necesario en ese modo.

En cualquier sistema puedes ejecutar:

```sh
./gradlew -PqetaraDesktopOnly=true :protocol:test :pc:test :pc:classes
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

La primera ejecución necesita conexión para descargar Gradle y dependencias. `--offline` o `-Offline` requieren una caché completa. La suma SHA-256 del wrapper está fijada en `gradle/wrapper/gradle-wrapper.properties`.

## APK Android desde un commit limpio

Para el APK final destinado a comprobación reproducible o F-Droid, compila desde un clon nuevo del commit elegido. Una carpeta de trabajo reutilizada puede conservar estado incremental de Kotlin: `--no-build-cache` por sí solo no lo elimina. En Windows, con `ANDROID_HOME` apuntando al SDK 36 instalado y las variables `QETARA_SIGNING_*` ausentes, usa:

```powershell
.\scripts\build-android-release.ps1 -Ref HEAD -Offline -MaxWorkers 2
```

El script resuelve `-Ref` a un commit y crea `build/android-release/<fecha-UTC>-<identificador>/source/` mediante un clon local sin hardlinks y checkout separado en ese commit. Solo incluye archivos versionados; no copia `local.properties`, salidas previas ni JSON privados. Comprueba el commit y el árbol limpio antes y después de `:app:assembleRelease --no-build-cache --no-daemon`, y rechaza las variables de firma sin mostrar ni modificar sus valores. Usa el SDK configurado y no instala SDKs. Omite `-Offline` cuando necesites descargar dependencias de las fuentes ya configuradas por el proyecto.

Cada ejecución conserva su clon, `build.log`, un único `Qetara-<versión>-unsigned.apk` seleccionado mediante `output-metadata.json`, y `provenance.json` con commit, versión, identificador Android, argumentos y SHA-256. No elimina ni reemplaza ejecuciones anteriores. Un error deja el registro para revisión y no anuncia un APK listo. El registro de procedencia permite repetir la compilación; la igualdad de bytes se comprueba contra una compilación independiente.

En Linux, prepara JDK 21 y el mismo SDK/Build Tools y usa también una carpeta nueva. Este ejemplo Bash compila el APK sin firma de la variante actual:

```bash
set -euo pipefail
: "${ANDROID_HOME:?Configura el SDK Android ya instalado}"
if compgen -e | grep '^QETARA_SIGNING_' >/dev/null; then
  printf '%s\n' 'Las variables QETARA_SIGNING_* deben estar ausentes.' >&2
  exit 1
fi
qa_repo=$(git rev-parse --show-toplevel)
qa_commit=$(git rev-parse --verify --end-of-options 'HEAD^{commit}')
qa_run=$(mktemp -d "${TMPDIR:-/tmp}/qetara-android-release.XXXXXXXX")
git clone --no-hardlinks --no-checkout -- "$qa_repo" "$qa_run/source"
git -C "$qa_run/source" checkout --detach "$qa_commit"
cd "$qa_run/source"
test "$(git rev-parse HEAD)" = "$qa_commit"
qa_status=$(git status --porcelain=v1 --untracked-files=all)
test -z "$qa_status"
test ! -e local.properties
./gradlew :app:assembleRelease --no-build-cache --no-daemon --max-workers=2 --console=plain 2>&1 | tee "$qa_run/build.log"
test "$(git rev-parse HEAD)" = "$qa_commit"
qa_status=$(git status --porcelain=v1 --untracked-files=all)
test -z "$qa_status"
sha256sum app/build/outputs/apk/release/app-release-unsigned.apk
```

Conserva el commit, el metadata, el registro y el hash de ambos sistemas. Para una misma versión, compara primero los APK sin firma; después comprueba la correspondencia con el firmado mediante `apksigcopier compare --unsigned firmado.apk sin-firma.apk`. Esta operación copia la firma sobre una copia temporal del APK reconstruido y verifica el resultado; no necesita la clave privada. [Documentación de apksigcopier](https://github.com/obfusk/apksigcopier#compare-copy--verify).

## Firma Android

El proyecto lee cuatro variables de entorno. Deben estar todas configuradas o todas ausentes:

- `QETARA_SIGNING_STORE`: ruta de la clave PKCS12/JKS.
- `QETARA_SIGNING_STORE_PASSWORD`: contraseña del almacén.
- `QETARA_SIGNING_ALIAS`: alias de la clave.
- `QETARA_SIGNING_KEY_PASSWORD`: contraseña de la clave.

No guardes contraseñas en el repositorio, en `gradle.properties` compartido ni en scripts de CI. Configúralas como secretos del entorno de distribución. Sin estas variables, `assembleRelease` genera un APK **sin firmar**; no debe presentarse como instalable. El APK debug es para desarrollo.

La clave de distribución se conserva en una carpeta privada fuera del código y de los paquetes de entrega. Conserva una copia segura del almacén y de sus datos de recuperación. Quien posea esa clave podrá firmar actualizaciones de Qetara. No los incluyas en un ZIP de fuentes ni los publiques como artefactos.

Una instalación anterior firmada con una clave diferente, incluida una clave debug, no puede actualizarse directamente con la nueva firma de distribución. Conserva sus archivos y trata la migración de forma explícita; nunca automatices la desinstalación de los datos de un usuario.

Para firmar el APK del clon limpio, configura las cuatro variables después de terminar la compilación. Usa Build Tools 36.0.0, una salida nueva y contraseñas mediante `env:`. Comprueba la alineación antes y después, sin modificar el APK firmado:

```powershell
$buildTools = Join-Path $env:ANDROID_HOME 'build-tools\36.0.0'
$unsigned = '<ruta del Qetara-1.4.2-unsigned.apk del clon limpio>'
$signed = '<ruta nueva para Qetara-1.4.2-signed.apk>'
if (Test-Path -LiteralPath $signed) { throw 'La salida firmada ya existe.' }
& (Join-Path $buildTools 'zipalign.exe') -c -P 16 4 $unsigned
if ($LASTEXITCODE -ne 0) { throw 'El APK sin firma no conserva la alineacion esperada.' }
& (Join-Path $buildTools 'apksigner.bat') sign --ks $env:QETARA_SIGNING_STORE --ks-key-alias $env:QETARA_SIGNING_ALIAS --ks-pass env:QETARA_SIGNING_STORE_PASSWORD --key-pass env:QETARA_SIGNING_KEY_PASSWORD --alignment-preserved true --v4-signing-enabled false --out $signed $unsigned
if ($LASTEXITCODE -ne 0) { throw 'La firma fallo.' }
& (Join-Path $buildTools 'apksigner.bat') verify --verbose --print-certs $signed
if ($LASTEXITCODE -ne 0) { throw 'La firma no verifica.' }
& (Join-Path $buildTools 'zipalign.exe') -c -P 16 4 $signed
if ($LASTEXITCODE -ne 0) { throw 'La alineacion del APK firmado no verifica.' }
Get-FileHash -LiteralPath $signed -Algorithm SHA256
```

Desde Build Tools 35, `apksigner` puede sustituir el relleno de alineación existente; `--alignment-preserved true` lo conserva para facilitar la comparación con apksigcopier. [Explicación y opción recomendada por apksigcopier](https://github.com/obfusk/apksigcopier#what-about-signatures-made-by-apksigner-from-build-tools--3500-rc1). `--v4-signing-enabled false` evita generar el archivo de firma separado `.idsig`; las contraseñas `env:` no se escriben en los argumentos. [Referencia de apksigner](https://developer.android.com/tools/apksigner). `zipalign -c -P 16 4` comprueba la alineación, incluida la de bibliotecas nativas a 16 KiB. [Referencia de zipalign](https://developer.android.com/tools/zipalign).

## Paquetes

En Windows:

```powershell
.\scripts\package.ps1 -Installer -RuntimeSourceDirectory C:\Qetara\Qetara-third-party-source
```

Selecciona en `JAVA_HOME` el JDK con el que vas a empaquetar. Para reproducir esta entrega se utilizó Eclipse Temurin 21.0.12.1+1. `-RuntimeSourceDirectory` identifica el paquete de fuentes de ese runtime; también puedes usar `QETARA_RUNTIME_SOURCE_DIR`. Si ambos están ausentes, el script busca `../Qetara-third-party-source`, junto al repositorio. La copia de código de Qetara se puede compilar y probar sin ese paquete: se exige cuando vas a generar una distribución con Java incluido.

El paquete de fuentes debe conservar `PROVENANCE.json` (incluidos `selected_version`, `source_archive` y `build_scripts_commit`), `SHA256SUMS.txt`, el archivo completo de fuentes de OpenJDK, el snapshot de los scripts Temurin y sus avisos. Antes de compilar, el script comprueba cada archivo declarado, rechaza archivos adicionales o enlaces y verifica que los metadatos coincidan con el archivo de fuentes. No descarga archivos automáticamente. Si cambias el JDK, prepara el paquete correspondiente y actualiza los avisos de la distribución; no reutilices por nombre las fuentes de una versión anterior.

El script verifica y construye los APK, JAR, aplicación Windows con Java incluido y ZIP portable. Con `-Installer` incluye también el MSI. Cada ejecución crea una carpeta nueva en `build/release/<fecha-UTC>-<identificador>/` y muestra su ruta al terminar; conserva las entregas anteriores.

`package.ps1` es el empaquetador general y utiliza la carpeta de trabajo existente. Para el APK Android final destinado a F-Droid o a comparación reproducible, usa `build-android-release.ps1` y el proceso de firma anterior; los APK de `package.ps1` no sustituyen esa compilación desde un clon limpio.

Los APK se seleccionan por `output-metadata.json`, y el JAR y MSI por la misma versión. No se incorporan instaladores anteriores ni APK ajenos al metadata actual. La carpeta incluye `LICENSE`, los avisos, `licenses/` y los fuentes comprobados en `Fuentes/Qetara-third-party-source/`. El script exige que `runtime/release` declare la misma `JAVA_VERSION` numérica que `PROVENANCE.json`, vuelve a comprobar el paquete tras la compilación y verifica su copia final. `SHA256SUMS.txt` cubre todos los archivos de esa ejecución con rutas relativas. Estos controles detectan archivos ausentes, modificados o de otra versión; no sustituyen la verificación de procedencia del proveedor ni prueban una reconstrucción idéntica del runtime. `-SkipVerification` sirve únicamente cuando ya se ejecutó la verificación del mismo código.

Para crear paquetes nativos de otro sistema, compila en ese sistema:

```sh
./gradlew -PqetaraDesktopOnly=true :pc:createDistributable
./gradlew -PqetaraDesktopOnly=true :pc:packageDmg   # macOS
./gradlew -PqetaraDesktopOnly=true :pc:packageDeb   # Linux
```

Cada aplicación portable debe conservar toda su carpeta, incluidos runtime, recursos y avisos legales. Al distribuir el portable o un instalador con Java, acompáñalo del paquete de fuentes correspondiente en el mismo conjunto de entrega; el ZIP portable por sí solo no contiene ese paquete. En macOS y Linux realiza también la comprobación y el acompañamiento de los fuentes del JDK utilizado, porque las tareas Gradle directas no ejecutan esta comprobación de `package.ps1`. El JAR necesita Java instalado y corresponde al sistema en que se empaquetó.

La firma Android local no equivale a la firma de código o notarización de Windows/macOS. Estos procesos necesitan las credenciales del editor y se realizan separadamente antes de una distribución pública que los requiera.

## Antes de compartir

Comprueba la firma del APK con `apksigner verify --verbose --print-certs`, las sumas SHA-256 y el arranque del paquete extraído. Registra versión, commit, sistema, pruebas realizadas y límites pendientes. Incluye LICENSE, avisos de terceros, las guías y las notas de versión.

El flujo CI incluido configura compilación y pruebas para Android y PC en los tres sistemas. Su presencia no significa que esos trabajos se hayan ejecutado: comprueba sus resultados en el repositorio antes de anunciar soporte verificado. La copia local está licenciada bajo MIT; publicar un repositorio o una release requiere una acción de publicación separada.

# Flash: prueba optativa PC y Android

Esta prueba usa el controlador de lotes de escritorio, `FlashOutgoingBatch` de Android y el motor Flash real en ambos procesos. Envía dos archivos por dirección (128 KiB y 512 KiB + 31 bytes), compara los cuatro códigos Noise antes de aprobar y exige igualdad SHA-256 del contenido recibido. Solo crea archivos de prueba en carpetas temporales propias; no abre actividades ni usa descargas existentes.

Por defecto la prueba cruza sockets TCP reales mediante ADB en un emulador. El modo opcional para un teléfono físico envía los archivos directamente por la red local y usa ADB solo para el control. Ambos modos buscan los pares explícitamente por dirección/puerto; no comprueban descubrimiento UDP, el servicio Android, permisos de notificaciones ni interacción visual. En PC se comprueba que activar pide una búsqueda, que activar de nuevo es idempotente y que la acción de volver a buscar sigue disponible. Solo el modo con teléfono físico aporta evidencia de transferencia por la red local real.

## Preparación

Usar un emulador o teléfono de prueba y la aplicación con ID `io.github.intelognatanael.qetara`. La aplicación y su APK de instrumentación deben estar compilados e instalados con firmas compatibles. No instalar sobre la aplicación histórica `com.example.wifidrop`. El runner de instrumentación habitual conserva su escenario anterior si se omite el argumento `scenario`.

Preparar `JAVA_HOME`, `ANDROID_HOME` y `adb` según [HANDOFF: preparar y comprobar el entorno](HANDOFF.md#preparar-y-comprobar-el-entorno). Compilar antes de iniciar el runner, sin otro Gradle simultáneo:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :pc:testClasses
```

Instalar los APK compatibles en el emulador de prueba y configurar en una terminal (cambiar `$qaSerial` por ese emulador):

```powershell
$qaSerial = 'emulator-5580'
adb -s $qaSerial forward --no-rebind tcp:39891 tcp:39891
adb -s $qaSerial forward --no-rebind tcp:39892 tcp:39892
adb -s $qaSerial reverse --no-rebind tcp:39893 tcp:39893
adb -s $qaSerial shell am instrument -w -r -e scenario flash-sockets io.github.intelognatanael.qetara.test/com.example.wifidrop.ReceivedFileOpenInstrumentation
```

El runner espera la conexión del test PC durante dos minutos. Mientras sigue esperando, ejecutar en otra terminal:

```powershell
$env:QETARA_FLASH_EMULATOR_QA = '1'
.\gradlew.bat :pc:test --rerun --tests com.example.wifidrop.pc.FlashEmulatorSocketTest
Remove-Item Env:QETARA_FLASH_EMULATOR_QA
```

El test se omite en ejecuciones ordinarias sin esa variable. Revisar tanto el resultado JUnit PC y sus cuatro hashes en `pc/build/test-results/test/TEST-com.example.wifidrop.pc.FlashEmulatorSocketTest.xml` como `result=PASS`, `files_received=2`, `files_sent=2` y `created_fixtures_removed=true` en la instrumentación. Un fallo o timeout no es una prueba aprobada. Los puertos 39891–39893 deben estar libres y reservarse solo para esta sesión.

Al terminar, retirar únicamente los tres reenvíos creados por esta prueba:

```powershell
adb -s $qaSerial forward --remove tcp:39891
adb -s $qaSerial forward --remove tcp:39892
adb -s $qaSerial reverse --remove tcp:39893
```

## Teléfono físico en la misma Wi-Fi: archivos por LAN, control por USB

Usar las IPv4 locales actuales del teléfono y del PC. En este modo solo se reenvía el puerto de control 39891: no crear el forward 39892 ni el reverse 39893 del escenario de emulador. El motor escucha temporalmente en las interfaces locales: TCP 39892 en Android y TCP 39893 en PC. Comprobar que esos puertos estén libres y accesibles entre ambos equipos; si la red o el firewall bloquean la conexión, registrar ese obstáculo sin desactivar el firewall ni cambiar políticas globales.

Con los APK compatibles ya instalados, iniciar el runner en la primera terminal:

```powershell
$qaSerial = '<serial USB del teléfono>'
$pcHost = '<IPv4 Wi-Fi del PC>'
adb -s $qaSerial forward --no-rebind tcp:39891 tcp:39891
adb -s $qaSerial shell am instrument -w -r -e scenario flash-sockets -e pc-host $pcHost io.github.intelognatanael.qetara.test/com.example.wifidrop.ReceivedFileOpenInstrumentation
```

En la segunda terminal, antes de que expire la espera de dos minutos:

```powershell
$env:QETARA_FLASH_EMULATOR_QA = '1'
$env:QETARA_FLASH_ANDROID_HOST = '<IPv4 Wi-Fi del teléfono>'
.\gradlew.bat :pc:test --rerun --tests com.example.wifidrop.pc.FlashEmulatorSocketTest
Remove-Item Env:QETARA_FLASH_ANDROID_HOST
Remove-Item Env:QETARA_FLASH_EMULATOR_QA
```

La variable de activación conserva su nombre histórico también para el teléfono. Exigir las mismas comprobaciones de hashes y resultados del escenario anterior, además de `TRANSPORT LAN_TCP` en JUnit PC y `LAN TCP payload` en la instrumentación. Al terminar, quitar únicamente el forward de control creado para esta sesión:

```powershell
adb -s $qaSerial forward --remove tcp:39891
```

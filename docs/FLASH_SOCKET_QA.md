# Flash: prueba optativa PC y emulador Android

Esta prueba usa el controlador de lotes de escritorio, `FlashOutgoingBatch` de Android y el motor Flash real en ambos procesos. Envía dos archivos por dirección (128 KiB y 512 KiB + 31 bytes), compara los cuatro códigos Noise antes de aprobar y exige igualdad SHA-256 del contenido recibido. Solo crea archivos de prueba en carpetas temporales propias; no abre actividades ni usa descargas existentes.

La prueba cruza sockets TCP reales mediante ADB. No representa descubrimiento UDP por Wi-Fi, un router físico, el servicio Android, permisos de notificaciones ni interacción visual. En PC comprueba que activar pide una búsqueda, que activar de nuevo es idempotente y que la acción de volver a buscar sigue disponible. Los pares se encuentran con la búsqueda explícita por dirección/puerto porque el emulador aísla broadcast; conserva el puerto reenviado.

## Preparación

Usar exclusivamente un emulador de prueba y la aplicación con ID `io.github.intelognatanael.qetara`. La aplicación y su APK de instrumentación deben estar compilados e instalados con firmas compatibles. No instalar sobre la aplicación histórica `com.example.wifidrop`. El runner de instrumentación habitual conserva su escenario anterior si se omite el argumento `scenario`.

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

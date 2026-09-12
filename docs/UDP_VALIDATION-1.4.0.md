# Flash: validación optativa de descubrimiento UDP por Wi-Fi

Estado: validación física ejecutada el 11 de septiembre de 2026. El segundo intento, sin cambiar el arnés, aprobó las cuatro comprobaciones; el primero falló durante una reactivación automática adicional de PC. Ambos resultados se conservan. Complementa la [prueba de transferencia TCP](FLASH_SOCKET_QA.md), que descubre mediante direcciones explícitas y no acredita UDP.

## Implementación revisada

Flash usa UDP 8989 para buscar equipos y TCP 8989 para transferir. El socket UDP escucha en todas las interfaces, permite broadcast y no comparte el puerto con otra activación. La búsqueda envía una consulta a `255.255.255.255` y a las direcciones de broadcast IPv4 de las interfaces activas que no sean loopback. La respuesta vuelve por UDP a la dirección y puerto de origen.

La consulta lleva un UUID; solo se aceptan anuncios con ese mismo valor durante diez segundos. Las búsquedas se limitan a una por segundo y las respuestas a una cada 200 ms. Los identificadores propios se ignoran. Por eso la prueba coordina fases y espera el cierre de las ventanas anteriores antes de comprobar búsquedas manuales.

Android activa el motor desde `FlashForegroundService`, adquiere su `WifiManager.MulticastLock` y solicita la primera búsqueda. Su acción `discoverPeers()` sin argumento vuelve a solicitar UDP. El manifiesto incluye INTERNET, ACCESS_WIFI_STATE y CHANGE_WIFI_MULTICAST_STATE; los locks se liberan al parar o destruir el servicio. PC solicita la primera búsqueda desde `DesktopFlashController.start()` y repite mediante `discover()`.

La prueba de protocolo existente valida una respuesta UDP y su UUID por loopback. Los tests del controlador cuentan solicitudes de búsqueda con transportes simulados. Este arnés añade la red física y el servicio Android real, sin usar `discoverAt()` ni direcciones explícitas para descubrir.

## Qué verifica el arnés

El control usa un único forward ADB TCP 39894 hacia un socket Android ligado a loopback. Ese canal comunica comandos e identificadores públicos de sesión; no envía paquetes Flash ni suministra direcciones a los motores. El descubrimiento cruza la Wi-Fi mediante los sockets UDP normales de Flash.

1. PC se activa primero. Al activar Android, el servicio debe descubrir automáticamente la sesión PC.
2. PC se reactiva con otro identificador y debe descubrir automáticamente la sesión Android.
3. Se deja caducar la ventana anterior y se reactiva Android. PC debe encontrar el identificador nuevo después de la acción manual de búsqueda; primero se exige que no esté en su lista.
4. Se deja caducar la ventana Android y se reactiva PC. Como preparación adicional, se exige que PC vuelva a descubrir automáticamente Android; después se comprueba que Android todavía no tenga el identificador PC nuevo y que lo encuentre mediante su acción manual.

Se comprueban identificadores de activación reales obtenidos por el canal de control, puerto 8989 y direcciones descubiertas que no sean loopback. El transporte PC rechaza expresamente cualquier llamada a `discoverAt`. La única vía que incorpora estos pares al motor es la respuesta UDP a una consulta vigente. El arnés no captura paquetes ni identifica cuál de las direcciones de broadcast concretas entregó la consulta.

Android recorre la activación del servicio de producción, sin adquirir otro lock desde el arnés. La adquisición y liberación del MulticastLock se verificaron por lectura del servicio; el test no consulta APIs ocultas ni afirma una medición independiente del lock. La prueba exige que el servicio propio vuelva a OFF al terminar. No envía archivos ni acepta solicitudes y no borra descargas existentes.

## Preparación y ventana coordinada

Usar teléfono físico y PC en la misma Wi-Fi, con difusión local permitida. Cerrar Flash en las dos aplicaciones y dejar libre TCP/UDP 8989. No ejecutar en paralelo otra prueba Flash. El puerto de control 39894 también debe estar libre. Si el router o firewall impide UDP, registrar el fallo; no desactivar protecciones ni cambiar políticas globales.

Disponer de la aplicación y una instrumentación con firmas compatibles según [la guía de pruebas](FLASH_SOCKET_QA.md). En esta ejecución se conservó la aplicación release ya instalada y se compiló, firmó e instaló únicamente el APK de instrumentación. Los archivos nuevos pertenecen únicamente a androidTest, tests PC y documentación; no alteran el APK de producción. El runner habitual conserva sus otros escenarios cuando no se usa `scenario=flash-udp`.

Compilación de preparación (terminarla antes de abrir la ventana):

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest :pc:testClasses --offline --no-daemon --max-workers=2 --console=plain
```

Tras instalar la instrumentación compatible, iniciar en la primera terminal:

```powershell
$qaSerial = '<serial USB del teléfono de prueba>'
adb -s $qaSerial forward --no-rebind tcp:39894 tcp:39894
adb -s $qaSerial shell am instrument -w -r -e scenario flash-udp io.github.intelognatanael.qetara.test/com.example.wifidrop.ReceivedFileOpenInstrumentation
```

En otra terminal, antes de los dos minutos de espera del runner:

```powershell
$env:QETARA_FLASH_UDP_QA = '1'
try {
    .\gradlew.bat :pc:test --rerun --tests com.example.wifidrop.pc.FlashLanUdpTest --offline --no-daemon --max-workers=2 --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'La prueba UDP no paso.' }
} finally {
    Remove-Item Env:QETARA_FLASH_UDP_QA -ErrorAction SilentlyContinue
}
```

La variable ausente omite el test durante pruebas ordinarias y CI. El arnés espera dos intervalos de 10,5 segundos para evitar confundir respuestas de una consulta previa con una búsqueda manual nueva; separa además las fases 350 ms para respetar el límite global de respuestas de 200 ms, que también cuenta los broadcasts propios. No acredita activaciones exactamente simultáneas ni requiere ninguna IP, SSID o contraseña en los comandos.

Exigir los cuatro mensajes PASS de UDP y `checks=4`, `explicit_discovery=0` en el resultado JUnit PC, además de `result=PASS`, `service_activations=2`, `manual_discovery_requests=1`, `peer_checks_passed=3` y `owned_service_stopped=true` en Android. Un timeout, una excepción de servicio o un error de socket no equivale a una prueba aprobada.

Al terminar, retirar únicamente el forward propio:

```powershell
adb -s $qaSerial forward --remove tcp:39894
```

## Resultado físico del 11 de septiembre de 2026

Se usaron un teléfono físico y PC Windows en la misma red local. La aplicación Android instalada corresponde a la candidata cuyo código es `588a92f2617815b5744eeb91a1da463c5c685f90`. El arnés se añadió sobre `12b5f55c28f2444f43134ff5a104f67b51c24541`; sus tres archivos Kotlin se identifican mediante SHA-256 en la evidencia local porque todavía no tenían commit propio al ejecutar la prueba. El código de producción PC y protocolo no cambió entre esas dos revisiones.

| Ejecución | JUnit PC | Android | Resultado observado |
| --- | --- | --- | --- |
| Primer intento, 14:26 UTC | 1 test, 1 fallo, 0 errores; 42,987 s | FAIL; servicio propio detenido | Aprobó Android automático, PC automático y PC manual. Timeout de 20 s al exigir otra búsqueda automática PC en la preparación de la fase 4. La búsqueda manual Android de esa fase no llegó a ejecutarse. |
| Segundo intento, 14:30 UTC | 1 test, 0 fallos, 0 errores, 0 omitidos; 24,013 s | PASS; 2 activaciones, 1 búsqueda manual y 3 pares comprobados | Las cuatro comprobaciones aprobaron con el mismo APK de instrumentación y el mismo arnés PC. No se cambiaron esperas ni se añadió búsqueda de rescate. |

El resultado PC del segundo intento contiene `checks=4`, `explicit_discovery=0` y `payload_forwarding=0`. Android confirmó `owned_service_stopped=true` en ambos intentos. Los logs Android/PC y XML de los dos intentos se guardan por separado en la evidencia local ignorada; el resumen local registra también sus hashes. El primer log Android conserva FAIL con texto vacío al cerrarse el canal de control; la causa de terminación se identifica en el XML PC, no en ese texto vacío.

| Artefacto | SHA-256 |
| --- | --- |
| APK release instalado, antes y después | `5d9e28651ffdaf0216d0a82c049b287851906065bbc713eefa77d793670e22f6` |
| APK de instrumentación usado en ambos intentos | `3f423d2bed55292537e308a678f22734849ede26cb13dd94aa9be2611eb8867d` |

La compilación de preparación terminó correctamente con `:app:assembleDebugAndroidTest :pc:testClasses` en modo offline. El APK de producción no se recompiló ni reinstaló. Después del segundo intento, el servicio Flash Android no aparecía en `dumpsys`, el forward propio 39894 estaba retirado y PC no tenía listeners TCP 8989/39894 ni UDP 8989. La aplicación gráfica PC siguió abierta. Android restringió el acceso de `ss` a netlink; no se presenta esa lectura como una comprobación independiente completa de sus sockets. La confirmación Android de cierre procede del estado OFF comprobado por el arnés y de la ausencia del servicio.

## Evidencia y límites

El fallo inicial acredita una intermitencia real en esta ejecución; una repetición aprobada no demuestra fiabilidad en todos los arranques. La búsqueda de producción envía una única ráfaga por solicitud, sin retransmitir durante la ventana de diez segundos. El límite global de respuestas de 200 ms también incluye consultas propias. Son propiedades comprobadas en el código, pero no se capturaron paquetes ni se determinó por qué faltó la respuesta durante el primer intento. No se atribuye el fallo al router, a una interfaz concreta ni a ese límite sin evidencia adicional.

Registrar commit de aplicación, commit del arnés, hashes de los APK instalados, plataforma, resultado JUnit, resultado Android y retirada del forward. Al preparar informes públicos, omitir seriales reales, direcciones privadas y SSID. No sustituye un recorrido visual, una transferencia posterior ni pruebas en otros routers, interfaces o versiones Android. La prueba TCP previa y esta prueba UDP deben conservar resultados separados.

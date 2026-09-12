# Validación de la candidata Qetara 1.4.0

Preparación del 11 de septiembre de 2026. Android: versión 1.4.0, código 7, identificador `io.github.intelognatanael.qetara`. PC comparte la versión 1.4.0. Las entregas anteriores se conservan.

## Verificación automatizada inicial

`scripts/verify.ps1 -Offline` terminó correctamente con Temurin 21.0.12.1+1, Gradle 9.1.0 y Android SDK 36. Se ejecutaron o reutilizaron según los inputs de Gradle 199 pruebas Android, 57 PC y 45 del protocolo: **301 pruebas, cero fallos, errores u omisiones**. Los casos de selección y lotes añadidos previamente se detallan en [Validación](VALIDATION.md).

La compilación debug contiene el nuevo identificador, versión y código comprobados en `output-metadata.json`. Lint debug y la ejecución separada de lint release terminaron sin errores; conservan las tres advertencias existentes `ModifierParameter`, `UsableSpace` y `UseKtx`.

La autoprueba CLI completó una transferencia local de 2 MiB y otra reanudada desde un parcial, verificando el hash. Estos dos casos usan loopback. La prueba física adicional se describe a continuación.

## Artefactos y reconstrucción

El código de la candidata corresponde al commit `588a92f2617815b5744eeb91a1da463c5c685f90`. Los ajustes posteriores del arnés de prueba para Wi-Fi física, la herramienta de compilación limpia y este informe son material de validación; no cambian el código de aplicación incluido en los binarios.

Windows y Ubuntu compilaron ese commit desde clones limpios y obtuvieron el mismo APK sin firma. El APK final se firmó con Build Tools 36.0.0 y `--alignment-preserved true`. Linux reprodujo exactamente el APK firmado copiando solo su firma pública mediante apksigcopier 1.1.1. La verificación de firma v2/v3 y la alineación ZIP de 16 KiB pasaron. Se conservaron el perfil ART, las bibliotecas nativas y la metadata VCS. Los entornos, comandos y límites están en [Reproducibilidad Android](REPRODUCIBILITY-1.4.0.md).

El nuevo `scripts/build-android-release.ps1` se ejecutó después con `-Ref 588a92f2617815b5744eeb91a1da463c5c685f90 -Offline`: creó otro clon limpio, ejecutó 51 tareas en 3 min 8 s y produjo nuevamente el SHA-256 sin firma indicado abajo. Su `provenance.json` verificó commit, identificador, versión, ausencia de variables de firma y árbol limpio antes y después.

| Artefacto | SHA-256 |
| --- | --- |
| APK final firmado, 14.020.330 bytes | `5d9e28651ffdaf0216d0a82c049b287851906065bbc713eefa77d793670e22f6` |
| APK sin firma, Windows y Linux | `9e1744b2820b1931af675f33c01ec803c2ce0604f5b3373be69cd742194ee9bf` |
| MSI instalado, 55.565.118 bytes | `05245a66781441811e22ec2572508f194492e90a31f8e7230e483d9d99ee5fe5` |

El empaquetado Windows generó MSI, ZIP portable y JAR. El ZIP se extrajo y se ejecutó su propio `Qetara.exe` con el runtime incluido: la autoprueba CLI de transferencia de 2 MiB y reanudación terminó con código cero. Después se instaló el MSI por usuario: código 0, sin reinicio, producto registrado como 1.4.0 y 190 archivos instalados. El ejecutable instalado también aprobó su autoprueba de transferencia y reanudación; los hashes del ejecutable, JAR principal y 149 archivos del runtime coinciden con el portable. Las diferencias de empaquetado, los avisos y el alcance están en [Validación MSI](MSI_VALIDATION-1.4.0.md). No se probaron actualización o desinstalación. Las fuentes correspondientes a Java acompañan la entrega; el MSI no tiene firma Authenticode.

La [validación local de F-Droid](FDROID_VALIDATION-1.4.0.md) ejecutó después fdroidserver 2.4.5 en Ubuntu WSL aislado: `readmeta`, formato, lint, escáner de fuentes, build y escáner del APK terminaron correctamente. Compiló exclusivamente el commit fijado, mediante un origen Git local y herramientas configuradas para este ensayo. El APK sin firma obtuvo nuevamente el hash indicado arriba; apksigcopier 1.1.1 reprodujo el APK firmado exacto y la verificación de firma pasó. La receta conserva el origen HTTPS previsto, todavía privado; faltan las URLs públicas necesarias para la inclusión. No se ejecutó el build-server o CI oficial de F-Droid.

## Comprobaciones sobre el APK final

El APK firmado indicado arriba se instaló tanto en un emulador Pixel 8 como en un teléfono CPH2743; ambos ejecutan Android 16 / API 36. Se extrajo el APK instalado en cada uno y su SHA-256 coincidió con la candidata. El arranque de `MainActivity` terminó con estado correcto y proceso activo en ambos. La aplicación histórica `com.example.wifidrop` del teléfono se conservó. Al terminar se retiró únicamente el paquete de instrumentación del teléfono y se cerró el emulador temporal.

En cada dispositivo pasaron las nueve comprobaciones instrumentadas de apertura y manejo de archivos: MIME de imagen y documento, conversión de URI heredada, límites de FileProvider, permisos de lectura al compartir y contratos de guardar copia. Los resultados declararon `result=PASS`, `checks_passed=9` y eliminación de los archivos temporales. Son comprobaciones de contratos Android; no certifican el comportamiento visual de cada visor externo.

La [prueba optativa Flash](FLASH_SOCKET_QA.md) pasó también en ambos dispositivos. En cada ejecución se enviaron **dos archivos por dirección**, de 128 KiB y 512 KiB + 31 bytes, con el motor Flash real y los controladores de lotes Android y PC. Se compararon los códigos de verificación Noise antes de aprobar cada operación y los cuatro hashes SHA-256 después de recibir los archivos. Las operaciones y solicitudes fueron distintas; no se exige que los códigos cortos sean únicos.

En el teléfono, los datos viajaron directamente por TCP a través de la Wi-Fi y el router compartidos con el PC; USB/ADB se utilizó únicamente para coordinar la prueba. En el emulador, los sockets atravesaron reenvíos ADB. Ambos lados terminaron correctamente y eliminaron sus archivos de prueba; los reenvíos temporales se retiraron.

La prueba de PC comprueba que activar Flash solicita búsqueda, que repetir la activación es idempotente y que se puede buscar otra vez. El descubrimiento de pares del arnés usa direcciones explícitas: **no valida descubrimiento UDP**, el servicio Android ni el recorrido visual de selección de archivos. La cancelación y otros estados conservan la cobertura automatizada previa; no se les atribuye aquí una nueva prueba manual física.

## Descubrimiento UDP físico

Un [arnés separado](UDP_VALIDATION-1.4.0.md) comprobó después la búsqueda inicial y manual mediante UDP real entre PC y el teléfono en la misma Wi-Fi, con el servicio Android de producción. No proporcionó direcciones explícitas a los motores y no envió archivos. La aplicación instalada mantuvo el SHA-256 firmado indicado arriba antes y después.

El primer intento aprobó tres comprobaciones y falló por timeout de 20 segundos en una búsqueda automática PC adicional, antes de ejecutar la fase manual Android. El segundo aprobó las cuatro comprobaciones con el mismo APK de instrumentación y arnés PC, sin cambiar esperas ni añadir consultas de rescate: JUnit sin fallos, `checks=4`, `explicit_discovery=0` y Android `result=PASS`. Ambos detuvieron el servicio propio; al terminar se retiró el forward de control.

Se conservan ambos resultados. No se capturaron paquetes ni se estableció la causa del timeout inicial; el segundo intento aprobado no demuestra fiabilidad en todos los arranques o routers. Esta prueba amplía la evidencia de descubrimiento y servicio Android, sin sustituir los lotes TCP anteriores ni el recorrido visual pendiente.

## Integración continua y alcance

La ejecución [34602646699](https://github.com/IntelogNatanael/Qetara/actions/runs/34602646699), vinculada a `588a92f`, terminó correctamente en sus cuatro trabajos: Android, Windows, macOS y Linux. Incluye compilaciones y pruebas; la subida rutinaria de artefactos se desactivó para evitar el fallo previo por cuota. La prueba Flash optativa de lotes se omite en CI cuando no hay un dispositivo preparado; sus ejecuciones sobre emulador y teléfono fueron locales. El nuevo arnés UDP y su ejecución física posterior no formaron parte de esa ejecución de CI.

La candidata está preparada para revisión con binarios, fuentes, licencias y sumas SHA-256. Se completaron la instalación MSI, una repetición UDP física aprobada con el fallo previo documentado y la validación local de la receta F-Droid. Siguen pendientes el recorrido visual de selección, envío y cancelación de lotes y la generación y comprobación del nuevo paquete de entrega. Esta evidencia no cubre todas las versiones de Android, routers o entornos de instalación, ni acredita aceptación oficial de F-Droid.

Los criterios de publicación, auditoría e identidad se conservan en [Preparación de publicación](PUBLICATION_READINESS.md) e [Identidad Android](ANDROID_IDENTITY.md). No se ha publicado una release pública ni enviado una solicitud a F-Droid desde esta preparación.

# Validación de Qetara 1.4.3 / código 10

Estado al 7 de octubre de 2026: **publicada en GitHub; validación local,
comprobación pública y CI GitHub aprobadas. Actualización de F-Droid pendiente**. Este informe corresponde exclusivamente al commit
`ee4c0aea8110f4ff8d11aa701cd93e1c7b3a3489`. Ese commit se publicó en `main`
y con la etiqueta anotada `v1.4.3`. La
[release GitHub 1.4.3](https://github.com/IntelogNatanael/Qetara/releases/tag/v1.4.3)
se publicó el `2026-10-07T22:44:30Z`, como release estable, con el APK firmado
y `SHA256SUMS.txt`. La descarga anónima y la reproducción desde el APK público también aprobaron. La
[MR !51433](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51433) no se
considera actualizada ni aceptada por estos resultados locales.

## Alcance de las fuentes

El commit modifica seis archivos: activa R8 y la reducción de recursos en
`release`, actualiza el comentario de ProGuard, fija 1.4.3/código 10 y añade
las notas general y Fastlane del código 10 en inglés y español. Conserva
`io.github.intelognatanael.qetara`, las dependencias y el protocolo. No cambia
código funcional de Android/PC ni incorpora los cambios locales del Canal Wi-Fi.
No se añaden reglas globales que desactiven las optimizaciones.

## Controles de cierre

| Control | Estado | Evidencia y límite |
| --- | --- | --- |
| Fuentes y versión | PASS | Commit fijado, versión/código correctos; `git diff --check` sin errores. |
| Build limpia Windows | PASS | `assembleRelease --no-build-cache`, clon nuevo y árbol limpio; 3 min 16 s. |
| Build limpia Linux | PASS | Clon independiente del mismo commit; 14 min 45 s. APK sin firma idéntico al de Windows. |
| Firma y alineación | PASS | APK firmado con el certificado existente; verificaciones de firma y alineación aprobadas. |
| Actualización física | PASS, alcance limitado | OPPO CPH2743, Android API 36: 1.4.2/9 → 1.4.3/10, conservando `firstInstallTime`. |
| Inspección de interfaz | PASS, alcance limitado | Arranque, «Acerca de» mostrando 1.4.3 y desplazamiento de los avisos legales. No acredita una revisión integral de UX. |
| Lote Flash y apertura por la interfaz real | PASS, transporte acotado | PC instalado 1.4.2 → APK Android 1.4.3 exacto: 5 archivos, 1 246 245 bytes, aprobación por lote y 5/5 SHA-256 correctos; «Abrir» muestra el archivo sintético de 1 MiB en Documents. TCP por ADB/USB; no comprueba LAN ni Wi-Fi Direct. |
| Prueba de contratos del APK optimizado | PASS, 15 comprobaciones | Segundo intento con el arnés corregido y el APK exacto: FileProvider, Flash TCP/UDP local, Noise individual/lote y rechazo de MAC corrupto; parada y limpieza verificadas. El primer FAIL se conserva. |
| Dependencias y avisos del APK | PASS incremental | 88 POM, 4 bibliotecas nativas, 12 assets legales y 8 textos canónicos comprobados. |
| Metadatos F-Droid | PASS local | `readmeta`, `rewritemeta` y `lint --force-yamllint`; segunda normalización estable. |
| Escáner de fuentes F-Droid | PASS local con avisos revisados | Código 0, cero errores y diez advertencias sobre TTF; contenido, tipo y permisos contrastados con el commit. |
| Build y escáner APK F-Droid | PASS local | fdroidserver 2.4.5, `build --test --no-tarball --scan-binary --stop …:10`, código 0; APK idéntico a las builds limpias. |
| Copia y verificación reproducible de la firma | PASS local | Linux y F-Droid reproducen byte a byte el APK firmado; apksigcopier 1.1.1 y `fdroidserver.common.verify_apks` aprobados, firma v2/v3 y alineación de 16 KiB verificadas. |
| Publicación GitHub | PASS | Fuentes `main`, etiqueta anotada `v1.4.3` y release estable publicados; APK firmado y `SHA256SUMS.txt` disponibles. |
| Comprobación desde URLs públicas | PASS | HTTP 200 sin credenciales; commit/etiqueta y propiedades públicos, APK, sumas, versión, VCS y certificado correctos. Ambas reconstrucciones reproducen el APK público. |
| CI GitHub del tag `v1.4.3` | PASS, 4/4 jobs | Commit exacto: Android y desktop macOS/Ubuntu/Windows; build, tests y lint Android, y autopruebas desktop aprobados. |
| Actualización de la MR y CI de contribución F-Droid | PENDIENTE | Consulta API del 7 de octubre, 22:46 UTC: la MR !51433 conserva su commit/CI de 1.4.2; no se afirma inclusión en F-Droid. |

Las builds limpias usan Gradle 9.1.0, AGP 9.0.0, JDK 21, SDK 36 y Build Tools
36.0.0. Ejecutan `minifyReleaseWithR8` y conservan sus salidas de diagnóstico.
El VCS incluido en los tres APK identifica el commit fijado. Windows, Linux y
el ensayo F-Droid produjeron APK sin firma idénticos. `mapping.txt` también
coincide en las tres builds; otros diagnósticos (`configuration.txt`, `seeds.txt`
y `usage.txt`) presentan diferencias registradas y no se declaran idénticos.
La copia pública de firma sobre las salidas Linux y F-Droid reprodujo el APK
firmado exacto. Estas comprobaciones no accedieron a la clave privada.

## Identidad de los artefactos

| Artefacto | Bytes | SHA-256 |
| --- | ---: | --- |
| APK sin firma Windows, Linux y F-Droid | 3 027 904 | `c73ecd37fba67f46585965fcb8ec3d05ea20413f99470757810e740e4b40cff4` |
| `Qetara-1.4.3.apk` firmado | 3 070 303 | `eb3e76742bea17be3680b427a6879570e85293b72e12ba563ed9c0f95b0df38f` |
| Receta pública propuesta, canónica | 715 | `0582224ea231060bb53723f0714754185d9032c8ce575d7ffbc7fe952bdeeef4` |
| `mapping.txt` Windows, Linux y F-Droid | 50 363 788 | `20bdcd115951da04910ffdf547d0d908b88edf2c905c642d5791cffa2a4b7674` |

Certificado Android SHA-256:
`5f5cdbfb03c1f62f7dc42aee12a5ea4a347b767ccfc268eb450ca69c98ba7a3b`.

El APK firmado se instaló como actualización de 1.4.2/código 9, sin
desinstalación. El sistema conservó `firstInstallTime=2026-09-11 08:16:45`.
Esto acredita continuidad de instalación; por sí solo no comprueba todos los
datos o preferencias del usuario.

## Ejecución del DEX optimizado y antecedentes

El envío desde la interfaz de producción de PC 1.4.2 al teléfono con el APK
1.4.3 firmado aprobado completó un lote sintético de cinco archivos, de
1 246 245 bytes en total. Apareció un diálogo de aprobación en cada equipo y
se comparó el código completo, coincidente. PC confirmó «5 archivos enviados
y confirmados». Los cinco SHA-256 de los archivos recibidos en
`Download/Qetara` coinciden con sus originales. Desde «Abrir» en Qetara,
el selector Android abrió Documents y su vista previa local mostró el
contenido conocido del archivo sintético de 1 MiB.

La conexión utilizó reenvío TCP por ADB sobre USB tras un error de acceso
denegado de sockets en la ruta LAN de Windows; VPN y firewall permanecieron
sin cambios. Esta prueba ejercita interfaz, Noise, transferencia y publicación
de archivos reales de producción, con ese transporte. No acredita
descubrimiento LAN, conexión Wi-Fi Direct ni funcionamiento sin el reenvío.
La evidencia `runtime/gui-flash-five-files.json` tiene SHA-256
`e49ca382574ac1ce15455767c1acd42247b06e978a8b67306bfc0980e74ca559`.

La primera prueba física del arnés verificó la identidad del APK, FileProvider
y el inicio/descubrimiento TCP de Flash, pero agotó la espera de una única
petición UDP y falló después al comprobar la parada. El intento completo
permanece **FAIL** y no llegó a la comprobación con el par Noise de PC.

La revisión detectó dos limitaciones del arnés: enviaba un único datagrama y
forzaba `stopService` inmediatamente después del contrato STOP. Se corrigieron
con reintentos UDP acotados y observación de la parada sin interrumpirla.
El resultado original no determina por sí solo un fallo de la aplicación ni
se convierte retroactivamente en PASS. Su fixture pendiente se inventarió y
eliminó por ID explícito tras validar estructura y contenido; se conservaron
`runtime/fixture-inventory-v1.txt` y `runtime/fixture-cleanup-v1.txt`.

La nueva ejecución `runtime/execution-20261007-173310/` terminó **PASS, 15
comprobaciones**, sobre el mismo APK firmado no depurable. Verificó MIME,
bytes y metadatos de FileProvider y rechazo de escape de sus raíces; inicio
del servicio Flash y descubrimiento TCP; nonce UDP y misma activación;
saludos Noise individual y de lote, rechazo de MAC corrupto y respuesta
posterior a esos rechazos. El par PC confirmó sus tres comprobaciones.

La parada dejó ausentes los servicios y la limpieza eliminó los fixtures.
Los contenidos de archivos de preferencias y las entradas del directorio
Flash externo permanecieron iguales antes y después de ese smoke. La prueba
usó túneles TCP ADB y UDP de loopback del teléfono: no cubre LAN ni Wi-Fi
Direct. No incluía aprobación o carga de archivos; ese alcance corresponde
al ensayo separado de interfaz y cinco archivos descrito arriba.

| Evidencia de la ejecución aprobada | SHA-256 |
| --- | --- |
| `execution-20261007-173310/result.json` | `8a46cdeb52fe8eab0462271ca3099f5d1be528705a31619280d5019bfc5e6bf6` |
| `execution-20261007-173310/instrumentation.txt` | `1d3a99b031447e12fc3edbbec28b9518f163f4c71f31d0bee6e3b708fe6ee4cb` |

Los 273 unit tests aprobados (218 Android y 55 protocolo), lint release sin
errores y con tres advertencias, y el APK experimental de 3 027 904 bytes
pertenecen al [preflight R8](FDROID_REVIEW-2026-10-07.md) anterior al commit y
al cambio de versión. Son antecedentes de las fuentes; los tests debug no
ejecutan el DEX optimizado del APK definitivo. No se reutilizan sus hashes ni
sus resultados como prueba de instalación o reproducción de 1.4.3.

La nueva [CI GitHub del tag `v1.4.3`](https://github.com/IntelogNatanael/Qetara/actions/runs/37698052755)
aprobó sus cuatro jobs sobre `ee4c0aea8110f4ff8d11aa701cd93e1c7b3a3489`.
Android ejecutó `protocol:test`, `minifyReleaseWithR8`, `assembleRelease`,
`testDebugUnitTest` y `lintRelease`, con build exitosa. Los jobs desktop de
macOS, Ubuntu y Windows también aprobaron sus autopruebas locales. Los logs
no proporcionan un total de tests que se pueda atribuir a este pase. Es
evidencia nueva del commit final, distinta del preflight y de la CI de F-Droid.

## Licencias y recursos incluidos

El [inventario 1.4.3](../licenses/android-release-dependency-inventory-1.4.3.json)
procede del grafo `package` y del modelo de bibliotecas que
`generateReleaseLintVitalReportModel` creó durante la build limpia. Contiene
88 dependencias externas, además del módulo propio `protocol` en el grafo.
Este modelo permite comprobar dependencias resueltas; no acredita un pase de
lint completo ni que R8 conserve todas las clases de cada dependencia.

Las 88 coordenadas y los SHA-256 de los POM disponibles en la caché WSL
existente coinciden con el inventario 1.4.2. Las cuatro bibliotecas nativas
empaquetadas y los doce assets legales son idénticos a la evidencia 1.4.2;
los assets también coinciden con las fuentes limpias. El agregado incluye
íntegros los ocho textos canónicos. No aparecieron entradas del logo retirado.
Los inventarios históricos permanecen intactos. Esta comprobación incremental
no es una auditoría exhaustiva del código de terceros ni una reconstrucción
de sus binarios nativos.

| Evidencia | SHA-256 |
| --- | --- |
| `licenses/android-release-dependency-inventory-1.4.3.json` | `0b3ccea632ba815d9451244e2342c6ada616346756b1b71cbe6156b099dcba35` |
| `license-final-artifact-check-1.4.3.json` local | `3001d76f4efe4dfa1a92dab31755f21373ec59254139bd0946a53493ad4e2ce5` |

## Verificación pública

La comprobación anónima del `2026-10-07T22:47:50Z` recibió HTTP 200 desde la
[URL versionada del APK](https://github.com/IntelogNatanael/Qetara/releases/download/v1.4.3/Qetara-1.4.3.apk),
sin autorización, cookies ni credenciales del origen. Los 3 070 303 bytes,
SHA-256, paquete/versión/código, VCS, certificado y firmas v2/v3 coinciden con
la entrega auditada; la alineación de 16 KiB también verifica. El archivo
[SHA256SUMS.txt](https://github.com/IntelogNatanael/Qetara/releases/download/v1.4.3/SHA256SUMS.txt)
publicado coincide. La etiqueta anotada tiene objeto
`01fc33428c48ccade204d21ea1711dbbfd15c9b3` y resuelve al commit fijado;
también se verificaron el acceso público al commit y a `gradle.properties`.

apksigcopier y `fdroidserver.common.verify_apks` reprodujeron el APK público
byte a byte desde ambas salidas sin firma Linux/F-Droid. Se reutilizaron esas
builds ya validadas: esta fase no volvió a compilar desde un clon HTTPS y no
equivale a CI o aceptación oficial de F-Droid.

| Evidencia pública y CI | SHA-256 |
| --- | --- |
| `linux/public-attempt-2/public-apk-validation-summary.json` | `7de64b745657e6c679683e2bdd5cb14a019d1e3aac959825198efca4d09a118d` |
| `github-tag-ci.json` | `6de63c3931cf336946a2237a4adf2c195a2c74d5b61207b1c400f03b997f1004` |

## Ensayo F-Droid y evidencia local

La receta propuesta fija 1.4.3/10 y el commit completo, usa `Binaries` global
con `v%v/Qetara-%v.apk`, conserva el certificado permitido y retira
`MaintainerNotes`. La URL 1.4.3 resultante ya corresponde a la release GitHub
publicada y comprobada mediante descarga anónima.

El ensayo local previo a la publicación usó una copia separada de la receta:
cambió `Repo` por el repositorio local y omitió `Binaries` porque el APK de
referencia aún no era público. Estos ajustes no entran en el YAML público. Se mantienen los escáneres;
el ensayo no acredita acceso HTTPS anónimo, comparación automática con la URL
pública ni aceptación del entorno oficial F-Droid.

fdroidserver 2.4.5 terminó la build con código 0, escaneo binario habilitado
y sin `--force`, `--skip-scan` ni reglas `scanignore` añadidas. El escáner del
APK aprobó sus comprobaciones DEX/SUSS de clases no libres conocidas y de
bloques adicionales de firma. Esto no constituye una auditoría exhaustiva
de todo el código ni una decisión de inclusión.

El escáner de fuentes conservó diez advertencias «executable binary, possibly
code», todas sobre TTF Inter/Noto. Se revisaron las diez: son archivos TrueType,
sus contenidos coinciden con los blobs del commit y Git registra modo `100644`;
el checkout sobre DrvFs los presenta con modo `0777`. No se retiraron fuentes
ni se eludió el escáner. Esta revisión de tipos, contenido y permisos es
independiente de la revisión de licencias de las fuentes tipográficas.

La reproducción local de firma está documentada por separado para Linux y
F-Droid; en ambas coinciden el hash firmado, certificado, firma v2/v3 y
alineación. La comprobación posterior mediante descarga de la URL pública también aprobó, con el alcance descrito arriba.

| Evidencia del cierre local | SHA-256 |
| --- | --- |
| `linux/validation-summary.json` | `74bbcdf95b4134e3a19ab4cad48dfb2a737d00b2ff53c246cd37e7322bfc5509` |
| `linux/signature-reproduction-linux.json` | `70e747f5fd2f9a36080de105974d32b4433afcaa3a2099ccd676100959385cde` |
| `linux/signature-reproduction-fdroid.json` | `524bc3b6f773f1155a01bd884e153160432e3990841bd876b650e83c821c84b3` |

Los registros no versionados se conservan en el workspace principal bajo
`.local/fdroid-r8-2026-10-07/`: `windows-clean-build.log`,
`Qetara-1.4.3.apk.verification.json`, los registros de versión instalada,
`license-final-artifact-check-1.4.3.json`, `linux/linux-result.json`,
`linux/metadata-checks.json`, `linux/source-scanner-exit.json` y
`linux/fdroid-local-adjustments.json`. `runtime/DIAGNOSIS-physical-v1.md`
identifica la ejecución fallida conservada y las reparaciones del arnés.
`runtime/execution-20261007-173310/` conserva el smoke aprobado, el registro
del par PC y los servicios antes/después. `runtime/gui-flash-five-files.json`
conserva el resultado independiente de la interfaz real, los cinco hashes
esperados/recibidos y la apertura del archivo.
La ejecución limpia Windows conserva además `provenance.json` y el clon
`build/android-release/20261007-220103-bfd1ebad602b4686a4b9e38a315c0f56/source/`.

## Pendientes para publicar

- [x] Completar la nueva prueba de contratos del APK optimizado; conservar
  el primer FAIL, la reparación del arnés, su limpieza y el nuevo PASS.
- [x] Cerrar build y escáner APK F-Droid; salida idéntica a los APK limpios.
- [x] Verificar la reproducción de la firma del APK definitivo desde Linux y F-Droid.
- [x] Publicar el commit y la etiqueta sin mover versiones anteriores; publicar
  el APK firmado y `SHA256SUMS.txt` tras cerrar los controles locales.
- [x] Verificar anónimamente el commit, etiqueta y propiedades públicos;
  descargar APK/sumas y comprobar versión, hash, certificado y reproducción.
- [x] Comprobar la CI GitHub del tag y sus cuatro jobs sobre el commit final.
- [ ] Actualizar la MR !51433 y revisar su nueva CI; distinguir aprobación
  local, CI de contribución e inclusión efectiva en el catálogo.

Los resultados y hashes de [1.4.2](VALIDATION-1.4.2.md) conservan su alcance
histórico. La CI desktop no equivale a publicar o instalar nuevos paquetes PC.

# Validación y fuentes de Qetara 1.4.2

Fecha: 6 de octubre de 2026. Identificador Android:
`io.github.intelognatanael.qetara`; versión `1.4.2`, código `9`.
Las compilaciones de esta revisión proceden del commit
`c960afafbef5ad463e22127e979408e7a3c4d3af`, con checkouts nuevos y limpios.
La receta F-Droid fija ese mismo commit. Los cambios posteriores de receta y
documentación no se atribuyen al APK ni modifican sus entradas de compilación.
También se corrigió después la preparación del SDK en GitHub Actions; no se
movió la etiqueta ni se cambiaron las fuentes de la aplicación validada.

Estado de cierre: compilaciones, pruebas, escáneres y reproducción de firma
completados. GitHub confirma el repositorio público y la etiqueta `v1.4.2`
identifica el commit indicado. La solicitud a F-Droid se presentó el 6 de
octubre en la [MR !51433](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51433);
la verificación de identidad de GitLab ya quedó resuelta. La primera CI que
ejecutó jobs aprobó ocho de nueve, incluidos build y comprobación del APK;
falló sólo la normalización de la URL de metadata. La nueva pipeline tras
incorporar el YAML canónico terminó con los nueve jobs obligatorios aprobados.
La MR sigue abierta,
`waiting-on-response`, pendiente de explicar la diferenciación funcional;
la inclusión no está aceptada. La verificación de acceso anónimo y el estado
del envío se registran al final del informe.

## Compilación limpia y reproducibilidad

Windows, Ubuntu WSL y el ensayo F-Droid produjeron APKs sin firma idénticos byte
a byte. Las dos compilaciones directas
ejecutaron las 51 tareas de release desde un checkout nuevo, sin reutilizar salidas
incrementales del proyecto. Se conservaron los perfiles ART y la metadata VCS;
esta última identifica el commit indicado arriba.

| Componente | Windows | Ubuntu WSL |
| --- | --- | --- |
| JDK | Eclipse Temurin 21.0.12.1+1 | OpenJDK 21.0.10 |
| Gradle | 9.1.0 | 9.1.0 |
| Android SDK | API 36, revisión 2 | API 36, revisión 2 |
| Build Tools | 36.0.0 | 36.0.0 |
| Resultado | Correcto, 5 min 26 s | Correcto, 4 min 55 s |

Las cachés de dependencias se reutilizaron; las salidas de tareas de Qetara no.
Windows usó `scripts/build-android-release.ps1 -Ref <commit> -Offline
-MaxWorkers 2`. Linux utilizó `:app:assembleRelease --no-daemon --no-build-cache
--no-configuration-cache --max-workers=2 --console=plain` en un clon nuevo.

| Artefacto | Bytes | SHA-256 |
| --- | ---: | --- |
| APK sin firma Windows, Linux y F-Droid | 14 837 252 | `124876116b6bb3ad7a3162dc320db5be9f6ce251204b0498034f6a9256dd1c65` |
| APK limpio firmado localmente | 14 884 743 | `841c166787e4cace65c059fe0e8deb8713ba82daa1100c32e300cf11780fe82b` |

La firma se aplicó fuera de Gradle con apksigner 36.0.0, preservando la
alineación ZIP. `apksigner verify` y `zipalign -c -P 16 4` terminaron
correctamente. El certificado es el de las instalaciones anteriores:
`5f5cdbfb03c1f62f7dc42aee12a5ea4a347b767ccfc268eb450ca69c98ba7a3b`.
La clave privada permanece fuera del repositorio y no se facilitó a Linux.

apksigcopier 1.1.1 copió la firma pública de la candidata Windows sobre cada
APK independiente, Linux y F-Droid. Ambos resultados coinciden byte a byte con
el APK firmado de la tabla. En ambos pasaron la verificación de firmas v2/v3
con apksigner y la comprobación de alineación de 16 KiB con zipalign.

## Pruebas y F-Droid

| Comprobación | Resultado del ensayo local |
| --- | --- |
| Coherencia de fuentes/receta | `audit-release.ps1 -RequireCurrentRecipe`: código 0, sin entradas Android/build distintas del commit fijado. |
| `fdroid readmeta` | Código 0. |
| `fdroid rewritemeta` | Código 0; YAML sin cambios de normalización. |
| `fdroid lint --force-yamllint` | Código 0, sin mensajes. |
| Escáner de fuentes con firmas actualizadas | Código 0; cero errores y advertencias. |
| Build y escáner del APK F-Droid | Código 0, 5 min 11 s; sin hallazgos del escáner de APK. |
| Suite de protocolo, PC y Android | 339 pruebas declaradas: 337 correctas, 2 omitidas, 0 fallos y 0 errores; ejecutada en el clon limpio Windows. |
| Lint Android debug/release | Cero errores y tres advertencias en cada variante: `UsableSpace` y dos `UseKtx`. |
| Autoprueba PC de transferencia y reanudación | Transferencia local de 2 MiB y reanudación desde un archivo parcial correctas, con verificación de hashes. |

El desglose de la suite es protocolo 55/55, PC 64/66 con dos omitidas y Android
218/218. Las omitidas son las pruebas opcionales con dispositivos
`FlashEmulatorSocketTest.windowsAndAndroidExchangeTwoFileBatchesOverRealSockets`
y `FlashLanUdpTest.androidServiceAndDesktopControllerDiscoverFreshSessionsByLanUdp`.
Se compilaron los APK debug y de instrumentación; no se instalaron ni se
ejecutó instrumentación en un dispositivo en esta fase.

El escáner de fuentes actualizó su base SUSS de 104 firmas, SHA-256
`3c9044c65dcc8c28c75754882c3d50b41e555dc10383343043e527a562ac3def`.
No hubo errores ni advertencias; sus tres mensajes informativos corresponden
a la retirada de wrappers Gradle que realiza F-Droid. No encontrar componentes
no libres conocidos no demuestra por sí solo conformidad exhaustiva.

El ensayo utiliza fdroidserver 2.4.5, apksigcopier 1.1.1 y configuración de
categorías de fdroiddata del commit
`7f3fb82b398ef8537ff478e97f2ea44c83b7b0a9`. El entorno local tiene SDK y Gradle
configurados explícitamente. Sólo la copia de metadata del ensayo sustituye
el origen HTTPS por el repositorio Git local; la receta publicable conserva
HTTPS. F-Droid prepara y limpia su propio checkout, sin compilar el directorio
de desarrollo. No se omiten sus escáneres ni la comprobación de versión.
La build ejecutada fue `fdroid build --test --no-tarball --scan-binary --stop
--no-refresh io.github.intelognatanael.qetara:9`, después de actualizar el
escáner. Es un ensayo local WSL; no es la CI ni el servidor oficial de F-Droid.

## GitHub Actions

La [ejecución de la etiqueta](https://github.com/IntelogNatanael/Qetara/actions/runs/37480392558)
aprobó las pruebas, el empaquetado y las autopruebas de escritorio en Windows,
Ubuntu y macOS. Android falló antes de compilar: la acción fijada
`android-actions/setup-android` solicitó su paquete predeterminado `tools`,
que sdkmanager no encontró. La misma causa afectó la ejecución de `main`.

Se corrigió el workflow para solicitar sólo `platform-tools`; la plataforma 36
y Build Tools 36.0.0 se siguen instalando explícitamente en el paso posterior.
Esta corrección no cambia las entradas del APK ni invalida las compilaciones
locales documentadas. La [CI del workflow corregido](https://github.com/IntelogNatanael/Qetara/actions/runs/37481564174),
commit `154729bcc00ab721be2d66ae0f18dc6a3b367ea2`, terminó correctamente:
Android en 6 min 12 s y escritorio en Windows, Ubuntu y macOS. Se ejecutaron
las compilaciones, lint y pruebas Android/protocolo y el empaquetado, pruebas
y autopruebas de escritorio definidos en el workflow. El fallo histórico de
la etiqueta queda registrado; no se presenta como una ejecución aprobada.

La CI valida ese commit posterior, cuyas entradas de aplicación coinciden con
la etiqueta. Los hashes de reproducibilidad de este informe siguen siendo los
de las compilaciones limpias del commit `c960afafbef5ad463e22127e979408e7a3c4d3af`;
no se atribuyen a los APK de Actions. La comprobación de coherencia de la receta
también pasó sobre `154729bcc00ab721be2d66ae0f18dc6a3b367ea2`.

## Licencias y revisión de publicación

El modelo release recién generado resuelve 88 dependencias externas, sin altas
ni bajas respecto de la candidata 1.4.0. Los 88 POM conservan sus hashes.
Se revisaron las fuentes y atribuciones de recursos, incluido Inter, y se
sincronizaron los avisos de PC y raíz con Android. El recurso Invertocat de
GitHub y sus avisos específicos se retiraron de ambos clientes.
El [inventario 1.4.2](../licenses/android-release-dependency-inventory-1.4.2.json)
registra el commit y los hashes del modelo, APK y POM. Los 12 avisos del APK
coinciden byte a byte con las fuentes; el agregado contiene íntegros los ocho
textos canónicos. El APK no contiene recursos Invertocat y sus cuatro
bibliotecas nativas Graphics Path coinciden con la procedencia documentada
de 1.0.1. No se identificó un bloqueo FLOSS nuevo en este alcance.

Antes de publicar se revisaron fuentes, historial y superficies remotas de
GitHub. Gitleaks 8.30.1 examinó 461 archivos y los diffs de 38 commits. Sus
coincidencias se revisaron: son constantes de código, IDs de Figma, el checksum
público de una herramienta y huellas públicas de certificados. No se
identificaron secretos reales en ese alcance. No se añadieron exclusiones para
ocultar las detecciones.

La revisión adicional inventarió 810 blobs, inspeccionó las 16 imágenes nuevas,
los metadatos de los 12 PRs y dos releases existentes y cuatro logs recientes
de Actions. La API no mostró artefactos Actions almacenados. También se
inspeccionaron las fuentes y tres capturas de la entrega móvil 1.3.1. Se
mantienen la atribución histórica y los correos de autoría ya documentados;
no se reescribió el historial. Este alcance no demuestra ausencia absoluta de
secretos ni equivale a revisar todos los logs de todas las ejecuciones.

## Publicación y límites

La etiqueta anotada [`v1.4.2`](https://github.com/IntelogNatanael/Qetara/tree/v1.4.2)
resuelve al commit `c960afafbef5ad463e22127e979408e7a3c4d3af`. Se publicó junto
con `main` mediante push atómico sin forzar y GitHub confirmó visibilidad
`PUBLIC` el 6 de octubre de 2026. La documentación, el inventario derivado y la
receta actualizada se añaden después del commit de fuentes; no cambian el APK.
La comprobación independiente desde Linux ejecutó `git ls-remote` y un clon
HTTPS nuevo de `v1.4.2`, con HOME vacío, configuración global/sistema desactivada,
sin helpers, cabeceras de autenticación ni prompts. La etiqueta anotada
`526030314145a04d36d5e99e6fd6908759aae5d7` resuelve al commit esperado y
`gradle.properties` descargado declara `1.4.2`/`9`. No se requirieron credenciales.
Una consulta HTTP anónima independiente confirmó después que la receta de
`main` declara versión `1.4.2`, código `9` y el commit validado.

El mismo APK firmado de este informe se publicó en la entrega
[`v1.4.2`](https://github.com/IntelogNatanael/Qetara/releases/tag/v1.4.2), el
6 de octubre de 2026 a las 15:03:39 UTC, como
[`Qetara-1.4.2.apk`](https://github.com/IntelogNatanael/Qetara/releases/download/v1.4.2/Qetara-1.4.2.apk),
junto con `SHA256SUMS.txt`. La receta incorpora esa URL exacta en `binary` y
conserva `AllowedAPKSigningKeys`; no se volvió a firmar ni compilar el archivo.

Una descarga HTTPS anónima desde Linux obtuvo HTTP 200 y confirmó los
14 884 743 bytes y el SHA-256 `841c166787e4cace65c059fe0e8deb8713ba82daa1100c32e300cf11780fe82b`.
Se comprobaron de nuevo el paquete, versión 1.4.2/código 9, commit VCS,
certificado, firmas v2/v3 y alineación de 16 KiB. apksigcopier reprodujo el APK
descargado desde el APK sin firma del ensayo F-Droid ya documentado. También
pasó `fdroidserver.common.verify_apks`, la función real de comparación usada
por build/publish en fdroidserver 2.4.5, sin modificar la herramienta. La huella
obtenida por `common.apk_signer_fingerprint` coincide con la permitida.

La receta preparada para el envío pasó otra vez `readmeta`, `rewritemeta` y lint
en el entorno local, todos con código 0, sin diferencias de normalización.
SHA-256 del YAML comprobado:
`f064c2dffb78b2ce4291c36e01be048602da00574a04e0720d0291d5fcd11ebb`.
No se ejecutó el CLI `fdroid publish`, que además requiere claves para firmar
el índice. La comparación sobre el APK público reutilizó la build independiente
del commit exacto; no se afirma una build nueva ni una ejecución oficial.

La solicitud se presentó el 6 de octubre de 2026 a las 15:54:26 UTC como
[New app: Qetara — MR !51433](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51433).
Se verificó abierta, sin borrador ni conflictos, con squash y un único YAML
nuevo en el diff. Su origen es el fork público `carlos5alentino/fdroiddata`,
rama `codex/qetara-1.4.2`, commit de metadata
`dd93e7e3e3da545e11fb1df90b451aaa47ac4ed5`, hacia `fdroid/fdroiddata:master`.
El YAML remoto inicial conservaba los 1 790 bytes LF y el SHA-256 validado arriba.
No se modificaron las fuentes fijadas, la etiqueta ni el APK.

La comprobación `audit-release.ps1 -RequireCurrentRecipe` se repitió a las
15:57 UTC: código 0, sin cambios en entradas Android/PC respecto de las fuentes
etiquetadas, receta 1.4.2/9 consistente y URL `binary` configurada. No fue una
nueva compilación.

Las pipelines [del push](https://gitlab.com/carlos5alentino/fdroiddata/-/pipelines/2918607293)
y [de la MR](https://gitlab.com/carlos5alentino/fdroiddata/-/pipelines/2918632569)
quedaron como `failed`. En ambas, GitLab exigía «Verify your identity to run
this pipeline», una verificación adicional a la del registro. La API de la
pipeline de la MR confirmó `jobs: []`, `yaml_errors: null` y `started_at: null`;
el intento de push tampoco tenía jobs ni errores YAML. El bloqueo ocurrió
antes de ejecutar la CI y no produjo resultados de build o pruebas.
La descripción inicial de la MR solicitó ayuda a los mantenedores para ejecutar
la CI en el proyecto principal. Ese bloqueo de identidad ya está resuelto;
los intentos sin jobs conservan su estado histórico.

La [pipeline 2918682556](https://gitlab.com/carlos5alentino/fdroiddata/-/pipelines/2918682556)
ejecutó la configuración de fdroiddata en el fork, con runners de GitLab, y
terminó con ocho de nueve jobs aprobados, incluidos `fdroid build` y `check apk`.
El [job de build 16975390342](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16975390342)
registró `Building io.github.intelognatanael.qetara:9`, obtuvo las fuentes de
`c960afafbef5ad463e22127e979408e7a3c4d3af` y completó la compilación. Después
registró `successfully verified`, la comparación satisfactoria del binario
construido con el APK público de referencia y el certificado permitido
`5f5cdbfb03c1f62f7dc42aee12a5ea4a347b767ccfc268eb450ca69c98ba7a3b`.
El log se conserva en
`.local/publication-2026-10-06/submission/gitlab/build-16975390342.log`.
Estos resultados pertenecen a la CI de contribución; no son una build de
producción ni una publicación en el catálogo de F-Droid.

El [job `check apk` 16975390351](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16975390351)
examinó los tres archivos DEX con firmas SUSS y dexdump 36.0.0, comprobó bloques
de firma adicionales y registró la validación del APK. Su log
`apk-16975390351.log`, en la misma carpeta de evidencia, también conserva
advertencias: permisos de `config.yml` en el entorno de CI y una tabla de
recursos específicos de Androguard que, ante API 36, recurrió a su nivel
máximo 28. La compilación emitió además advertencias de APIs obsoletas y sobre
una biblioteca nativa que no pudo reducir con stripping. El resultado aprobado
no equivale a cero advertencias ni a una verificación exhaustiva de todas las
funciones de Android 36.

El único fallo fue [rewritemeta](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16975390347):
el serializador ruamel.yaml de CI coloca la URL escalar en la línea siguiente a `binary:`
y conserva un espacio al final de esa clave. No convierte la URL en una lista
ni cambia su valor. Se descargó el YAML canónico del artefacto del job y se
incorporó sin alterar sus bytes a la receta local y a la rama de la MR,
commit `b7f7f882612613a94d55dd589c946da69ea78d9b`. Tiene 1 797 bytes, finales LF
y SHA-256 `5377907e627663ebfe9991e4e9db3371516e18d88f75056dc34c15a0848e1657`.
Este es el hash actual de la receta; el anterior identifica el ensayo y envío
iniciales. No se cambiaron las fuentes fijadas, la etiqueta ni el APK.
La [pipeline 2919175178](https://gitlab.com/carlos5alentino/fdroiddata/-/pipelines/2919175178)
de la corrección terminó en `success`, sobre el commit de metadata
`b7f7f882612613a94d55dd589c946da69ea78d9b`, con `updated_at`
`2026-10-06T18:55:37.091Z`. Sus nueve jobs obligatorios aprobaron:

| Job | Resultado |
| --- | --- |
| [fdroid build · 16979192163](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16979192163) | `success` |
| [checkupdates · 16979192164](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16979192164) | `success` |
| [git redirect · 16979192165](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16979192165) | `success` |
| [fdroid lint · 16979192166](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16979192166) | `success` |
| [fdroid rewritemeta · 16979192167](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16979192167) | `success` |
| [tools check scripts · 16979192168](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16979192168) | `success` |
| [schema validation · 16979192169](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16979192169) | `success` |
| [check source code · 16979192170](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16979192170) | `success` |
| [check apk · 16979192171](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16979192171) | `success` |

La nueva build registró de nuevo `Building io.github.intelognatanael.qetara:9`
y el commit de fuentes `c960afafbef5ad463e22127e979408e7a3c4d3af`. Gradle
terminó con `BUILD SUCCESSFUL in 4m 19s`; a las 18:52:33 UTC se registraron la
verificación satisfactoria, la comparación del binario construido con el APK
público y el certificado permitido ya documentado. El log
`build-16979192163.log` se conserva en la misma carpeta de evidencia de GitLab.

El nuevo escáner de APK examinó `classes.dex`, `classes2.dex` y `classes3.dex`
con dexdump 36.0.0 y firmas SUSS, comprobó los bloques de firma adicionales
y registró `APK file was successfully validated!` y `Job succeeded`.
El log `apk-16979192171.log` conserva las advertencias de permisos de
`config.yml` y del recurso de Androguard a su tabla API 28 ante API 36, además
de `SyntaxWarning` por secuencias de escape en la herramienta Python clint.
La aprobación de los nueve jobs no se presenta como ausencia de advertencias,
aceptación editorial ni publicación en el catálogo de F-Droid.

La comprobación local `audit-release.ps1 -RequireCurrentRecipe` se repitió a
las 18:59:46 UTC: resultado aprobado, versión 1.4.2/código 9, `binary_url`
configurada, ningún cambio en entradas Android o PC respecto del commit fijado
y `findings: []`. El hash de la receta local siguió coincidiendo exactamente
con el artefacto canónico de CI. Esta comprobación de coherencia no volvió a
compilar ni a ejecutar las auditorías previas.

La MR permanece abierta, con la etiqueta `waiting-on-response` tras el
[comentario de linsui](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51433#note_3965121841),
que pide justificar las funciones o mejoras que la distinguen de aplicaciones
existentes y plantea contribuir a ellas. No se ha rechazado ni cerrado la
solicitud. El borrador local
`.local/publication-2026-10-06/submission/gitlab/reply-linsui-draft.txt`
no está publicado: espera revisión del usuario. La revisión, aceptación e
inclusión siguen pendientes; las auditorías locales anteriores conservan su
alcance y no se presentan como repetidas por esta CI.

La [revisión de UX del 5 de octubre](UX_REVIEW-2026-10-05.md) documenta las
pruebas físicas anteriores y sus límites. No se repitieron en esta fase y sus
binarios incrementales no se confunden con el APK limpio aquí identificado.
Los hallazgos de UX pendientes y el problema de conexión física PC → Android
siguen abiertos. La igualdad de APKs tampoco demuestra que cada biblioteca o
compilador de terceros se haya reconstruido desde sus fuentes.

La evidencia completa se conserva localmente en
`.local/publication-2026-10-06/` y en la carpeta de build limpia de Windows.
Incluye logs, procedencia, resultados de escáneres y hashes; no se publican
claves ni las capturas privadas del teléfono del usuario. Las instrucciones
para repetir el proceso están en [RELEASING](RELEASING.md) y
[preparación F-Droid](../fdroid/README.md).

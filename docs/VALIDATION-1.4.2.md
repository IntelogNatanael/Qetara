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
identifica el commit indicado. No constituye una solicitud ni aceptación de
F-Droid. La verificación de acceso anónimo se registra al final del informe.

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

| Comprobación | Resultado de esta ejecución |
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

Esta fase prepara y publica fuentes. El APK firmado de este informe permanece
local; todavía falta publicar su URL versionada y añadir `binary`/`Binaries`
para el flujo de firma del desarrollador elegido. La inclusión en el catálogo
requiere presentar la receta y superar la revisión y build oficiales.

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

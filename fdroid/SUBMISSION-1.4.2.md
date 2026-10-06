# Solicitud presentada: Qetara 1.4.2

Estado del 6 de octubre de 2026: APK y fuentes publicados, receta validada;
solicitud presentada como [**New app: Qetara — MR !51433**](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51433),
creada a las 15:54:26 UTC. Está abierta, sin borrador ni conflictos, marcada
`waiting-on-response`. La verificación de identidad de GitLab ya quedó resuelta.
La pipeline 2918682556 aprobó ocho de nueve jobs, incluidos `fdroid build` y
`check apk`; sólo falló la normalización de metadata. La pipeline 2919175178,
con el YAML canónico de CI, terminó con los nueve jobs obligatorios aprobados.
La revisión e inclusión en F-Droid
siguen pendientes; la CI de contribución no acredita una build de producción
ni la publicación en el catálogo.

La [receta final](metadata/io.github.intelognatanael.qetara.yml) es el único
archivo añadido en el diff de la MR, bajo
`metadata/io.github.intelognatanael.qetara.yml`. Sus 1 797 bytes, con finales LF,
coinciden exactamente con el YAML canónico descargado del artefacto de CI:
SHA-256 `5377907e627663ebfe9991e4e9db3371516e18d88f75056dc34c15a0848e1657`.
Los informes, binarios y ficha Fastlane permanecen fuera de ese diff.

## Envío y siguiente paso

La MR procede del fork público
[`carlos5alentino/fdroiddata`](https://gitlab.com/carlos5alentino/fdroiddata),
rama no protegida `codex/qetara-1.4.2`, ahora en el commit
`b7f7f882612613a94d55dd589c946da69ea78d9b`, hacia `fdroid/fdroiddata:master`.
Está configurada con squash. El envío inicial estaba en
`dd93e7e3e3da545e11fb1df90b451aaa47ac4ed5`; la actualización sólo adopta el
formato de metadata producido por CI. El commit de fuentes Android fijado
por la receta no cambió.

Los intentos de pipeline [del push](https://gitlab.com/carlos5alentino/fdroiddata/-/pipelines/2918607293)
y [de la MR](https://gitlab.com/carlos5alentino/fdroiddata/-/pipelines/2918632569)
quedaron como `failed`. La interfaz mostraba «Verify your identity to run this
pipeline» y aclaraba que esa verificación era adicional a la del registro.
La API de la pipeline de la MR confirmó `jobs: []`, `yaml_errors: null` y
`started_at: null`; el intento de push tampoco tenía jobs ni errores YAML.
Este bloqueo no aporta resultados de compilación o pruebas de la receta.

La descripción inicial solicitó ayuda a los mantenedores para ejecutar la CI
en el proyecto principal. El bloqueo de identidad ya está resuelto. La
[pipeline 2918682556](https://gitlab.com/carlos5alentino/fdroiddata/-/pipelines/2918682556)
ejecutó la configuración de fdroiddata en el fork y runners de GitLab:
ocho de nueve jobs aprobaron, incluidos `fdroid build` y `check apk`.
El [job de build](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16975390342)
compiló `io.github.intelognatanael.qetara:9` desde
`c960afafbef5ad463e22127e979408e7a3c4d3af`, verificó el APK construido contra el
binario público de referencia y confirmó el certificado permitido. El log
`build-16975390342.log` queda en
`.local/publication-2026-10-06/submission/gitlab/`.

El único fallo fue [rewritemeta](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16975390347).
Su artefacto canónico coloca la URL escalar en la línea siguiente a `binary:`
y conserva un espacio al final de la clave; no transforma el valor en una lista.
Se copiaron exactamente esos bytes a la receta local y la rama de la MR.
La [pipeline 2919175178](https://gitlab.com/carlos5alentino/fdroiddata/-/pipelines/2919175178)
terminó en `success`, sobre `b7f7f882612613a94d55dd589c946da69ea78d9b`, con
`updated_at` `2026-10-06T18:55:37.091Z`: sus nueve jobs obligatorios aprobaron.
La nueva [build 16979192163](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16979192163)
reconstruyó el código 9 desde el mismo commit de fuentes y verificó otra vez
la coincidencia con el APK público y el certificado permitido. También
aprobaron [rewritemeta](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16979192167)
y el [escáner del APK](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16979192171).
Los logs `build-16979192163.log` y `apk-16979192171.log` se conservan en la
misma carpeta de evidencia. Las fuentes, la etiqueta, el APK y su firma
permanecen iguales.
La descripción de la MR se actualizó a las 18:59:33 UTC con los nueve jobs
aprobados, los enlaces a la nueva build y escáner del APK y sus advertencias.

[linsui pidió justificar la diferenciación funcional](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51433#note_3965121841)
frente a aplicaciones existentes y sugirió contribuir a ellas. La MR sigue
abierta, `waiting-on-response`; ese comentario no es un rechazo ni un cierre.
El borrador `.local/publication-2026-10-06/submission/gitlab/reply-linsui-draft.txt`
no se ha publicado y espera revisión del usuario. Queda atender la revisión
de inclusión. La validación local y las CI de contribución no sustituyen la
aceptación de F-Droid.

Las [instrucciones de contribución](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md)
y la [plantilla oficial](https://gitlab.com/fdroid/fdroiddata/-/blob/master/.gitlab/merge_request_templates/App%20inclusion.md)
se revisaron en la versión `6d78762a154be40aa7847f0b8a15e2728338d489`.
Las búsquedas públicas del nombre y el identificador en MR, issues de
fdroiddata y RFP, en todos los estados, no encontraron solicitudes previas
antes de crear esta MR. El seguimiento continúa en !51433.

## Resumen técnico para la revisión

Add Qetara 1.4.2 (versionCode 9), application ID
`io.github.intelognatanael.qetara`.

Qetara shares files and messages between Android devices and its FLOSS desktop
companion over a local network, without an account or central transfer service.
Supported Android devices can also use Wi-Fi Direct. Optional Flash sharing
requires confirmation on both devices and starts disabled.

- Source and issue tracker: [IntelogNatanael/Qetara](https://github.com/IntelogNatanael/Qetara)
  and [issues](https://github.com/IntelogNatanael/Qetara/issues).
- License: MIT; upstream includes dependency notices and provenance records.
- Source tag: `v1.4.2`; full commit:
  `c960afafbef5ad463e22127e979408e7a3c4d3af`.
- Public reference APK: [Qetara-1.4.2.apk](https://github.com/IntelogNatanael/Qetara/releases/download/v1.4.2/Qetara-1.4.2.apk).
- APK SHA-256: `841c166787e4cace65c059fe0e8deb8713ba82daa1100c32e300cf11780fe82b`.
- Signing certificate SHA-256:
  `5f5cdbfb03c1f62f7dc42aee12a5ea4a347b767ccfc268eb450ca69c98ba7a3b`.

The recipe pins the standard Gradle release of the `app` module. Clean Windows,
Linux and local fdroidserver 2.4.5 builds produced identical unsigned APKs.
Public-signature copying reproduced the developer-signed APK byte for byte.
After publication, an anonymous download passed package/version, VCS,
certificate and alignment checks, apksigcopier and the actual
`fdroidserver.common.verify_apks` comparison. Metadata parsing,
normalization and lint passed in that local environment. No scanner bypass or
ART/VCS removal was used.

The local environment used JDK 21, Gradle 9.1.0, Android platform 36 revision 2
and Build Tools 36.0.0. GitLab identity verification has since been resolved.
[Pipeline 2918682556](https://gitlab.com/carlos5alentino/fdroiddata/-/pipelines/2918682556),
using fdroiddata's CI configuration in the fork and GitLab runners, passed
eight of nine jobs, including `fdroid build` and `check apk`. The build verified
the reconstructed APK against the public reference and its allowed signer.
Only metadata normalization failed. The exact canonical YAML from that job's
artifact was adopted; [pipeline 2919175178](https://gitlab.com/carlos5alentino/fdroiddata/-/pipelines/2919175178)
then passed all nine required jobs, including the repeated
[build and reference comparison](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16979192163),
[metadata normalization](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16979192167)
and [APK scan](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16979192171).
These contribution checks do not constitute production publication or
acceptance. [MR !51433](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51433)
remains open, awaiting a response about functional differentiation; a local
reply draft awaits the author's review and has not been posted.
See the [complete validation report](https://github.com/IntelogNatanael/Qetara/blob/main/docs/VALIDATION-1.4.2.md)
for build hashes, tests, license review and known UX/connectivity limitations.
The full `fdroid publish` CLI was not run; it also requires index-signing keys.

The author authorized submission. No AntiFeatures were identified in the
reviewed Android/protocol code and dependency inventory; this remains subject
to independent review. The pinned release includes en-US and es-ES Fastlane
summaries, descriptions and changelog 9. Store screenshots and a separate
Fastlane icon are pending; no historical private captures are being supplied.

Updates are intentionally manual during first inclusion, so each future
developer-signed artifact can be checked before proposing its recipe. A later
tag-based policy needs `UpdateCheckData` for `qetaraVersion` and
`qetaraVersionCode` in `gradle.properties`, with stable-tag filtering. This
proposal contains only version 1.4.2/9 and one universal APK of 14,884,743 bytes.

## Comprobaciones que siguen abiertas

El fork, la rama, el diff y la MR se comprobaron al presentar la solicitud.
La respuesta al mantenedor sigue pendiente de revisión del usuario. La nueva
pipeline aprobó sus nueve jobs; los intentos iniciales bloqueados no se
presentan como ejecuciones aprobadas y la primera pipeline ejecutada conserva
su fallo de formato. También faltan la
revisión, aceptación y publicación en el catálogo de F-Droid. El informe de UX
conserva hallazgos pendientes, por lo que los resultados locales no se presentan como una
certificación exhaustiva de todos los criterios funcionales de inclusión.

Las capturas y el icono de ficha mejorarán su presentación, pero la
[documentación de metadata](https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/)
y la plantilla distinguen esos recursos recomendados de los textos obligatorios.
Las imágenes deben proceder de una revisión real y publicable de la aplicación;
no se modifica la etiqueta validada para añadirlas.

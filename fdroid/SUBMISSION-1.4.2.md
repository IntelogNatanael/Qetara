# Solicitud presentada: Qetara 1.4.2

Estado del 6 de octubre de 2026: APK y fuentes publicados, receta validada;
solicitud presentada como [**New app: Qetara — MR !51433**](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51433),
creada a las 15:54:26 UTC. Está abierta, sin borrador ni conflictos. La CI de
GitLab está bloqueada por verificación adicional de identidad; la compilación,
revisión e inclusión oficiales en F-Droid siguen pendientes.

La [receta final](metadata/io.github.intelognatanael.qetara.yml) es el único
archivo añadido en el diff de la MR, bajo
`metadata/io.github.intelognatanael.qetara.yml`. Se comprobó que sus 1 790 bytes,
con finales LF, coinciden con la receta validada: SHA-256
`f064c2dffb78b2ce4291c36e01be048602da00574a04e0720d0291d5fcd11ebb`.
Los informes, binarios y ficha Fastlane permanecen fuera de ese diff.

## Envío y siguiente paso

La MR procede del fork público
[`carlos5alentino/fdroiddata`](https://gitlab.com/carlos5alentino/fdroiddata),
rama no protegida `codex/qetara-1.4.2`, commit
`dd93e7e3e3da545e11fb1df90b451aaa47ac4ed5`, hacia `fdroid/fdroiddata:master`.
Está configurada con squash. Ese commit contiene la propuesta de metadata;
el commit de fuentes Android fijado por la receta no cambió.

Los intentos de pipeline [del push](https://gitlab.com/carlos5alentino/fdroiddata/-/pipelines/2918607293)
y [de la MR](https://gitlab.com/carlos5alentino/fdroiddata/-/pipelines/2918632569)
figuran como `failed`. La interfaz muestra «Verify your identity to run this
pipeline» y aclara que esa verificación es adicional a la del registro.
La API de la pipeline de la MR confirmó `jobs: []`, `yaml_errors: null` y
`started_at: null`; el intento de push tampoco tenía jobs ni errores YAML.
Este bloqueo no aporta resultados de compilación o pruebas de la receta.

La descripción de la MR ya solicita a los mantenedores ejecutar la CI en el
proyecto principal, siguiendo la plantilla oficial, que indica no aportar
teléfono ni tarjeta sólo para habilitar esa CI. Queda atender la ejecución y
la revisión, resolver observaciones y registrar sus resultados. La validación
local y la CI de GitHub no sustituyen la CI ni la aceptación de F-Droid.

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
`fdroidserver.common.verify_apks` comparison. Final metadata parsing,
normalization and lint passed. No scanner bypass or ART/VCS removal was used.

The environment used JDK 21, Gradle 9.1.0, Android platform 36 revision 2 and
Build Tools 36.0.0. These are local results. GitLab's pipelines are blocked by
additional identity verification; maintainer assistance has been requested
in [MR !51433](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51433).
The F-Droid build and review remain pending. See the [complete validation report](https://github.com/IntelogNatanael/Qetara/blob/main/docs/VALIDATION-1.4.2.md)
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
Las casillas de CI e informes siguen pendientes: ningún intento bloqueado se
presenta como una ejecución aprobada. También faltan la revisión, aceptación
y publicación en el catálogo de F-Droid. El informe de UX conserva hallazgos
pendientes, por lo que los resultados locales no se presentan como una
certificación exhaustiva de todos los criterios funcionales de inclusión.

Las capturas y el icono de ficha mejorarán su presentación, pero la
[documentación de metadata](https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/)
y la plantilla distinguen esos recursos recomendados de los textos obligatorios.
Las imágenes deben proceder de una revisión real y publicable de la aplicación;
no se modifica la etiqueta validada para añadirlas.

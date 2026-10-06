# Envío preparado: Qetara 1.4.2

Estado del 6 de octubre de 2026: APK y fuentes publicados, receta validada;
solicitud a F-Droid **todavía no enviada**. Falta una cuenta GitLab autenticada
para crear el fork, ejecutar o solicitar su pipeline y abrir la merge request.

La [receta final](metadata/io.github.intelognatanael.qetara.yml) es el único
archivo que debe incorporarse a fdroiddata, bajo
`metadata/io.github.intelognatanael.qetara.yml`. No hay que copiar los informes,
binarios ni la ficha Fastlane a ese repositorio.

## Pasos de envío

1. Iniciar sesión en GitLab y crear un fork público de
   [fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata).
2. Crear una rama no protegida `codex/qetara-1.4.2`, añadir la receta con finales
   de línea LF y comprobar que el diff sólo contiene ese archivo.
3. Ejecutar la pipeline del fork y resolver sus observaciones. Si el servicio
   impide usar sus runners, indicarlo en la solicitud para que los mantenedores
   puedan activar la CI de F-Droid. Su plantilla indica que no se debe aportar
   teléfono ni tarjeta solamente para habilitar esa CI.
4. Abrir la merge request hacia `fdroid/fdroiddata:master`, con título
   **New app: Qetara** y la plantilla oficial **App inclusion**. Usar el texto
   preparado abajo y marcar sólo las comprobaciones realmente completadas.
5. Registrar su URL y atender la revisión. La validación local y la CI de
   GitHub no sustituyen la pipeline ni la aceptación de F-Droid.

Las [instrucciones de contribución](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md)
y la [plantilla oficial](https://gitlab.com/fdroid/fdroiddata/-/blob/master/.gitlab/merge_request_templates/App%20inclusion.md)
se revisaron en la versión `6d78762a154be40aa7847f0b8a15e2728338d489`.
Las búsquedas públicas del nombre y el identificador en MR, issues de
fdroiddata y RFP, en todos los estados, no encontraron coincidencias. Debe
revisarse de nuevo antes de enviar si ha transcurrido tiempo.

## Texto preparado para la solicitud

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
Build Tools 36.0.0. These are local results; the F-Droid pipeline and review
remain pending. See the [complete validation report](https://github.com/IntelogNatanael/Qetara/blob/main/docs/VALIDATION-1.4.2.md)
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

El fork, su diff y pipeline, la URL de MR y la revisión oficial todavía no
existen. No deben darse por aprobados en la plantilla. El informe de UX conserva
hallazgos pendientes, por lo que los resultados locales no se presentan como
una certificación exhaustiva de todos los criterios funcionales de inclusión.

Las capturas y el icono de ficha mejorarán su presentación, pero la
[documentación de metadata](https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/)
y la plantilla distinguen esos recursos recomendados de los textos obligatorios.
Las imágenes deben proceder de una revisión real y publicable de la aplicación;
no se modifica la etiqueta validada para añadirlas.

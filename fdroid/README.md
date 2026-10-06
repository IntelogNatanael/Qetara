# Preparación de F-Droid

`metadata/io.github.intelognatanael.qetara.yml` describe la candidata Android
1.4.2/código 9 del commit `c960afafbef5ad463e22127e979408e7a3c4d3af`.
Se presentó el 6 de octubre de 2026 en la
[MR !51433 de fdroiddata](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51433),
abierta y pendiente de revisión e inclusión. Su campo `binary`
apunta al [APK firmado publicado](https://github.com/IntelogNatanael/Qetara/releases/download/v1.4.2/Qetara-1.4.2.apk)
y `AllowedAPKSigningKeys` conserva el certificado de distribución existente.

**Estado del 6 de octubre de 2026:** las fuentes son públicas bajo la etiqueta
`v1.4.2`; un clon HTTPS sin credenciales verificó el commit y la versión. La
receta corresponde a 1.4.2/9. `readmeta`, `rewritemeta`, lint, escáner de fuentes,
build y escáner APK terminaron con código 0 en fdroidserver 2.4.5. Windows,
Linux y el ensayo F-Droid produjeron APKs sin firma idénticos; la copia pública
de firma sobre las dos reconstrucciones también coincide byte a byte con la
candidata firmada. Las evidencias nuevas y sus límites están en
[VALIDATION-1.4.2](../docs/VALIDATION-1.4.2.md). Los resultados locales no
equivalen a aceptación de F-Droid.

La verificación de identidad de GitLab ya quedó resuelta. La
[pipeline 2918682556](https://gitlab.com/carlos5alentino/fdroiddata/-/pipelines/2918682556)
ejecutó la configuración de fdroiddata en el fork, con runners de GitLab:
ocho de nueve jobs aprobaron, incluidos `fdroid build` y `check apk`. La build
reconstruyó el código 9 del commit fijado y verificó su coincidencia con el APK
público y el certificado permitido. Sólo falló `fdroid rewritemeta`, por el
salto de línea de la URL `binary`. La receta actual reproduce exactamente el
artefacto canónico de CI. La [pipeline de la corrección](https://gitlab.com/carlos5alentino/fdroiddata/-/pipelines/2919175178)
terminó con los nueve jobs obligatorios aprobados, incluidos
[build y comparación con el APK público](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16979192163),
[rewritemeta](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16979192167) y
[escáner del APK](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16979192171).
Esta CI de contribución no equivale a una build de producción ni a la
publicación en el catálogo.

La MR sigue abierta, `waiting-on-response`, tras la
[petición de linsui de explicar la diferenciación funcional](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51433#note_3965121841).
El borrador local de respuesta espera revisión del usuario y no está publicado.
La solicitud no está rechazada ni cerrada.

**Alcance histórico:** la candidata 1.4.0/código 7, del commit
`588a92f2617815b5744eeb91a1da463c5c685f90`, se preparó el 11 de septiembre
de 2026. Sus resultados y hashes se conservan en
[FDROID_VALIDATION-1.4.0](../docs/FDROID_VALIDATION-1.4.0.md), pero no acreditan
esta nueva build. El
[informe del 23 de septiembre](../docs/AUDIT_RELEASE-2026-09-23.md) distingue
el estado de fuentes, paquetes e instalaciones en esa fecha.

La receta fija el commit completo y la huella del certificado del desarrollador.
Las actualizaciones automáticas permanecen desactivadas durante la preparación
de esta candidata. Se conserva la compilación Gradle estándar del módulo `app`,
sin omitir escáneres, alterar el versionado ni retirar perfiles ART o metadata VCS.

## Validación

Antes de reutilizar la receta, desde la raíz del proyecto ejecuta:

```powershell
.\scripts\audit-release.ps1
.\scripts\audit-release.ps1 -RequireCurrentRecipe
```

El primer comando genera un inventario JSON local; el segundo devuelve código 1
si la versión o las entradas Android/build no corresponden al commit propuesto.
Es una comprobación de coherencia de fuentes y de nuestra receta de una sola
build. No compila, instala, firma, consulta dispositivos ni verifica una URL
pública. Tampoco sustituye `fdroid lint`, sus escáneres o la build aislada.

En un entorno F-Droid preparado con JDK 21, Gradle 9.1.0, plataforma Android 36
revisión 2 y Build Tools 36.0.0, coloca el YAML en `metadata/` de un checkout de
fdroiddata que incluya su configuración oficial de categorías. Ejecuta:

```sh
fdroid readmeta
fdroid rewritemeta io.github.intelognatanael.qetara
fdroid lint --force-yamllint io.github.intelognatanael.qetara
fdroid scanner --refresh --json --exit-code io.github.intelognatanael.qetara:9
fdroid build --test --no-tarball --scan-binary --stop io.github.intelognatanael.qetara:9
```

Los comandos de escáner y build preparan y limpian su propio checkout de trabajo.
No deben apuntar al directorio donde desarrollas la aplicación. `--test` mantiene
la salida en el área de prueba; no se utilizan `--force` ni `--skip-scan`.

El [informe de 1.4.2/9](../docs/VALIDATION-1.4.2.md) registra el commit,
las versiones de herramientas, las adaptaciones del entorno y los hashes
nuevos. El [estado de publicación](../docs/PUBLICATION_READINESS.md) distingue
esta revisión de la evidencia histórica. Las categorías `Connectivity`,
`File Transfer` y
`Messaging` siguen presentes en la
[configuración oficial de fdroiddata](https://gitlab.com/fdroid/fdroiddata/-/raw/master/config/categories.yml),
consultada el 6 de octubre de 2026.

## Publicación y solicitud de inclusión

El acceso público por Git HTTPS al commit fijado ya se comprobó el 6 de octubre
de 2026 mediante un clon Linux sin credenciales. El repositorio era privado al
iniciar esta ejecución y se cambió a público tras validar las fuentes. El ensayo
de compilación F-Droid utilizó una copia local de ese mismo commit como origen;
la comprobación HTTPS posterior no repitió la build. El YAML conserva la URL
HTTPS del repositorio. Las sustituciones locales de ensayos quedan fuera de la
receta pública.

El APK firmado se publicó el 6 de octubre de 2026 en la entrega
[`v1.4.2`](https://github.com/IntelogNatanael/Qetara/releases/tag/v1.4.2), junto a
`SHA256SUMS.txt`. La receta usa la URL versionada real mediante `binary` y
mantiene `AllowedAPKSigningKeys`. El archivo publicado tiene SHA-256
`841c166787e4cace65c059fe0e8deb8713ba82daa1100c32e300cf11780fe82b` y 14 884 743
bytes. Los resultados sobre la descarga pública se registran en
[VALIDATION-1.4.2](../docs/VALIDATION-1.4.2.md).

La ficha utiliza los resúmenes y descripciones Fastlane del commit fijado,
en inglés y español; la receta no los duplica ni anula mediante `Summary` o
`Description`. `AuthorName` identifica el pseudónimo público del mantenedor.
La [MR !51433](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51433)
procede del fork público `carlos5alentino/fdroiddata`, rama
`codex/qetara-1.4.2`, ahora en `b7f7f882612613a94d55dd589c946da69ea78d9b`, y
propone sólo el YAML, sin cambiar el commit de fuentes, la etiqueta ni el APK.
El YAML canónico tiene 1 797 bytes LF y SHA-256
`5377907e627663ebfe9991e4e9db3371516e18d88f75056dc34c15a0848e1657`.
El único cambio respecto del envío inicial coloca la URL escalar en la línea
siguiente a `binary:`, con el espacio final generado por el serializador de CI;
no es una lista ni cambia la URL. Se conservan las actualizaciones manuales
durante la primera inclusión.
El [registro de envío](SUBMISSION-1.4.2.md) conserva los primeros intentos de CI
bloqueados, la ejecución posterior y la corrección de formato. La revisión y
publicación en F-Droid siguen pendientes.

La URL del APK y la reproducción de su firma corresponden a la opción elegida
de conservar la clave del desarrollador. F-Droid también admite compilaciones
firmadas por F-Droid; la reproducibilidad no es un requisito general de
inclusión. Cambiar la clave afecta a la continuidad de actualizaciones Android
y no se ha adoptado aquí esa alternativa.

## Cerrar una nueva candidata

1. Cierra los cambios de Android y PC con una versión común y un código Android
   superior al de los APK ya distribuidos. Registra el commit real de las fuentes.
2. Compila el APK desde un checkout limpio siguiendo
   [RELEASING](../docs/RELEASING.md). Repite pruebas, lint, revisión de licencias
   y escáneres sobre esa revisión; conserva las evidencias nuevas por separado.
3. Cuando exista ese commit, sustituye el bloque de build de esta propuesta y
   `CurrentVersion`/`CurrentVersionCode` por los valores reales. Retira del YAML
   los hashes históricos que ya no describan el APK propuesto. Ejecuta el guard
   y los comandos F-Droid en un checkout aislado.
4. Comprueba el acceso anónimo a las fuentes publicadas. Cuando se autorice
   además publicar el APK firmado, registra su URL versionada real. Configura
   `binary`/`Binaries` y comprueba la descarga, el certificado y la copia de
   firma desde la build F-Droid.
5. Somete la receta a la CI y revisión de fdroiddata. El resultado local no
   acredita la aceptación ni publicación oficial.

La ficha editable está en `fastlane/metadata/android/en-US` y
`fastlane/metadata/android/es-ES`, con nombre, resumen y descripción. Las capturas
y el icono de tienda deben corresponder a la candidata final y conservar la
geometría de la marca. Las notas del código 9 están en `changelogs/9.txt` de ambos
idiomas. Las capturas y el icono de tienda siguen pendientes; no se presentan
capturas históricas como imágenes del APK nuevo.

Las actualizaciones continúan manuales durante la preparación. Si después se
habilitan por tags estables, hay que configurar `UpdateCheckData` para extraer
`qetaraVersionCode` y `qetaraVersion` de `gradle.properties`: F-Droid no ejecuta
Gradle para resolver esas propiedades. El patrón debe excluir tags RC y no
debe anunciar una actualización hasta que sus fuentes estén publicadas.

La revisión de inclusión, el escáner y la build del entorno oficial F-Droid
siguen siendo procesos independientes del ensayo local. Consulta las
[instrucciones oficiales de envío](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
y la [referencia de metadata](https://f-droid.org/docs/Build_Metadata_Reference/).

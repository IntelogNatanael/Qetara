# Preparación de F-Droid

## 1.4.5 / código 12

La fuente final G de 1.4.5 es el commit
`93cad15dc89d4a9ae4c9ac24a286e3dc3a4ae1a0`, árbol
`e1f921a271dedea774888989e7d4a602541b7b6b`. Al corte del 31 de octubre de
2026 UTC se publicó la release estable y se verificaron su tag, el ZIP de
fuentes, el APK firmado y las sumas mediante descarga anónima. La comparación
del APK público con las reconstrucciones Linux y F-Droid local aprobó en ambas.
[Release estable v1.4.5](https://github.com/IntelogNatanael/Qetara/releases/tag/v1.4.5).

S/T tienen APK y pruebas propios. U quedó fijada en
`5627d698701f817e28a759c1832c0c64204d4731`, árbol
`20db65e8fde681d48508de79102d5988607bac60`, con seis capturas T y las
correcciones de Chat/recibidos. Sus tests JVM, lint y build limpia Windows
aprobaron. Las dos capturas de Descargas obtenidas en U se revisaron y copiaron
idénticas a Fastlane. Los metadatos de tienda en `v1.4.5` incluyen cuatro imágenes
por idioma, tres de T y una de U. El
[informe de validación](../docs/VALIDATION-1.4.5.md) distingue esos resultados
de las reconstrucciones E/A/C anteriores. El diff U→G sólo contiene las dos
imágenes de Descargas. Windows, Linux y la build local F-Droid de G produjeron
los mismos 3 736 684 bytes sin firma. El escaneo de fuentes, el de APK y el
guard de cambios exactos aprobaron. La copia de firma sobre las reconstrucciones
Linux y F-Droid reprodujo el APK firmado de 3 778 967 bytes en ambos casos.
El informe de validación registra los SHA-256 y el certificado.

La receta canónica de G ocupa 859 bytes, SHA-256
`570dd3051d1baf12d9911a60c78c947c079e360a069a51790c3414cdbff23cf2`.
fdroidserver 2.4.5 aprobó la lectura, la normalización, el lint y una segunda
normalización estable. El único cambio de contenido respecto de la receta
archivada en G sustituye el SHA de E por el SHA de G en la build.

La copia registrada de la metadata oficial declara **1.4.4/código 11**, commit
`e379ccc9519514ef3f091821b333502c954cae54`, con `AutoUpdateMode: Version`,
`UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$` y lectura de la versión
desde `gradle.properties`. Antes de publicar, la consulta de GitHub había
identificado `main` en `9436eddefa3eae607e59cc4266190700fbc4f332`. La etiqueta
anotada `v1.4.5` publicada después identifica las fuentes G.

Después de publicar la etiqueta, fdroidserver 2.4.5 detectó 1.4.5/12 al
primer intento con `checkupdates --auto` en una copia aislada de la receta
oficial. Conservó la build 11, añadió la 12 con el commit G y actualizó los
dos campos de versión. Se verificaron el árbol G y el resto de la metadata.
`lint --force-yamllint` terminó con código 0. El SHA-256 de esa receta con
historial es `1ce48bd84ff65ae404289b5c151e488c87399c0d89d6713ec03ba38899d3c53a`.

Al 31 de octubre de 2026 a las 04:56:30 UTC, la receta oficial en
`fdroiddata` master (`19d306d6815e424f80262be09d5e571401a12d29`) y el catálogo
público indicaban **1.4.4/código 11**. La prueba aislada mantuvo intacta la
receta de origen. Siguen pendientes la actualización de la metadata oficial
a 1.4.5, su build y la publicación en el índice.
[Metadata oficial en el corte](https://gitlab.com/fdroid/fdroiddata/-/blob/19d306d6815e424f80262be09d5e571401a12d29/metadata/io.github.intelognatanael.qetara.yml) · [Ficha F-Droid](https://f-droid.org/packages/io.github.intelognatanael.qetara/).

## Historial local E/F de 1.4.5

Las fuentes del cierre anterior quedaron fijadas en el commit **E**
`29f6874615b271aad6b5184d1dad5aa406a389fa`, árbol
`009fd2154d0599ec8731cfcafcd74f89562233ee`. La
receta pública preparada en F apuntó a E, en una actualización posterior
limitada a documentación y metadata. La receta incluida dentro de E aún fijaba A;
la actualización documental F se incorpora por separado y no cambia la
identidad de las fuentes del APK.

Con fdroidserver 2.4.5 aprobaron `readmeta`, `rewritemeta`,
`lint --force-yamllint` y una segunda normalización estable, todos con código 0.
La receta canónica ocupa **859 bytes**, SHA-256
`6e2c377e74d88343b13c526b561211f0928ac0d0dfb1987c10b742aa05447380`.
Conserva `Repo` HTTPS, `Binaries`, el certificado permitido y la actualización
por etiquetas. El overlay local separado cambia únicamente `Repo` y omite
`Binaries` para el ensayo previo a publicación; no se incorpora al YAML público.

Windows, Linux y el ensayo local F-Droid de E produjeron APK sin firma idénticos
byte a byte. El escáner de fuentes con `--refresh` y el comando de build con
R8 y escaneo binario terminaron con código 0. El guard de E declaró antes la
limpieza de firma generada por la función instalada y la retirada de tres
wrappers; después comprobó bytes, rutas y modos Git exactos, sin otros cambios.
Ese guard aprobó. El fallo del guard original de A y su auditoría posterior
se conservan en el historial, separados de E.

Las copias de firma desde Linux y F-Droid reprodujeron byte a byte el APK
firmado de E, con apksigcopier y `fdroidserver.common.verify_apks`; aprobaron
certificado, firmas v2/v3 y alineación de 16 KiB. E/F se cerró antes de la
publicación de G. El [informe de la entrega](../docs/VALIDATION-1.4.5.md) y el
[procedimiento de cierre](../docs/RELEASE-CLOSURE-1.4.5.md) detallan la evidencia
y los límites de cada paso.

La [validación de 1.4.4](../docs/VALIDATION-1.4.4.md) registra la entrega
anterior y su CI; los párrafos siguientes conservan el historial de 1.4.3
y 1.4.2. Esas observaciones describen el estado del catálogo en sus fechas.

## Historial de la publicación 1.4.3

**Estado al 7 de octubre de 2026:** la receta local y la MR !51433 proponen
**1.4.3/código 10**,
commit `ee4c0aea8110f4ff8d11aa701cd93e1c7b3a3489`, con R8, `Binaries` global y
sin `MaintainerNotes`. Su metadata pasó lectura, normalización estable y lint.
Las builds Windows, Linux y F-Droid produjeron APK sin firma y `mapping.txt`
idénticos. El ensayo local fdroidserver 2.4.5 aprobó build y escáneres; sus
diez advertencias TTF por permisos DrvFs se revisaron contra los blobs Git.
La copia de firma sobre Linux/F-Droid reproduce el APK firmado exacto. El
APK optimizado aprobó 15 comprobaciones de contratos y el lote por interfaz
real, con alcance de transporte ADB/USB y loopback. La
[release estable GitHub 1.4.3](https://github.com/IntelogNatanael/Qetara/releases/tag/v1.4.3),
fuentes/etiqueta, APK y sumas se publicaron el 7 de octubre. La comprobación
anónima pública y la reproducción del APK descargado aprobaron; también la
[CI GitHub del tag, 4/4 jobs](https://github.com/IntelogNatanael/Qetara/actions/runs/37698052755).
La MR !51433 y su descripción ya están actualizadas a 1.4.3 con el
[commit de metadata `45acfb7b4cda9da3026615d9534cbeb437dd5e65`](https://gitlab.com/carlos5alentino/fdroiddata/-/commit/45acfb7b4cda9da3026615d9534cbeb437dd5e65).
Se verificaron la autoría UNSA, el diff limitado al YAML de Qetara y la receta
pública idéntica a la validada: 715 bytes, SHA-256
`0582224ea231060bb53723f0714754185d9032c8ce575d7ffbc7fe952bdeeef4`.
La [pipeline 2924351724](https://gitlab.com/carlos5alentino/fdroiddata/-/pipelines/2924351724)
aprobó los nueve jobs obligatorios en 6 min 29 s sobre el nuevo commit.
La [build](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/17015490814)
reconstruyó las fuentes fijadas con R8 y aprobó la comparación y firma con el
APK público. El [escáner del APK](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/17015490822)
aprobó y reconoció R8 9.0.32, modo release/full; desapareció el aviso de
ausencia de marcador. Los cuatro avisos menores sobre permisos son los
mismos de 1.4.2. En aquel corte del 7 de octubre, la revisión del mantenedor
y la inclusión en F-Droid seguían pendientes. Esa CI correspondía al fork
de contribución.
La [validación de 1.4.3](../docs/VALIDATION-1.4.3.md) separa cada resultado y
pendiente. La URL derivada de `Binaries` apunta a la release ya publicada;
su comprobación desde descarga pública se registra separadamente.

## Historial de la publicación 1.4.2

Los párrafos siguientes conservan el estado del 6 de octubre de 2026; sus
hashes y pipelines no acreditan la versión 1.4.3.

La receta presentada originalmente describía Android 1.4.2/código 9 del commit
`c960afafbef5ad463e22127e979408e7a3c4d3af`.
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
salto de línea de la URL `binary`. La receta corregida de 1.4.2 reproducía exactamente el
artefacto canónico de CI. La [pipeline de la corrección](https://gitlab.com/carlos5alentino/fdroiddata/-/pipelines/2919175178)
terminó con los nueve jobs obligatorios aprobados, incluidos
[build y comparación con el APK público](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16979192163),
[rewritemeta](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16979192167) y
[escáner del APK](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/16979192171).
Esta CI de contribución no equivale a una build de producción ni a la
publicación en el catálogo.

El 6 de octubre la MR seguía abierta tras la
[petición de linsui de explicar la diferenciación funcional](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51433#note_3965121841).
La respuesta enviada por el usuario por correo quedó reflejada en el
[comentario de GitLab del 6 de octubre, 15:02 de Lima](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51433#note_3965764686).
La entrega se confirmó en `reply-delivery-confirmed.json`; no se publicó un
comentario duplicado. La solicitud permanecía abierta y pendiente de revisión.

**Alcance histórico:** la candidata 1.4.0/código 7, del commit
`588a92f2617815b5744eeb91a1da463c5c685f90`, se preparó el 11 de septiembre
de 2026. Sus resultados y hashes se conservan en
[FDROID_VALIDATION-1.4.0](../docs/FDROID_VALIDATION-1.4.0.md), pero no acreditan
esta nueva build. El
[informe del 23 de septiembre](../docs/AUDIT_RELEASE-2026-09-23.md) distingue
el estado de fuentes, paquetes e instalaciones en esa fecha.

La receta fija el commit completo y la huella del certificado del desarrollador.
Desde la receta de 1.4.4, `AutoUpdateMode: Version` y `UpdateCheckMode` filtran
etiquetas de versión; `UpdateCheckData` lee las propiedades de Gradle.
Se conserva la compilación Gradle estándar del módulo `app`,
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
qetara_app=io.github.intelognatanael.qetara
qetara_code='<versionCode de la receta preparada>'
fdroid readmeta
fdroid rewritemeta "$qetara_app"
fdroid lint --force-yamllint "$qetara_app"
fdroid scanner --refresh --json --exit-code "$qetara_app:$qetara_code"
fdroid build --test --no-tarball --scan-binary --stop "$qetara_app:$qetara_code"
```

Sustituye el marcador de código por el `versionCode` de la receta que realmente
se va a comprobar; no uses el código de una candidata con una receta anterior.
Los comandos de escáner y build preparan y limpian su propio checkout de trabajo.
No deben apuntar al directorio donde desarrollas la aplicación. `--test` mantiene
la salida en el área de prueba; no se utilizan `--force` ni `--skip-scan`.

El [informe de 1.4.3/10](../docs/VALIDATION-1.4.3.md) registra el commit,
las versiones de herramientas, las adaptaciones del entorno y los hashes
nuevos. El [estado de publicación](../docs/PUBLICATION_READINESS.md) distingue
esta revisión de la evidencia histórica. Las categorías `Connectivity`,
`File Transfer` y
`Messaging` siguen presentes en la
[configuración oficial de fdroiddata](https://gitlab.com/fdroid/fdroiddata/-/raw/master/config/categories.yml),
consultada el 6 de octubre de 2026.

## Historial de publicación y solicitud de inclusión de 1.4.2

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
`codex/qetara-1.4.2`, entonces en `b7f7f882612613a94d55dd589c946da69ea78d9b`, y
propone sólo el YAML, sin cambiar el commit de fuentes, la etiqueta ni el APK.
El YAML canónico tiene 1 797 bytes LF y SHA-256
`5377907e627663ebfe9991e4e9db3371516e18d88f75056dc34c15a0848e1657`.
El único cambio respecto del envío inicial coloca la URL escalar en la línea
siguiente a `binary:`, con el espacio final generado por el serializador de CI;
no es una lista ni cambia la URL. Se conservan las actualizaciones manuales
durante la primera inclusión.
El [registro de envío](SUBMISSION-1.4.2.md) conserva los primeros intentos de CI
bloqueados, la ejecución posterior y la corrección de formato. En el corte
de 1.4.2 seguían pendientes la revisión y la publicación en F-Droid.

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
4. Publica las fuentes y el APK firmado dentro del alcance de la entrega;
   registra la URL versionada real y comprueba el acceso anónimo. Mantén
   `binary`/`Binaries` y verifica descarga, certificado y reproducción de firma
   desde la build F-Droid.
5. Para una app con actualización automática ya configurada, comprueba primero
   la detección de la etiqueta y revisa la metadata generada. Si requiere una
   corrección manual, limita el cambio a lo necesario y somételo al proceso
   de fdroiddata. Registra la CI y la publicación oficial por separado;
   el resultado local no acredita su aceptación ni presencia en el catálogo.

La ficha editable está en `fastlane/metadata/android/en-US` y
`fastlane/metadata/android/es-ES`, con nombre, resumen y descripción. Las capturas
y el icono de tienda deben representar el comportamiento de la candidata
final y conservar la geometría de la marca. El manifiesto debe identificar el
APK real de cada captura; una captura de una fuente provisional no se vuelve
una captura de G al incorporarla a ese commit. Las notas de 1.4.5 están en `changelogs/12.txt` de ambos
idiomas y se conservan las de versiones anteriores. La existencia de capturas
o iconos en la ficha no acredita una revisión contra el APK final; no se
presentan capturas históricas como imágenes del APK nuevo.

La receta configura `AutoUpdateMode: Version` y etiquetas estables mediante
`UpdateCheckMode`; `UpdateCheckData` extrae `qetaraVersionCode` y
`qetaraVersion` de `gradle.properties`, sin ejecutar Gradle. El patrón excluye
etiquetas RC. La etiqueta de 1.4.5 y su comprobación después de publicarla
se documentan al comienzo de este archivo.

La revisión de inclusión, el escáner y la build del entorno oficial F-Droid
siguen siendo procesos independientes del ensayo local. Consulta las
[instrucciones oficiales de envío](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
y la [referencia de metadata](https://f-droid.org/docs/Build_Metadata_Reference/).

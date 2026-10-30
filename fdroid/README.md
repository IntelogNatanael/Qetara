# Preparación de F-Droid

## Candidata local 1.4.5 / código 12

Las fuentes 1.4.5/12 quedaron fijadas en el commit **A**
`baec571b7c82b799848368f5e42027677695ae2c`, árbol
`77befa99b93aeee295b2abcc0148d608fb429fe6`. La
[receta pública preparada](metadata/io.github.intelognatanael.qetara.yml)
apunta a A y forma parte de una actualización posterior limitada a documentación
y metadata. La receta incluida dentro de A sigue siendo la histórica 1.4.4/11;
la actualización documental B se incorpora por separado y no cambia la
identidad de las fuentes del APK.

Con fdroidserver 2.4.5 aprobaron `readmeta`, `rewritemeta`,
`lint --force-yamllint` y una segunda normalización estable, todos con código 0.
La receta canónica ocupa **859 bytes**, SHA-256
`e7f43c4c0de982ffc590d64f6a4ae158106097cdb39ce9e4ee9c4c23a106e39b`.
Conserva `Repo` HTTPS, `Binaries`, el certificado permitido y la actualización
por etiquetas. El overlay local separado cambia únicamente `Repo` y omite
`Binaries` para el ensayo previo a publicación; no se incorpora al YAML público.

Windows, Linux y el ensayo local F-Droid produjeron APK sin firma idénticos
byte a byte. El escáner de fuentes con `--refresh` y el comando de build con
R8 y escaneo binario terminaron con código 0. Después falló el guard del
runner, que exigía un checkout sin modificaciones: F-Droid retiró los tres
wrappers y 11 líneas de configuración de firma. Una auditoría independiente
generó la limpieza con la función instalada, verificó el diff exacto y los
APK, y aprobó sin modificar ni recompilar las fuentes. El fallo original
permanece registrado y no se presenta como un PASS del runner.

Las copias de firma desde Linux y F-Droid reprodujeron byte a byte el APK
firmado de A, con apksigcopier y `fdroidserver.common.verify_apks`; aprobaron
certificado, firmas v2/v3 y alineación de 16 KiB. No se declara publicación de
1.4.5, etiqueta, descarga pública, CI remota ni aceptación de F-Droid. El
[informe de la candidata](../docs/VALIDATION-1.4.5.md) y el
[procedimiento de cierre](../docs/RELEASE-CLOSURE-1.4.5.md) detallan la evidencia
y los límites de cada paso.

La [validación de 1.4.4](../docs/VALIDATION-1.4.4.md) registra la entrega
anterior y su CI; los párrafos siguientes conservan el historial de 1.4.3
y 1.4.2, sin atribuir sus resultados a la nueva candidata ni describir el
estado actual del catálogo.

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
mismos de 1.4.2. La revisión del mantenedor y la inclusión en F-Droid siguen
pendientes; esta CI de contribución no acredita una build de producción o
publicación en el catálogo.
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
geometría de la marca. Las notas de 1.4.5 están en `changelogs/12.txt` de ambos
idiomas y se conservan las de versiones anteriores. La existencia de capturas
o iconos en la ficha no acredita una revisión contra el APK final; no se
presentan capturas históricas como imágenes del APK nuevo.

La receta configura `AutoUpdateMode: Version` y etiquetas estables mediante
`UpdateCheckMode`; `UpdateCheckData` extrae `qetaraVersionCode` y
`qetaraVersion` de `gradle.properties`, sin ejecutar Gradle. El patrón excluye
etiquetas RC. Preparar esa configuración no publica las fuentes ni crea una
etiqueta de 1.4.5.

La revisión de inclusión, el escáner y la build del entorno oficial F-Droid
siguen siendo procesos independientes del ensayo local. Consulta las
[instrucciones oficiales de envío](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
y la [referencia de metadata](https://f-droid.org/docs/Build_Metadata_Reference/).

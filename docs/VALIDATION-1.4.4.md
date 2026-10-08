# Validación de Qetara 1.4.4 / código 11

Estado al 8 de octubre de 2026: **publicada en GitHub; validación local,
reproducción del APK público y CI GitHub/F-Droid aprobadas**. Las fuentes corresponden a
`e379ccc9519514ef3f091821b333502c954cae54`, publicado en `main` y con la
etiqueta anotada `v1.4.4`. La
[release estable](https://github.com/IntelogNatanael/Qetara/releases/tag/v1.4.4)
se publicó el `2026-10-08T20:53:22Z`. La revisión del mantenedor y la inclusión
en el catálogo F-Droid siguen pendientes.

## Tres observaciones atendidas

- **Actualizaciones automáticas.** La receta usa `AutoUpdateMode: Version`,
  filtra etiquetas con `Tags ^v[0-9]+\.[0-9]+\.[0-9]+$` y extrae
  `qetaraVersionCode` y `qetaraVersion` de `gradle.properties`. La propuesta
  fija el SHA completo. El job remoto `checkupdates` también aprobó en la CI de contribución.
- **Icono de tienda.** Ambas fichas incluyen el mismo PNG de 512 × 512,
  exportado desde el vector existente. Conserva geometría, proporciones,
  fondo marfil `#FFF7ED` y símbolo azul marino `#102A43`.
- **Español e inglés.** Interfaz Android, notificaciones y errores disponen
  de ambos idiomas, con inglés como respaldo. Se declara la selección de
  idioma por aplicación de Android y se actualizan fichas y changelogs/11.

## Controles de cierre

| Control | Estado | Evidencia y límite |
| --- | --- | --- |
| Fuentes e identidad | PASS | Commit exacto, árbol limpio, autor y committer institucionales. |
| Build limpia Windows | PASS | Clon completo, R8, `assembleRelease --no-build-cache`; 4 min 4 s. |
| Build limpia Linux | PASS | Mismo commit, R8; 4 min 30 s. APK sin firma idéntico al de Windows. |
| Firma, alineación e instalación | PASS | Certificado esperado, firma y alineación verificadas; hash instalado igual al APK publicado. |
| Idiomas y arranque físicos | PASS, alcance limitado | APK definitivo: cinco comprobaciones Java por idioma y arranque frío correcto en 693 ms; un OPPO CPH2743, Android 16/API 36. |
| Metadatos F-Droid | PASS local y formato CI | Valores validados con `readmeta`, `rewritemeta` y `lint --force-yamllint`; formato final semánticamente idéntico, con `readmeta` y job CI `rewritemeta` aprobados. |
| Escáner de fuentes F-Droid | PASS local | Código 0, cero avisos y cero errores en el checkout final sobre sistema de archivos Linux. |
| Build y escáner APK F-Droid | PASS local | fdroidserver 2.4.5, `build --test --no-tarball --scan-binary --stop …:11`, código 0; R8 y escaneos DEX/bloques de firma completados. |
| Reproducción de la firma | PASS local | Linux y F-Droid reproducen byte a byte el APK firmado; `common.verify_apks`, firmas v2/v3 y alineación de 16 KiB aprobados. |
| Publicación y descarga | PASS | Etiqueta exacta, release estable, descarga anónima HTTP 200; APK y sumas coinciden. |
| CI GitHub de main y tag | PASS, 4/4 jobs cada una | Commit exacto: Android y desktop Ubuntu/macOS/Windows. No se atribuye un total de tests a estas CI sin sus informes. |
| Receta de contribución publicada | PASS | Commit de metadata exacto, diff limitado al YAML de Qetara; 862 bytes idénticos al formato canónico de CI; valores iguales a la receta validada. |
| CI de contribución F-Droid | PASS, 9/9 jobs | Pipeline 2928173160 sobre metadata 245c67c; ninguno con `allow_failure`. Build, comparación con APK público, firma, escáneres y actualización automática aprobados. |

Las tres builds producen el mismo APK sin firma y el mismo `mapping.txt` de
R8. No se declaran idénticos los demás diagnósticos: `configuration.txt`,
`seeds.txt` y `usage.txt` presentan diferencias registradas entre plataformas.
La build final local Windows ejecutó `assembleRelease`, incluido lint vital;
no se presenta como una nueva ejecución local de `lintRelease` completo.

Los 239 tests Android y 55 de protocolo, sin fallos, errores ni omitidos, y
el pase local de `lintRelease` corresponden al commit previo con el mismo
árbol. Son antecedentes conservados, separados de la
[CI de main](https://github.com/IntelogNatanael/Qetara/actions/runs/37841793754)
y [CI del tag v1.4.4](https://github.com/IntelogNatanael/Qetara/actions/runs/37842778198)
aprobadas. Los tests de unidades no ejecutan por sí solos el DEX optimizado.

## Identidad de los artefactos finales

| Artefacto | Bytes | SHA-256 |
| --- | ---: | --- |
| APK sin firma Windows, Linux y F-Droid | 3 695 164 | `2ec459d1165456bb717720c4319e393bf98baf1b1a48743ec9ec1d0e0abdefba` |
| `Qetara-1.4.4.apk` firmado, instalado y público | 3 738 007 | `644a303f72b1311802153566ad8fa0f6c9a3598c1a6ff8ccd12ebb14d8add518` |
| Receta pública propuesta, formato CI | 862 | `18aaed8e990b1a1d7b35cb2dded8975053715fb891dad7f9f443c689f831ae50` |
| `mapping.txt` Windows, Linux y F-Droid | 49 457 914 | `806d55c042db55be7271d2b14ba41a3fa37c1ded9f45c549da0bc58d1083d56d` |

Certificado Android SHA-256:
`5f5cdbfb03c1f62f7dc42aee12a5ea4a347b767ccfc268eb450ca69c98ba7a3b`.
El VCS incluido en el APK identifica el commit definitivo. La etiqueta anotada
tiene objeto `30ad1e7cfa03b9238fd5778673a5db50023ad98b` y resuelve a ese commit.
La [descarga versionada](https://github.com/IntelogNatanael/Qetara/releases/download/v1.4.4/Qetara-1.4.4.apk)
sin credenciales devolvió HTTP 200 y coincide con el APK firmado e instalado;
[SHA256SUMS.txt](https://github.com/IntelogNatanael/Qetara/releases/download/v1.4.4/SHA256SUMS.txt)
también coincide.

## Ejecución física: alcance por artefacto

La campaña funcional amplia utilizó el APK provisional
`8b14e601c334020a485ba9047cacbdba0cbab2a93c48c7d77fc84344eec40f27`.
Se inspeccionaron conexión, mensajes y Flash en inglés y español; también
preferencias, envío, descargas y aprobación de confianza en inglés. Flash
permaneció activo durante el cambio observado de inglés a español.

Con notificaciones y dispositivos Wi-Fi cercanos denegados se ejercitaron
arranque y LAN ordinaria. El emisor confirmó 51 bytes PC → teléfono tras
aprobar la huella; el archivo apareció en Descargas. Se verificó
el archivo recibido frente al fixture mediante SHA-256:
`58a957afe2ad236fb48de836144f5e45a919185696838ff917032e7d7348954a`.

El APK publicado `644a…d518` repitió verificación del hash instalado, arranque
en frío y cinco comprobaciones Java por idioma: locales declarados, resolución
de recursos, respaldo inglés, plurales/formato e idioma efectivo. **No se
repitieron en ese hash la campaña funcional amplia ni la captura de red.**
Los registros finales acreditan locales de aplicación `[]`, dispositivos
cercanos concedido y notificaciones denegadas, como en el estado original.

La comparación entre ambos APK firmados encontró 394 entradas ZIP comunes:
390 con contenido idéntico y sólo cuatro diferencias, en
`META-INF/version-control-info.textproto`, `META-INF/MANIFEST.MF`,
`META-INF/QETARA-D.SF` y `META-INF/QETARA-D.RSA`. No hay entradas añadidas o
retiradas; código y recursos coinciden. Esta comparación trata el contenido
descomprimido de las entradas; los bloques de firma APK v2/v3 quedan fuera.
La igualdad observada no equivale a repetir los escenarios funcionales.

Todo el ensayo físico corresponde a un único Android 16/API 36. Quedan
pendientes pruebas en API 32 o inferior y Wi-Fi Direct entre dos teléfonos.
No acredita entrega de notificaciones con permiso denegado ni todos los
flujos de transferencia.

## Observación de red acotada

Con el APK provisional `8b14…0f27`, PCAPdroid 2.0.2 configurado para Qetara
registró 1 614 paquetes y 81 358 bytes de paquetes durante unos 12 minutos,
sin identificar pares no locales bajo la clasificación proporcionada.
Predominan los sondeos del puerto 8988; el conteo no representa transferencias
completadas ni acredita captura entrante del servidor de archivos. La
observación se limita a ese intervalo y configuración, y el PCAP clásico no
prueba por sí solo la atribución al paquete Android. El informe local
`runtime-validation-candidate.json` conserva el intervalo, conteos y límites
del análisis; no se repitió la captura sobre el APK final.

## Ensayo F-Droid y contribución

La receta pública se analizó sin cambios. El ensayo local usó una copia que
sustituye `Repo` por el repositorio local y omite `Binaries`, pues se preparó
antes de publicar la referencia. No añadió exclusiones de escaneo ni usó
`--force` o `--skip-scan`. Estos ajustes no entran en el YAML publicado.
El escaneo DEX busca clases no libres conocidas y bloques extra de firma;
no es una auditoría exhaustiva del código.

La reproducción posterior usa apksigcopier 1.1.1 y fdroidserver 2.4.5 sin
acceder a la clave privada. Para F-Droid se usó como referencia el APK
descargado anónimamente; para Linux, el APK firmado idéntico. El informe de
reproducción indica `public_url_downloaded: false` porque ese script no
descarga: la descarga pública está comprobada por separado. Este ensayo
local no equivale a una build de producción del catálogo.

El [commit de metadata](https://gitlab.com/carlos5alentino/fdroiddata/-/commit/245c67c06bbd42a73ff699787db9d4590d9b1642)
publicó la normalización del YAML de Qetara sin cambiar sus valores. La
[pipeline 2928173160](https://gitlab.com/carlos5alentino/fdroiddata/-/pipelines/2928173160)
terminó el `2026-10-08T21:09:57Z` con nueve de nueve jobs aprobados sobre
`245c67c06bbd42a73ff699787db9d4590d9b1642`, sin jobs permitidos a fallar.
El [job de build](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/17043695209)
compiló el commit exacto, descargó el APK público 1.4.4, comparó ambos binarios
con éxito y aceptó el certificado esperado. El
[job de APK](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/17043695217)
aprobó los escaneos DEX y de bloques extra de firma. El
[job de actualizaciones](https://gitlab.com/carlos5alentino/fdroiddata/-/jobs/17043695210)
detectó `v1.4.4`, extrajo `1.4.4 (11)` y confirmó que la receta está actualizada.
Los restantes jobs de esquema, fuentes, herramientas, formato, lint y redirección
también aprobaron. Estos resultados pertenecen a esta versión, no a la CI de 1.4.3.

## Diagnósticos e intentos anteriores conservados

- La primera CI de contribución detectó una diferencia de formato en
  `UpdateCheckData`: fdroidserver local 2.4.5 aceptaba una línea y el escritor
  usado por CI exigía el escalar en la siguiente línea. La receta pasó de
  859 bytes (`49432c063c7a7ed0b11c585a7556fc97229962e5fb993c2166c8db118a2c4e3b`)
  a los 862 bytes finales. PyYAML y ruamel verificaron igualdad semántica
  completa, incluido el pin y las expresiones; `readmeta` aislado aprobó.
  Su aviso de apksigner ausente corresponde a ese entorno mínimo de lectura,
  no a las verificaciones de firma separadas. El primer job permanece FAIL;
  el nuevo `rewritemeta` aprobó. No se cambió el APK ni se movió la etiqueta.
- El primer APK Windows incluía `NO_VALID_GIT_FOUND`. El clon completo
  corrigió la procedencia; después se corrigió autor/committer antes de
  publicar. El commit anterior `4a4934ad8e4f1a29bbbd6d30d043a8a99a822850`
  y el definitivo comparten el árbol
  `3b072365ec511f179a394626d7c5b8b515a2ea97`, pero el SHA incrustado cambia
  los APK. Se repitieron las builds finales en directorios separados.
- El primer arnés Kotlin falló al resolver `kotlin.jvm.internal.Intrinsics`.
  Su fallo se conserva; el posterior arnés Java aprobado no lo transforma
  retroactivamente en éxito.
- El escáner anterior dio diez avisos sobre TTF. Cada archivo tenía modo Git
  `100644`, formato TrueType y tablas válidas, bytes idénticos al blob y
  hashes de procedencia local; los textos OFL 1.1 están presentes. DrvFs es
  una explicación compatible con los avisos según la
  [conversión de permisos documentada por Microsoft](https://learn.microsoft.com/en-us/windows/wsl/file-permissions),
  sin afirmar que se midieran sus opciones de montaje. La ejecución final
  sobre sistema de archivos Linux conserva las fuentes y da cero avisos.

Se conservan como antecedentes el APK sin firma anterior
`e0c0066213643f5e45f2596b07d8f6b18d24a3bafe8354ae615b0052e7deaf3f`
y el firmado intermedio
`e84eebbd47fd54326ea2fb95c4b51c77833454a46f2011af6ffcc3f1f354dcf7`.
La comprobación intermedia de idiomas y arranque de 609 ms corresponde a
ese segundo hash, sustituido antes de publicar.

## Pendientes de contribución

- [x] Completar la CI GitLab y revisar los nueve jobs, incluida la comparación
  remota con el APK público, los escáneres y la detección de actualizaciones.
- [ ] Obtener revisión del mantenedor e inclusión efectiva en F-Droid.

Los registros originales e informes históricos permanecen conservados; los
resúmenes finales registran su evidencia por hash. La CI desktop no implica
publicación o instalación de nuevos paquetes PC.

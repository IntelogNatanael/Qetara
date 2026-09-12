# Validación local de la receta F-Droid — Qetara 1.4.0

El 11 de septiembre de 2026 se validó la
[receta propuesta](../fdroid/metadata/io.github.intelognatanael.qetara.yml) para
`io.github.intelognatanael.qetara`, versión 1.4.0/código 7, con fdroidserver
2.4.5 en Ubuntu WSL aislado. **La lectura, el formato, lint, el escáner de
fuentes, la build y el escáner del APK terminaron correctamente.** El APK
producido por `fdroid build` reprodujo exactamente la candidata firmada mediante
copia de su firma pública.

Las fuentes de la candidata son exclusivamente el commit
`588a92f2617815b5744eeb91a1da463c5c685f90`. Los cambios documentales o de pruebas
posteriores del repositorio no formaron parte de esta build.

## Resultados

| Comprobación | Resultado |
|---|---|
| `fdroid readmeta` | Código 0 |
| `fdroid rewritemeta` | Código 0; formato aplicado al YAML propuesto |
| `fdroid lint --force-yamllint` | Código 0 |
| Escáner de fuentes, firmas actualizadas | Código 0; cero errores y cero advertencias |
| `fdroid build --test --scan-binary --stop` | Código 0; una build correcta |
| Escáner del APK | Sin hallazgos de clases no libres conocidas ni bloques de firma rechazados |
| Comparación del APK sin firma | Idéntico a las builds limpias Windows y Linux |
| Copia estándar de firma con apksigcopier 1.1.1 | Código 0; firma verificada y APK idéntico a la candidata |

| Artefacto | SHA-256 |
|---|---|
| APK sin firma generado por F-Droid | `9e1744b2820b1931af675f33c01ec803c2ce0604f5b3373be69cd742194ee9bf` |
| APK firmado reproducido | `5d9e28651ffdaf0216d0a82c049b287851906065bbc713eefa77d793670e22f6` |
| Certificado del desarrollador | `5f5cdbfb03c1f62f7dc42aee12a5ea4a347b767ccfc268eb450ca69c98ba7a3b` |

El APK sin firma ocupa 13.973.063 bytes. Se verificaron su identificador, versión
y metadata VCS. No se modificaron DEX, perfiles ART o recursos para conseguir la
coincidencia. La firma original usó apksigner 36 con
`--alignment-preserved true`, como explica el
[informe de reproducibilidad](REPRODUCIBILITY-1.4.0.md).

## Herramientas y ejecución

Se instaló fdroidserver 2.4.5 desde su distribución oficial PyPI en un entorno
Python 3.12 aislado. El archivo fuente descargado tiene SHA-256
`f9b52646264c732678e32e37e23a995db20cc61d45622dda5830ce23255547f4`.
La instalación conservó un informe de versiones, URLs y hashes de las
dependencias de las herramientas. No se instalaron paquetes globales ni se
modificaron dependencias de Qetara.

Lint utilizó la configuración oficial de categorías de fdroiddata del commit
`82feaead52000b34c02d3761328504eef8cc7b5d`. El escáner descargó la base SUSS
de F-Droid mediante `--refresh`; la instantánea usada contiene 104 definiciones.
El resumen de evidencia conserva sus hashes y timestamps.

El entorno de build usó OpenJDK 21.0.10, Gradle 9.1.0, plataforma Android 36
revisión 2 y Build Tools 36.0.0. Se reutilizó el SDK aislado y una caché separada
de artefactos de dependencias. No se copiaron resultados previos de tareas del
proyecto. `GRADLE_OPTS` fijó dos workers, desactivó el daemon persistente y las
cachés de tareas y configuración.

Estos fueron los comandos de validación, ejecutados dentro del área F-Droid:

```sh
fdroid readmeta
fdroid rewritemeta io.github.intelognatanael.qetara
fdroid lint --force-yamllint io.github.intelognatanael.qetara
fdroid scanner --refresh --json --exit-code io.github.intelognatanael.qetara:7
fdroid build --test --no-tarball --scan-binary --stop --no-refresh io.github.intelognatanael.qetara:7
```

No se utilizó `--force`, `--skip-scan` ni `novcheck`. `--no-tarball` solo evitó
generar el archivo adicional de fuentes; no omitió la compilación o el escáner.
`--no-refresh` de la build evitó volver a consultar el origen Git local ya
preparado; las firmas del escáner se habían actualizado en el paso anterior.

## Adaptaciones locales

El YAML público conserva el origen HTTPS de GitHub. Solo en la copia de metadata
del ensayo se sustituyó `Repo` por un origen Git local que contenía el commit
fijado. F-Droid creó y limpió su propio checkout; no compiló el directorio de
desarrollo ni la revisión HEAD posterior.

El `config.yml` aislado seleccionó el SDK y el ejecutable verificado de Gradle
9.1.0. La ruta predeterminada `gradlew-fdroid` del paquete Python instalado no
estaba disponible. Esta configuración permitió ejecutar la receta con Gradle
9.1.0; no acredita por sí sola la selección de herramientas del contenedor
oficial.

F-Droid generó sus `local.properties`, retiró la configuración de firma y
eliminó los wrappers de Gradle antes de compilar con la herramienta configurada.
Son cambios de preparación realizados por fdroidserver sobre su checkout
aislado; el APK resultante mantuvo la igualdad binaria comprobada.

## Pendientes para inclusión

La validación local está completada. La publicación sigue requiriendo fuentes
accesibles públicamente: la URL de GitHub respondió HTTP 404 a una consulta
anónima durante el ensayo. No se cambió la visibilidad del repositorio.

También falta una URL pública versionada del APK firmado. La receta fija
`AllowedAPKSigningKeys`, pero deja pendiente `binary`/`Binaries` hasta disponer
de una dirección real. Ese campo y una nueva comprobación de descarga/firma son
necesarios para proponer distribución con la clave del desarrollador.

No se ejecutó el build-server oficial ni su pipeline CI. Tampoco se presentó una
solicitud, publicó una release o cambió el repositorio remoto. El éxito de los
escáneres no reemplaza la revisión humana de licencias y fuentes ni reconstruye
cada dependencia o compilador. La inclusión sigue sujeta a la revisión de
F-Droid.

Las instrucciones empleadas proceden de la
[guía oficial de envío](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/),
la [referencia de metadata](https://f-droid.org/docs/Build_Metadata_Reference/),
la [guía de compilación local](https://f-droid.org/docs/Building_Applications/)
y la [distribución oficial de fdroidserver](https://pypi.org/project/fdroidserver/2.4.5/).

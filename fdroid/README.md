# Preparación de F-Droid

`metadata/io.github.intelognatanael.qetara.yml` describe la candidata Android
1.4.0/código 7 del commit `588a92f2617815b5744eeb91a1da463c5c685f90`.
Es una propuesta para revisión; no ha sido enviada a F-Droid ni publica nada.

**Alcance histórico:** esa candidata se preparó el 11 de septiembre de 2026.
Hay cambios de aplicación posteriores en el repositorio; la receta y sus hashes
no representan automáticamente las fuentes actuales. El
[informe del 23 de septiembre](../docs/AUDIT_RELEASE-2026-09-23.md) distingue
fuentes, paquetes e instalaciones. No se ha sustituido el commit por una
referencia futura ni se atribuye la validación anterior al código nuevo.

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
fdroid scanner --refresh --json --exit-code io.github.intelognatanael.qetara:7
fdroid build --test --no-tarball --scan-binary --stop io.github.intelognatanael.qetara:7
```

Los comandos de escáner y build preparan y limpian su propio checkout de trabajo.
No deben apuntar al directorio donde desarrollas la aplicación. `--test` mantiene
la salida en el área de prueba; no se utilizan `--force` ni `--skip-scan`.

Los resultados y las adaptaciones del ensayo local se documentan en
[`docs/FDROID_VALIDATION-1.4.0.md`](../docs/FDROID_VALIDATION-1.4.0.md).

## Antes de solicitar inclusión

El repositorio de fuentes debe ser accesible públicamente por HTTPS sin
autenticación. La consulta autenticada del 23 de septiembre sigue indicando
`PRIVATE`. El ensayo histórico usó una copia local del mismo commit como origen.
El YAML propuesto
conserva la dirección HTTPS prevista; el origen local no forma parte de la
receta pública.

La candidata firmada tampoco tiene todavía una URL pública versionada.
Para distribuir con la clave del desarrollador, debe añadirse esa URL real
mediante `binary` o `Binaries` y repetirse su verificación. La huella
`AllowedAPKSigningKeys` ya está fijada. No se ha inventado una dirección de
descarga ni se ha declarado que el APK esté publicado.

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
4. Publica las fuentes y la URL versionada real del APK firmado cuando se
   autorice la publicación. Configura `binary`/`Binaries` y comprueba la descarga,
   el certificado y la copia de firma desde la build F-Droid.
5. Somete la receta a la CI y revisión de fdroiddata. El resultado local no
   acredita la aceptación ni publicación oficial.

La ficha editable está en `fastlane/metadata/android/en-US` y
`fastlane/metadata/android/es-ES`, con nombre, resumen y descripción. Las capturas
y el icono de tienda deben corresponder a la candidata final y conservar la
geometría de la marca. Esos recursos y las notas por código de versión siguen
pendientes; no se presentan capturas históricas como imágenes del APK nuevo.

Las actualizaciones continúan manuales durante la preparación. Si después se
habilitan por tags estables, hay que configurar `UpdateCheckData` para extraer
`qetaraVersionCode` y `qetaraVersion` de `gradle.properties`: F-Droid no ejecuta
Gradle para resolver esas propiedades. El patrón debe excluir tags RC y no
debe anunciar una actualización hasta que sus fuentes estén publicadas.

La revisión de inclusión, el escáner y la build del entorno oficial F-Droid
siguen siendo procesos independientes del ensayo local. Consulta las
[instrucciones oficiales de envío](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
y la [referencia de metadata](https://f-droid.org/docs/Build_Metadata_Reference/).

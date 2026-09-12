# Propuesta de receta F-Droid

`metadata/io.github.intelognatanael.qetara.yml` describe la candidata Android
1.4.0/código 7 del commit `588a92f2617815b5744eeb91a1da463c5c685f90`.
Es una propuesta para revisión; no ha sido enviada a F-Droid ni publica nada.

La receta fija el commit completo y la huella del certificado del desarrollador.
Las actualizaciones automáticas permanecen desactivadas durante la preparación
de esta candidata. Se conserva la compilación Gradle estándar del módulo `app`,
sin omitir escáneres, alterar el versionado ni retirar perfiles ART o metadata VCS.

## Validación

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
autenticación. Durante esta validación sigue siendo privado, por lo que el
ensayo usa una copia local del mismo commit como origen. El YAML propuesto
conserva la dirección HTTPS prevista; el origen local no forma parte de la
receta pública.

La candidata firmada tampoco tiene todavía una URL pública versionada.
Para distribuir con la clave del desarrollador, debe añadirse esa URL real
mediante `binary` o `Binaries` y repetirse su verificación. La huella
`AllowedAPKSigningKeys` ya está fijada. No se ha inventado una dirección de
descarga ni se ha declarado que el APK esté publicado.

La revisión de inclusión, el escáner y la build del entorno oficial F-Droid
siguen siendo procesos independientes del ensayo local. Consulta las
[instrucciones oficiales de envío](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
y la [referencia de metadata](https://f-droid.org/docs/Build_Metadata_Reference/).

# Reproducibilidad Android de Qetara 1.4.0

Verificación local completada el 11 de septiembre de 2026 sobre el commit
`588a92f2617815b5744eeb91a1da463c5c685f90`, identificador
`io.github.intelognatanael.qetara`, versión 1.4.0 y código 7.

Dos checkouts limpios independientes, Windows y Ubuntu Linux, produjeron APKs sin
firma **idénticos byte a byte**. Al copiar únicamente la firma pública del APK
Windows al resultado Linux con apksigcopier 1.1.1, apksigner verificó los esquemas
v2 y v3. El APK reproducido también resultó **idéntico byte a byte al firmado**.
La reconstrucción Linux no accedió a la clave privada.

| Evidencia | SHA-256 |
|---|---|
| APK sin firma, Windows y Linux | `9e1744b2820b1931af675f33c01ec803c2ce0604f5b3373be69cd742194ee9bf` |
| APK firmado y reproducido | `5d9e28651ffdaf0216d0a82c049b287851906065bbc713eefa77d793670e22f6` |
| Certificado de firma | `5f5cdbfb03c1f62f7dc42aee12a5ea4a347b767ccfc268eb450ca69c98ba7a3b` |

El APK sin firma ocupa 13.973.063 bytes; el firmado, 14.020.330 bytes. El
certificado coincide con el utilizado en la distribución anterior. El nuevo
identificador constituye otra aplicación Android aunque conserve esa clave.

## Entornos y compilación

| Componente | Windows | Ubuntu Linux |
|---|---|---|
| JDK | Temurin 21.0.12.1 | OpenJDK 21.0.10+7-Ubuntu-124.04 |
| Gradle | 9.1.0 | 9.1.0 |
| Android Gradle Plugin | 9.0.0 | 9.0.0 |
| Plugin Kotlin Compose | 2.2.10 | 2.2.10 |
| Plataforma Android | API 36, revisión 2, extensión 17 | API 36, revisión 2, extensión 17 |
| Android Build Tools | 36.0.0 | 36.0.0 |
| Resultado | 51 tareas ejecutadas, 2 min 40 s | 51 tareas ejecutadas, 4 min 46 s |

Se usaron checkouts limpios del commit indicado, sin resultados previos del
proyecto. El checkout Linux no contenía `.local`, `local.properties` ni otros
archivos ignorados del entorno Windows. En Linux se ejecutó:

```sh
./gradlew :app:assembleRelease --no-daemon --no-build-cache --no-configuration-cache --max-workers=2 --console=plain
```

En Windows se ejecutó el siguiente comando; `<clean-checkout>` sustituye la
ruta local del clon limpio:

```text
gradlew.bat --project-dir <clean-checkout> :app:assembleRelease --offline --no-build-cache --no-daemon --max-workers=2 --console=plain
```

La caché de configuración estaba desactivada por defecto en Windows. Para
repetir la receta se recomienda explicitar `--no-configuration-cache`, como se
hizo en Linux; esa opción no formó parte del comando Windows ejecutado.
Deshabilitar la caché de tareas no equivale a prescindir de la caché de dependencias: Linux
reutilizó una instantánea separada de artefactos de dependencias y la distribución
Gradle fijada. No reutilizó transformaciones ni resultados de tareas Windows.
Los componentes Linux faltantes se obtuvieron de los repositorios públicos
configurados. Build Tools se descargó de Google y se contrastó su checksum con
el manifest oficial. No se instaló ningún paquete del sistema.

El SDK compartió la plataforma independiente del sistema operativo y sus
licencias. SHA-256 de `android.jar`:
`d9eb9da824d9e247a352f570f01e1169e725b2954bca9e283a71786c59b59f9a`.
No se instaló NDK en el entorno aislado. Las cuatro bibliotecas nativas
`libandroidx.graphics.path.so` conservaron los bytes de su AAR y la alineación
ELF PT_LOAD de 16 KiB. La comprobación `zipalign -c -P 16 4` pasó también sobre
el APK firmado reproducido. El perfil ART y la metadata VCS permanecieron
incluidos y coincidieron entre ambos sistemas.

## Firma y comprobación

El APK ya alineado se firmó fuera de Gradle con apksigner de Build Tools 36.0.0
y **`--alignment-preserved true`**. La clave y las contraseñas solo estuvieron
disponibles en el proceso breve de firma. Esta plantilla usa variables del
proceso y evita contraseñas literales en argumentos:

```sh
apksigner sign --alignment-preserved true --v4-signing-enabled false \
  --ks "$QETARA_SIGNING_STORE" --ks-key-alias "$QETARA_SIGNING_ALIAS" \
  --ks-pass env:QETARA_SIGNING_STORE_PASSWORD \
  --key-pass env:QETARA_SIGNING_KEY_PASSWORD \
  --out signed.apk unsigned.apk
```

La comprobación Linux usó apksigcopier 1.1.1, con click 8.1.8, instalado en un
entorno Python aislado desde wheels oficiales cuyos SHA-256 fueron verificados.
El modo estándar bastó; no se modificaron el DEX, los perfiles ni el padding del
APK Linux para obtener la coincidencia:

```sh
apksigcopier copy signed.apk unsigned-linux.apk reproduced.apk
apksigner verify --verbose --print-certs reproduced.apk
zipalign -c -P 16 4 reproduced.apk
sha256sum signed.apk reproduced.apk
```

Los tres comandos de copia/verificación/alineación terminaron con código cero
y ambos hashes fueron el SHA-256 firmado indicado arriba.

## Diferencias resueltas

La primera build Windows se ejecutó sobre un checkout de trabajo con resultados
incrementales anteriores. Su clase Kotlin `P2pScreenRouteKt` retenía una consulta
Compose al campo `P2pScreenRouteEffectsInput.$stable` que la compilación Linux
limpia no emitió. Esto afectaba a `classes3.dex` y su perfil ART. Reconstruir
Windows desde un clon limpio produjo el mismo `.class` y APK que Linux,
sin cambiar fuentes ni desactivar perfiles.

Después, la firma predeterminada de apksigner 36 reemplazaba el padding ZIP y
fallaba la copia de firma aun con APKs sin firma idénticos. La opción
`--alignment-preserved true` conservó la alineación existente y resolvió la
verificación. Es una solución documentada por el propio
[proyecto apksigcopier](https://github.com/obfusk/apksigcopier#what-about-signatures-made-by-apksigner-from-build-tools--3500-rc1).
Se conservaron las versiones actuales de las herramientas utilizadas en esta
prueba; no fue necesario instalar apksigner 34.

## Alcance

Esta prueba verifica dos reconstrucciones de la aplicación sobre el commit y
las herramientas indicados. No acredita la reconstrucción desde fuentes de cada
dependencia transitiva, compilador o biblioteca nativa. La procedencia y las
licencias de AndroidX graphics-path se documentan por separado en
[`licenses/androidx-graphics-path-1.0.1-PROVENANCE.md`](../licenses/androidx-graphics-path-1.0.1-PROVENANCE.md).

Después se completó la [validación local de la receta F-Droid](FDROID_VALIDATION-1.4.0.md)
con fdroidserver 2.4.5: lectura, formato, lint, escáneres de fuentes y APK y
build pasaron. El APK de esa build coincide con el unsigned indicado arriba y
su copia estándar de firma reproduce la misma candidata. El ensayo usó un
origen Git local del commit fijado y herramientas configuradas localmente.
No se ejecutó el servidor o CI oficial de F-Droid; siguen pendientes el acceso
público a las fuentes y al APK y la revisión de inclusión. La igualdad local y
la copia de firma no acreditan aprobación de F-Droid. Su procedimiento está descrito en la
[documentación oficial de reproducibilidad](https://f-droid.org/docs/Reproducible_Builds/).

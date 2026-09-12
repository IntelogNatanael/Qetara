# Validación de instalación MSI 1.4.0

El 11 de septiembre de 2026 se instaló y comprobó en Windows el MSI de la candidata `1.4.0-rc.1`. La instalación silenciosa terminó con código **0**, sin solicitar reinicio, y el ejecutable instalado pasó su autoprueba de transferencia y reanudación.

## Artefacto e instalación

| Dato | Resultado |
| --- | --- |
| Archivo | `Windows/Qetara-1.4.0.msi` de la candidata |
| Tamaño | 55 565 118 bytes |
| SHA-256 | `05245a66781441811e22ec2572508f194492e90a31f8e7230e483d9d99ee5fe5` |
| ProductCode | `{B9239E4C-5124-3E81-895E-508302CC80C3}` |
| UpgradeCode | `{042CE66B-D923-4B77-A354-F4DCC6EA5244}` |
| Versión MSI, registrada y del ejecutable | `1.4.0` |
| Destino | `%LOCALAPPDATA%\Qetara\Qetara.exe` |
| Contexto Windows Installer | `UserUnmanaged` — valor 2 de `MsiEnumProductsEx`, instalación por usuario |
| Estado del producto instalado | 5, instalación local registrada |
| Fin de la instalación | `2026-09-11T13:59:48Z` |

Se comprobó el hash antes de instalar y se consultaron las tablas Property, Directory, Upgrade y CustomAction del MSI. La carpeta de destino no existía. El registro de la instalación efectiva confirmó `ProductState=-1` antes de instalar y no identificó productos relacionados para actualizar. No se desinstalaron versiones manualmente ni se modificaron datos de otras aplicaciones.

La instalación se ejecutó con `msiexec /i <MSI> /qn /norestart /L*V <registro>`. Se verificó el producto desde la misma cuenta que instaló el MSI: el entorno aislado de herramientas utiliza otro HKCU, por lo que su consulta inicial no basta para comprobar el registro del usuario real. Windows Installer confirmó después el contexto por usuario y la versión 1.4.0. Los accesos directos del escritorio y del menú Inicio apuntan al ejecutable instalado.

El MSI presenta estado Authenticode `NotSigned`. El hash identifica el archivo comprobado; esta validación no acredita una firma de editor Windows.

## Archivos, runtime y avisos

Se encontraron **190 archivos instalados**, el mismo número de filas de la tabla File del MSI. Se comparó su contenido con `Qetara-1.4.0-windows-portable.zip` de la misma candidata:

- Los **149 archivos del runtime**, incluidos los **50 archivos de `runtime/legal/`**, coinciden por SHA-256 con el portable. `runtime/release` declara `JAVA_VERSION="21.0.12.1"` y los nueve módulos de la imagen Java incluida.
- El ejecutable y el JAR principal coinciden por SHA-256. El JAR principal contiene los 12 avisos e inventarios esperados bajo `open_source_licenses/`, incluidos los textos de licencia y atribución de Qetara, Apache, Circum, Noto, GitHub, Noise, Skiko/Skia y la procedencia de graphics-path.
- En la comparación directa de las 190 rutas del portable, 187 coinciden exactamente. Las diferencias se explican por el empaquetado: `.jpackage.xml` no forma parte del MSI, la instalación incorpora `app/.package` con el texto `Qetara`, y cambia el nombre del JAR auxiliar de Skiko y su referencia en `Qetara.cfg`.
- Ese JAR auxiliar mide 293 bytes y conserva el mismo `META-INF/MANIFEST.MF`. La comparación binaria encontró únicamente cuatro bytes distintos en campos de fecha/hora ZIP; sus entradas no cambiaron de contenido. La biblioteca nativa de Skiko coincide con la del portable.

Los fuentes de OpenJDK/Temurin acompañan la candidata en `Fuentes/Qetara-third-party-source/`. No se instalan dentro de la carpeta de la aplicación; deben conservarse en el conjunto que redistribuye el runtime, conforme al proceso descrito en [RELEASING.md](RELEASING.md).

## Autoprueba del ejecutable instalado

Se lanzó el `Qetara.exe` instalado en modo oculto, con `--no-gui --self-test --no-receiver --no-interactive`, credenciales de prueba y una carpeta exclusiva bajo `.local/publication-audit/msi-validation/selftest/`. El estado temporal y los archivos de prueba quedaron en esa carpeta; la autoprueba no utiliza la identidad habitual del usuario.

El proceso terminó con código **0** y confirmó ambos casos sobre TCP local, puerto 49889:

1. Envío completo de un archivo de **2 097 409 bytes** y comprobación de hash.
2. Reanudación desde un parcial del mismo archivo y nueva comprobación de hash.

Una comprobación externa al programa confirmó que el archivo de origen y el recibido tras reanudar tienen el mismo SHA-256:

```text
015a57e8d3a1cb00eedee7e0a1d89924b1c00f38c977741b680eda264309105e
```

Esta autoprueba comprueba el lanzador instalado, sus dependencias y la transferencia local. No sustituye la revisión visual, una prueba de actualización/desinstalación, ni las pruebas Flash entre dispositivos descritas en [FLASH_SOCKET_QA.md](FLASH_SOCKET_QA.md).

## Evidencias locales

La carpeta `.local/publication-audit/msi-validation/` conserva el preflight, el registro detallado de msiexec, el contexto y producto registrados, los hashes de archivos y avisos, la comparación binaria de Skiko y los registros/resultados de la autoprueba. Es evidencia local: contiene rutas de la máquina y estado de prueba y no debe incluirse íntegra en una distribución pública. Este documento recoge los resultados necesarios sin copiar esos datos privados.

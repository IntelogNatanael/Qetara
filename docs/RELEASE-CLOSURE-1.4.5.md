# Procedimiento y estado del cierre de Qetara 1.4.5 / código 12

Este documento reúne el procedimiento, los criterios y el estado del cierre
local. Los resultados, hashes y límites de cada comprobación se registran en
el [informe público de la candidata](VALIDATION-1.4.5.md).
El informe interno de la campaña anterior y sus originales se conservan
como evidencia privada local; no forman parte de los archivos publicados.
La publicación queda fuera de esta preparación.

## Fuentes fijadas y alcance del cierre

El 30 de octubre de 2026 se fijó el commit de fuentes **A**,
`baec571b7c82b799848368f5e42027677695ae2c`, árbol
`77befa99b93aeee295b2abcc0148d608fb429fe6`: 49 rutas revisadas, versión
1.4.5/código 12 y árbol seguido limpio al confirmar. El informe privado quedó
sin seguimiento. Los ensayos v9 son anteriores a la creación de A; no se
presentan como builds de un clon limpio de ese commit.

La receta pública preparada para A aprobó lectura, normalización estable y
lint con fdroidserver 2.4.5. Windows, Linux y F-Droid produjeron tres APK sin
firma idénticos byte a byte, SHA-256
`a39a9dc9a336dfa224be297b64f6f843ec8f5e0e48d418ad1c8ce36ac15a0962`.
Las reproducciones de firma sobre Linux y F-Droid coincidieron con el APK
firmado de A, SHA-256
`d449c2a7f5edb725e9095928c88a608d027722ff76e04be6bdc8890c16f933b6`,
con firmas v2/v3 y alineación de 16 KiB verificadas.

El comando de build F-Droid terminó con código 0; el guard posterior del
runner falló porque exigía un checkout sin cambios seguidos. Una auditoría
independiente aprobó la transformación exacta de F-Droid: retirada de tres
wrappers y limpieza de 11 líneas de firma mediante la función instalada,
sin otras modificaciones. Se conservaron el fallo y los originales. El
informe distingue esos tres resultados; no declara un PASS del runner original.

El APK firmado se instaló conservando los datos y aprobó una repetición física
acotada: cinco archivos Flash PC → Android con hashes correctos, texto Unicode
exacto en el XML receptor, texto de regreso confirmado visualmente y un archivo
Compartir Android → PC con hash y estado terminal correctos. El informe
precisa qué se midió en cada recorrido y separa los ensayos debug anteriores.
F-01 y Wi-Fi Direct con un segundo teléfono siguen pendientes.

Esta actualización documental y de receta se incorpora por separado de A,
en una revisión **B** limitada a esos archivos.
Los APK, su metadata VCS y la futura etiqueta de la entrega deben identificar
A. Construir desde B produciría otra identidad VCS aunque el código no
cambiara, y exigiría nueva evidencia para esos APK.

El alcance del commit de fuentes incluye la implementación Android, escritorio y
protocolo, sus recursos bilingües, las pruebas nuevas y modificadas,
`gradle.properties` con 1.4.5/12, el changelog y las notas de tienda
`fastlane/metadata/android/{es-ES,en-US}/changelogs/12.txt`. Los documentos de
validación que entren en Git deben conservar la identidad de sus artefactos
y explicar qué evidencia sólo existe localmente.

No deben entrar salidas `build/`, cachés `.gradle/` o `.kotlin/`, APK, runtimes,
`local.properties`, material de firma, capturas y logs privados de `.local/`,
volcados de portapapeles, direcciones de la LAN ni identificadores del teléfono.
Los fixtures sintéticos reutilizables pertenecen a las pruebas únicamente
cuando sean necesarios para ejecutarlas, sin copiar una sesión privada.

Antes de confirmar cualquier actualización, revisar `git diff HEAD`, los archivos sin seguimiento y
el diff preparado completo. Los estados `MM` requieren especial atención:
la copia preparada puede ser anterior a la que se probó. No basta revisar
sólo `git diff --cached` si después se preparan cambios adicionales.

Para cada nuevo commit de fuentes, registrar su SHA completo y
`git rev-parse 'HEAD^{tree}'`, comprobar que el árbol esté limpio y repetir
las comprobaciones afectadas.
El APK construido previamente en un árbol modificado conserva su identidad
histórica: un commit posterior no lo convierte en un APK de ese commit.
Una modificación del SHA, aunque conserve los mismos archivos de compilación,
puede cambiar `META-INF/version-control-info.textproto` y el hash del APK.

## Comprobaciones y evidencia

| Control | Evidencia necesaria para declararlo completado |
| --- | --- |
| Fuentes fijadas | Commit y árbol completos; estado limpio; inventario de archivos preparado revisado. |
| Tests y compilación afectados | Comando, código de salida, XML con fallos/errores/omitidas y distinción entre tareas ejecutadas, `UP-TO-DATE` y `FROM-CACHE`. |
| Lint release | Informe de la ejecución identificada; número de errores y advertencias, sin equiparar build satisfactoria con cero advertencias. |
| Windows limpio | Nuevo clon del commit, APK sin firma, `output-metadata.json`, log, `provenance.json`, SHA-256 y metadata VCS correcta. |
| Linux limpio | Otro clon nuevo del mismo commit sobre sistema de archivos Linux; versiones y argumentos; APK sin firma y SHA-256 comparado con Windows. |
| Ensayo F-Droid | Receta pinneada al commit, ajustes locales explícitos, lectura/formato/lint, escáneres y build con códigos de salida y artefacto identificado. |
| Firma y reproducción | APK firmado del commit, certificado permitido, verificación de firma/alineación y copia de firma sobre cada reconstrucción que se declare reproducible. |
| Recorrido físico final | Identidad del APK realmente instalado; alcance repetido y alcance heredado, separados. No deducir una nueva prueba GUI de la igualdad de recursos. |
| Diseño | Revisión registrada contra el archivo y cuenta Figma autorizados, o limitación explícita con referencia local conservada. |
| Entrega pública | Permanece pendiente: etiqueta, fuentes públicas, APK/sumas, descarga anónima y CI pertenecientes al commit final. |

Si una herramienta modifica el checkout durante su preparación normal, un
guard estricto puede fallar después de una build correcta. Conservar ambos
resultados. Antes de aceptar el artefacto, generar la transformación esperada
con la herramienta y versión identificadas y compararla exactamente con el
diff completo. No restaurar el checkout, ampliar una lista de excepciones ni
omitir comprobaciones para hacer pasar el guard. En este cierre, la auditoría
se escribió en una carpeta nueva, no recompiló y verificó la conservación de
las entradas originales antes y después de reproducir las firmas.

El detalle funcional pendiente, incluido cualquier hallazgo de severidad
menor, pertenece al informe de validación. Preparar esta lista no lo resuelve.

## Reconstrucción con las herramientas existentes

La inspección de sólo lectura del 30 de octubre de 2026 encontró Ubuntu
24.04.3 en WSL, OpenJDK 21.0.10+7, fdroidserver 2.4.5 y apksigcopier 1.1.1.
No se ejecutaron builds como parte de esa inspección. El preflight posterior
aprobó esas herramientas. Sus ubicaciones concretas y los registros completos
se conservan en la evidencia privada; estos son los recursos reutilizados:

| Recurso | Versión o alcance comprobado |
| --- | --- |
| SDK Android y Build Tools | Plataforma 36 revisión 2; Build Tools 36.0.0. |
| fdroidserver | 2.4.5, entorno Python existente. |
| apksigcopier CLI | 1.1.1, entorno existente separado. |
| Caché de Gradle Linux | Caché previa dedicada, alrededor de 1,1 GiB. |
| Caché de Gradle F-Droid | Otra caché previa dedicada, alrededor de 890 MiB. |

El wrapper del proyecto fija Gradle 9.1.0 y su SHA-256. El `android.jar`
comprobado en WSL tiene SHA-256
`d9eb9da824d9e247a352f570f01e1169e725b2954bca9e283a71786c59b59f9a`.
No se encontraron `init.d`, `init.gradle`, `init.gradle.kts` ni
`gradle.properties` en los dos primeros niveles de esas dos cachés. Esto
no demuestra ausencia de cualquier configuración externa: registrar el
entorno realmente utilizado antes de compilar, sin mostrar secretos.
Las ubicaciones temporales se deben comprobar de nuevo antes de reutilizarlas.

1. Compilar Windows con `scripts/build-android-release.ps1 -Ref <SHA-completo>
   -Offline -MaxWorkers 2`, usando JDK 21, el SDK existente y ninguna variable
   `QETARA_SIGNING_*`. El script crea un clon completo nuevo, comprueba el árbol
   y ejecuta `assembleRelease --no-build-cache`. Conservar el log de cualquier
   intento fallido; retirar `-Offline` sólo si faltan dependencias y se decide
   descargarlas desde los repositorios configurados.
2. Crear un clon nuevo del mismo SHA en una ruta exclusiva del sistema de
   archivos Linux, sin copiar `build/`, `.gradle/`, `.kotlin/` ni configuraciones
   locales. Usar el SDK anterior, `GRADLE_USER_HOME` de Linux y la secuencia de
   [compilación limpia](RELEASING.md#apk-android-desde-un-commit-limpio), con
   `--no-build-cache --no-configuration-cache --no-daemon --max-workers=2`.
   Si se usa `--offline`, comprobar que realmente termina: la existencia de
   una caché no garantiza que estén todas las dependencias de esta revisión.
3. Guardar APK, logs, metadata VCS, versiones de herramientas y hashes de
   los diagnósticos R8. Comparar primero los APK sin firma y `mapping.txt`.
   Registrar por separado cualquier diferencia en `configuration.txt`,
   `seeds.txt` o `usage.txt`, sin declarar idénticos todos los diagnósticos.
4. Preparar el ensayo F-Droid en otro directorio nuevo Linux, con su caché
   existente propia. Mantener los escáneres activos; no usar `--force`,
   `--skip-scan` ni exclusiones nuevas para obtener un resultado favorable.
   Ejecutar lectura, normalización y lint sobre la receta preparada; después
   escaneo de fuentes y `build --test --no-tarball --scan-binary --stop` para
   `io.github.intelognatanael.qetara:12`. Registrar versiones/hashes/fechas de
   bases de escaneo y de la configuración de categorías. Una caché antigua
   no acredita una base actualizada.

Reutilizar SDK, wrapper y dependencias evita duplicar gigabytes y descargar
de nuevo las mismas entradas. Los proyectos y sus salidas sí se reconstruyen
desde directorios nuevos. La descripción correcta del resultado, si ambos
compilan y coinciden, es «dos reconstrucciones limpias, Windows y Linux, con
cachés de dependencias reutilizadas». No son dos entornos aprovisionados
independientemente desde cero. Dos ejecuciones sobre el mismo directorio o
una tarea `UP-TO-DATE` no constituyen una segunda reconstrucción limpia.

En la inspección quedaban unos 31,8 GB decimales libres en la unidad C:.
WSL informó mucho más espacio lógico disponible en su disco virtual; esa
cifra no acredita espacio físico libre adicional en el host. Ejecutar las
builds secuencialmente y medir el espacio real antes de cada una. Conservar
artefactos y logs; no borrar evidencias o cachés históricas como parte implícita
de esta preparación. Si se libera una salida descartable, verificar su ruta
absoluta y la conservación previa de la evidencia necesaria.

## Receta, firma y publicación posterior

La receta almacenada dentro de A es la histórica 1.4.4/11. La
[receta pública preparada para la actualización posterior](../fdroid/metadata/io.github.intelognatanael.qetara.yml)
fija el SHA completo de A y 1.4.5/12. Conserva el origen HTTPS, `Binaries`,
el certificado permitido y la configuración de actualización por etiquetas.
Su versión canónica validada ocupa 859 bytes, SHA-256
`e7f43c4c0de982ffc590d64f6a4ae158106097cdb39ce9e4ee9c4c23a106e39b`.
Los únicos cambios semánticos frente a la receta de A son `versionName`,
`versionCode`, `Builds[0].commit`, `CurrentVersion` y `CurrentVersionCode`.
La lectura, normalización repetida y lint aprobaron con código 0. También
aprobaron los comandos de escáner y build F-Droid; la condición estricta del
runner y la auditoría posterior conservan los estados separados indicados arriba.

No se puede incluir en un commit una receta que contenga el SHA de ese mismo
commit. Por eso se fijó primero A y se preparó después la receta que apunta
a A. Si se elige otro commit como fuente definitiva, repetir las builds y
la comparación con esa identidad. El guard
`audit-release.ps1 -RequireCurrentRecipe` comprueba coherencia de fuentes y
metadata; no demuestra que la URL declarada esté publicada.

Antes de publicar, el ensayo local puede sustituir sólo `Repo` por el origen
Git local y omitir `Binaries` en una copia de la receta. Guardar el diff y
hash de ambas recetas; esos ajustes no entran en el YAML público. Ese ensayo
no descarga ni compara por sí solo un APK público inexistente.

Firmar únicamente el APK del clon limpio elegido, conservando la alineación
y el certificado existente, según [Firma Android](RELEASING.md#firma-android).
Para la reproducción, la copia de firma usa el APK firmado como referencia
y no requiere la clave privada. Registrar la comparación de apksigcopier,
`fdroidserver.common.verify_apks`, la firma, alineación y SHA-256 resultante.
Un hash de la candidata anterior no sustituye estas operaciones.

La evidencia pública futura debe poder revisarse desde un clon ordinario.
Los enlaces `../../` a una carpeta de trabajo y los enlaces a `build/` no son
artefactos publicados. Mantener los originales privados; preparar un resumen
sanitizado con commit, comandos, códigos de salida, conteos, hashes, tamaños
y límites, y un manifiesto de los logs/artefactos que realmente se adjunten.
No atribuir evidencia pública a un archivo que sólo existe en la máquina.

Después de una publicación autorizada, comprobar por separado que la etiqueta
resuelve al commit elegido, que el APK y las sumas se descargan sin credenciales,
que su identidad y certificado coinciden y que se reproduce ese APK descargado.
La CI remota y la inclusión en F-Droid se informan sólo cuando existan evidencias
de esas ejecuciones; los resultados de 1.4.4 no acreditan 1.4.5.

# Procedimiento y estado del cierre de Qetara 1.4.5 / código 12

Este documento reúne el procedimiento, los criterios y el estado del cierre
de la entrega. Los resultados, hashes y límites de cada comprobación se
registran en el [informe de validación](VALIDATION-1.4.5.md).
El informe interno de la campaña anterior y sus originales se conservan
como evidencia privada local; no forman parte de los archivos publicados.
La release estable se publicó el 31 de octubre de 2026 a las 04:55:34 UTC.
[Qetara v1.4.5 en GitHub](https://github.com/IntelogNatanael/Qetara/releases/tag/v1.4.5).

## Estado actual y secuencia de cierre

Al 31 de octubre de 2026 UTC, la fuente final G es el commit
`93cad15dc89d4a9ae4c9ac24a286e3dc3a4ae1a0`, árbol
`e1f921a271dedea774888989e7d4a602541b7b6b`. La CI del push a la rama de
validación de G aprobó sus cuatro trabajos en el primer intento. [CI de la rama G](https://github.com/IntelogNatanael/Qetara/actions/runs/41169629256).

La build Windows de G terminó en 3 min 11 s, con R8 y las 50 tareas
ejecutadas. La comparación de los APK sin firma de U y G encontró 390 entradas
descomprimidas idénticas de 391. Sólo cambió la metadata del commit. El
informe separa esta comparación de la comprobación directa de interfaz en G.
Las builds limpias de Windows, Linux y F-Droid local produjeron APK sin firma
idénticos en una comparación directa de sus 3 736 684 bytes. Los escáneres de
fuentes y APK y el guard de cambios exactos de F-Droid aprobaron. La copia de
firma sobre las reconstrucciones de Linux y F-Droid reprodujo en ambos casos
los 3 778 967 bytes del firmado, con firmas v2/v3 y alineación de 16 KiB.

U es el commit `5627d698701f817e28a759c1832c0c64204d4731`, árbol
`20db65e8fde681d48508de79102d5988607bac60`. Su release Windows limpio aprobó
con R8 y 50/50 tareas; el APK firmado U verificó certificado, v2/v3 y alineación.
Los recorridos anteriores de T conservan su propia procedencia.
Sus seis capturas aprobadas de Flash/Chat/lote, tres por idioma, se copiaron
idénticas a Fastlane y forman parte de U. Las dos capturas de Descargas
obtenidas en U también se revisaron y copiaron. El conjunto comprende cuatro
imágenes por idioma.
U aprobó 49 suites y 298 tests JVM, incluidos sus diez casos nuevos, con cero
fallos/errores/omisiones; lint debug/release 0/46 y 0/47. F-01 tiene un
recorrido positivo real en T, con 16 MiB
recibidos íntegros, adjunto conservado y estado listo sin reconfirmación. E
reprodujo la invalidación, pero su recepción usó otro PIN y no completó el
handshake; no se declara comparación PSK simétrica.

1. Se verificó el diff U→G. Sólo incorpora las dos capturas de Descargas.
   Los XML y lint de U acreditan las mismas entradas de aplicación y pruebas.
2. Los metadatos de tienda en `v1.4.5` incluyen ocho imágenes revisadas. Las dos de
   Descargas muestran la biblioteca de U con cinco documentos en cada idioma.
   El ensayo verificó sus bytes y los de dos mensajes por receptor, y comprobó
   la biblioteca tras relanzar la app.
3. G quedó fijada con la identidad de arriba y conserva los avisos de corte
   histórico. El informe registra por separado el alcance real de F-01.
4. Las tres reconstrucciones y las dos reproducciones de firma de G
   aprobaron. El informe registra los hashes de cada artefacto.
5. La CI remota de rama aprobó 4/4 antes de crear la etiqueta. En un
   emulador se instaló el firmado G sobre U y se verificaron el arranque,
   los cinco documentos recibidos y los mensajes anteriores.
6. La etiqueta anotada, el APK, el archivo de sumas y el ZIP se comprobaron
   por acceso anónimo. El ZIP corresponde al árbol G y apksigcopier aprobó
   la comparación del APK descargado con las dos reconstrucciones.

7. Sobre una copia aislada de la metadata oficial, `checkupdates --auto`
   conservó la build 11, añadió 1.4.5/código 12 con las fuentes G y aprobó
   lint. A las 04:56:30 UTC del 31 de octubre, fdroiddata y el catálogo
   seguían en 1.4.4/código 11. [Ficha oficial](https://f-droid.org/packages/io.github.intelognatanael.qetara/).

La actualización documental H conserva el tag en G y añade la receta que
fija esas fuentes. La detección de F-Droid y su distribución oficial se
registran por separado en el informe. Los comandos siguientes describen los
controles reutilizables.

## Antecedente E/F y alcance de las fuentes

Las fuentes del cierre anterior son el commit **E**,
`29f6874615b271aad6b5184d1dad5aa406a389fa`, árbol
`009fd2154d0599ec8731cfcafcd74f89562233ee`, hijo de C
`877eb96f2d71f827a95f3c8df80198879e60b738`. E declaraba 1.4.5/código 12 y
no se publicó. E corrige la orden de detener descubrimiento;
C había corregido la actualización del estado P2P al iniciar y reanudar.
Los 266 tests JVM Android aprobaron sobre el árbol de trabajo del contenido
fijado en E; las builds release se hicieron desde clones nuevos. El análisis
lint de E se ejecutó, con informe reutilizado: cero errores y 44 advertencias.

Windows, Linux y F-Droid produjeron tres APK sin firma idénticos byte a byte,
SHA-256 `530d589e725b336d4c0530de92eb847f445886a9dbe8aef224b5982261b9a79e`.
Las reproducciones Linux/F-Droid coincidieron con el APK firmado de E,
SHA-256 `12cd3270c28bea5dfff2375907e9688dcf0e3458fd2645afacbc248c847e7fab`,
con certificado, firmas v2/v3 y alineación de 16 KiB verificados.

El runner de E declaró la transformación F-Droid esperada antes del escaneo
y la build. Generó la retirada de firma con la función instalada en memoria
y exigió exactamente esos bytes y la eliminación de los tres wrappers, sin
otros cambios seguidos. Escáneres, build y guard exacto aprobaron. El fallo
del guard de A y su auditoría posterior se conservan como historial; tampoco
se oculta el defecto físico encontrado en C antes de fijar E.

Con el APK E comprobado en ambos teléfonos, Android API 36 y API 33
completaron Chat en ambos sentidos y dos lotes de cinco archivos, con diez
SHA-256 recibidos correctos. Sockets y contadores apoyan el transporte por
el grupo Wi-Fi Direct, manteniendo OPPO como propietario y HONOR como cliente.
No hubo PCAP ni aislamiento del router y hubo WLAN concurrente para control
ADB. También aprobaron detener/reiniciar búsqueda, Home/reanudar, conservación
de preparación y reconexión con sesión renovada. El informe delimita cada
resultado. En aquella ronda E, F-01 seguía pendiente. Flash sobre P2P y la
inversión de roles del grupo quedaron fuera de esa ronda física. El cierre dejó ambos teléfonos sin grupo
ni descubrimiento, con sesiones cerradas y las preferencias finales registradas.

La actualización documental y de receta **F**, commit
`ecf0854218145d9b095114282a0fec3971042c59`, se incorporó por separado de E.
Los APK históricos y su metadata VCS identifican E. Esa regla de identidad
también se aplica a esta entrega. La etiqueta estable resuelve a G. Compilar F o H produciría otra
identidad VCS aunque sólo cambiasen documentos.

## Criterios para revisar fuentes y evidencias

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
| Recorridos y capturas | Identidad del APK realmente instalado; separar teléfonos físicos, emuladores y arneses sintéticos, así como alcance repetido y heredado. Registrar la procedencia de cada captura; la igualdad de recursos no demuestra una nueva prueba GUI. |
| Diseño | Revisión registrada contra el archivo y cuenta Figma autorizados, o limitación explícita con referencia local conservada. |
| Entrega pública | Etiqueta resuelta al commit final, fuentes, APK y sumas descargados sin credenciales, reproducciones del APK descargado y CI identificada por revisión. |

Si una herramienta modifica el checkout durante su preparación normal, un
guard estricto puede fallar después de una build correcta. Conservar ambos
resultados. Antes de aceptar el artefacto, generar la transformación esperada
con la herramienta y versión identificadas y compararla exactamente con el
diff completo. No restaurar el checkout, ampliar una lista de excepciones ni
omitir comprobaciones para hacer pasar el guard. En el cierre A, la auditoría
se escribió en una carpeta nueva, no recompiló y verificó la conservación de
las entradas originales antes y después de reproducir las firmas.
En E, el guard se declaró antes de compilar y aprobó en la misma ejecución,
conservando la expectativa y el diff observado como evidencias separadas.

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

En la inspección inicial del 30 de octubre quedaban unos 31,8 GB decimales
libres en C:; esa cifra es histórica y no describe el espacio disponible ahora.
WSL informó mucho más espacio lógico disponible en su disco virtual; esa
cifra no acredita espacio físico libre adicional en el host. Ejecutar las
builds secuencialmente y medir el espacio real antes de cada una. Conservar
artefactos y logs; no borrar evidencias o cachés históricas como parte implícita
de esta preparación. Si se libera una salida descartable, verificar su ruta
absoluta y la conservación previa de la evidencia necesaria.

## Receta, firma y publicación posterior

La receta canónica de G se preparó para H y conserva `Repo` HTTPS y `Binaries`.
Su SHA-256 es `570dd3051d1baf12d9911a60c78c947c079e360a069a51790c3414cdbff23cf2`.
Se verificaron lectura, normalización, lint y segunda normalización estable.
La copia de ensayo utiliza un origen local y conserva su evidencia por separado.

Como antecedente, la receta almacenada dentro de E fijaba A. La receta
preparada en F fijó el SHA completo de E y 1.4.5/12. Conserva el origen HTTPS, `Binaries`,
el certificado permitido y la configuración de actualización por etiquetas.
Su versión canónica validada ocupa 859 bytes, SHA-256
`6e2c377e74d88343b13c526b561211f0928ac0d0dfb1987c10b742aa05447380`.
El único cambio semántico frente a la receta incluida en E es `Builds[0].commit`.
La lectura, normalización repetida y lint aprobaron con código 0. También
aprobaron los comandos de escáner y build F-Droid y el guard exacto predeclarado.

No se puede incluir en un commit una receta que contenga el SHA de ese mismo
commit. E/F siguió esa secuencia. Para G/H, las builds y la comparación de G
figuran en el informe de validación. El guard
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

La evidencia pública se documenta para su revisión desde un clon ordinario.
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

La receta oficial 1.4.4/11 configuraba `AutoUpdateMode: Version`, etiquetas
estables y lectura de Gradle. La prueba del actualizador conservó su historial.
La receta de una sola build incluida en este repositorio fija G y se validó
por separado. La incorporación de 1.4.5 al repositorio y al catálogo F-Droid
corresponde a su proceso oficial. [Estado de distribución](VALIDATION-1.4.5.md#publicación-y-f-droid-al-corte).

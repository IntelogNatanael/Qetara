# Validación de Qetara 1.4.5 / código 12

**Estado al 30 de octubre de 2026: candidata local, no publicada.** Las fuentes
1.4.5/12 quedaron fijadas en el commit A
`baec571b7c82b799848368f5e42027677695ae2c`. Aprobaron los recorridos descritos
de Flash, Chat normal y Compartir por LAN, con sus versiones de ensayo
diferenciadas. Los XML conservados registran 426 pruebas JVM aprobadas y tres
omitidas; Chat v9 aprobó 48 comprobaciones y su revisión visual indicada abajo.
Las reconstrucciones Windows, Linux y F-Droid de A produjeron APK sin firma
idénticos byte a byte; Linux y F-Droid reprodujeron el APK firmado exacto.
El comando de build F-Droid aprobó; su runner falló después por cambios
estándar de preparación y una auditoría independiente validó exactamente
esas transformaciones. El fallo original permanece registrado. El APK release
R8 firmado de A se instaló conservando datos y aprobó los recorridos acotados
de Flash y Compartir descritos abajo. Los APK anteriores conservan su propio
alcance. F-01 y Wi-Fi Direct siguen abiertos; no se ha publicado 1.4.5.

Este informe es un resumen público autocontenido. Los originales de los ensayos
—manifiestos, registros, XML y capturas— se conservan como evidencia
local privada y no se distribuyen con este documento. Los hashes permiten
identificar los artefactos y registros conservados; no hacen que esos archivos
estén disponibles públicamente. El [procedimiento de cierre](RELEASE-CLOSURE-1.4.5.md)
define la evidencia que debe añadirse para cada nueva comprobación.

## Identidad de las fuentes y estado del cierre

El commit de fuentes **A** es
`baec571b7c82b799848368f5e42027677695ae2c`, árbol
`77befa99b93aeee295b2abcc0148d608fb429fe6`. Incluye 49 rutas revisadas y declara
`qetaraVersion=1.4.5` y `qetaraVersionCode=12` en
[gradle.properties](../gradle.properties). Al fijarlo, el árbol de archivos
seguidos estaba limpio; el informe privado quedó sin seguimiento y fuera del
commit. El registro local de fijación tiene SHA-256
`85366747b9bdc96be5b507bb74358a10886d2823667a097333ad1f960913430c`.

Este documento y la receta que apunta a A se incorporan por separado de A,
en una actualización **B** limitada a documentación y metadata. Los APK del cierre se
compilaron explícitamente desde A y registran A en su metadata VCS; la revisión
posterior de este informe no sustituye esa identidad. La receta contenida
dentro de A sigue siendo la histórica 1.4.4/11;
la [receta preparada para 1.4.5/12](../fdroid/metadata/io.github.intelognatanael.qetara.yml)
fija A desde esta actualización posterior.

La campaña anterior partió del commit
`9436eddefa3eae607e59cc4266190700fbc4f332` y declaró `qetaraVersion=1.4.5` y
`qetaraVersionCode=12` en [gradle.properties](../gradle.properties). Sus cambios
no tenían un commit propio durante la campaña física. Se conservó un
manifiesto de 285 archivos de fuentes, SHA-256
`7682aa99c1beae3b19ab2c6d2d4c64541e5b93a539c7c11c6e9936f3e2933b00`.
Ese inventario identifica la instantánea ensayada y no sustituye un commit.

Los pases v6 y v9 descritos abajo precedieron a la creación de A. No se
presentan como compilaciones de un clon limpio de A ni se les trasladan los
hashes de la campaña anterior.

| Control del nuevo cierre | Estado de este corte documental |
| --- | --- |
| Commit definitivo de fuentes y árbol limpio | A y su árbol identificados arriba; 49 rutas revisadas y árbol seguido limpio al fijarlo. |
| Repetición de pruebas JVM afectadas | 429 registradas: 426 aprobadas, tres omitidas; cero fallos/errores. Android volvió a ejecutarse en v9, antes de crear A. |
| Debug, instrumentación y lint del cierre | Pase v9 terminado correctamente antes de A; lint release conservado con cero errores y 44 advertencias. La compilación de instrumentación no sustituye sus recorridos. |
| APK Windows desde un clon nuevo de A | Aprobado en 3 min 19 s: 50 tareas ejecutadas de 50. APK sin firma, procedencia, hash y metadata VCS de A comprobados. |
| Reconstrucción Linux y comparación con Windows | Aprobada: 150,0 s del comando; Gradle registra 2 min 29 s y 50 tareas ejecutadas. APK idéntico byte a byte a Windows. |
| Receta pública 1.4.5/12 fijada a A | Lectura, normalización estable y lint aprobados; receta canónica y hash registrados abajo. |
| Build y escáneres F-Droid de A | Comandos aprobados. El guard posterior del runner falló; la auditoría independiente de las cuatro transformaciones exactas aprobó y preservó el fallo original. APK idéntico a Windows/Linux. |
| Firma e instalación del APK de A | Firmas v2/v3 y alineación de 16 KiB aprobadas; instalado como actualización conservando los datos. |
| Reproducción de firma sobre Linux/F-Droid | Ambas aprobadas con apksigcopier y `fdroidserver.common.verify_apks`; copias idénticas al APK firmado de A. |
| Recorridos físicos del release R8 de A | Flash: cinco archivos PC → Android con SHA-256 correctos, texto Unicode exacto en el XML receptor y texto de regreso confirmado visualmente. Compartir: un archivo Android → PC con SHA-256 correcto y estado terminal completado. Límites detallados abajo. |
| Revisión contra Figma | Referencias autorizadas Flash 4:22 y Chat 22:24; revisión acotada de consentimiento y estructura de conversación, no de todas las pantallas. |
| Publicación, descarga anónima y CI remota | No realizadas para esta candidata. |

## Comprobaciones anteriores a la fijación de A

### Chat normal y Compartir por LAN

El Chat normal completó mensajes en ambas direcciones entre Android y PC.
Android utilizó el APK release R8 anterior, SHA-256
`823321931da5643bae9ba68d855496533054a54087e4829d047363d696c68252`.
La respuesta Unicode preparada en PC se conservó en el borrador tras un
intento fallido con la VPN activa. El usuario desconectó la VPN y el reintento
terminó correctamente. Se registra la secuencia observada, sin atribuir el
fallo a una regla de red concreta ni afirmar una comparación byte a byte de
ese mensaje. Este resultado no corresponde al APK debug posterior.

Compartir normal completó **cinco archivos Android → PC y cinco PC → Android**,
con los diez SHA-256 recibidos coincidentes. Utilizó estos artefactos locales:

| Artefacto del ensayo Compartir | Bytes | SHA-256 |
| --- | ---: | --- |
| APK Android debug de ensayo v4, firmado | 24 119 073 | `167ce381021652722369a689adb49fb76e923e5282f3da2c446a177a5a0472d8` |
| JAR escritorio 1.4.5 | 1 733 562 | `8e0f72af6a0769b57bb30fac387bfd4cc38001435339a9b79bbb34c3a3536b11` |
| JAR protocolo 1.4.5 | 114 897 | `f710985e553019dd6cba05da8f90aaeedf666c2691c8fb2f2cd22108a408a147` |

El APK v4 declara 1.4.5/código 12 y aprobó firma y alineación con el certificado
de distribución existente. Sigue siendo una variante debug de ensayo; no es
el release final ni una reconstrucción del commit definitivo. Los dos JAR
coinciden con el manifiesto del ensayo y con los archivos de la distribución
comprobados durante este cierre.

Las cargas fueron los cinco fixtures de 97, 4 096, 65 537, 1 048 576 y
3 145 728 bytes cuyos hashes figuran en la tabla de archivos de la campaña
Flash. Los resultados de Compartir son nuevos y separados de los de Flash.
Los archivos recibidos con nombres ya existentes obtuvieron el sufijo
`(1)`; los originales se conservaron. Los resúmenes locales de Android → PC
y PC → Android tienen SHA-256 respectivos
`8f080f93eee8d64d7b7dca35ded43159387d526283eab222c366f2d649ecf49b` y
`a2da2c8f6fb1d68b07af6fc4f55c4fe4c12139215282f4459c1feb36700566c6`.

**F-01 sigue abierto.** Después del lote de cinco archivos PC → Android,
la sesión seguía activa, pero Android volvió a pedir confirmar el PC. Los
cinco adjuntos preparados y el borrador se conservaron. La observación es
compatible con el [hallazgo preexistente F-01](VISUAL_FINDINGS-1.4.0.md#f-01-la-confirmación-manual-puede-perderse-al-conocer-la-identidad-del-mismo-destino):
una confirmación manual inicialmente sin identidad conocida puede invalidarse
cuando la recepción autenticada incorpora la identidad del PC. La lectura
de código sustenta esa explicación como inferencia; los XML de interfaz no
registran las identidades antes y después y no demuestran esa causalidad
para este ensayo.

La recuperación es volver a **Conectar → Mostrar código/PIN → Usar esta
sesión**. No se cambió la guardia de identidad en este cierre: una corrección
requiere evidencia autenticada de la transición y debe conservar la
invalidación cuando el destino realmente cambie. La nueva petición de
confirmación no invalida los diez hashes coincidentes ni acredita pérdida
de archivos; tampoco se presenta como un problema ya resuelto.

La entrega de archivos aprobada no ocultó un defecto de presentación:
la actividad podía conservar «Enviando» después de terminar el lote.
`P2pSendSummary` y `P2pUi` ahora sustituyen ese estado transitorio por el
resumen terminal. El pase JVM v6 aprobó los tres casos nuevos de regresión,
incluida la resolución en ambos idiomas. La comprobación visual posterior
del estado corregido se registra en el recorrido de Compartir del APK de A:
un archivo por LAN, sin extrapolar ese alcance a todos los tamaños de lote.

### Instrumentación visual Android y correcciones

Estas pruebas usan la interfaz real con fixtures sintéticos sobre la variante
debug. Comprueban interacciones, texto y geometría de los estados preparados;
no se cuentan como transferencias LAN adicionales ni como ejecución del
release R8 definitivo.

| Ronda | Resultado acreditado y límite |
| --- | --- |
| FlashText v3, español e inglés | 16 comprobaciones aprobadas por idioma. Copiar/Pegar exacto y privado, límites UTF-8, cancelación/cambio/pérdida/actualización del receptor sin perder preparación, y compositor con teclado real a escalas 1,0 y 1,6. |
| Compartir v2, español e inglés | Nueve comprobaciones aprobadas por idioma: revisión de uno, tres y cinco archivos a escalas 1,0, 1,6 y 2,0; nombres completos y acciones accesibles. |
| FlashBilingual v3, inglés | 36 comprobaciones automatizadas aprobadas, pero la revisión visual encontró solapamiento entre la aprobación y «Reject» a escala 2,0. Ese PASS instrumental no acreditaba una disposición visual correcta. |
| FlashBilingual v6, español e inglés | 36 comprobaciones aprobadas por idioma tras corregir las acciones: aprobaciones de texto y lotes, errores y texto largo a escalas 1,0, 1,6 y 2,0. Ahora exige objetivos de aprobación y rechazo sin intersección y geometría estable durante la captura. La revisión visual de la escena corregida a escala 2,0 confirma ambas acciones completas. |
| Chat visual v9, español e inglés | Ocho comprobaciones por idioma y escala (1,0, 1,6 y 2,0): 48 aprobadas. Incluye historial alcanzable, sesión sin confirmar y posterior envío del mismo borrador, salto desde Más sin superposición, 2000 caracteres con tres adjuntos y teclado real, canal y diálogo de eliminación. Las capturas de ambos idiomas a 2,0 se revisaron sin nuevos defectos bloqueantes en esas vistas. |

La corrección del solapamiento coloca ambas decisiones de aprobación en una
columna, con botones que ocupan el ancho disponible. Se contrastó el contenido
y la estructura de consentimiento con la
[referencia Flash 4:22 de Figma](https://www.figma.com/design/KRV4r4m1p1fSOFZOHOMV0B?node-id=4-22)
del archivo autorizado de UNSA, equipo `dev-UNSA`. La revisión fue acotada a
ese consentimiento. Para Chat se consultó la [referencia 22:24](https://www.figma.com/design/KRV4r4m1p1fSOFZOHOMV0B?node-id=22-24): encabezado breve, historial desplazable y editor accesible.

Chat v9 compacta el encabezado con letra grande y prioriza el destinatario.
La acción «Ir al último mensaje» queda en Más, fuera del área de lectura,
y no aparece en un historial vacío. Los intentos anteriores permanecen
registrados como fallidos: detectaron lecturas obsoletas de accesibilidad,
una aserción incorrectamente lanzada en el hilo principal y un arnés que
desplazaba la lista invertida en una sola dirección. La revisión visual v8
también descubrió la superposición real del botón sobre el historial.
La repetición v9 acredita las correcciones; los PASS anteriores no se
convierten retroactivamente en una aprobación visual final.

El APK debug v6 de estas comprobaciones tiene SHA-256
`97e589f49d62c969a5af44af624b772d880a3b990cf83f684b163e045b4c64f3`.
El APK debug v9 del Chat tiene SHA-256
`8a6034609481c1eb4ab1650e787fd0b53359a0c0661cdbde4c12750965fdbcdc`.
Las escalas modifican temporalmente el contexto de la Activity durante el
ensayo; no cambian la escala global del teléfono. Cada escala de Chat v9 se
ejecuta en un proceso separado y registra la restauración de configuración;
no acredita transiciones de escala dentro de una sesión. Las capturas del
compositor largo se toman antes de abrir Más y comprueban que el teclado
real sigue visible; la captura se limita al área de la aplicación. Los
manifiestos de Flash v6 registran la restauración de configuración, estado
y portapapeles.

El detalle P3 de integridad de texto entrante quedó corregido en las fuentes:
se conserva la metadata de la operación antes de encolar el trabajo de UI y
se elige el error de texto sin leer un archivo parcial ni crear un recibo.
**Cinco pruebas JVM aprobaron**, incluidas corrupción real de los bytes,
ausencia de publicación y resolución del mensaje en español e inglés.
Esto acredita esa corrección y sus pruebas; no modifica el APK R8 anterior.

Las comprobaciones bilingües corresponden a Android. **La interfaz de
escritorio de esta entrega está en español**; no se declara una validación
equivalente del escritorio en inglés.

### Pruebas JVM y lint

Los XML actuales, posteriores al pase Android v9, contienen:

| Módulo | Suites | Registradas | Aprobadas | Omitidas |
| --- | ---: | ---: | ---: | ---: |
| Android JVM | 43 | 259 | 259 | 0 |
| Protocolo JVM | 6 | 68 | 68 | 0 |
| Escritorio JVM | 16 | 102 | 99 | 3 |
| Total | 65 | 429 | 426 | 3 |

No hay fallos ni errores en esos XML. Las tres omitidas siguen siendo
`FlashEmulatorSocketTest`, `FlashLanUdpTest` y `FlashTextEmulatorSocketTest`.
No se presenta la suma de XML de varios módulos como una única ejecución
nueva de todas las pruebas. El pase v9 volvió a ejecutar Android y terminó
correctamente en 2 min 26 s, con 93 tareas: 18 ejecutadas y 75 actualizadas.
Incluye los tres nuevos casos del resumen de Compartir.

`lintRelease` terminó correctamente. Su tarea de informe quedó `UP-TO-DATE`
y el XML conservado registra **cero errores y 44 advertencias**, SHA-256
`7c91ae717dbbd76a876b8be2fd17d3f9086404192cde76401cf93d5e77ca0040`.
No se declara un informe regenerado ni cero advertencias.

### Comprobación de la ficha móvil F-Droid

En la comprobación móvil de esta sesión, buscar Qetara permitió abrir su ficha
y observar el icono clásico. La versión publicada que mostraba la ficha era
1.4.4; tener 1.4.5 instalada localmente no acredita su publicación. Se conserva
el fondo marfil `#FFF7ED`, el símbolo azul marino `#102A43` y su geometría.

## Reconstrucciones y receta del commit A

El preflight WSL aprobó con OpenJDK 21.0.10, Gradle 9.1.0, fdroidserver 2.4.5,
apksigcopier 1.1.1, plataforma Android 36 revisión 2 y Build Tools 36.0.0.
Ese resultado comprueba herramientas; por sí solo no acredita una compilación
ni reproducción de un APK. Se reutilizaron el SDK y las cachés de dependencias;
las builds usaron clones nuevos y desactivaron la caché de resultados.
No son aprovisionamientos independientes de herramientas.

La compilación Windows desde un clon nuevo de A aprobó en **3 min 19 s**, con
**50 tareas ejecutadas de 50**. Se usó
`scripts/build-android-release.ps1 -Ref baec571b7c82b799848368f5e42027677695ae2c`
en modo sin conexión. El script ejecutó
`:app:assembleRelease --no-build-cache --no-daemon --max-workers=2 --console=plain --offline`;
el log registra `minifyReleaseWithR8`. La procedencia confirma el commit A,
árbol limpio antes y después, versión 1.4.5/código 12, paquete
`io.github.intelognatanael.qetara` y ausencia de variables de firma durante
la build. El registro de procedencia tiene SHA-256
`6da054aba56ecab5f1f89f7efb2129e96fc760c98582b090db3c40f4b473341b`.

| Artefacto del commit A | Bytes | SHA-256 |
| --- | ---: | --- |
| APK release R8 sin firma de Windows, Linux y F-Droid, cada uno | 3 735 556 | `a39a9dc9a336dfa224be297b64f6f843ec8f5e0e48d418ad1c8ce36ac15a0962` |
| APK release firmado e instalado | 3 778 967 | `d449c2a7f5edb725e9095928c88a608d027722ff76e04be6bdc8890c16f933b6` |
| APK firmado reproducido desde Linux y desde F-Droid, cada uno | 3 778 967 | `d449c2a7f5edb725e9095928c88a608d027722ff76e04be6bdc8890c16f933b6` |

La metadata `META-INF/version-control-info.textproto` de los APK comprobados
identifica exactamente A y no contiene `NO_VALID_GIT_FOUND`. El APK firmado
aprobó firma y alineación con el certificado SHA-256
`5f5cdbfb03c1f62f7dc42aee12a5ea4a347b767ccfc268eb450ca69c98ba7a3b`.
Se instaló en el teléfono de ensayo como actualización, reemplazando el APK
debug v9 y conservando los datos. Los recorridos físicos de este APK R8 se
describen en su apartado propio, separados de la instalación y de los ensayos
debug anteriores.

La build Linux ejecutó
`./gradlew :app:assembleRelease --no-daemon --no-build-cache --no-configuration-cache --max-workers=2 --console=plain`
desde otro clon nuevo de A. Terminó con código 0 en 150,0 s; Gradle registra
2 min 29 s y 50 tareas ejecutadas. La igualdad con Windows se comprobó por
comparación directa de bytes, además del SHA-256.

### Ensayo F-Droid y auditoría del fallo posterior

`fdroid scanner --refresh --json --exit-code io.github.intelognatanael.qetara:12`
terminó con código 0 en 9,17 s, sin errores ni advertencias. Sus tres mensajes
informativos registran la retirada de `gradlew`, `gradlew.bat` y
`gradle/wrapper/gradle-wrapper.jar`. Después,
`fdroid build --verbose --test --no-tarball --scan-binary --stop --no-refresh io.github.intelognatanael.qetara:12`
terminó con código 0 en 175,1 s; la compilación Gradle registró 2 min 30 s y
50 tareas ejecutadas. El log acredita R8, escaneo DEX y comprobación de bloques
de firma adicionales. No se usaron `--force`, `--skip-scan` ni exclusiones nuevas.

El runner falló **después** de ese comando: su guard exigía que no hubiera
cambios en archivos seguidos. F-Droid había retirado los tres wrappers y
11 líneas de configuración de firma en `app/build.gradle.kts`. Ese FAIL
permanece conservado; no se declara que el runner original terminara bien.

Una auditoría posterior, con salida nueva y sin recompilar, generó la
transformación esperada mediante la función instalada
`fdroidserver.common.remove_signing_keys`, ejecutada exclusivamente en memoria.
El resultado coincidió byte a byte con el archivo preparado por F-Droid.
Se verificaron la regla instalada de retirada de wrappers, las cuatro rutas
exactas del diff completo, HEAD/árbol de A, recetas, comandos, escáneres,
identidad del APK y coincidencia directa de los tres APK sin firma.
Los originales, el checkout modificado por F-Droid y el fallo del guard se
conservaron. Esta **auditoría independiente aprobó**; no restauró ni alteró el
checkout para hacer pasar la condición original.

La build usó el Gradle externo 9.1.0 configurado por F-Droid, JDK 21 y el SDK
comprobado. La caché de usuario no contenía configuración ni scripts de
inicialización adicionales; el directorio de inicialización de la distribución
contenía sólo su archivo informativo. La base de escaneo reutilizada tras
`--refresh` tenía 104 firmas y SHA-256
`5114edc83a6fbe52cae34ff38add8b33f20e651e43e31475d36314d6eb06a991`.
Sus timestamps se conservaron; no se deduce de ello que todos los datos de
herramientas o categorías sean los últimos disponibles.

La copia de firma sobre Linux y F-Droid aprobó con apksigcopier 1.1.1 y
`fdroidserver.common.verify_apks` 2.4.5. Ambas reproducciones coincidieron
byte a byte con el APK firmado de A, con certificado permitido, firmas v2/v3
y alineación de 16 KiB verificadas. La reproducción no accedió a la clave
privada ni descargó un APK público. El registro conjunto de auditoría y
reproducción tiene SHA-256
`d72b92c82552309f11621662ac85fb42a2c8bc17c352303d937f1376804457b0`.

Los diagnósticos R8 tienen un alcance distinto de la igualdad de APK:

| Diagnóstico | Comparación |
| --- | --- |
| `mapping.txt` | Mismo SHA-256 en Windows, Linux y F-Droid: `c241c5c07f61bba80c3054a0303e857287ddbc00fd713e9aa50c8ad1147528de`. |
| `configuration.txt` | Difiere entre las tres builds. |
| `seeds.txt` | Difiere entre las tres builds. |
| `usage.txt` | Linux y F-Droid coinciden; Windows difiere. |

Se conservaron tamaños y hashes de cada diagnóstico. La igualdad de los APK
no se presenta como igualdad de todos los archivos auxiliares.

### Receta pública y límites de publicación

La receta pública preparada fija A, versión 1.4.5/código 12, conserva el origen
Git HTTPS, `Binaries`, el certificado permitido y la actualización por
etiquetas. Sobre esa receta se ejecutaron estas comprobaciones con
fdroidserver 2.4.5, todas con código de salida **0**:

| Comando | Resultado |
| --- | --- |
| `fdroid readmeta` | Lectura aprobada. |
| `fdroid rewritemeta io.github.intelognatanael.qetara` | Normalización aprobada sin cambios semánticos. |
| `fdroid lint --force-yamllint io.github.intelognatanael.qetara` | Lint aprobado. |
| Segunda ejecución de `rewritemeta` | Formato estable. |

La receta canónica incorporada en esta actualización ocupa **859 bytes**,
SHA-256 `e7f43c4c0de982ffc590d64f6a4ae158106097cdb39ce9e4ee9c4c23a106e39b`.
El registro local de las comprobaciones tiene SHA-256
`75ffea870d15ee32561e2971a92782ddc2688dd5e712769aad8cec16d15130ab`.
La configuración de categorías reutilizada se verificó por hash contra la
revisión histórica de fdroiddata
`7f3fb82b398ef8537ff478e97f2ea44c83b7b0a9`; no se afirma que sea la última.

Para el ensayo local previo a publicación se preparó otra copia con exactamente
dos cambios: `Repo` apunta al repositorio local y se omite `Binaries`. Su
SHA-256 es `3c950ba2fc12545ea3d762e8f5be0d4e954ea55e09fbf6e43419f67e74dda670`.
Esa copia no forma parte del YAML público. La lectura y el lint de metadata
no equivalen a los escáneres de fuentes/APK, a la build F-Droid ni a una
comparación con un APK público. No se descargó la URL de referencia de 1.4.5
y no se declara su publicación, una etiqueta ni una CI remota.

## Recorridos físicos del APK release de A

Esta repetición utilizó el APK firmado de A, SHA-256
`d449c2a7f5edb725e9095928c88a608d027722ff76e04be6bdc8890c16f933b6`, y el
JAR de escritorio de ensayo ya identificado, SHA-256
`8e0f72af6a0769b57bb30fac387bfd4cc38001435339a9b79bbb34c3a3536b11`.
Las transferencias circularon por LAN real; USB se usó para control y
evidencia. La identidad reconstruida desde A corresponde al APK Android;
el JAR conserva su propia procedencia de ensayo.

| Recorrido | Resultado y límite |
| --- | --- |
| Flash, cinco archivos PC → Android | Una aprobación por lado para el lote. Los cinco archivos de 97, 4 096, 65 537, 1 048 576 y 3 145 728 bytes coincidieron con los SHA-256 de los fixtures documentados abajo. |
| Flash, texto Unicode PC → Android | Fixture de 46 bytes UTF-8. El texto recibido extraído del XML de interfaz coincidió exactamente con el esperado; no se presenta como una nueva prueba de Copiar/Pegar. |
| Flash, texto Android → PC | Se observó el texto sintético de 21 bytes `QA145FINAL_ANDROID_PC` y la entrega confirmada. La comprobación fue visual: en este corte no se calculó un hash del contenido recibido ni se repitió Copiar/Pegar exacto. |
| Compartir normal, un archivo Android → PC | Archivo de 97 bytes, SHA-256 recibido `cec76e27c7fda765bfe09acaa5be3a8d75c0341ac682904255eda770cc295717`, idéntico al origen. La interfaz mostró «Último envío completado» y «1 archivo entregado», sin «Enviando» persistente. Actividad mostró «Enviado · Completado» y el nombre correcto. |

En Compartir se mantuvo denegado el permiso de notificaciones cuando Android
lo solicitó; la entrega terminó correctamente. Esto acredita ese recorrido
con un archivo y el estado terminal corregido en el release R8. Los lotes
de cinco en ambas direcciones y la cobertura visual bilingüe conservan las
versiones de ensayo anteriores indicadas en sus respectivos apartados.

Los resúmenes locales de esta repetición tienen estos SHA-256:

| Resumen | SHA-256 |
| --- | --- |
| Flash, lote de cinco PC → Android | `438505dfeedce5cdb3b88b5a4397b638b3f3a28fba537877244d545bc1066ae1` |
| Flash, texto Unicode PC → Android | `fa7fed8d7e4830d3a1db3b2fc6d82d0f87c8df1e6ee0ca3d7e9c002634cac709` |
| Compartir normal, un archivo Android → PC | `83ebb910111a767143f62cfb7ca551cd886bf010ae6449a6e353e19090a40961` |

Al cerrar los ensayos, el teléfono conservó Qetara 1.4.5/código 12 sin el
indicador `DEBUGGABLE`. La escala global quedó en 1,0 y los idiomas de la app
en seguir al sistema. Los permisos de notificaciones y dispositivos Wi-Fi
cercanos permanecieron denegados. Se cerró la sesión normal y Flash quedó
desactivado en ambos equipos. En PC se desactivó la recepción y se cerró la
ventana. Las listas de reenvíos ADB quedaron vacías y se desinstaló únicamente
el paquete de instrumentación; la aplicación principal permaneció instalada.

## Artefactos de la campaña anterior

| Artefacto | Bytes | SHA-256 |
| --- | ---: | --- |
| APK Android release R8 sin firma | 3 735 556 | `68433336f657769ee4bc373729eab50161146502311729c4127842c9cad8031a` |
| APK Android release firmado e instalado | 3 778 967 | `823321931da5643bae9ba68d855496533054a54087e4829d047363d696c68252` |
| JAR del módulo escritorio 1.4.5 usado en la distribución | 1 733 090 | `d655ec1b7571e9ede20193f39306a38f460f64186412b7320915e395600037a6` |
| JAR del protocolo 1.4.5 usado en la distribución | 114 897 | `f710985e553019dd6cba05da8f90aaeedf666c2691c8fb2f2cd22108a408a147` |

El APK se identificó como 1.4.5/código 12. La firma y alineación aprobaron,
y su certificado SHA-256 fue
`5f5cdbfb03c1f62f7dc42aee12a5ea4a347b767ccfc268eb450ca69c98ba7a3b`,
coincidente con la identidad de distribución documentada en
[1.4.4](VALIDATION-1.4.4.md). Se instaló como actualización, conservando los
datos. El empaquetado de escritorio creó una distribución ejecutable;
no se registra un MSI nuevo ni la sustitución de la instalación existente.

Los dos hashes de JAR identifican los módulos comprobados de escritorio,
no un paquete completo ni todos los archivos de su runtime. Ninguno de los
artefactos de esta tabla se declara producto de las fuentes posteriores.

## Campaña física anterior: ambas candidatas 1.4.5

El ensayo utilizó un OPPO CPH2743 con Android 16/API 36 y un PC Windows,
con las versiones de la tabla anterior. Las transferencias circularon por
Wi-Fi LAN real, sin reenvíos ADB; USB se utilizó sólo para control e inspección.
El usuario autorizó el permiso de firewall solicitado por Windows para la
distribución candidata. No se modificó código ni se recompiló durante estos
recorridos físicos.

El ejecutable candidato de escritorio arrancó y activó Flash. Con Flash
activado primero en PC y después en Android, abrir el selector de PC encontró
el teléfono sin pulsar una búsqueda adicional. La comprobación identificó
el ejecutable de la candidata y no se atribuye a la instalación anterior.

### Archivos

Se completaron cinco archivos PC → Android y cinco Android → PC, usando
los controles de la interfaz. Cada lote tuvo una aprobación en cada equipo
para el conjunto completo. Los diez resultados coincidieron con los hashes
de los cinco fixtures sintéticos:

| Fixture | Bytes | SHA-256 esperado y recibido en ambas direcciones |
| --- | ---: | --- |
| 1 | 97 | `cec76e27c7fda765bfe09acaa5be3a8d75c0341ac682904255eda770cc295717` |
| 2 | 4 096 | `c01934b950607a25cd2c50a17db184a2a518761e3d8c347f4edc4eca8c86b364` |
| 3 | 65 537 | `75e71b9fc96fc7d9578cccf7eb05c3d0cb1210a28d78a5a520906bd013ce1a91` |
| 4 | 1 048 576 | `8d5155f607cd7e81883dfbff9051a5add6b148411be6e093d4fa26e6eed29d78` |
| 5 | 3 145 728 | `781e13bcdbf4fd2671e2bb5bb84429da257e1fb595b1e5e2289e7b28d3157faf` |

En Android se eligieron los cinco archivos mediante «Adjuntar» y el selector
del sistema. La interfaz confirmó los cinco como entregados y confirmados.
Estos resultados se registraron de forma separada de los lotes con versiones
anteriores, aunque reutilizaron las mismas cargas binarias.

### Texto y portapapeles

El fixture contenía 106 bytes UTF-8, con Unicode, párrafos y espacios
exteriores; SHA-256
`24984150c9bfbb37c5dfaad279d78eb2a6b00c5cf54762d8b094116dda81488c`.

| Recorrido por interfaz | Resultado medido |
| --- | --- |
| PC → Android | Cuerpo recibido idéntico a los 106 bytes originales. |
| Copiar → Pegar en Android | Resultado y borrador idénticos al fixture, incluidos los espacios exteriores. |
| Android → PC | Archivo recibido idéntico al fixture por SHA-256. |
| Copiar → Pegar → reenviar en PC | Texto recibido de nuevo en Android idéntico a los 106 bytes originales. |

El resumen local del recorrido tiene SHA-256
`e2835b0634e88db3591681f346a448af2c5305658155d7ddec6b8b212fc0da36`.
La comprobación de Copiar/Pegar en PC se realizó por reenvío y comparación
exacta del contenido recibido; no se infirió sólo de su aspecto visual.

Una consulta inicial del archivo recibido utilizó un nombre transcrito
incorrectamente. Se corrigió consultando el sistema de archivos antes de
calcular el hash final; no fue una transferencia fallida.

Al terminar, Flash quedó desactivado en ambos equipos. El idioma Android se
restauró a seguir el sistema. El permiso de red autorizado no se revocó al
detener Flash.

## Antecedentes con versiones distintas

Antes de usar ambas candidatas se probaron dos combinaciones que conservan
su propio alcance:

- Android 1.4.4 oficial y escritorio 1.4.3 completaron los mismos tamaños de
  lote con hashes coincidentes. Un primer intento de regreso expiró mientras
  se demoraba la aprobación y sólo el reintento se contabilizó como aprobado.
  Ese ensayo no valida 1.4.5 ni explica por sí solo un fallo de otra sesión.
- Android candidato 1.4.5 y escritorio instalado 1.4.3 intercambiaron un texto
  de 115 bytes, SHA-256
  `3f8981544ed2c5507c6c4d470b756716687fb430c12f66706550bed4eb9bae99`.
  En Android aprobaron Copiar/Pegar exacto y el cambio de idioma con el mismo
  borrador. La inspección de Copiar/Pegar en aquel escritorio fue sólo visual.
  Se observaron compositor y resultados legibles en español e inglés sin
  teclado visible, y descubrimiento al abrir el selector de Android con PC
  activado después. No se extiende ese resultado a todos los estados visuales.

## Automatización anterior al nuevo cierre

Los XML conservados de la instantánea anterior registraron **408 pruebas
aprobadas, cero fallos, cero errores y tres omitidas**, de 411 registradas.

| Módulo | Suites | Registradas | Aprobadas | Omitidas |
| --- | ---: | ---: | ---: | ---: |
| Android JVM | 42 | 251 | 251 | 0 |
| Protocolo JVM | 6 | 68 | 68 | 0 |
| Escritorio JVM | 15 | 92 | 89 | 3 |

Las omitidas fueron `FlashEmulatorSocketTest`, `FlashLanUdpTest` y
`FlashTextEmulatorSocketTest`, escenarios optativos que no se presentan como
ejecutados. El resumen JSON de esos resultados tiene SHA-256
`2f406127835e427a6cd2da3e1585a0b84b055c2f300463cc4e69107ef706d418`.

El pase previo de PC y protocolo terminó correctamente con 12 tareas
ejecutadas. El posterior pase Android/PC terminó correctamente con 137
tareas: 82 ejecutadas y 55 actualizadas. Incluyó pruebas JVM, `lintRelease`,
release R8, debug e instrumentación compilada. En ese segundo pase,
`:protocol:test` fue `UP-TO-DATE`: no fue una nueva ejecución de sus 68 pruebas.
El lint release registró **0 errores y 44 advertencias**. Compilar el APK
de instrumentación no demuestra haber ejecutado sus escenarios en el teléfono.

Un intento Android previo falló al compilar un arnés que usaba el parámetro
`status` en vez del estado localizado. La corrección permitió la compilación
posterior; el intento fallido sigue conservado como tal. El empaquetado de
escritorio terminó correctamente con cinco tareas ejecutadas y seis
actualizadas. Ninguno de estos pases constituye dos reconstrucciones limpias
independientes.

## Límites y pendientes

Los apartados históricos conservan los resultados y límites de sus propios
artefactos. Las comprobaciones nuevas de Chat normal, Compartir, transiciones
de receptor y corrección P3 se acreditan sólo con el alcance descrito en el
apartado de cierre. La repetición Flash v6 y su revisión visual acreditan la
corrección del solapamiento; no convierten las capturas anteriores en una
aprobación visual. El estado de Chat corresponde a la ronda indicada arriba.

El hallazgo preexistente F-01 de nueva confirmación después de recibir sigue
pendiente, con la recuperación manual y el límite causal indicados arriba.
No se considera corregido ni se reduce la verificación de identidad para
evitar ese aviso.

**Wi-Fi Direct permanece pendiente:** no había un segundo teléfono disponible
para esa prueba. Un solo Android 16 y una LAN no cubren versiones anteriores
de Android, otros equipos, redes o todos los órdenes de activación. El
escritorio se validó en español; no se extiende a él la cobertura bilingüe
de Android.

La revisión Figma acreditada cubre el consentimiento Flash 4:22 y la estructura
de Chat 22:24; no se afirma una revisión final completa de todas las pantallas.
Las fuentes quedaron fijadas en A. Se acreditan las tres builds, su comparación
y reproducción con los límites del ensayo F-Droid y sus transformaciones
auditadas. Los recorridos físicos posteriores se atribuyen al hash del APK
firmado de A; no amplían automáticamente el alcance de pruebas anteriores.

Los resultados históricos de [1.4.4](VALIDATION-1.4.4.md) y sus pipelines
no acreditan la nueva versión. No se declara una release, una etiqueta pública,
un APK descargable, una CI remota ni una inclusión nueva en F-Droid para 1.4.5.

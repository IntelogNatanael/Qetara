# Validación de Qetara 1.1.0

La entrega se verificó mediante compilación, pruebas automatizadas, ejecuciones nativas de Windows y flujos funcionales entre dos emuladores Android. Las comprobaciones se realizaron el 6 de septiembre de 2026. Este informe delimita lo probado y las plataformas pendientes.

## Compilación y pruebas

| Componente | Pruebas | Fallos / errores / omitidas |
| --- | ---: | --- |
| Android | 127 | 0 / 0 / 0 |
| PC | 30 | 0 / 0 / 0 |
| Protocolo compartido | 22 | 0 / 0 / 0 |
| **Total** | **179** | **0 / 0 / 0** |

La compilación final Android produjo APK debug y release firmado. La compilación Windows produjo JAR, aplicación portable e instalador MSI. Los registros y el inventario de pruebas se incluyen en `Verificacion/integration-build-final.log` y `Verificacion/final-validation.json` de la entrega.

Android Lint terminó sin errores en debug y release. Cada variante conserva cinco advertencias: dos avisos de versiones nuevas de dependencias, orden del parámetro `Modifier`, API recomendada de espacio asignable y extensión KTX para URI. El compilador también informa de dos APIs obsoletas: `LocalClipboardManager` y `NetworkInfo`. No se presenta la compilación como libre de advertencias.

Las pruebas cubren validación de nombres, tamaños, bloques, posiciones de reanudación e integridad; publicación sin sobrescritura; cancelación de sockets; límites de concurrencia; deduplicación de archivos y mensajes; identidad y aprobación; pertenencia a grupos; rutas y permisos; borradores y destinatarios; expiración y cierre de sesión. Hay 66 pruebas de lógica de experiencia Android dentro del total Android.

Las regresiones finales ejercitan el orden entre cerrar y activar, la espera de callbacks de trabajos cancelados, la invalidación de cierres antiguos y la admisión de trabajos frente al cierre. Dos pruebas deterministas con latches y Jobs reales comprueban que un trabajo tardío se rechaza o queda incluido en el conjunto que se cancela.

## Entorno de las pruebas funcionales

Se usaron dos emuladores Android API 36 dedicados a QA. Uno se configuró con 360 dp de ancho y tamaño de fuente del sistema 1,3. Windows ejecutó la aplicación nativa de 64 bits con el runtime incluido; las identidades y archivos de prueba se aislaron en carpetas de trabajo.

Para atravesar el NAT de los emuladores se utilizaron reenvíos ADB y la dirección del host visible desde Android. Los casos usan las aplicaciones y sockets reales. No demuestran el comportamiento de un router físico, el descubrimiento en todas las LAN ni la radio Wi-Fi Direct entre teléfonos.

## Archivos, mensajes y experiencia de uso

| Caso | Resultado observado |
| --- | --- |
| Android → Windows | Un TXT de 95 bytes llegó con SHA-256 idéntico. Comprobado durante la evolución, antes de los ajustes finales de cierre. |
| Android → Android | Envío conjunto de TXT de 95 bytes y PNG de 9066 bytes; ambos se publicaron en `Download/Qetara` y sus hashes coincidieron. |
| Archivos repetidos | Reenviar explícitamente creó copias numeradas y conservó los anteriores. |
| Abrir una descarga | La imagen recibida se abrió desde Qetara en el visor Android. |
| Windows → Android, equipo nuevo | PIN incorrecto rechazado sin aprobar ni publicar. Con PIN correcto se pidió aprobación; la huella coincidió con la PC. Tras aprobar y reintentar llegó un archivo con nombre Unicode de 311296 bytes, con SHA-256 idéntico. |
| Windows PC10 → Android12, repetición final | Archivo de 311296 bytes recibido y comparado por SHA-256 con el original. |
| Windows nativo → Windows nativo | Envío con espacios y Unicode en el nombre, confirmación y hash coincidente; PIN incorrecto rechazado. |
| CLI Windows final | Ayuda con salida 0; opciones desconocidas, puerto inválido y provisión antigua con salida 1 y explicación. Huella contrastada con SHA-256 de la clave pública real. |
| Self-test Windows final | Archivo completo de 2 MB y reanudación desde un parcial, con integridad verificada y salida 0, desde una ruta con espacios. |
| Chat Android | Mensaje enviado con el teclado abierto y mostrado como recibido en el otro Android. |
| Chat con imagen | Imagen de 9066 bytes más mensaje enviados con teclado abierto; texto recibido exacto y SHA-256 de la imagen coincidente. |
| Borradores y adjuntos | Cambiar de Chat a Enviar conservó la selección de Chat y dejó Enviar con su propia selección independiente. |

La repetición Windows PC10 → Android12 produjo el SHA-256 `587f3d6470f16c2a9ebfa08fcc394c8446bd112703235d3e978336367bcbfbcd`, idéntico al original de 311296 bytes.

Los casos de emparejamiento inicial y algunos intercambios se realizaron sobre compilaciones anteriores de esta misma evolución. Los cambios posteriores y los casos repetidos sobre el cierre final se identifican en `Verificacion/functional-validation.json`; no se presenta cada caso como repetido sobre cada binario intermedio.

En el emulador de 360 dp con fuente 1,3, el campo del chat con un adjunto pasó de quedar reducido a 11 px a disponer de 186 px. Con el teclado abierto, el campo ocupó las coordenadas verticales 312–498 y Enviar 539–593, por encima del teclado que comenzaba en 680. Se comprobó el envío real, no sólo la disposición visual. El caso de imagen y mensaje se repitió en ambos Android finales; la imagen conservó su SHA-256 y el texto se mostró recibido exactamente. [Captura con texto ampliado y teclado abierto](images/android-chat-large-text.png).

El primer lanzamiento permite explorar sin pedir permisos automáticamente. La petición de notificaciones apareció al enviar o recibir. La denegación repetida del permiso de dispositivos cercanos ofreció la ruta a Ajustes y el estado se actualizó al regresar.

## Cierre, receptor y emparejamiento

En Android12, cerrar sesión detuvo tres conexiones abiertas en 204 ms. La sesión continuó cerrada después de finalizar el proceso y volver a abrir la aplicación; Descargas conservó el acceso a ocho archivos. La activación explícita recuperó la recepción.

La secuencia rápida Cerrar → Activar detectó una carrera durante QA y motivó la corrección final. Después de corregirla se ejecutaron cinco ciclos consecutivos con tres conexiones abiertas por ciclo: las conexiones anteriores se cerraron y el descubrimiento volvió a responder. En la repetición final sobre Android12, los cinco ciclos pasaron. Se programó una pausa de 200 ms entre acciones; la separación real de los comandos fue de 359–453 ms, incluida la latencia de ADB. La respuesta activa se observó 343–563 ms después de activar. Esta repetición incorpora la corrección de admisión de las colas frente a la cancelación.

Las siete sondas finales sobre Android12 pasaron y comprobaron descubrimiento mientras otro cliente estaba inactivo, límite de cuatro clientes y rechazo del quinto, reutilización de plazas, cierre de conexiones sin autenticar tras aproximadamente diez segundos y rechazo de la petición antigua de credenciales con `secure_credentials_required`.

La prueba Noise comprobó denegación, aprobación por huella, respuesta cifrada y rechazo de una clave sustituta que reutilizaba el mismo identificador. La respuesta cifrada aprobada para la clave original y el rechazo de la clave sustituta se repitieron y pasaron sobre Android12. PC utiliza código y PIN manuales y rechaza la provisión automática. La posesión del código y PIN no identifica por sí sola a una persona.

Es una revisión técnica local, sin auditoría de seguridad independiente. La deduplicación no garantiza una transacción exactamente una vez ante todo corte eléctrico; los límites de persistencia y de acreditación de la interfaz Direct se documentan en [la revisión de seguridad](SECURITY_REVIEW.md).

## Windows, runtime e instalador

Windows incluye Eclipse Temurin 21.0.12.1+1. Se comprobaron nueve módulos del runtime, 70 DLL idénticas al JDK oficial seleccionado y 50 archivos de avisos legales. Las fuentes correspondientes de OpenJDK, los scripts de Temurin, las licencias, la procedencia y sus hashes acompañan el paquete en `Fuentes/Qetara-third-party-source`. No se afirma haber reconstruido OpenJDK de forma idéntica byte a byte.

El MSI se generó correctamente y sus metadatos identifican Qetara 1.1.0, fabricante «Qetara contributors». Se extrajo con WiX sin instalar: 187 archivos coincidieron byte a byte con la aplicación portable; el JAR restante conservó contenido idéntico con otro nombre generado, la configuración efectiva coincidió y los marcadores de empaquetado fueron los esperados. El ejecutable extraído mostró ayuda con salida 0. El descompilador informó siete avisos de referencias UI; no se interpreta la extracción como una prueba de esa interfaz.

**No se ejecutaron la instalación, actualización ni desinstalación del MSI.** El instalador y el ejecutable no tienen firma comercial de editor de Windows.

La interfaz de escritorio se revisó y la creación de sesión se comprobó durante la evolución. La prueba interactiva final quedó limitada por un diálogo protegido de Firewall de Windows alojado en PickerHost, que cubría la aplicación. No se automatizó ese diálogo ni se cambiaron reglas de firewall. La verificación final de Windows se apoya en compilación, pruebas y CLI nativo; no acredita un recorrido completo de la interfaz final con ratón y teclado.

## Firma, fuentes y entrega

El APK de distribución está firmado con una clave local y se instaló en el emulador de QA. La huella SHA-256 del certificado es:

`5f5cdbfb03c1f62f7dc42aee12a5ea4a347b767ccfc268eb450ca69c98ba7a3b`

La clave privada y su recuperación se entregan por separado y quedan fuera del código fuente y del ZIP de la entrega. Para actualizar una instalación Android debe conservarse la misma firma.

El ensamblador exige fuentes confirmadas en Git, coincidencia de `HEAD` y `product-evolution`, igualdad de los artefactos empaquetados con los actuales, inventarios completos y hashes válidos. Compara todas las entradas del portable, conserva el historial original en un bundle y verifica los ZIP. El commit y el certificado están en `Verificacion/provenance.json`; `SHA256SUMS.txt` cubre los archivos de la entrega y el hash externo identifica el ZIP completo.

La revisión del conjunto de fuentes no encontró nombres de claves, archivos generados ni marcadores obvios de secretos en los candidatos a Git. Esto no se presenta como una auditoría exhaustiva de secretos. El código propio se entrega localmente bajo MIT, con las licencias de terceros. No se publicó una release remota ni se cambió la visibilidad del repositorio.

## Límites de plataforma pendientes

- Wi-Fi Direct y permisos específicos entre teléfonos físicos de distintos fabricantes.
- macOS y Linux: CI y empaquetado configurados, sin ejecución ni paquetes verificados en esta sesión.
- Instalación, actualización, desinstalación y recorrido completo de la interfaz Windows final.
- Lectores de pantalla y más configuraciones de accesibilidad y tamaños de pantalla.
- Redes públicas o administradas, aislamiento de clientes, cambios de router, suspensión y cortes eléctricos en toda su variedad.
- Android anterior a API 36 y matriz de dispositivos físicos. El mínimo configurado es API 24.
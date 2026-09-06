# Validación de Qetara 1.2.0

La entrega se verificó el 6 de septiembre de 2026 mediante pruebas automatizadas, aplicaciones Android en emuladores API 36 y ejecutables nativos Windows de 64 bits. Esta revisión amplía la entrega anterior; su evidencia histórica se conserva en [Validación 1.1.0](VALIDATION-1.1.0.md).

## Compilación y pruebas

| Componente | Pruebas | Fallos / errores / omitidas |
| --- | ---: | --- |
| Android | 152 | 0 / 0 / 0 |
| PC | 37 | 0 / 0 / 0 |
| Protocolo compartido | 30 | 0 / 0 / 0 |
| **Total** | **219** | **0 / 0 / 0** |

Se generaron APK debug y release firmado, JAR Windows, aplicación portable con Java incluido y MSI. El inventario por caso está en `Verificacion/final-validation.json` del paquete y los registros identifican cada compilación. Las tareas Gradle sin trabajo nuevo o excluidas no se contabilizan como pruebas omitidas.

Android Lint terminó sin errores y con cinco advertencias en cada variante. El compilador conserva dos avisos de APIs obsoletas (`LocalClipboardManager` y `NetworkInfo`). Las advertencias concretas están en el inventario; no se presenta la compilación como libre de advertencias.

Las regresiones nuevas comprueban recuperación de borradores y caducidad original, consumo único de adjuntos externos, destinatarios explícitos sin sustitución, coherencia de preferencias LAN/Direct, cancelación de sockets y destinatarios pendientes, confirmación por destinatario y publicación recuperable de archivos.

## Android: continuidad y conexión

Las pruebas usaron dos emuladores dedicados API 36. El receptor también se comprobó a 360 dp y fuente del sistema 1,3. El NAT se atravesó con reenvíos ADB y la dirección del host visible desde Android; estos casos no acreditan la radio Wi-Fi Direct ni todos los routers físicos.

- La selección de un PNG en Enviar, el código y el PIN conservaron su valor al pasar de orientación vertical a horizontal y volver.
- Chat conservó texto, PNG y destinatario tras la misma rotación. Al retirar el proceso de segundo plano y restaurar su tarea, el texto y el adjunto seguían presentes. La autorización manual volvió a «Sesión por confirmar», como corresponde; el estado guardado no equivale a confianza vigente.
- Si un archivo temporal ya no existe, se omite de la selección restaurada y se comunica la recuperación incompleta. Los límites de tamaño y número de referencias se prueban sin introducir archivos en el estado guardado.
- Se reprodujo una preferencia heredada con modo LAN y vista simple Direct: la interfaz indicaba misma Wi-Fi, pero el destino manual quedaba vacío. La normalización alinea ambos campos después de aplicar la disponibilidad de transportes y conserva la vista Avanzada. Las regresiones incluyen una IP real y el cambio explícito a Direct.
- El APK firmado 1.2.0 se instaló sobre 1.1.0, con el mismo certificado y sin desinstalar. Se conservaron la instalación original, archivos recibidos e historial observado.

Los informes de Android identifican el APK y la compilación utilizados en cada caso. Una rotación, una recuperación de tarea y una actualización de APK son operaciones diferentes; no se presenta la recuperación de tarea como persistencia garantizada después de forzar detención, borrar datos o reiniciar el equipo.

## Windows: archivos, mensajes e instalación

| Caso | Resultado observado |
| --- | --- |
| Selector nativo | El diálogo tiene una ventana propietaria y seleccionó un nombre Unicode con espacios. |
| Interfaz Windows → Android | Un equipo nuevo recibió la indicación de aprobar su huella. Tras compararla y aprobar en Android, el reintento llegó y la interfaz mostró Completado. |
| Integridad | Archivo de 311296 bytes, SHA-256 `587f3d6470f16c2a9ebfa08fcc394c8446bd112703235d3e978336367bcbfbcd`, idéntico al original. |
| Chat con teclado | Ctrl+Enter envió «Qetara 1.2.0: mensaje desde Windows, con teclado.»; Android mostró el texto exacto y Windows limpió el borrador confirmado. |
| Preferencias | Se guardaron desde la interfaz y la siguiente compilación recuperó el puerto configurado. |
| CLI nativo final | Nueve casos portables pasaron con sus códigos de salida esperados, incluidos ayuda, argumentos inválidos, PIN incorrecto, transferencia, self-test, cierre y caducidad. |
| Windows → Windows | Dos procesos nativos transfirieron `envío con espacios ñ.bin`, 1357911 bytes, SHA-256 `f14b18496cb76447b81ddee6442a3d7c3c0a344912d72b5dfd74116494a3c79f`. Sólo hubo un archivo publicado; el PIN incorrecto no publicó otro. |
| Self-test | Archivo completo de 2 MB y reanudación de un parcial con integridad, desde rutas con espacios. |
| Caducidad | El receptor de un minuto terminó naturalmente con salida 0; 69807 ms totales incluyen arranque nativo/JVM. |
| Cierre interactivo | El proceso terminó 58 ms después de recibir la orden de salida. |
| MSI real | Instalación 1.1.0, actualización a 1.2.0 y desinstalación terminaron con salida 0. Windows Installer dejó de registrar la versión anterior; la nueva estaba instalada antes de desinstalar. |
| Limpieza MSI | Después de desinstalar desaparecieron el ejecutable, la carpeta de prueba y los dos accesos directos. La ayuda del ejecutable instalado había terminado con salida 0. |

La ayuda del ejecutable instalado se repitió mediante su ruta absoluta en una instalación nueva, con salida 0, y se desinstaló después. El primer harness CLI había ejecutado el portable al solicitar el instalado; ese caso se retiró de su recuento y se conserva la corrección en la evidencia.

La interfaz de envío y chat se probó con una compilación de esta evolución anterior únicamente al ajuste semántico del checkbox de Canal y a la actualización de metadatos de avisos. La compilación posterior recuperó sus preferencias y creó una sesión. La prueba gráfica final de recepción quedó ante el aviso de permisos del Firewall de Windows; no se modificaron reglas automáticamente ni se acredita un recorrido completo de recepción gráfica. Los receptores CLI y las transferencias por sockets sí se probaron.

Las pruebas automatizadas de PC ejercitan cancelación durante una transferencia real, conservación del parcial, reanudación desde su offset y deduplicación de un reintento del mismo envío. También verifican que un resultado tardío de la conversación A no se atribuya a B y que un envío parcial a un canal conserve el componente no confirmado. Una repetición manual es un envío nuevo; no se anuncia entrega exactamente una vez entre acciones diferentes del usuario.

El APK final pasó siete sondas del receptor: descubrimiento, rechazo de credenciales antiguas, atención con un cliente inactivo, límite de cuatro conexiones, liberación de plazas y timeout sin autenticar. Cinco ciclos de Cerrar → Activar con tres conexiones por ciclo cerraron las conexiones anteriores y recuperaron el descubrimiento. Las acciones tuvieron separaciones reales de 313–391 ms y el receptor volvió a responder 156–203 ms después de activar.

La compatibilidad PC 1.1.0 → 1.2.0 y PC 1.2.0 → 1.1.0 se comprobó con procesos nativos aislados. Ambos sentidos conservaron nombre Unicode y SHA-256, con un solo archivo por destino; los cuatro procesos terminaron y liberaron sus puertos. Esta prueba confirma los casos de archivo realizados, no todos los cambios posibles de versión o plataforma.

## Recuperación de publicación

Se reprodujo el corte de proceso entre publicar el archivo y guardar su recibo. El protocolo ahora guarda el recibo antes de publicar, conserva compatibilidad con el formato anterior y sólo considera completado un archivo cuyo tamaño y hash coinciden. Las pruebas incluyen fallo de escritura, cancelación, colisión, archivo vacío y reintento.

Además de las pruebas unitarias, una JVM independiente ejecutó `Runtime.halt` antes y después de mover el archivo; otro proceso abrió el estado y reintentó. Las sondas detectaron el comportamiento anterior y verificaron la recuperación corregida. El JAR de protocolo empaquetado se comparó con el probado. No es una prueba de cortes eléctricos de hardware ni una transacción exactamente una vez ante cualquier fallo. Una interrupción abrupta antes de publicar puede dejar una reserva vacía; no se elimina automáticamente un archivo ambiguo. Los límites están en [la revisión de seguridad](SECURITY_REVIEW.md).

## Código abierto, runtime y firma

La distribución incluye fuentes MIT de Qetara, historial Git, guías de contribución, licencias y fuentes correspondientes del runtime Windows Eclipse Temurin 21.0.12.1+1. Se comprobaron nueve módulos, 70 DLL idénticas al JDK seleccionado y 50 archivos legales. No se afirma haber reconstruido OpenJDK byte a byte.

El script de empaquetado ahora requiere un paquete de fuentes del runtime, comprueba su inventario y SHA-256 antes de compilar, exige coincidencia de versión numérica del runtime y verifica de nuevo la copia final. Las pruebas de preflight rechazan ausencias, cambios, rutas externas, duplicados y metadatos incompatibles. Un manifiesto aportado localmente demuestra consistencia interna; no autentica por sí solo al proveedor.

El certificado Android conserva la huella SHA-256:

`5f5cdbfb03c1f62f7dc42aee12a5ea4a347b767ccfc268eb450ca69c98ba7a3b`

La clave privada queda separada del repositorio y de la entrega comprimida. El MSI y ejecutable Windows no tienen firma comercial de editor. El ensamblador exige fuentes confirmadas, artefactos actuales, correspondencia completa del portable, licencias conservadas y hashes válidos. El commit está en `Verificacion/provenance.json`. El ZIP de fuentes y el bundle conservan los bytes de las licencias originales y el permiso ejecutable de `gradlew`.

La revisión acotada de nombres y patrones no encontró claves privadas, archivos de compilación ni marcadores obvios de secretos en los candidatos a Git. No es una auditoría exhaustiva de secretos. No se publicó una release remota ni se cambió la visibilidad del repositorio.

## Límites pendientes

- Teléfonos físicos, Wi-Fi Direct entre fabricantes y Android anterior a API 36 (mínimo configurado API 24).
- macOS y Linux: tareas CI configuradas, sin ejecución ni paquetes verificados en esta sesión.
- Recorrido gráfico completo de recepción Windows, sujeto al permiso local de firewall.
- Lectores de pantalla y una matriz más amplia de pantallas, routers, redes administradas y suspensiones.
- Auditoría criptográfica independiente y cortes eléctricos reales.

La indisponibilidad del servidor Penpot MCP se documenta en la revisión de experiencia; se preservó la identidad existente y se revisaron las aplicaciones renderizadas.

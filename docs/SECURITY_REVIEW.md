# Revisión defensiva de Qetara 1.1.0

Fecha: 6 de septiembre de 2026. Alcance: cambios de transferencia Android, protocolo compartido y controles de ciclo de vida de escritorio realizados durante esta entrega. Esta revisión de implementación y regresiones no es una auditoría criptográfica independiente, una prueba de penetración completa ni una certificación de hardware.

## Hallazgos tratados

| Problema comprobado | Comportamiento corregido | Evidencia dirigida |
| --- | --- | --- |
| El intercambio antiguo podía exponer token/PIN. | Paquete 6 rechazado siempre; capacidad 12 explícita y cifrada, sin fallback en claro. | Pruebas de protocolo y prueba real de rechazo legacy en emulador. |
| El rechazo previo al handshake impedía que un PC nuevo enviara a Android con código/PIN manuales. | Primero PSK y Noise; luego aprobación de la clave observada; los payloads se rechazan hasta aprobar y reintentar. | `AuthenticatedPeerAuthorizationTest`; validación real de esta ruta registrada en el informe de entrega. |
| Una solicitud reemplazada con el mismo ID podía heredar el clic de aprobación anterior. | Consumo atómico de ID, clave e instante exactos; otro clic o reemplazo no los hereda. | `CredentialApprovalPolicyTest` y `P2pTrustPresenterTest`, incluidos reemplazo y aprobación concurrente. |
| Equipos LAN confiados podían incorporarse a listas y reenvíos Wi-Fi Direct. | Contexto del grupo actual, endpoint local observado después del handshake, clave vigente y revalidación del destino antes del contenido. | `DirectGroupPolicyTest` y pruebas de routing Android: LAN/Direct, grupo renovado, callbacks antiguos, sockets loopback y ausencia de fallback. |
| Olvidar y volver a confiar podía reutilizar evidencia de ruta previa. | Clave ligada a la observación, borrado de rutas, límite monotónico de revocación y poda al reasignar ID/IP. | Regresiones de olvido, nueva clave, callback tardío y reasignación en `DirectGroupPolicyTest`. |
| Tamaños, offsets, colisiones y ACK perdido podían causar fallos de almacenamiento o duplicación ordinaria. | Validación previa a escritura, SHA-256, nombres reservados sin reemplazo y recibos persistidos acotados. | Pruebas del módulo `protocol`, incluidos archivos vacíos, límites y colisiones concurrentes. |
| Un socket inactivo bloqueaba descubrimiento y el estado de escucha parecía una transferencia. | Pool acotado, timeout de autenticación y estados separados de escucha/recepción. | Prueba de sockets contra APK: descubrimiento con cliente inactivo, rechazo del quinto cliente, liberación y timeout de 10 segundos. |

Los nombres de pruebas identifican casos presentes en el código fuente. El resultado final de compilación, la cantidad de pruebas y la versión exacta del APK se registran en el informe de validación de la entrega; no deben inferirse solamente de esta tabla.

## Cierre y cancelación de sesión

El cierre explícito es persistente y se comprueba tanto en la interfaz como en backend y servicio. `SessionNetworkGateTest` cubre rechazo de nuevas operaciones, cancelación de solicitudes activas y rechazo de un resultado tardío aunque el transporte capture la cancelación. `UxSessionPersistencePolicyTest` cubre que una instantánea antigua de ajustes no cambie la decisión actual. La persistencia del archivo de preferencias y el cierre real de sockets se validan por separado en el emulador; esas pruebas de política no sustituyen una prueba de la plataforma. `ObservedPeerTrustTest` cubre que un callback tardío tras Olvidar no recupere el indicador de confianza.

La revisión acotada de escritorio no encontró un ciclo de bloqueo entre el callback sincrónico de Swing y la parada: la espera del hilo receptor ocurre fuera del hilo de interfaz. Sí se corrigió la invalidación de generación, estado y ofertas al cerrar la ventana, para que un callback ya encolado no inicie un envío posterior al cierre.

La prueba de cerrar y activar con una pausa programada de 200 ms encontró una carrera del servicio. Se corrigió con una barrera que espera los Jobs cancelados y sus callbacks antes de publicar otra sesión, un ticket de generación ligado al startId original y `stopSelfResult`. Una solicitud posterior invalida el cierre anterior. La interfaz también observa la destrucción del servicio para reconciliar una activación vigente, sin recrear servicios para detenerlos cuando ya están parados. `SessionLifecycleFenceTest` cubre cuatro casos, incluido un callback final deliberadamente retenido y otro cierre durante la espera. La repetición de la secuencia en APK se registra en la validación final. La admisión de envíos y mensajes vuelve a comprobar el cierre bajo el lock de cada cola; registra el Job y su callback antes de liberar ese lock. STOP captura los hijos bajo el orden fijo sendLock → messageLock. `SessionWorkerAdmissionTest` fuerza el caller que pasó un chequeo anterior y el cierre durante la creación de un Job LAZY, para comprobar rechazo o drenaje completo sin iniciar su contenido.

## Límites conservados

- El primer contacto necesita comparar la huella con el equipo esperado. El nombre, ID anunciado y descubrimiento LAN no acreditan por sí solos identidad. El escritorio utiliza la sesión compartida y no dispone del directorio de confianza de Android.
- Una conexión observada por el endpoint de Wi-Fi Direct no prueba por sí sola la interfaz física de ingreso en todos los kernels. Las pruebas loopback no equivalen a pruebas de dos radios Wi-Fi Direct.
- Olvidar revoca autorizaciones futuras y rutas guardadas; una recepción ya autorizada requiere Cancelar para detenerse.
- Un fallo abrupto entre reserva y movimiento puede dejar un archivo vacío; entre publicación y recibo puede causar duplicado al reintentar. No hay transacción conjunta ni garantía exactamente una vez ante corte de energía.
- SHA-256 verifica integridad del contenido transferido, no que un archivo sea seguro para abrir. El compromiso del dispositivo o sus datos locales queda fuera de la protección del transporte.

## Runtime y procedencia

La distribución de escritorio usa Eclipse Temurin 21.0.12.1+1 aislado de la instalación Java del usuario. Se verificó el ZIP oficial por SHA-256 y se compararon las 70 DLL y 50 avisos legales del runtime generado con ese ZIP. Los fuentes oficiales completos, scripts de construcción del commit correspondiente, licencias y evidencia se entregan en el paquete hermano `Qetara-third-party-source`. La comparación no afirma que la imagen de módulos transformada por jlink sea idéntica a la imagen completa del JDK, ni que se haya reproducido el binario desde cero. Las firmas OpenPGP se conservaron y no se verificaron en esta sesión.

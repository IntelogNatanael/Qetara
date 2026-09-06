# Seguridad

Qetara transfiere archivos y mensajes entre dispositivos. La seguridad de una sesión también depende de reconocer el otro equipo y mantener privados sus datos de acceso.

## Informar un problema

No publiques claves, PIN, archivos personales ni una demostración que exponga a otros usuarios. Si el repositorio ofrece la opción de informar una vulnerabilidad de forma privada, úsala; en caso contrario acuerda un canal privado con el mantenedor antes de enviar detalles sensibles.

Incluye la versión o commit afectado, plataforma, requisitos para reproducir, impacto observado y un caso de prueba con datos sintéticos. Se agradecen correcciones y pruebas de regresión.

## Alcance y límites

El proyecto utiliza la biblioteca noise-java para sus canales protegidos. No presenta esta entrega como una auditoría criptográfica independiente. El descubrimiento de dispositivos ocurre en la red local y no debe tratarse como una prueba de identidad.

La versión 1.1.0 rechaza el mecanismo antiguo que entregaba credenciales sin cifrar. Los pares que solo conocen ese mecanismo necesitan emparejamiento manual o actualización. Nunca se debe reactivar como alternativa silenciosa.

Consulta docs/PRIVACY.md para almacenamiento y permisos. El registro de validación de cada entrega debe distinguir pruebas automatizadas, pruebas en emulador y pruebas entre dispositivos físicos.

## Identidad, rutas y operaciones en curso

Android admite el emparejamiento manual desde Windows cuando el otro equipo conoce el código y PIN vigentes. Primero comprueba el reto y el handshake Noise con PSK; después muestra la huella para aprobación. No procesa archivos ni mensajes hasta que se aprueba esa solicitud exacta. Una clave distinta para un equipo con identidad guardada se rechaza y no sustituye el pin.

La aprobación queda ligada al ID, clave Noise e instante mostrados. Olvidar un equipo elimina su pin y las observaciones de ruta anteriores, incluidos callbacks tardíos. Olvidar no cancela una recepción que ya había sido autorizada; usa Cancelar para detener la transferencia activa.

Para Wi-Fi Direct, el anfitrión utiliza el contexto actual informado por Android y observaciones cifradas del endpoint local del grupo. La confianza histórica o el descubrimiento LAN no bastan para añadir destinatarios al grupo. Esta comprobación no demuestra la interfaz física de entrada en todos los sistemas ni reemplaza la validación con hardware real.

La publicación de archivos evita reemplazar archivos previos y verifica SHA-256. El recibo se prepara antes del movimiento y se valida contra el archivo real al reintentar, lo que permite recuperar un intento tras el cierre del proceso entre publicación y ACK. Un cierre abrupto antes de mover el archivo aún puede dejar una reserva vacía. No se garantiza entrega exactamente una vez ante cualquier fallo de almacenamiento o energía.

El detalle y los casos de regresión están en [el protocolo](docs/PROTOCOL.md) y [la revisión defensiva de esta entrega](docs/SECURITY_REVIEW.md).

El control **Cerrar sesión** detiene recepción, envíos y operaciones de conexión de Qetara, y conserva esa decisión al volver a abrir la aplicación. Cambiar ajustes o red no la reactiva; se requiere **Activar sesión**. La red Wi-Fi del sistema puede permanecer conectada.

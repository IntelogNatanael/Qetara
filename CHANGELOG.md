# Cambios en Qetara

## 1.1.0 — 6 de septiembre de 2026

Esta versión convierte la base Android y PC en una entrega local instalable, con una experiencia de conexión, envío y recuperación más clara.

- Android organiza Conectar, Enviar, Chat, Canal y Descargas, adapta la navegación al tamaño del texto y pide permisos cuando una acción los necesita.
- La sesión se confirma antes de ofrecer el envío. La dirección manual, el transporte y el equipo seleccionado se mantienen coherentes.
- Cerrar sesión detiene la recepción y los envíos, conserva los archivos y mantiene la sesión cerrada al volver a abrir la aplicación. La reactivación espera el cierre de los trabajos anteriores.
- Los adjuntos y borradores conservan su contexto de envío, conversación o canal. Descargas incorpora búsqueda, orden y apertura o compartición.
- El chat mantiene la escritura y el envío accesibles con el teclado abierto en pantallas pequeñas y con texto ampliado. Los envíos completados conservan una confirmación visible.
- PC incorpora espacios de compartir, recibir, mensajes, actividad y ajustes, con destino visible, progreso y cancelación.
- El intercambio automático de credenciales entre Android usa un canal Noise autenticado y aprobación por huella. El mecanismo heredado en texto plano se rechaza. El PC utiliza código y PIN manuales.
- Los mensajes de grupos Wi-Fi Direct se restringen al grupo activo y a participantes observados en su interfaz; la confianza histórica de la LAN no acredita pertenencia al grupo.
- Los receptores limitan conexiones simultáneas, validan tamaños y nombres, verifican contenido antes de publicar y conservan archivos anteriores al repetir nombres.
- Se añaden licencia MIT, avisos de terceros accesibles sin conexión, guías de uso y contribución, scripts de verificación y empaquetado, y configuración de CI.
- Android dispone de configuración de firma de distribución mediante variables de entorno. Windows incluye un runtime Java actualizado, con sus avisos y fuentes correspondientes en la entrega.

El detalle de comprobaciones y sus límites se registra en `docs/VALIDATION.md`. La configuración para otros sistemas operativos no equivale a haber ejecutado sus instaladores. El repositorio remoto permanece con su visibilidad original.

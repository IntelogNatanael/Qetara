# Privacidad

Qetara permite compartir archivos y mensajes directamente entre dispositivos. No requiere una cuenta y el código de las aplicaciones no incorpora un servicio de analítica ni un servidor externo de transferencia. Al abrir un enlace de GitHub, el navegador se conecta a ese sitio bajo sus propias condiciones.

## En la red

El descubrimiento anuncia información para reconocer y localizar dispositivos en la red local. Otros equipos de esa red pueden observar esos anuncios; un nombre visible no certifica quién controla el dispositivo. Los códigos de sesión y PIN deben compartirse únicamente con el destinatario previsto.

La respuesta pública de descubrimiento no revela si Qetara recuerda o confía en quien consulta. La verificación de identidad sigue realizándose al establecer una conexión autenticada.

Los archivos y mensajes se envían al equipo seleccionado. El canal Wi-Fi permite comunicación con los participantes de la red que se hayan unido: revisa el contexto antes de enviar contenido.

## En el dispositivo

Qetara conserva los archivos recibidos, historial de transferencias, mensajes y preferencias necesarios para sus funciones. La identidad criptográfica y las decisiones de confianza se almacenan localmente. El manifiesto desactiva la copia de seguridad de Android y las reglas excluyen los dominios de datos privados de la aplicación de las copias y de la transferencia entre dispositivos. Esto no elimina copias que se hayan realizado anteriormente ni afecta a archivos que hayas exportado. El comportamiento de las herramientas de migración del fabricante requiere comprobación en cada equipo.

Android también conserva el estado de la tarea para poder recrear la pantalla: borradores, referencias a archivos seleccionados y las credenciales de sesión con su vencimiento original. Recuperar ese estado no confirma al destinatario ni reactiva una sesión cerrada. Los permisos de acceso a cada archivo se vuelven a comprobar.

Los archivos que exportes o compartas con otra aplicación quedan también sujetos al almacenamiento y las reglas de esa aplicación. Desinstalar Qetara puede eliminar sus datos privados; conserva los archivos importantes antes de hacerlo.

En PC, los archivos recibidos se guardan en la carpeta que elijas. Las preferencias de la interfaz no deben guardar el PIN o código de sesión como preferencias permanentes. Los registros de diagnóstico pueden contener nombres de archivos, equipos o direcciones de red: revísalos antes de compartirlos.

## PIN, portapapeles y vista de aplicaciones recientes

En Android, el PIN aparece oculto. Puedes tocar **Mostrar** para consultarlo y **Ocultar** para volver a cubrirlo. Qetara lo oculta de nuevo al pasar a segundo plano, recrear la pantalla o cambiar la sesión. El valor de la sesión se conserva según las reglas anteriores; la elección de mostrarlo no se guarda.

Al copiar un código, un mensaje o texto seleccionado dentro de Qetara, la aplicación marca el contenido como sensible para que Android y los teclados compatibles eviten mostrarlo en su vista previa. Esto es una indicación de presentación: no cifra el portapapeles, no impide pegar y no impide que la aplicación en la que pegas reciba el contenido. Qetara no incorpora un lector global del portapapeles ni borra automáticamente lo que copies.

En Android 13 o posterior, Qetara pide al sistema que no guarde una captura de su contenido para la miniatura de aplicaciones recientes. La API cubre la miniatura cuando la actividad ha dejado de estar iniciada: una vista en directo durante la transición puede seguir mostrando la pantalla. Esta protección no se aplica a Android 12L y anteriores. Las capturas de pantalla que realices con Qetara abierta siguen disponibles.

La implementación sigue las API de Android para [contenido sensible en el portapapeles](https://developer.android.com/develop/ui/compose/touch-input/copy-and-paste#sensitive-content), [miniaturas recientes](https://developer.android.com/reference/android/app/Activity#setRecentsScreenshotEnabled(boolean)) y [exclusiones de copia de seguridad](https://developer.android.com/identity/data/autobackup).

## Permisos de Android

| Permiso o capacidad | Uso |
| --- | --- |
| Red y estado Wi-Fi | Descubrir equipos, establecer conexiones y transferir datos. |
| Dispositivos Wi-Fi cercanos | Funciones de Wi-Fi Direct en versiones modernas de Android. |
| Ubicación en Android 12 y anteriores | Requisito del sistema para descubrir redes o pares Wi-Fi Direct. |
| Notificaciones y servicio en primer plano | Mostrar y mantener visible una transferencia o conexión activa. |
| Almacenamiento en Android 9 y anteriores | Exportar archivos donde las versiones antiguas exigen ese permiso. |
| Selección y acceso a archivos compartidos | Acceder a los elementos que seleccionas o envías desde otra app. |

Los permisos se solicitan en relación con la función que los necesita. Rechazar uno puede limitar esa función; no debería provocar un cierre inesperado.

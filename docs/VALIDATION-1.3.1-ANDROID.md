# Validación móvil de Qetara 1.3.1

Corrección de apertura de archivos Android, 7 de septiembre de 2026. Versión 1.3.1, código 6. La entrega móvil conserva los certificados de distribución y depuración anteriores y es compatible con Qetara PC 1.3.0. El código de red y los protocolos no cambian.

## Causa y solución

Las rutas `content://` del historial y de Flash se abrían con `*/*` sin consultar al proveedor. Un PNG exportado correctamente como `image/png` perdía su tipo al abrirse. La llamada directa a `ACTION_VIEW` podía seleccionar una aplicación predeterminada para ese tipo genérico.

Android puede abrir directamente cuando sólo hay una aplicación compatible; Qetara no fuerza la aparición de una lista vacía o de aplicaciones incompatibles.

La apertura conserva el tipo MIME concreto del proveedor y, si falta o es genérico, consulta el nombre visible y el mapa de extensiones de Android. Usa `ACTION_CHOOSER` con título «Abrir con», URI de contenido y permiso temporal de lectura. No establece un paquete de destino. Compartir, exportar y Guardar una copia comparten el mismo criterio de tipo de archivo. El proveedor no se exporta ni concede escritura; su única nueva raíz pública es `Download/Qetara` para las exportaciones heredadas.

## Verificación

- Compilación debug, release firmada y APK de instrumentación completadas; Android Lint sin errores (tres advertencias existentes por variante).
- 180 pruebas unitarias Android correctas, incluidas siete nuevas pruebas de política MIME; cero fallos, errores u omisiones.
- Nueve comprobaciones instrumentadas correctas sobre Android 16/API 36: mapeo de imágenes, PNG y PDF exportados a MediaStore, apertura privada, conversión de URI heredada, límite de la raíz pública, selector y permisos de lectura, compartir, Guardar una copia y tipo desconocido. Se eliminaron los archivos creados por la instrumentación.
- Recorrido visual real: pulsar un PNG recibido en Qetara abrió y mostró la imagen en Fotos con `image/png`; pulsar el PDF de prueba abrió y mostró el documento en el visor PDF con `application/pdf`. No se subieron archivos a Drive.
- Android abrió directamente el único visor compatible en esa configuración. La solicitud de `ACTION_CHOOSER`, la ausencia de aplicación fija y la concesión de sólo lectura se verificaron con instrumentación. Guardar una copia se verificó a nivel de contrato/Intent; no se atribuye una prueba visual completa a ese flujo.
- El historial visual con sesión cerrada estaba vacío pese a conservar registros previos. No se activó una sesión para modificar ese estado ajeno a esta corrección; las rutas `content://` del proveedor sí se ejercitaron directamente en la instrumentación.

APK debug utilizado en las pruebas: SHA-256 `c2f60a3776a0c47e01402914af050b5a4e341b375c858dc9fb379ffdcd762a3a`. La entrega incluye manifiesto de resultados, capturas, Intents y registros verificables en `Verificacion/`. La sesión del teléfono físico permaneció pausada.

## Alcance

No se analizan los bytes para inferir un formato. Un archivo sin extensión y sin tipo informado permanece desconocido; un proveedor que declara un tipo concreto incorrecto requiere diagnóstico aparte. El selector muestra las aplicaciones instaladas que declaran admitir el tipo: Drive puede aparecer si admite imágenes, pero Qetara no lo selecciona por su cuenta. Una aplicación puede anunciar compatibilidad y luego no poder decodificar un formato específico.

La sesión del teléfono físico continúa pausada. Las nuevas pruebas se realizan en el emulador dedicado Android 16; no constituyen una nueva prueba física en Android 7–9. Los tipos HEIC/HEIF/AVIF se verifican como metadatos, sin atribuir una prueba de decodificación de esas imágenes.

Referencias de plataforma: [tipo de una URI de contenido](https://developer.android.com/reference/android/content/ContentResolver#getType(android.net.Uri)), [selector y propagación de permisos](https://developer.android.com/reference/android/content/Intent#createChooser(android.content.Intent,%20java.lang.CharSequence)), [FileProvider y concesiones por archivo](https://developer.android.com/reference/androidx/core/content/FileProvider).

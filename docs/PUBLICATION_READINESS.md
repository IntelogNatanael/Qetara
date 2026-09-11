# Preparación de la publicación pública

Qetara 1.4.0, código Android 7. Preparación del 11 de septiembre de 2026; código de la candidata fijado en `588a92f2617815b5744eeb91a1da463c5c685f90`. Este documento registra resultados y condiciones de publicación; no anuncia una release pública ni la aceptación de F-Droid.

## Historial y material privado

Gitleaks 8.30.1, con reglas predeterminadas, examinó todas las referencias y reflogs (`--all --reflog --full-history`) y el árbol publicable. La descarga Windows x64 se contrastó con el checksum oficial y con el digest de la API de GitHub: `d29144deff3a68aa93ced33dddf84b7fdc26070add4aa0f4513094c8332afc4e`. Se usaron redacción completa, inspección de archivos comprimidos y decodificación limitada; las excepciones de comentarios no se aceptaron.

El pase sobre la candidata examinó 29 commits por referencias/reflog y 300 archivos publicables. Sus tres coincidencias por ámbito se revisaron: una clave de `SavedStateHandle` y `TOKEN_ALPHABET` son identificadores y datos del protocolo; la tercera es el checksum público de Gitleaks escrito arriba. No son credenciales. Una inspección local previa cubrió 28 objetos commit y 501 objetos blob, incluidos los no alcanzables, y buscó formatos de claves, tokens, contraseñas, rutas privadas y datos personales. No se identificaron credenciales reales expuestas. Estos métodos no demuestran ausencia absoluta de secretos.

Un pase adicional revisó los diez archivos posteriores de documentación, herramienta de compilación y QA por Wi-Fi física: cero errores y únicamente el falso positivo conocido del checksum público. Se comprobaron también los quince enlaces locales de esos documentos. Las últimas adiciones textuales registran el resultado de la herramienta y aclaran que la clave no forma parte de la entrega; no incorporan credenciales.

Los almacenes de firma y su configuración se localizaron fuera del repositorio. `.gitignore` protege también los directorios locales de herramientas, entregas y archivos `.env`, sin depender solamente del `exclude` de esta copia. La entrega de fuentes debe salir de un commit mediante Git, no de comprimir el directorio de trabajo con sus archivos ignorados.

El historial conserva correos de autoría, incluidos dominios de correo personal e institucional. Publicar Git también publica esos metadatos. Se mantiene la atribución existente y no se ha reescrito el historial; este hecho debe formar parte de la decisión de visibilidad pública.

## Licencias y recursos

El código propio conserva MIT. El [inventario Android release](../licenses/android-release-dependency-inventory.json) registra 88 coordenadas externas: todas estaban en el inventario previo y las 88 sumas de sus POM coinciden. No aparecen dependencias resueltas de Firebase, Play Services, anuncios, analítica o actualización automática. Un inventario de POMs no sustituye la revisión de los componentes internos de cada biblioteca.

Los doce SVG conservados en `design/penpot/extracted-icons` tienen ahora procedencia Circum Icons y licencia MPL 2.0 documentadas por archivo. Los TTF Noto conservan OFL. El logotipo GitHub tiene un aviso separado que documenta su uso como enlace a un perfil y los límites de ese permiso. Véanse [avisos de terceros](../THIRD_PARTY_NOTICES.md) y las tablas de procedencia enlazadas allí. Los paquetes `.penpot` locales excluidos de Git no se incluyen en la entrega de fuentes.

Android incorpora código nativo de `androidx.graphics:graphics-path:1.0.1`; no debe describirse como una aplicación sin bibliotecas nativas. La [revisión de sus fuentes oficiales](../licenses/androidx-graphics-path-1.0.1-PROVENANCE.md) fija el commit de publicación, los hashes y las atribuciones Apache 2.0/AOSP de los fuentes y auxiliares matemáticos. No se identificó una biblioteca Skia adicional redistribuida ni un runtime libc++ enlazado. Se documenta también la excepción LLVM aplicable a fragmentos compilados de cabeceras. Esto no equivale a reconstruir el AAR byte a byte. Las comprobaciones del APK anterior no se atribuyen automáticamente a la candidata.

## Identidad, firma y versión

El usuario eligió `io.github.intelognatanael.qetara`; se conserva la clave de distribución existente. Los detalles y efectos para las instalaciones anteriores están en [Identidad Android](ANDROID_IDENTITY.md). La versión y su código se centralizan en `gradle.properties`, y Build Tools se fija en 36.0.0.

La firma se aplicó a una copia de la compilación unsigned limpia, fuera de Gradle, preservando la alineación ZIP. Se comprobaron el identificador, la versión, la alineación ZIP de 16 KiB y la firma contra la huella esperada. El APK debug no sustituye al APK de distribución.

## Verificación de la candidata

Los resultados están en [Validación 1.4.0](VALIDATION-1.4.0.md): 301 pruebas automatizadas iniciales aprobadas, cero errores de lint, comprobaciones instrumentadas sobre el APK final en emulador y teléfono, y lotes Flash de dos archivos por dirección a través de una Wi-Fi real. Dos clones limpios Windows y Linux produjeron APKs idénticos; la copia pública de firma también produjo un APK firmado idéntico. Véase [Reproducibilidad](REPRODUCIBILITY-1.4.0.md). Esta verificación local no equivale a una compilación o aceptación oficial de F-Droid.

La ejecución anterior de CI [34599364412](https://github.com/IntelogNatanael/Qetara/actions/runs/34599364412) completó las compilaciones y pruebas de sus cuatro trabajos, pero falló al subir artefactos por cuota agotada. El workflow revisado sigue construyendo y probando Android y PC; la subida opcional queda limitada a informes en ejecuciones manuales, con retención de tres días. No se borraron artefactos existentes ni se aumentó la cuota. Los paquetes con Java no se suben desde CI sin sus fuentes correspondientes.

La nueva ejecución de CI [34602646699](https://github.com/IntelogNatanael/Qetara/actions/runs/34602646699) aprobó los cuatro trabajos para el commit de la candidata. El empaquetado Windows utilizó `scripts/package.ps1` y el paquete de fuentes correspondiente al runtime; verificó su procedencia, hashes y correspondencia de versión. El ejecutable extraído del portable aprobó la autoprueba de transferencia y reanudación con su Java incluido. El APK Android de entrega procede de la compilación limpia, según [el procedimiento de release](RELEASING.md).

## Antes de la publicación y de F-Droid

La aceptación de la release estable debe completar el recorrido visual de selección y envío de varios archivos, cancelación y descubrimiento UDP inicial/manual de Flash, además de instalación MSI. El informe distingue la cobertura automatizada, los contratos instrumentados y la transferencia física ya completados de esos recorridos pendientes.

Quedan fuera de estos tres pasos la acción de hacer público GitHub, publicar la release y enviar la solicitud al catálogo. Para esa fase deben quedar revisados el contacto privado de seguridad, la ficha y capturas de la aplicación y la receta F-Droid. La reutilización de nuestra firma en F-Droid exige verificar la reconstrucción y copia de firma; la aceptación final corresponde a sus mantenedores.

Referencias: [Gitleaks 8.30.1](https://github.com/gitleaks/gitleaks/releases/tag/v8.30.1), [política de inclusión](https://f-droid.org/en/docs/Inclusion_Policy/), [reproducibilidad F-Droid](https://f-droid.org/docs/Reproducible_Builds/).

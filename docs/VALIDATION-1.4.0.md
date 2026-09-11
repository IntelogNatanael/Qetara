# Validación de la candidata Qetara 1.4.0

Preparación del 11 de septiembre de 2026. Android: versión 1.4.0, código 7, identificador `io.github.intelognatanael.qetara`. PC comparte la versión 1.4.0. Las entregas anteriores se conservan.

## Verificación automatizada inicial

`scripts/verify.ps1 -Offline` terminó correctamente con Temurin 21.0.12.1+1, Gradle 9.1.0 y Android SDK 36. Se ejecutaron o reutilizaron según los inputs de Gradle 199 pruebas Android, 57 PC y 45 del protocolo: **301 pruebas, cero fallos, errores u omisiones**. Los casos de selección y lotes añadidos previamente se detallan en [Validación](VALIDATION.md).

La compilación debug contiene el nuevo identificador, versión y código comprobados en `output-metadata.json`. Lint debug y la ejecución separada de lint release terminaron sin errores; conservan las tres advertencias existentes `ModifierParameter`, `UsableSpace` y `UseKtx`.

La autoprueba CLI completó una transferencia local de 2 MiB y otra reanudada desde un parcial, verificando el hash. Son pruebas por loopback; no equivalen a un recorrido físico entre teléfono y ordenador en un router.

## Cierre de la candidata

La firma, los paquetes, las comprobaciones instrumentadas y la reconstrucción Linux deben quedar vinculados a un commit concreto y a sus hashes antes de anunciar la candidata como validada. Los resultados se incorporan después de esas ejecuciones; este estado inicial no las da por realizadas.

Los criterios de publicación, auditoría e identidad se conservan en [Preparación de publicación](PUBLICATION_READINESS.md) e [Identidad Android](ANDROID_IDENTITY.md). No se ha publicado una release pública ni enviado una solicitud a F-Droid desde esta preparación.

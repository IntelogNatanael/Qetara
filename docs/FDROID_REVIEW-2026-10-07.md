# Revisión F-Droid del 7 de octubre de 2026

**Registro histórico del preflight R8**, anterior al commit y a la versión
definitiva. El estado posterior de la candidata **1.4.3/código 10** se sigue
en [Validación 1.4.3](VALIDATION-1.4.3.md). Los resultados, hashes y pendientes
que figuran a continuación se conservan con su alcance original; no describen
por sí solos el APK definitivo ni su publicación.

Las observaciones del mantenedor en la MR !51433 piden usar `Binaries`, retirar
`MaintainerNotes` y habilitar R8. Se preparan en la rama local `codex/fdroid-r8`,
base `1ebb9181aca49ac57a4898a8f061e735785b96be`, separada de los cambios locales
del Canal Wi-Fi. No se modificaron la etiqueta ni el APK público 1.4.2.

## Cambios preparados

- `Binaries` global usa
  `https://github.com/IntelogNatanael/Qetara/releases/download/v%v/Qetara-%v.apk`.
  Se retiró `binary` de la build y se conserva el certificado permitido.
- Se eliminó `MaintainerNotes`; la documentación histórica sigue disponible.
- La variante Android `release` activa `isMinifyEnabled` e `isShrinkResources`
  con `proguard-android-optimize.txt`. No se añadieron reglas propias de excepciones globales,
  `dontwarn`, `dontobfuscate` ni reglas para conservar paquetes completos.

La [referencia oficial de F-Droid](https://f-droid.org/en/docs/Build_Metadata_Reference/#Binaries)
define la sustitución de `%v` por la versión. La
[configuración oficial de Android](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)
respalda las dos opciones para AGP 9.0.0, que ya utiliza el proyecto.

## Comprobaciones de metadatos

fdroidserver 2.4.5 en un directorio nuevo: `readmeta`, `rewritemeta` y
`lint --force-yamllint` terminaron con código 0. Una segunda normalización no
cambió los bytes. La receta canónica tiene 713 bytes y SHA-256
`304f169c53f4d5dc6427c739cb01b236ae7b32f478eeb463b3a52bd290bade9d`.

La receta todavía apunta a las fuentes y al APK originales 1.4.2/9. Esta
comprobación acredita los dos ajustes de metadatos; no incorpora R8 a esa
versión publicada. Las fuentes Android modificadas requieren una nueva versión,
commit y artefacto, previsiblemente 1.4.3/código 10, antes de cambiar el pin.

## Revisión de compatibilidad R8

La revisión estática de `app` y `protocol` no identificó JNI propio,
serialización reflectiva ni carga dinámica de clases de la aplicación. Las
entradas Android están declaradas en el manifiesto y el ViewModel tiene una
fábrica con construcción directa. La reflexión explícita detectada usa clases
del framework: `java.nio.file.Files` y `javax.crypto.AEADBadTagException`.

Se inspeccionaron las 31 clases del JAR real `noise-java:0.0.1`, SHA-256
`bfff9bdf6e4111407e447e7c79d5524b791de57f7cf7bbb096383aa05f94939f`.
Las fábricas criptográficas instancian sus clases directamente y no requieren
una regla que conserve todo el paquete. La revisión estática no sustituye una
prueba de ejecución del DEX optimizado.

## Resultado de compilación y regresión

Temurin 21.0.12.1+1, Gradle 9.1.0, SDK 36, Build Tools 36.0.0, AGP 9.0.0.
Se ejecutó en el worktree aislado nuevo:

```powershell
.\gradlew.bat :app:assembleRelease :app:testDebugUnitTest :app:lintRelease :protocol:test --offline --no-daemon --max-workers=2 --console=plain
```

Resultado: `BUILD SUCCESSFUL` en 4 min 47 s; 80 tareas ejecutadas. Android:
218 tests aprobados; protocolo: 55 aprobados. Cero fallos, errores u omitidos.
Lint release: cero errores y tres advertencias (`UsableSpace` y dos `UseKtx`).
El primer intento usó `testReleaseUnitTest`, tarea inexistente en esta
configuración, y falló antes de compilar. Su log se conserva por separado.

Se ejecutó `minifyReleaseWithR8`; `mapping.txt` identifica R8 9.0.32 y min API 24.
Se generaron configuration, mapping, resources, seeds y usage. La configuración
combinada contiene reglas del SDK y de consumidores AndroidX/coroutines; no se
neutralizó R8 mediante `dontshrink`, `dontoptimize` o `dontobfuscate`.

APK experimental **sin firma**, todavía con versión 1.4.2/código 9:
3 027 904 bytes, SHA-256
`4250f295e6355137e22a8b6bfc474ec4f7f6cb3c7d971daffb978286427cdee2`.
El APK sin firma de la compilación limpia histórica medía 14 837 252 bytes;
la reducción observada es de aproximadamente 79,6 %. No es un APK publicado,
instalado ni comprobado reproducible. Su metadata VCS señala la base Git; el
cambio R8 aún está sin commit, por lo que ese SHA de fuente por sí solo no lo
reproduce. No usarlo como sustituto del APK público 1.4.2.

La evidencia se conserva en el workspace principal bajo
`.local/fdroid-r8-2026-10-07/`: receta canónica, validadores, XML unitarios, lint,
configuración/mapping R8, hashes y diff de fuentes. Los logs de la compilación
están en `.local/r8-preflight-2026-10-07.log` y
`.local/r8-preflight-2026-10-07-retry.log`.

## Alcance antes de actualizar la solicitud

Los unit tests normales no ejecutan el DEX procesado por R8. La instrumentación
actual llama clases internas de la app y no se puede atribuir sin adaptación
al APK ofuscado. No se deben añadir reglas globales a producción sólo para
conservar la API de pruebas. Al comenzar esta fase ADB no detectó dispositivos.

Para cerrar la nueva entrega faltan una candidata con versión/código nuevos,
pruebas de ejecución de su APK optimizado, compilaciones limpias independientes,
escáneres, firma con la clave existente y reproducción de esa firma. Sólo después
corresponde publicar el nuevo APK y actualizar el commit/versión de la MR.
Los resultados de 1.4.2 no acreditan el artefacto optimizado.

No se enviaron respuestas por correo ni comentarios públicos en esta fase.

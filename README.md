# Qetara

Comparte archivos y mensajes entre tus dispositivos, sin cuentas.

Qetara conecta Android y PC por tu red local. Entre dispositivos Android también admite Wi-Fi Direct. El código está disponible bajo la [licencia MIT](LICENSE).

## Empezar

1. Abre Qetara en ambos dispositivos y conéctalos a la misma red Wi-Fi.
2. En el equipo que recibirá, prepara una sesión y deja el receptor activo.
3. En el equipo que envía, elige el destino y usa el código y PIN de esa sesión.
4. Elige un archivo y envíalo. Comprueba la confirmación antes de cerrar la aplicación.

En Android puedes compartir hacia Qetara desde otras aplicaciones. Si tu red impide que los dispositivos se vean, usa la dirección local del receptor o, entre Android, Wi-Fi Direct.

Consulta la [guía de uso](docs/USER_GUIDE.md) para conectar equipos, recuperar un envío y encontrar los archivos recibidos.

### Compartir con Flash

**Flash es opcional y empieza apagado.** Actívalo en Android y PC para compartir un archivo por la misma red local sin crear ni escribir código de sesión o PIN. Elige el archivo y el equipo, compara la verificación que aparece en ambas pantallas y acepta el envío en los dos dispositivos. Puedes seguir el progreso, cancelar y abrir lo recibido. La activación termina a los 30 minutos o cuando la desactives.

Flash tiene una sesión temporal independiente; la conexión habitual, los mensajes y los controles avanzados siguen disponibles. Consulta [la guía de Flash](docs/FLASH.md), incluidos los pasos cuando la red impide descubrir equipos.

## Qué incluye

- **Android:** conexión por LAN y Wi-Fi Direct, compartir desde otras apps, mensajes directos, canal Wi-Fi y biblioteca de descargas.
- **PC:** aplicación de escritorio Compose y modo de línea de comandos, con envío, recepción y mensajes en red local.
- **Protocolo compartido:** validación de las transferencias y contrato WDRP utilizado por ambas aplicaciones.

Los archivos viajan directamente entre los dispositivos. El descubrimiento local anuncia información necesaria para encontrarlos; consulta [privacidad](docs/PRIVACY.md) y [seguridad](SECURITY.md).

## Compilar

Necesitas JDK 17 o superior (JDK 21 usado para esta entrega). Android requiere el SDK con plataforma 36; el modo de compilación exclusivo de PC no requiere ese SDK. El wrapper incluido usa Gradle 9.1.0 y comprueba su descarga con SHA-256.

En Windows:

```powershell
.\scripts\verify.ps1
.\gradlew.bat :pc:run
```

En macOS o Linux:

```sh
./gradlew :protocol:test :pc:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :pc:run
```

Para compilar solamente PC usa `./gradlew -PqetaraDesktopOnly=true :pc:run` o, en Windows, `.\scripts\verify.ps1 -DesktopOnly`.

Configura la variable de entorno ANDROID_HOME con la ubicación de tu SDK, o crea local.properties a partir de local.properties.example. La primera compilación descarga dependencias. Con caché completa puedes usar --offline.

Para crear los paquetes de distribución, consulta [la guía de publicación](docs/RELEASING.md). El empaquetado de PC incluye el runtime de Java; cada instalador se genera en su propio sistema operativo.

## Contribuir

Las mejoras deben hacer más sencilla una tarea real y mantener los datos del usuario a salvo. Lee [CONTRIBUTING.md](CONTRIBUTING.md), la [arquitectura](ARCHITECTURE.md) y los [principios de experiencia](docs/EXPERIENCE.md).

Para problemas de seguridad, sigue [SECURITY.md](SECURITY.md). Los recursos y dependencias de terceros conservan sus propias licencias: [avisos de terceros](THIRD_PARTY_NOTICES.md).

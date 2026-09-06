# Qetara PC (Compose Desktop + CLI)

Este módulo ejecuta una app de escritorio con dos modos:

- **GUI Compose Desktop** (recomendado para uso diario)
- **CLI interactivo** (automatización / debug)

Mantiene el mismo protocolo y motor de transferencia (`WDRP v4 + Noise`).

## Ejecutar GUI Compose Desktop

```powershell
.\gradlew.bat :pc:run
```

También puedes lanzar GUI explícitamente:

```powershell
.\gradlew.bat :pc:run --args="--gui"
```

Desde la GUI puedes:

- Iniciar/detener receptor (`Android/PC -> Qetara PC`)
- Enviar archivo a Android o a otra PC (`Qetara PC -> Android/PC`)
- Enviar mensajes y archivos por chat directo o Canal Wi-Fi a equipos Qetara detectados
- Configurar token, PIN, host, puerto, carpeta destino, reintentos y TTL
- Ver eventos operativos en una consola integrada con auto-scroll y errores resaltados
- Separar visualmente el bloque de recepción, el bloque de envío y el historial de eventos

## Ejecutar CLI interactivo

```powershell
.\gradlew.bat :pc:run --args="--no-gui --token ABCD1234 --pin 123456"
```

Comandos interactivos:

- `help`
- `status`
- `host <ip>`
- `token <TOKEN>`
- `pin <PIN6>`
- `send <ip> "<ruta archivo>"`
- `send "<ruta archivo>"` (usa host por defecto)
- `exit`

## Envío one-shot Qetara PC -> Android/PC

```powershell
.\gradlew.bat :pc:run --args="--no-gui --token ABCD1234 --pin 123456 --send-host 192.168.1.20 --send-file C:\Temp\foto.jpg --no-interactive"
```

## Envío PC -> PC

En la PC receptora:

1. Abre Qetara PC.
2. Usa el mismo token y PIN que la PC emisora.
3. Define la carpeta destino.
4. Pulsa `Iniciar receptor`.
5. Anota la IP local de esa PC y el puerto configurado.

En la PC emisora:

1. Abre Qetara PC.
2. Usa el mismo token, PIN y puerto.
3. En `Host destino`, escribe la IP local de la PC receptora.
4. Elige el archivo.
5. Pulsa `Enviar archivo`.

También puedes hacerlo por CLI:

```powershell
.\gradlew.bat :pc:run --args="--no-gui --token ABCD1234 --pin 123456 --send-host 192.168.1.50 --send-file C:\Temp\documento.pdf --no-interactive"
```

## Solo receptor (sin shell interactivo)

```powershell
.\gradlew.bat :pc:run --args="--no-gui --token ABCD1234 --pin 123456 --no-interactive"
```

## Self-test E2E local

```powershell
.\gradlew.bat :pc:run --args="--no-gui --token ABCD1234 --pin 123456 --self-test --no-receiver --no-interactive"
```

El self-test valida:

- Envío completo + hash
- Reanudación desde archivo parcial + hash

## Compatibilidad de protocolo

- `WDRP v4`
- Noise `NoisePSK_XX_25519_ChaChaPoly_SHA256`
- Prólogo: `WifiDrop/v4|FILE`
- Reanudación por offset (`SECURE_FRAME_FILE_RESUME`)
- Integridad final `SHA-256`

## Notas

- Puerto por defecto: `8988`
- Carpeta destino por defecto: `~/Downloads/Qetara`
- PC utiliza emparejamiento manual: introduce el mismo código y PIN en ambos equipos. La opción retirada `--allow-credentials-share` se rechaza; PC no entrega credenciales por red.
- En el primer envío PC → Android, aprueba la conexión en el teléfono, compara la huella mostrada por ambos equipos y vuelve a enviar. Consulta la [guía de escritorio](../docs/DESKTOP_GUIDE.md).

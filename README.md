# Qetara

Qetara es una app local-first para transferir archivos y mensajes sin cables ni cuentas externas. El proyecto incluye una aplicacion Android, una aplicacion de escritorio para PC y un modulo compartido de protocolo.

## Modulos

- `app`: aplicacion Android con Wi-Fi Direct, LAN, chat, canal Wi-Fi, descargas y flujo de envio.
- `pc`: Qetara PC, con interfaz Compose Desktop y modo CLI para automatizacion/debug.
- `protocol`: contrato y utilidades compartidas del protocolo WDRP.

## Estado local rapido

Compilar Android:

```powershell
.\gradlew.bat :app:assembleDebug --offline
```

Compilar Qetara PC:

```powershell
.\gradlew.bat :pc:classes --offline
```

Ejecutar Qetara PC:

```powershell
.\gradlew.bat :pc:run --args="--gui"
```

Verificacion completa local:

```powershell
.\scripts\verify.ps1 -Offline
```

## GitHub

El repositorio incluye workflow en `.github/workflows/qetara-ci.yml` para compilar Android, ejecutar lint/tests unitarios y correr el self-test del modulo PC.

Antes de subir:

1. Verifica que `local.properties`, `.gradle/`, `.kotlin/`, `build/`, APK/AAB y capturas temporales no entren al commit.
2. Ejecuta `.\scripts\verify.ps1 -Offline` si ya tienes dependencias en cache.
3. Crea el primer commit local y conecta el remoto de GitHub.

## Documentacion

- Arquitectura general: `ARCHITECTURE.md`
- Qetara PC: `pc/README.md`
- Referencias de diseno: `design/penpot/README.md`

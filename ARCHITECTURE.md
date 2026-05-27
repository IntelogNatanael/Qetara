# Arquitectura

## Objetivo

Separar claramente `frontend`, `estado de pantalla` y `backend operativo` para que los cambios de UX no rompan transporte, sesión o cola de envíos.

## Capas

### 1. UI Compose

Archivos principales:

- `app/src/main/java/com/example/wifidrop/P2pUi.kt`
- `app/src/main/java/com/example/wifidrop/P2pMessagesTab.kt`
- `app/src/main/java/com/example/wifidrop/P2pUiShared.kt`

Responsabilidad:

- Renderizar pantalla
- Emitir eventos de intención del usuario
- No hablar directamente con servicios Android de transferencia o Wi-Fi Direct

### 2. Presentación

Archivos principales:

- `app/src/main/java/com/example/wifidrop/MainActivity.kt`
- `app/src/main/java/com/example/wifidrop/P2pScreenState.kt`
- `app/src/main/java/com/example/wifidrop/P2pExperienceState.kt`

Responsabilidad:

- Orquestar estado observable para UI
- Traducir reglas de producto a `P2pScreenState`
- Delegar operaciones al contrato backend

### 3. Backend para UI

Archivo principal:

- `app/src/main/java/com/example/wifidrop/backend/P2pBackend.kt`

Responsabilidad:

- Ser el único punto de entrada del frontend hacia:
  - `WifiDirectController`
  - `TransferForegroundService`
- Exponer estado backend con `StateFlow`
- Encapsular detalles Android/runtime

Regla:

- `MainActivity` y cualquier UI nueva deben usar `P2pBackend`, no `TransferForegroundService` ni `WifiDirectController` de forma directa.

### 4. Runtime / transporte

Archivos principales:

- `app/src/main/java/com/example/wifidrop/TransferForegroundService.kt`
- `app/src/main/java/com/example/wifidrop/WifiDirectController.kt`
- `app/src/main/java/com/example/wifidrop/FileTransfer.kt`

Responsabilidad:

- Red
- Protocolo
- Cola de envío
- Mensajería
- Sesión segura

## Reglas de mantenimiento

1. Si un cambio es visual, debería tocar UI o presentación, no runtime.
2. Si una pantalla necesita una nueva operación backend, se agrega primero al contrato `P2pBackend`.
3. Si un dato es puramente visual, no debe vivir dentro de `TransferForegroundService`.
4. Si una regla aplica a más de una tab, debe modelarse antes como estado de presentación o backend, no duplicarse en Compose.

## Siguiente orden recomendado

1. Extraer el armado de `P2pScreenState` desde `MainActivity` a un mapper/factory dedicado.
2. Mover stores persistentes a un paquete `data/`.
3. Separar `ui/`, `presentation/`, `backend/` y `runtime/` físicamente en paquetes.
4. Añadir tests de reglas de producto para `Wi‑Fi Direct`, `Chat` y `Global LAN`.

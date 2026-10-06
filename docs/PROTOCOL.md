# Qetara transport and compatibility

Qetara uses a local TCP connection (port 8988 by default). Android and desktop share the constants, credential generation, input validation, received-file publication and retry receipts in the `protocol` module. The application protocol remains WDRP version 4 so existing version 4 file and message transfers can still interoperate.

## What is visible on the network

The initial envelope contains the magic `0x57445250`, protocol version, packet type, device ID, device label and a client nonce. Discovery, the authentication challenge and its expiry, and an initial success/error response are visible to the local network. Device labels and discovery advertisements are hints, not authenticated identity.

For file, message and presence packets, the challenge response and the existing `NoisePSK_XX_25519_ChaChaPoly_SHA256` handshake use the session token and PIN. File names, file lengths, SHA-256 hashes, file bytes and chat payloads are then carried in authenticated encrypted frames. A SHA-256 file digest is verified before a received file is published. It detects incomplete or changed content; it does not prove that a document is harmless.

Generated tokens, PINs and nonces use `SecureRandom`. A manually entered token can be 4–32 letters/digits and the PIN contains six ASCII digits. Session credentials should be exchanged with the intended recipient. A shared session secret is not a substitute for an independently verified identity.

## Encrypted credential capability

The original `PACKET_CREDENTIALS_REQUEST` (6) is permanently retired. Every new receiver rejects it with `secure_credentials_required`, including requests claiming an already trusted device ID. No new client falls back to that plaintext exchange.

Android supports the explicit `PACKET_SECURE_CREDENTIALS_REQUEST` (12) extension:

1. The client sends the ordinary envelope with packet type 12.
2. A supporting host sends a normal `PACKET_RESULT` containing `true` and `secure_credentials_v1`. An unsupported peer returns an error; the client offers manual token/PIN entry.
3. Both sides perform `Noise_XX_25519_ChaChaPoly_SHA256`, without a PSK, with the prologue `WifiDrop/v4|CREDENTIALS`.
4. The host obtains the requesting device's static public key from the completed handshake. An unfamiliar key requires explicit host approval. The approval is bound to that observed key, not just the claimed device name or ID. A different key for an existing pinned identity is rejected with `noise_key_mismatch`.
5. Only the encrypted channel may contain `SECURE_FRAME_CREDENTIALS_RESPONSE` (8): host device ID, host label, token, PIN and expiry. Refusals use encrypted `SECURE_FRAME_RESULT` frames.
6. The requesting Android device retains the host key for later connections. Subsequent sending and receiving check known pinned identities. Forgetting a device deliberately removes its local pin and requires pairing again.

The first pairing uses trust on first use and a human approval. Compare the requesting device's fingerprint shown in About with the fingerprint in the host's approval dialog. Encryption alone does not establish who owns a previously unknown key. Desktop uses manual token/PIN pairing and rejects credential-share requests; it does not implement Android's trust directory.

### Manual pairing with the Android receiver

A new Windows peer that was given the current token and PIN completes the challenge and PSK Noise handshake first. Android then prompts for the authenticated static-key fingerprint. Until that exact request is approved and its key pinned, Android returns encrypted `confirmacion_host_requerida` before processing any HELLO, FILE or MESSAGE payload. The sender must retry after approval. A changed pinned identity fails with `noise_key_mismatch`; knowledge of a session code cannot silently replace it.

Successful encrypted provisioning also registers the learned peer ID and the requested endpoint for chat routing. The service rechecks the pinned key when applying the report. This endpoint record alone does not grant Wi-Fi Direct group membership.

## Transfer completion and retries

A file is announced by name, byte length and an exact 64-character SHA-256 digest. The receiver returns a resume offset. Negative offsets, offsets beyond the announced length, oversized chunks, zero-length data chunks and chunks crossing the announced boundary are rejected before disk writes. Empty files are valid and carry no data chunks.

Incomplete bytes remain in `.partial` so an interrupted transfer can resume. A final file is published under a reserved unique name. Existing files are never intentionally replaced; collisions use a numeric suffix. Portable names retain Unicode letters, reject path components and reserved Windows device names, and are bounded for filesystem limits. Available disk capacity is checked before accepting the remaining bytes, with space reserved for the application and the operating system. Android exports to Downloads/Qetara; an export is reported successful only after its MediaStore entry is published. Legacy exports stage a complete second copy and retain the verified original on failure.

The client nonce identifies a transfer attempt. Android keeps that nonce stable across queued retries. Completion receipts bind the peer, attempt, name, size and digest to the saved file. If an acknowledgement was lost, the next connection can return the full resume length and acknowledge the already verified file instead of creating another copy. A new intentional send has a new attempt ID and may create a separate file.

Message delivery uses the same stable attempt principle and persists a receipt after successful processing. This prevents ordinary reconnect/retry duplicates. Receipt retention is bounded to 512 recent file attempts and 512 recent message attempts. These safeguards do not claim a transactional exactly-once guarantee across a power failure between application storage and receipt creation, or after receipt eviction.

The receiver admits at most four active clients. File payloads are serialized separately so an idle socket or a concurrent chat does not block discovery or overwrite another partial file. Listening readiness is reported only after binding; the receiving indicator is reserved for actual files.

Cancellation closes active sockets instead of waiting for their normal network timeout. Receiving cancellation has a generation counter so an unrelated sender finishing cannot clear the cancel request. Application shutdown prevents queue workers from launching further tasks. Resumed bytes do not count as newly transferred data when calculating speed and ETA.

### Publication and process-failure limits

Receive publication reserves a unique final filename and records that destination before moving the verified staging file into it; it does not overwrite a pre-existing user file. The receipt is flushed and synced through a temporary file before replacement. Its byte format remains compatible: UTF destination name, long byte count and UTF SHA-256. Recovery checks the actual destination size and SHA-256, so a prepared receipt alone cannot acknowledge missing or incomplete content. A restart after the payload move can therefore recover the same attempt even when the caller never sent its acknowledgement.

The commit helper is reached only after FILE_DONE and successful verification of the complete payload. It checks cancellation around receipt preparation; an ordinary error or cancellation removes its reservation and retains the partial source. A process killed before moving a nonempty file may still leave an empty reservation, which recovery does not overwrite or automatically delete. For a verified zero-byte payload, an empty destination with its prepared receipt already contains the complete accepted file. Tests separately cover cancellation and receipt failure for zero-byte files.

Receipt data and directory entries are not one filesystem transaction. Process-termination tests do not establish persistence after sudden power loss, and no exactly-once guarantee is claimed for every storage or hardware failure.

## Chat and channel boundaries

User text is limited to 2,000 characters. Transport envelopes have a separate 16 Ki-character limit so JSON metadata and escaped text are not silently truncated. Paragraph breaks and internal spaces are preserved.

The channel markers are `\u2063QGL\u2063` for a plain global-LAN message and `\u2063QCT\u2063` for a structured payload. Structured kinds include `user`, `direct_relay`, `channel_relay`, `roster`, `file_offer` and `file_request`. Global channel traffic is rejected when the receiver has not joined the channel. Joining does not automatically enable file downloads.

A file offer or request must refer to the authenticated sender. A request cannot redirect an offered file to an arbitrary payload-supplied IP address. Relay and roster metadata do not establish cryptographic trust or replace a trusted peer's address.

## Validation and limitations

The automated checks cover malformed lengths/hashes/resume offsets, empty files, reserved filenames, concurrent filename collisions, changed or deleted completed files, retry receipts, transport-envelope preservation, throughput after resume, and the actual Noise library configuration (including ciphertext tampering). Desktop loopback tests exercise supported transport paths.

This is defensive engineering and regression testing, not an independent cryptographic audit. A compromised device or local app data remains outside the transport's protection. Metadata in discovery is visible. Storage behavior and Wi-Fi Direct support depend on the device and OS; physical-device interoperability still requires the release checks described in the project documentation.


## Alcance del grupo Wi-Fi Direct

El descubrimiento general de Qetara y la confianza guardada no acreditan pertenencia a un grupo Wi-Fi Direct. El servicio conserva un contexto del grupo informado por Android y lo vigila también cuando la pantalla está en segundo plano. Obtiene la interfaz mediante `WifiP2pGroup.getInterface()` y sus direcciones IPv4 reales mediante `NetworkInterface`; no presupone una subred ni una dirección fija. La dirección del anfitrión procede de `WifiP2pInfo.groupOwnerAddress`.

Una observación de participante registra el peer ID, la IP remota, la dirección local del socket aceptado y el instante monotónico de aceptación. Sólo se publica tras autenticar la identidad Noise. El anfitrión admite en listas, roster y reenvío únicamente identidades confiadas con una observación posterior al inicio del grupo y dirigida a su endpoint local de ese grupo. Se valida también la ruta del mensaje entrante, para que una observación anterior de la misma identidad no autorice una solicitud recibida por LAN. El cliente utiliza únicamente el anfitrión informado por Android; un roster recibido no incorpora sus entradas al descubrimiento general ni acredita sockets hacia otros clientes.

El contexto mantiene su identificador mientras el grupo permanece estable. La desconexión, el cambio de grupo o la destrucción del servicio invalidan ese contexto; los callbacks antiguos se descartan. Sin permiso, grupo o interfaz verificables, el anfitrión no selecciona ni reenvía a candidatos históricos. El envío directo explícito y el Canal Wi-Fi LAN mantienen sus rutas independientes.

Los mensajes de grupo en cola conservan el identificador del grupo y el peer ID esperado. El servicio vuelve a comprobar ambos antes de conectar y después del handshake, antes de escribir el contenido. Los sockets de estos mensajes se enlazan a una dirección local del grupo. Los errores `grupo_direct_no_acreditado`, `grupo_direct_renovado` y `destino_fuera_grupo_direct` detienen el envío y solicitan recuperar el grupo o elegir un destino actual.

Esta política acredita una conexión cifrada observada por el endpoint del grupo y evita mezclar automáticamente equipos LAN. `Socket.getLocalAddress()` identifica el endpoint local; no constituye por sí solo una prueba de la interfaz física de ingreso en todos los kernels. Las pruebas locales cubren cruces LAN/Direct, identidad de destino, épocas de grupo, rutas antiguas, revalidación de colas y endpoints de sockets loopback. No equivalen a una validación de hardware Wi-Fi Direct físico.

Referencias oficiales: [interfaz del grupo Android](https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pGroup#getInterface()), [dirección del anfitrión](https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pInfo#groupOwnerAddress), [endpoint local de Socket](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/Socket.html#getLocalAddress()).


La aprobación de credenciales consume de forma atómica la solicitud exacta mostrada: peer ID, clave estática Noise y instante de la solicitud. Un reemplazo con el mismo ID y otra clave o instante conserva su propia solicitud y no hereda una aprobación anterior. Confianza y pin se publican bajo el mismo bloqueo del almacén, sin exponer una concesión intermedia sin clave. La evidencia de rutas Direct también conserva la clave Noise: al olvidar un equipo se borran sus observaciones y se establece un límite monotónico de revocación, por lo que un callback tardío anterior al olvido tampoco vuelve a acreditarlo.

Forgetting a peer revokes future authorization, queued outgoing work and saved route evidence. It is not a stop command for a file already authorized and being received. Cancel the active transfer to stop that operation.

## Explicit Android session closure

Closing a session persists a disabled flag, cancels credential requests, presence/probes and service send/receive operations, and blocks new start/send/retry requests. General preference updates preserve the current flag, so an old settings snapshot cannot reactivate it. Only the explicit Activate session action changes it back. The UI cancels its own automatic work and the backend independently enforces the same decision. Android discovery and group negotiation are asked to stop; an already formed network can remain available without a Qetara listener. These framework requests are asynchronous ([WifiP2pManager API](https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager)).

Publishing a late observation also reconciles its trust flag with the current trust store under the store monitor. A receive callback that started before Forget cannot restore the removed trust flag or publish an outdated trusted-peer snapshot.

Rapid close/activate is ordered by a lifecycle generation and the original Android service startId. The old close waits for all cancelled session workers and their callbacks. A later command invalidates its permission to stop the service, and activation waits for that drain before publishing configuration and clearing the shutdown flag. Pending approval and route evidence from the drained session are discarded. `stopSelfResult` additionally protects requests that Android has accepted but not yet delivered ([Service API](https://developer.android.com/reference/android/app/Service#stopSelfResult(int))).

## Flash v1: optional temporary file exchange

Flash has its own opt-in lifetime and transport, independent of WDRP v4. It never imports a normal-session token, PIN or saved trust decision. Defaults are TCP/UDP 8989, a 30-minute activation, a 90-second decision window and one batch at a time, with files processed in order. The engine admits at most four sockets and caps file metadata, encrypted frames, peer records and pending work. The detailed wire and adapter contract is in [protocol/FLASH.md](../protocol/FLASH.md).

The clear envelope is three big-endian 32-bit integers: magic `0x51464c53` (`QFLS`), version `1`, and kind. Kinds are TCP presence probe `1`, TCP single-file transfer `2`, UDP discovery `3`, presence announcement `4`, and TCP batch transfer `5`. UDP requests use a random UUID nonce; only replies matching the current discovery window are accepted. Announcements disclose an activation UUID, sanitized label, TCP port and remaining lifetime. IP addresses come from the socket endpoint. The explicit TCP presence probe is a fallback for discovery only; it grants no transfer permission. IPv4 literals are restricted to private, link-local and loopback ranges, with no public DNS lookup.

A transfer upgrades to `Noise_XX_25519_ChaChaPoly_SHA256`. Single-file kind `2` retains prologue `Qetara/Flash/v1/BOTH_CONFIRM_EVERY_FILE` for compatibility. Batch kind `5` requires the receiver to echo integer `5` before Noise and uses prologue `Qetara/Flash/v1/BOTH_CONFIRM_EXACT_BATCH`. A peer without batch support produces `batch_incompatible`; there is no automatic fallback to per-file approvals. Each activation generates a temporary X25519 static key pair; each Noise handshake uses fresh ephemeral keys. The displayed verification is the first eight bytes of the final Noise handshake hash: 16 uppercase hexadecimal digits in four groups. No handwritten encryption or persistent pairing key is introduced. The selected discovery ID is rechecked inside the encrypted hello, but remains an untrusted label until the users compare the complete verification on both devices.

Both parties exchange encrypted hello frames (`10`). A single-file offer (`11`) contains a sanitized filename, 64-bit size and SHA-256. A batch offer (`16`) contains the file count followed by those fields for every file in order: at most 128 files and 50 KiB of plaintext for the whole manifest. Each endpoint binds a fresh approval ID to the exact offered selection, peer, verification, deadline, activation and connection. The UI shows the file list, count and total size. Both users approve once for that selection and send an encrypted boolean decision (`12`). No file-content frame is sent or staged until both decisions are affirmative and the activation and approval remain valid. A later batch or new connection requires fresh approval; rejection or expiry cannot authorize a new attempt. Noise XX without pre-established trust needs external verification: the two-screen comparison is a required part of this protocol, consistent with the [Noise security considerations](https://noiseprotocol.org/noise.html#security-considerations).

Transport frames use a bounded cleartext length prefix followed by authenticated ciphertext and are capped at 50 KiB of plaintext. File chunks (`13`) contain a bounded byte count and at most 48 KiB of data; finish (`14`) ends one file's payload. The receiver checks total length and SHA-256, synchronizes the staging file and publishes it through the shared collision-safe file storage helper. Only then does it send an affirmative acknowledgement (`15`). A batch repeats this sequence in manifest order on the same channel; every file keeps its own operation ID, progress and result. Cancellation, failure and deactivation stop the remaining batch and invalidate pending decisions. Publication and deactivation share a lock; a file already published is preserved even if a later file fails or the final acknowledgement is lost. A batch is not atomic. Flash does not automatically resume or retry an interrupted operation.

This is an implementation contract with regression tests, not an independent cryptographic audit. Discovery remains observable and forgeable. Users must compare the full verification, and must not approve solely because a name looks familiar. Compromised endpoints are outside this transport protection. Flash does not alter normal-session compatibility or Wi-Fi Direct authorization rules.

# Flash protocol, version 1

Flash is an explicit, temporary local file exchange, separate from WDRP version 4. Opening its UI must not activate it. Neither mode switches nor discovery results may approve an operation. The production default is one operation, four admitted TCP sockets and a maximum 30-minute activation. A fresh activation generates a new random identifier and Noise static identity in memory; neither is written to a trust store.

## Discovery and identity

TCP and UDP default to port 8989. The network-order header is three signed 32-bit integers: magic `0x51464c53` (QFLS), version `1`, packet kind. UDP kind 3 is a discovery query followed by a Java `writeUTF` UUID nonce. Kind 4 echoes that nonce and returns an activation record: UTF identifier, UTF label, integer TCP port, long remaining milliseconds. UDP replies are bounded to 1024 bytes, nonce-correlated for 10 seconds and response-rate limited. They are unverified advertisements. TCP kind 1 provides the same public activation record (kind 4 without a nonce) for an explicit private IPv4 address; a manually supplied forwarded port remains the target port.

Discovery contains no file metadata, credentials or cryptographic keys. The temporary identifier, display name, port, remaining activation and source IP are visible to other devices on the local network. Public IPv4, DNS names and IPv6 are not supported by this initial implementation. Guest-network isolation and blocked broadcast can prevent discovery; the explicit address fallback cannot bypass a network firewall.

## Transfer and approval

TCP kind 2 starts `Noise_XX_25519_ChaChaPoly_SHA256`, using the existing noise-java implementation. The prologue is `Qetara/Flash/v1/BOTH_CONFIRM_EVERY_FILE`. Noise handshake frames have a 4096-byte limit. The complete handshake hash supplies the displayed verification code: its first eight bytes, hexadecimal uppercase, in four groups of four characters (64 bits). Both users must compare all groups on the actual devices and explicitly approve each file. Being on the same Wi-Fi, seeing a familiar name, or having used normal Qetara before is not approval.

After Noise, each side sends an encrypted activation record (frame 10). The initiator sends frame 11: sanitized filename (UTF), byte count (long), SHA-256 hex digest (UTF). Those metadata travel before human approval so the receiving user can review the offer; they are encrypted but the human identity comparison has not yet completed. No file payload is accepted or staged before both encrypted decisions (frame 12, boolean) are true. Local approval is bound to an unguessable request ID, the exact operation and current activation; each decision is consumed once. Late, cancelled and expired requests cannot authorize a later attempt. There is no silent TOFU or automatic acceptance.

Encrypted frames use Noise's cipher states, maximum 50 KiB plaintext and the cipher's exact MAC overhead. Data frame 13 contains an integer count and at most 48 KiB of bytes. Frame 14 ends the payload. Size, frame structure and SHA-256 must match before publication. Frame 15 (true) acknowledges the receiver's verified publication. Normal token/PIN authentication is not used or modified by Flash.

## Storage, cancellation and limits

The receiver reserves capacity, stages into `.flash-partial` on the destination volume, syncs the complete staged file and uses the shared no-overwrite publication helper. Existing names receive collision suffixes; remote paths cannot escape the destination. Stop and publication admission share a lock. Once committed, cancellation or loss of the acknowledgement never deletes the received file.

There is no automatic retry, resumable Flash upload, or cross-process deduplication in version 1. Ordinary rejection, cancellation, timeout and connection failure remove the current partial file. Abrupt process termination can leave private staging files or an empty final-name reservation, as with the shared publication helper. Do not describe this implementation as power-loss transactional or exactly-once delivery. If acknowledgement is lost, the sender reports an uncertain outcome and asks the user to check the receiver before sending again.

Activation and approval deadlines use monotonic time, with wall-clock values only for UI and early expiry. An independent scheduled close also bounds activation. Every stop closes listening/discovery/accepted sockets, clears pending decisions and destroys the activation key. Admitted sockets and pending operations are bounded; malformed unauthenticated traffic cannot generate repeated UI approval notifications without completing Noise and a valid offer.

## Adapter contract

`FlashEngine` owns networking and background hashing. `start()` binds synchronously and invokes `onState` before returning. `send()` reserves an operation and returns its ID before hashing; argument/state failures are synchronous. `discover()` and `discoverAt()` perform asynchronous network work. `approve()` consumes an exact request and returns whether it applied. `cancel()` and `stop()` revoke work; restarting creates a new activation.

Listener callbacks are serialized, may originate on any engine thread and must return promptly. Dispatch them to the UI in order. Use a generation fence for commands and activations, but do not replace an earlier queued state event with a future `snapshot()` that discards an already queued successful receipt. `onReceived` means the verified local file exists; a later `onError` can mean only that its ACK failed. Preserve that file/result. Android may export a second copy to public Downloads, but export failure must preserve the private received original. Never put SAS approvals, temporary identities, or activation state into persistent preferences.

## Verification

The initial regression suite exercises real Noise loopback connections: bilateral approval and matching/fresh verification codes, name collisions, rejection, cancellation/restart, expiry and late approval, empty files, source mutation/hash failure, Stop during payload, TCP and UDP discovery, private address restrictions, hostile paths, oversized ciphertext, and premature payload instead of a second approval. These tests do not constitute an independent cryptographic audit or establish behavior on every Android/Windows network or power-loss scenario.

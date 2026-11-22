# Flash input review · 21 November 2026

This local correction is based on the published 1.4.5 source and its closure
documentation (`e165deaf95e459a1b2a6fae055ccff7b3e8fbc37`). It does not replace
the `v1.4.5` tag or constitute a new F-Droid release.

## Desktop file drops

The window registered an AWT drop target on the outer JFrame. Compose 1.8.2
registers its own target on the rendering container inside that frame, so the
application's handler was outside the effective native drop path.

The workspace now registers a Compose drag-and-drop target covering its content.
Flash highlights the composer while it can accept a file drop. Files can be
prepared before choosing a receiver. Dropping files does not submit them, and
the message draft is retained. A busy session rejects new drops. Unusable input
reports an error while retaining the previous selection. The accepted native
operation is COPY, including when a source offers COPY and MOVE and proposes MOVE.

A native test also reproduced a Compose 1.8.2 edge case: entering directly at the
drop position and releasing without another drag-over event had not selected a
child target. The file target now initializes that hit-test through the existing
native Compose bridge when the drag starts. This fixes fast drops without
replacing the text field or registering competing native targets.

`DesktopFlashDropPreview` is a manual native DnD harness with Swing file sources
and the production Flash composer in one window. Its optional `legacy` mode
recreates the previous outer-frame registration. Transport in this harness is
simulated; its send log is not evidence of a network transfer.

## Android file-send action

The enabled file-send action used an outlined button, unlike text sending.
It now uses a filled Material button with the current theme's primary color.
The existing receiver, import and busy conditions are unchanged, as are the
separate text/file actions and their English and Spanish labels.

The UI scenario checks the rendered fill and accessibility enabled state with
five attachments and an empty or nonempty draft, a missing receiver, an active
send, an import in progress and no attachments.

## Design reference

The active [UNSA Figma file](https://www.figma.com/design/KRV4r4m1p1fSOFZOHOMV0B)
was consulted using the UNSA account and dev-UNSA team. Its Flash batch component
(`4:43`, action `4:40`) uses a filled primary action (`#0A6B77`). The file contains
brand, batch-confirmation and PC-channel references, not a complete Flash
composer specification. This correction preserves the existing application
palette and launcher geometry.

## Validation

- Android debug build, instrumentation APK and unit tests succeeded. All 298
  unit tests in 49 suites passed. Lint reported zero errors and 43 warnings.
- The targeted `flash-file-send-ui` scenario passed all six checks in Spanish
  and all six in English on an API 36 emulator. Enabled fill matched the theme
  primary `#0A6B77`; unavailable actions used the disabled fill `#DAE0E5`.
  The screenshots were inspected. No transfer or physical-phone test is claimed.
- The broader existing `flash-text-ui` scenario completed 19 checks, including
  draft/receiver preservation and the new file-action checks, before a keyboard
  layout polling timeout. The capture showed complete actions above the IME.
  Its polling now reads the accessibility tree once per sample instead of four
  times and retains the same consecutive-stability checks. The complete broad
  scenario was not rerun to PASS and must not be counted as passed.
- Desktop suite: 113 tests declared, 110 passed and three opt-in Android network
  integration tests skipped; zero failures or errors. No network transfer is
  claimed from those skipped tests.
- A regression using the real Compose 1.8.2 AWT bridge reproduced two failures
  before initial-position handling. All five bridge tests passed afterwards,
  including a drop with no intermediate move, COPY negotiation, changing to busy,
  rejecting unsupported data and reporting a transferable read failure.
- In a native Windows window, the old outer-frame-only harness rejected a file
  dropped over the editor. The corrected final harness accepted that exact first
  gesture, then accepted two more files over the editor. It retained all three
  attachments and the exact Unicode draft without selecting a receiver or
  submitting a transfer. Both native source logs reported COPY (`action=1`)
  even though the source initially proposed MOVE (`proposedAction=2`).
- Additional native checks on the same handler before the initial-position fix
  covered duplicates, invalid directories, MOVE-only rejection, preserving
  prepared content while busy and explicit submission of a two-file batch to
  the simulated transport. These are attachment/controller checks, not network
  delivery evidence.

Only dedicated emulators and synthetic desktop fixtures were used. The emulator
was shut down after the targeted bilingual checks. Physical phones and the
installed desktop application were not upgraded. No release was published.

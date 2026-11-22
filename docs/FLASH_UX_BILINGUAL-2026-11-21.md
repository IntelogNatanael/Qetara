# Flash composer: English and Spanish

This local candidate follows the input fixes in `7635b4a`. It does not change the
release version, the network protocol, the signing setup or the F-Droid recipe.

## Changes

- Compact session information and secondary Details controls.
- Content-dependent actions: no send action for empty content, message or files
  when only one type is prepared, and two explicitly separate actions when both
  are prepared. Each operation still requires bilateral batch approval.
- Attachment count/size and individual removal. Desktop removal leaves original
  files untouched. Android cleanup is restricted to private outgoing cache copies;
  reattaching the same source uses a separate import directory.
- Short recent activity previews with full text and copy actions. Android keeps
  the composer above the keyboard; desktop collapses recent activity by default.
- Stable status areas, including copy feedback and transfer errors. An old copy
  acknowledgement cannot cover a new error or a retry with the same error value.
  Repeated desktop copy actions restart their acknowledgement even when the
  message text is identical.
- English and Spanish Android resources. Desktop Flash resolves UI, retained
  status/error text and file chooser labels from the JVM/system locale: Spanish
  for `es`, English otherwise. The surrounding legacy desktop shell is outside
  this change and still uses its existing Spanish copy. User content is not translated.

The existing theme, classic logo geometry and appearance preference are preserved.
Text and attachments are not silently combined into one transfer.

## Design reference

[Editable Figma reference](https://www.figma.com/design/KRV4r4m1p1fSOFZOHOMV0B?node-id=28-505)
uses the authorized UNSA account and dev-UNSA team. The local design record is in
`design/figma/flash-composer-2026-11-21/`. These are design references, not evidence
of a network transfer. Compact Android attachment review and responsive desktop
activity are platform adaptations of the same hierarchy.

## Verification

| Check | Result |
| --- | --- |
| Android debug build and test APK | Passed |
| Android lint on final sources | Completed: 0 errors, 47 warnings (including existing resource/style warnings and the new optional Modifier parameter order warning) |
| Android unit tests | 300 passed in 49 suites; no failures or skips |
| Desktop unit tests | 115 passed in 18 suites; 3 opt-in network checks skipped |
| Android attachment/action scenarios | 9 checks passed in English and 9 in Spanish |
| Android composer scenarios | 5 checks passed in English and 5 in Spanish |
| Desktop production-composable rendering | 38 captures generated across both languages, two window sizes, large text and the surrounding desktop shell |
| Independent code review | No outstanding actionable findings after fixes |

The Android runtime checks used only the dedicated API 36 emulator. They cover
empty/message/files/mixed action states, primary/disabled file-send color,
individual removal and busy guards, real IME at font scales 1.0 and 1.6,
three-line previews, scrolling the full long message, exact sensitive clipboard
copy, and errors after copy/retry. They use synthetic process state and do not
prove a network transfer. A first English composer run failed because the test
queried accessibility while Android's temporary clipboard overlay was active;
the error was visible in the app capture. The test now waits for Qetara's own
window without interacting with SystemUI. Both complete reruns passed.

Desktop captures render production composables with an in-memory transport at
1160×800 and 800×620. Representative English/Spanish mixed, file-only, approval,
large-font and workspace captures were visually inspected. They are not native
mouse/keyboard or network tests. The existing desktop native-drop bridge tests
remain in the passing unit suite. The three omitted opt-in tests cover real
Android file/text sockets and LAN discovery; no new Wi-Fi Direct or device-to-device
transfer claim is made. The older broad `flash-text-ui` scenario was not rerun.

Private logs and captures are kept outside the source tree in
`../evidence/ux-bilingual/`. The emulator's original English locale, 1.0 font scale
and light system mode were verified at the end; the emulator started for this
task was shut down. The runtime fixtures report restored process state.

The user’s physical phones and installed desktop application are not modified
by this validation. Publishing or installing this candidate is a separate step.

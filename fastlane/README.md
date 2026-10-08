# Store metadata

The Android listing is maintained in `metadata/android/en-US` and
`metadata/android/es-ES`. Keep both descriptions aligned with the features in
the corresponding release. Name each changelog after its Android `versionCode`
from `gradle.properties`; preserve the notes for previous releases.

F-Droid's text limits are 50 characters for the title, 80 for the short
description, 4,000 for the full description, and 500 for each changelog.
See the [F-Droid metadata guide](https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/#fastlane-structure).

To regenerate the 512 × 512 store icons on Windows, run from the repository root:

```powershell
./scripts/export-store-icon.ps1
```

The exporter uses the Android launcher vectors in `app/src/main/res/drawable`,
preserves the symbol's shape, proportions, scale, and classic ivory/navy colors,
and writes only the two locales' `images/icon.png` files. It does not require
additional packages or modify Android or desktop launcher resources.

Use screenshots from the matching app version and language. Store local build
logs, validation results, and their hashes outside the tracked source tree;
they describe a particular checkout and are not store metadata.

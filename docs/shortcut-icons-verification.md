# Quick-action icons — working-branch verification

Checked 7 October 2026 for rc.59. Final signed-APK/publication data recorded in GitHub Release.

Icon menu loads shortcuts through `LauncherApps.getShortcutBadgedIconDrawable`. 32 dp image replaces arrow in existing 48 dp slot; original colors/shape/system profile badge retained. Activity executor loads; results apply only to current open dialog. Missing/unavailable icons retain arrow. No extra permissions/contact reads/saved avatars.

## Completed checks

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` passed; 15 unit tests.
- `./gradlew :app:connectedDebugAndroidTest`: 48 each on dedicated Pixel 7 arm64 API 31 (`emulator-5580`)/API 36 (`emulator-5582`). One timeout each: `HomeSurfaceTest.fullHeightHomeRetainsTouchKeyboardAndAccessibleNavigation` (31), `WidgetPickerTest.previewsFallbacksAndAlphabetChooseTheDisplayedProviderWithoutBindingDuringBrowse` (36). Full runs not successful.
- Isolated `ShortcutIconTest,HomeSurfaceTest,WidgetPickerTest`: all four passed each device. New ShortcutIconTest also passed full run: published bitmap/resource shortcuts, missing icon/unavailable shortcut, text alignment/accessible row/correct launch.
- Real menu/test-avatar captures reviewed both; images/text aligned. Fake conversation created only by test/deleted in `finally`. Local `.impeccable/review/shortcut-icons-emulator-5580.png`/`shortcut-icons-emulator-5582.png`.
- Lint: 0 errors, 27 warnings. Versus `HEAD` (26), only new `GradleDependency` notification for Material 1.14.0. Dependency remains 1.13.0; mutable network version notice, not new violation in changed code. Other warnings match original branch.
- `git diff --check`, Markdown paths/JSON sidecar clean.

## Limitations

Avatars depend on app-published shortcut data. User-account Telegram/real work profiles untested. No Telegram API requests. Full Niagara parity is not claimed.

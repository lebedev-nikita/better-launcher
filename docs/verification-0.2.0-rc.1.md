# Better Launcher 0.2.0-rc.1 verification

Date: 2026-10-04. Minor candidate; versionName 0.2.0-rc.1, code 8, minSdk 31, targetSdk 36.

## Contents

Final build includes left widgets/one home widget, 0.1.3 ICU4J Latin alphabet, 0.1.4 dimming/0.1.5 hold menu. Sources fixed at `5f7fdf5`; only release documentation changes afterward. Code raised to 8 after stable 0.1.5/code7. Preliminary candidates unpublished.

Historical pre-alphabet widget checks in [report](widgets-verification.md), alphabet [0.1.3](verification-0.1.3.md), dimming [0.1.4](releases/0.1.4.md), menus [report](app-menu-verification.md). They do not replace final-APK verification.

## Automated checks

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` passed.
- 9 unit tests, 0 errors: search/favorites/transliteration/alphabet.
- Lint: 0 errors, 27 previous warnings, no new warnings.
- `ANDROID_SERIAL=emulator-5572 ./gradlew :app:connectedDebugAndroidTest`: all 11 passed dedicated Android 12 (`BetterLauncher_Widgets_API_31`): storage/backup, atomic import rejection, placement, catalog/transliteration, alphabet/row reuse, dimming slider/widget navigation.

Pre-dimming run: 8/9 connected; navigation missed visible focused home within 5 seconds. First smoke temporarily empty accessibility tree; retry passed fully. These do not replace final successful 11-test fixed-snapshot run.

Final APK reinstalled/HOME assigned after connected. All current `scripts/smoke-test.py --serial emulator-5572` scenarios passed final APK: no clock/date, widget swipes/buttons, Back/HOME, Settings/search/Camera, Phone menu/hold/alphabet. No launcher crashes. 1080×2400/font scale1.0/original orientation retained.

## APK

- `better-launcher-0.2.0-rc.1-debug.apk`, 28 714 307 bytes.
- SHA-256: `95ab32a2ca233202c0b3e7a15e833285750aac6b89362c82d6248d97bd641738`.
- `apksigner verify` confirms v2; certificate SHA-256 `7618fbcfaeec605563e1f7b8200be66d60c923c268cbeebdbbbc98b54fd71b5b` matches published 0.1.2/0.1.3/downloaded 0.1.5.
- Checked package `app.better.launcher`, name 0.2.0-rc.1, code8, min31/target36.

Android 12 installed published 0.1.5/updated using `adb install -r`; private SharedPreferences matched byte for byte. Final full smoke followed upgrade.

## Limitations

Repeated Android 16 UI during preliminary preparation stopped before first scenario: two `uiautomator dump` exit137, including after dedicated restart. Later install: `Failure calling service package: Broken pipe (32)`. Final Latin-alphabet APK on Android 16 untested. Post-final-merge narrow/font1.3/landscape/left-handed unconfirmed; earlier widget-layout checks retained historically.

Physical devices/third-party providers/work profiles/Private Space/full TalkBack untested. Debug-signed APK; production signing/store not configured. Full Niagara parity is not claimed.

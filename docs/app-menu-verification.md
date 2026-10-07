# App-menu verification 0.1.5

Date: 2026-10-04. Hold menu updated from two supplied screenshots, using original Canvas icons and real installed-app icons.

## Change

Navy surface, light-blue labels/icons, app-icon heading, expandable More and separate Better Launcher Settings entry. Primary commands immediately available; rename, attached apps and hiding under More. Rows grow with text; height recalculates on expansion, limited to 85% of screen. Scrolling reaches the final command. Insets respect bars/cutouts. Expansion announces accessibility state; keyboard actions available.

## Build and package

- `./gradlew --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` passed, 9 unit tests/no errors.
- Lint: 0 errors, 27 warnings; ID/text comparison with original 0.1.2 XML found no new warnings.
- Contrast `#BCD7F3` on `#031629`: 12.30:1.
- APK: versionName 0.1.5, versionCode 7, minSdk 31, targetSdk 36. `apksigner verify` confirms v2 signing/certificate matching published 0.1.2/0.1.3. Upgrade over 0.1.2 installed on dedicated emulators before final system-bar styling change.
- Final build includes `origin/main` through `37cf924`: widget screen, alphabet transliteration, dimming/current navigation test retained. All four Gradle checks rerun successfully after final merge; versionCode raised to 7.

## Device checks

- `ANDROID_SERIAL=emulator-5584 ./gradlew --max-workers=2 :app:connectedDebugAndroidTest`: 11 tests/no errors on dedicated `BetterLauncher_AppMenu_API_31`, Android 12, Google APIs arm64-v8a, Pixel 7. Main APK reinstalled/HOME assigned afterward.
- Before final alphabet/dimming changes, final menu appearance checked on Android 12: full `scripts/smoke-test.py`, portrait 1080×2400, narrow 840×1867/font scale 1.3, landscape 2400×1080. Each expanded actions, scrolled to hide/Settings, opened Settings/returned Back. Merges did not change menu code. Final 0.1.5 APK reran full Android 12 smoke: widgets, Settings/Back, search, Camera launch/HOME, swipe popup, hold/More, alphabet; no Better Launcher crashes. Size/scale/orientation/animations restored.
- Dedicated Android 16 `BetterLauncher_AppMenu_API_36` (`emulator-5582`) incomplete: system-process restarts, UI Automator exit 137, then System UI isn't responding ANR. Retry with 4 GB RAM/hardware graphics did not resolve. No successful Android 16 UI run claimed.

## Boundaries

Local screenshots/reports in `.impeccable/review/`, outside Git. Fixed reference menu color, no wallpaper adaptation. Physical phone, full TalkBack, work profiles/missing Niagara features not independently checked.

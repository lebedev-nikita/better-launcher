# Favorites-order verification

Date: 2026-10-04. Working editor code checked; no new release published.

Editor opens from favorite-app menu → Change order, Settings → Favorite apps and folder editing. Drag apps/folders by ≡ or use arrows. Order saves immediately; window stays open. Unavailable app/profile IDs remain saved.

## Automated checks

| Check | Result |
| --- | --- |
| `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` | Passed; 5 unit tests, lint 0 errors/27 warnings, no new editor warnings |
| `:app:assembleDebugAndroidTest` | Passed, including final test-input changes |
| `:app:connectedDebugAndroidTest`, Android 12 / API 31 | 19/20 passed including FavoritesOrderTest. WidgetPickerTest image-preview timeout; isolated retry passed 1/1. Full run not entirely successful |
| FavoritesOrderTest, Android 16 / API 36 | Passed 1/1: real drag, arrows, folder/unavailable ID, HOME order, Activity recreation, export/import, Back/HOME |

[FavoritesOrderTest](../app/src/androidTest/java/app/better/launcher/FavoritesOrderTest.kt) injects touch via UiAutomation/checks saved list, not only row positions. Unit test covers multi-position move with folder/different profile serials. Full Android 12 used earlier input version with same production code; late final-test retry stopped on `com.android.systemui` ANR.

## UI

Real editor captures reviewed on dedicated read-only `emulator-5680` (12)/`emulator-5682` (16).

| Configuration | Android 12 | Android 16 |
| --- | --- | --- |
| Portrait 1080×2400, font scale 1 | Drag/arrows/save/fixed lower actions/Back passed | Functional test passed; appearance reviewed |
| Narrow 945×2100, font scale 1.3 | Same actions passed; text/buttons accessible | Appearance reviewed; separate functional run unfinished |
| Landscape 2400×1080, font scale 1 | Same actions passed; inline buttons/lower actions accessible | Appearance reviewed; separate functional run not performed |

Ignored local captures `.impeccable/review/favorites-emulator-{5680,5682}-{portrait,narrow,landscape}.png`. Size/font/orientation restored; test emulators stopped.

## Limitations

Full Android 12 `scripts/smoke-test.py` passed portrait/narrow font 1.3. Landscape stopped at Camera launch: Better Launcher remained active, cause unresolved. Separate landscape editor check passed.

Android 16 full smoke stopped: UI Automator lacked fresh hierarchy (`null root node`) although captures showed UI. Some emulator runs had system ANRs. Full Android 16 smoke unconfirmed.

Physical devices/real work profile/TalkBack/keyboard/left-handed mode unverified. Arrows have accessible labels/standard focus; profile-ID integrity confirmed using fixtures.

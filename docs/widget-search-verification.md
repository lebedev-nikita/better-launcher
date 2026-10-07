# Widget-search verification — 5 October 2026

Search widgets available when adding to home/widget page. All query words match widget/app names case/diacritics insensitive. Clear restores catalog; alphabet reflects results. Browsing/search allocate no IDs.

## Verified published APK

[0.2.0-rc.40](https://github.com/better-launcher/better-launcher/releases/tag/v0.2.0-rc.40), code 49, tag `c07c77c7a6a7bd50cdc034c2b718b7909c064fa1`. Search integrated into main before candidate build; WidgetPickerView matches tested implementation. Concurrent branches published candidates during checks; older locally prepared rc.38 was not released separately.

- Downloaded release APK: 2 457 952 bytes, SHA-256 `9551954f93bf13d4eff14d361a7befb5d3fad394a423ddc024a8f995f947c327`, matches published checksum/GitHub asset digest.
- Not debuggable, valid v2/compatible signature, R8/resource shrinking.
- Tag contains search commit/is ancestor of origin/main.

## Source build/checks

Post-main commit `2b25977`: assembleDebug/assembleDebugAndroidTest/testDebugUnitTest/lintDebug passed. All 15 unit tests; lint 0 errors/27 previous warnings, no new warnings. Locally prepared signed search release also passed assembleRelease/lintRelease; published rc.40 independently checked from download.

## Android 12 / API 31

Dedicated BetterLauncher_WidgetSearch_API_31, explicit `127.0.0.1:5661`, separate adb server port 5038.

- WidgetPickerTest/SystemTransliterationTest: 7/7. Covers pre-load search, widget/app names, case/Cyrillic, no results, clearing/spaces, filtered provider, IME Done/previews/no allocated browse IDs.
- Published signed rc.40 passed all 11 tagged scripts/smoke-test.py checks including HOME/Back, app search, Camera, menus/alphabet; no Better Launcher crashes.
- Same release checked clock search/no matches/clear/keyboard/Back/HOME in portrait 1080×2400, narrow 945×2100/font scale 1.3, landscape 2160×1080. Captures reviewed; landscape fullscreen keyboard Back returns filtered list.
- Left-handed mode checked: search works, picker alphabet remains right; original mode restored.
- Temporary size/font/onscreen-keyboard visibility restored. Main signed APK reinstalled/HOME assigned after instrumentation.

## Limitations

Full connected failed: interrupted after 19/31 scenarios with menu/favorites-order/navigation UI waits. Corrected isolated 7-test set passed; not a full-suite result. API 36 unconfirmed: dedicated emulator unstable due to system_server/graphics failures. Physical phone/full TalkBack session untested.

Local logs/native captures in `.impeccable/review/widget-search/`, outside Git. Historical 0.1.0 facts in docs/verification.md retained.

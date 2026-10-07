# App-list smoothness verification

Date: 2026-10-04. Fix included in patch 0.1.2.

## Cause and fix

Opening All apps/alphabet synchronously created/measured the entire catalog in a `ScrollView`. `AppCatalog.visible()` repeatedly normalized names, read settings/sorted apps. Moving within one letter repeated navigation, keyboard hiding/deferred scrolling.

Results now use native `ListView` with row reuse/separate heading/app types. Names/search strings/order prepared during background catalog loading. Alias/hidden/import changes invalidate index. Alphabet draws its wave on every move but navigates only on letter change/new touch. Section navigation selects position directly; deferred keyboard opening canceled on letter navigation/screen exit.

## Reproduction and measurement

Dedicated `BetterLauncher_Smooth_API_31` (Android 12, Pixel 7, 1080×2400, 420 dpi) installed temporary APK with 300 launcher activity aliases. Main APK contains only real installed apps; fixture/measurement test excluded from release.

Temporary instrumentation waited for catalog, returned home/called real C-selection handler ten times. Main-thread time measured with `SystemClock.elapsedRealtimeNanos()`, `waitForIdleSync()` between passes; all window Views counted after layout. Compared published 0.1.0/fixed APK.

| Metric | 0.1.0 | Fix |
| --- | ---: | ---: |
| Catalog apps | 318 | 317 |
| Window Views after navigation/layout | 1317 | 69 |
| Median opening-handler time, 10 passes | 1220.8 ms | 45.6 ms |
| Median separate repeated `visible()`, 10 passes | 591.4 ms | 0.15 ms |

Times include screen creation/data preparation, not full touch-to-frame latency. Concurrent host Android checks mean absolute times/speedup cannot be transferred to physical phones. View reduction/no repeated sort verified independently of host load. Fixture/temporary test/diagnostic logs removed afterward.

## Regression checks

- `AppSearchIndexTest`: alias/original-name, Latin/Cyrillic search, section order; repeated queries do not reread thousands of names.
- `AppNavigationTest`: same-letter movement does not repeat navigation, new same-letter touch works; thousand-item list creates only viewport rows/reuses Views; last selected section overrides earlier requests.
- `LauncherIntegrationTest`: prepared index updates after rename/hide/restore/alias removal; existing backup/real-catalog checks retained.

Alphabet test reproduced defect on published 0.1.0: expected `[A, B]`, actual `[A, A, A, B]`. Fixed version passes. Keyboard ListView selects first available app after nonclickable heading; regression respects standard behavior.

## Final checks

- `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug` passed; 5 unit tests, lint 0 errors/27 existing warnings, no new warnings.
- `:app:connectedDebugAndroidTest`: 5 passed on Android 12/16, dedicated serial `emulator-5574` with sequential AVDs.
- Final 0.1.2 reinstalled/HOME assigned after connected; all current `scripts/smoke-test.py` scenarios pass on both.
- Both checked portrait 1080×2400, narrow 945×2100/font scale 1.3 and horizontal 2400×1080: Camera search, alphabet query reset, HOME/deferred-IME cancellation. Left alphabet independently checked Android 12.
- Android 12 landscape keyboard uses fullscreen editor. Query entry, IME close/subsequent letter navigation checked; standard keyboard behavior preserved.
- Captures visually reviewed: row sizing/scrolling with large font, accessible search/alphabet in tested layouts. Verification covers smoothness/scenarios, not full accessibility audit.
- No Better Launcher runtime crashes after scenarios. v2 signing verified/matches 0.1.0/0.1.1; versionName 0.1.2, versionCode 3, minSdk 31, targetSdk 36.

Local captures/reports in `.impeccable/review/smooth-api31*`/`.impeccable/review/smooth-api36*`, outside Git. Size/font/orientation/alphabet side restored.

Physical phone/work profiles/full TalkBack unverified. Full Niagara parity is not claimed.

# Verification 0.1.0

Primary platform: dedicated Android Emulator `BetterLauncher_API_36`, Android 16/API 36, Google APIs arm64-v8a, Pixel 7. Minimum Android 12/API 31 also checked on `BetterLauncher_API_31`, same profile/image. Physical phone not yet checked.

## Build and automated checks

- `:app:assembleDebug` passed.
- `:app:testDebugUnitTest`: 3 tests, 0 errors—search, Latin/Cyrillic grouping, favorites ordering.
- `:app:connectedDebugAndroidTest`: 3 tests, 0 errors—folders/settings persistence, backup, atomic malformed-file rejection, real catalog, rename/hide.
- `:app:lintDebug`: 0 errors, 35 warnings, mostly legacy compatibility APIs, small Canvas allocations, tool versions/standard View touch listeners. Generated report: `app/build/reports/lint-results-debug.html`.

## UI through adb

`python3 scripts/smoke-test.py --serial emulator-5580`:

1. Settings button opens Settings; system Back returns home.
2. `camera` search retains Camera/removes Phone from results.
3. Tap actually launches Camera; HOME returns Better Launcher.
4. Horizontal Phone swipe opens app menu.
5. Long press shows rename/hide.
6. Vertical alphabet movement opens list/selects section.
7. No Better Launcher AndroidRuntime crashes after checks.

All six scenarios/crash check also passed Android 12. Script handles differing active-app field names in Android 12/16 `dumpsys`.

System Back gesture edge preserved. Letters 32 dp from edge; touch strip 48 dp wide.

Additional real integrations on the same emulator:

- Test SMS appeared in real Messages/home preview.
- Notification menu shows Messages actions; Reply opens native field, sending not performed.
- Dismiss notification removes preview after menu closure.
- System Clock Digital widget added through Android permission, configured externally, shown through AppWidgetHost, then removed.

## Visual verification

Captures directly from emulator, without browser mockup:

- Normal phone 1080×2400, 420 dpi.
- Narrow phone 945×2100, 420 dpi, system font scale 1.3.
- Horizontal phone 2400×1080.
- Home, app list/Settings.

Local `.impeccable/review/`, excluded from Git; scale/rotation restored.

Independent fix review: `disposition: ship`, all five findings resolved—list/footer overlap, built-in wallpaper transparency, secondary-text contrast, Material 3 toggles/alphabet touch width. Calculated minimum secondary contrast on built-in/white system wallpaper: 4.60:1 in both dimming states. Verdict covers these five fixes; exact Niagara visual/full functional parity unconfirmed.

## Boundaries

LauncherApps/settings checked on emulator. Direct reply sending, different music apps, work profiles, calendar providers/weather requests/third-party widgets require suitable data/apps. UI contains no fake notifications/weather/events.

Debug APK signed with local debug key; `apksigner verify` confirms v2. Copy: `artifacts/better-launcher-debug.apk`. Production signing/publication not configured. Android 13–15 checked through API levels/lint, not run on those devices.

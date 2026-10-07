# Separate widget-screen verification

Date: 2026-10-04. Source changes after 0.1.2 checked; no new release published.

## Behavior

Right swipe on empty home opens left widget screen; left returns home. Navigation buttons available; Back returns home, HOME always shows home. App/widget-content gestures retained. At most one home widget; others in left scroll list. Upgrading old stack retains first on home.

## Checks

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` passed: 5 unit tests, lint 0 errors/27 existing warnings, no new warnings.
- `ANDROID_SERIAL=emulator-5572 ./gradlew :app:connectedDebugAndroidTest`: 8 passed dedicated Android 12. New checks: placement/migration, import preserves local IDs/pending binding, atomic malformed-backup rejection, navigation buttons, picker cancel, Back/HOME.
- Final APK installed/HOME assigned on `BetterLauncher_Widgets_API_31` (`emulator-5572`,12)/`BetterLauncher_Widgets_API_36` (`emulator-5570`,16), Google APIs arm64-v8a, Pixel 6. All current smoke scenarios pass both including real inter-screen swipes; no launcher crashes.
- Both checked narrow 320dp/font scale 1.3, left alphabet/landscape. Bottom actions accessible; alphabet leaves footer/inset space. Widget page survives rotation. Native captures reviewed in local `.impeccable/review/widgets-api31-final/`/`widgets-api36-final/`.
- Real Clock checked binding cancel, add left, transfer home, replace home retaining old left, removal without automatically promoting another, host-ID release. Android 16 additionally external Clock configuration. These predate 0.1.2 list-fix merge; storage/widget-controller code unchanged afterward.

Main APK reinstalled after connected. Temporary widgets removed; size/font/orientation/side restored. Captures/build reports excluded from Git.

Physical phone, third-party providers/full TalkBack untested.

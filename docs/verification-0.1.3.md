# Better Launcher 0.1.3 verification

Date: 2026-10-04. Android 12+; versionName 0.1.3, versionCode 4, minSdk 31, targetSdk 36.

## Implementation

Rail contains ★, A–Z/final #. ICU4J 78.3 Russian-Latin/BGN groups full names: Яндекс → Yandeks → Y, Йога → Yoga → Y, Банк → Bank → B, Видео → Video → V. First Latin letter determines section; numbers/symbols/Ь/Ъ go to #. NFC preserves compound Cyrillic including Й; Latin diacritics removed for grouping.

ICU rules load in background catalog loading; Cyrillic results cached, mutable Transliterator access synchronized. 0.1.2 prepared search/row reuse retained. Rail respects side system insets; short windows show available Latin groups/end with #.

## Automated checks

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` passed.
- 9 unit tests: original search/favorites, transliteration including Яндекс/Y, compound Й, # order, short/empty rail, prepared search without repeated name reads.
- Lint: 0 errors, 27 existing warnings; no new ID/message versus baseline. Historical 0.1.0's 35 retained separately.
- `ANDROID_SERIAL=emulator-5602 ./gradlew :app:connectedDebugAndroidTest`: 6 passed before widget-screen merge on Android 12/API 31, BetterLauncher_LatinRail_API_31. Real apps/aliases, mixed B/Б/V/В, Яндекс/Y, final numbers, hide/index refresh, backup/integrity/navigation/row reuse.

## Test environment

Several host emulators ran concurrently. Early UI stopped on UI Automator freezes/Android 16 watchdog system restarts before app install; not successful checks. Android 12 previously passed no clock/date, Settings/Back/search; full smoke incomplete.

For later dedicated Android 16, root adb set `ro.hw_timeout_multiplier` to 10/restarted framework. [AOSP](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r4/core/java/android/os/Build.java) provides this for slow software emulators; not part of APK. Services froze again before installation; final Android 16 connected/new-rail scenarios not performed. Increased timeout did not produce successful verification.

## UI limitations

Final full smoke, real Y/# taps, narrow/font scale 1.3, landscape/left-handed after final merge unconfirmed. Previous 0.1.2/widget checks remain historical, not this APK's proof. Android 12 integration confirmed Яндекс/Y, mixed B/Б/V/В, final number/hide alias; grouping source unchanged afterward.

Android 12 connected uninstalled app; APK reinstalled/HOME assigned dedicated AVD. Display/font/orientation/aliases unchanged during final attempts. AVDs stopped after failed UI.

## APK

Same debug certificate as published 0.1.0/0.1.2; `apksigner verify` confirms v2. Production signing not configured.

- File: `better-launcher-0.1.3-debug.apk`.
- Size: 28 943 432 bytes; ICU4J increases debug size.
- SHA-256: `af29deece189babe226352b308635b33e290105b9e35f0625a0e50d30825dfcf`.
- Certificate SHA-256: `7618fbcfaeec605563e1f7b8200be66d60c923c268cbeebdbbbc98b54fd71b5b`.

Physical devices/work profiles/Private Space/full TalkBack untested. Transliteration designed for Russian; other Cyrillic alphabets untested separately. Full Niagara parity is not claimed.

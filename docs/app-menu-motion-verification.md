# Menu verification 0.2.0-rc.8

Date: 2026-10-04. Version: 0.2.0-rc.8, versionCode 17, minSdk 31, targetSdk 36.

## Change

Hold menu rises over 300 ms on curve (0.16, 1, 0.3, 1) after calculating system insets/window height. Property animator performs entry without per-frame layout changes. Android window controls dimming/180 ms downward exit. System animation scale respected; disabled animations show menu immediately.

App-name tap opens existing rename dialog. Heading has ripple, keyboard focus, accessible label/minimum 48 dp touch height. Rename removed from More; attached apps/hiding remain.

## Build

- `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:lintRelease` passed final code.
- 4 unit tests, no errors.
- Debug/release lint: 0 errors, 27 warnings, no new warnings from change. Breakdown: UseKtx 16, DrawAllocation 2, ClickableViewAccessibility 2, OldTargetApi 1, UnusedAttribute 1, AndroidGradlePluginVersion 1, GradleDependency 1, ObsoleteSdkInt 1, MonochromeLauncherIcon 1, ViewConstructor 1.
- Release APK: 2 425 352 bytes, R8/resource shrinking enabled, not debuggable. +10 325 bytes versus release 0.1.7 includes current widget previews/animation resources; no separate ICU library.
- `apksigner verify`: valid v2 signature. Certificate SHA-256: `7618fbcfaeec605563e1f7b8200be66d60c923c268cbeebdbbbc98b54fd71b5b`, compatible with previous APKs. Original Android Debug certificate retained for updates; build itself release.
- APK SHA-256: `39e8d4c83780a7ede45a88a6b8f7324807fc7a23c3b3d8decc0ed3cf706f46a2`.

## Device checks

### Android 12 / API 31

Dedicated `BetterLauncher_MenuMotion_RC3_API_31`, serial `emulator-5656`, Google APIs arm64-v8a. Final full `:app:connectedDebugAndroidTest`: 19 PASSED, including AppMenuTest requiring visible heading, alias save, HOME cancellation/empty-name restoration. Previous software-rendering attempts stopped on Android system failures/missing HOME focus; successful run followed hardware-renderer restart/explicit HOME assignment.

Signed release APK passed full `scripts/smoke-test.py`: widgets, Settings/Back, Camera search/launch/HOME, swipe popup, hold/additional actions, rename heading/HOME/final alphabet section. No Better Launcher crashes.

Menu separately checked in portrait 720×1600/280 dpi (411 dp width), narrow 560×1245/font scale 1.3 (320 dp), landscape 1600×720 and disabled window/transition/animator animations. Each confirmed visible heading, rename/HOME without save, absent old action, expansion, scroll to Settings/Back. Native captures reviewed. Tab/Enter heading activation/left-alphabet menu also passed; side restored. Scale-1 opening/closing recording confirms sequential decelerating rise/downward exit, without first-frame final position/reverse inset jump. Physical-phone frame rate not measured.

### Android 16 / API 36

Dedicated `BetterLauncher_MenuMotion_RC3_API_36`, serial `emulator-5658`, Google APIs arm64-v8a. Signed release APK passed isolated menu scenarios in portrait 720×1600/280 dpi, landscape 1600×720 and disabled animations: visible heading tap, rename/HOME, additional actions without Rename, scroll, Settings/Back. Recording with restored animation scale 1 shows rise/sequential downward exit; physical-phone smoothness not measured.

Full smoke/narrow screen unconfirmed: System UI ANRs, exit 137/UI Automator null roots. Retry passed widgets, Settings/Back, search, Camera launch/HOME, then waited unsuccessfully for Open Phone after horizontal swipe. Narrow configuration change did not confirm hold-menu opening. These are not successful attempts.

## UI-test conditions

Emulators/serials/installed version isolated from other tasks. Release APK reinstalled/HOME assigned after connected tests. System error dialogs temporarily hidden on test devices after confirmed com.android.phone/System UI failures; Better Launcher crash logcat checks retained. Local helper corrected for fresh UI tree/nonzero visible bounds before tapping. Final `dumpsys package` confirmed code 17/name 0.2.0-rc.8 on both. Error-dialog hiding reverted; wm overrides/font/orientation/animations/alphabet side restored.

## Boundaries

Physical phone, full TalkBack/work profiles unverified. Exact Niagara motion unconfirmed: reference was static screenshots. Full parity not claimed. Local logs/captures/recordings in `.impeccable/review/`, outside Git.

# Compact-alphabet verification

Initial working-change verification after 0.1.7; later candidate results at the end.

## Change

Alphabet shows ★/nonempty visible-app sections only. # last if matching apps exist. Bottom rail 8 dp above reserved footer; spacing 20 dp at font scale 1.0, grows with fonts/shrinks with limited height. Favorites/list share bottom reservation so letters do not move under finger.

## Completed

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` passed.
- 4 unit tests, 0 errors: populated sections/order/conditional #/empty catalog.
- `ANDROID_SERIAL=emulator-5580 ./gradlew :app:connectedDebugAndroidTest`: 18 tests, 0 errors on dedicated `BetterLauncher_CompactRail_API_31`, Android 12/API 31. Adds bottom-section access, free top area/Back edge both handedness modes, keyboard only existing sections/stable rail on list/home transitions.
- Lint: 0 errors, 27 warnings; before/after XML comparison found no new warnings.
- `git diff --check`/`.impeccable/design.json` parsing passed.

Debug APK reinstalled/HOME assigned after connected.

## UI limitations

Full Android 12 `scripts/smoke-test.py` incomplete: UI Automator null root prevented fresh tree. Not successful smoke. Loading-screen capture confirms bottom ★, not ready-catalog verification. Additional scenario also stopped reading tree.

Dedicated `BetterLauncher_CompactRail_API_36` install failed with package-service Broken pipe, then input/window/activity stopped responding. Android 16 UI unverified. Narrow/font scale 1.3, landscape, keyboard/left-handed real-screen checks unfinished; source/instrumentation do not substitute. Temporary settings restored; physical phone/TalkBack untested.

Verdict: implementation/automated checks ready; full visual verification needs working-emulator rerun before release.

## Update: 0.2.0-rc.4

After `origin/main` (`3e11b26`) rebase/AGENTS reread, final debug/release/lint passed: 4 unit tests, 19 Android 12 connected, 0 errors/27 previous warnings, no new warnings. New main test covers heading renaming.

Final signed release (code 12) installed/HOME assigned after connected. Updated full Android 12 smoke passed including heading rename/HOME cancellation. Separate scenario covered lower sections/stable alphabet/footer/IME/Back in portrait 1080×2400, 945×2100/font scale 1.3, left-handed and actual landscape 2400×1080. Initial user_rotation command did not rotate; false landscape rejected. Retry used `wm fixed-to-user-rotation enabled`/`wm user-rotation lock 1`, visually checked. Size/font/orientation/right side restored. Local captures `.impeccable/review/compact-alphabet/`.

apksigner verified certificate matching published 0.1.7; debugging disabled, R8/resource shrinking enabled. Size: 2 416 396 bytes, SHA-256 `4912a6c002b1de7bbc62994b2acc6a19cdb9193f81af025ab2f808f82e447e69`. +1369 bytes from three main animation XMLs/code; no ICU4J.

Verdict: ready as candidate with listed limitations. Final Android 16 unverified; preliminary pre-rebase run stopped on UI Automator. Physical devices/work profiles/TalkBack unverified. [Release notes](releases/0.2.0-rc.4.md).

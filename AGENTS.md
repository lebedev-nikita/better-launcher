# Working on Better Launcher

These instructions apply throughout the repository. The user's explicit task defines the scope and takes precedence over local recommendations.

## Getting started

1. Check `git status --short`, the current branch and `git remote -v`. Preserve other people's uncommitted changes and use the remote appropriate to the task. The primary repository is `better-launcher/better-launcher`; `personal`, when present, points to the starter repository.
2. Read [PRODUCT.md](PRODUCT.md) for requirements and [docs/features.md](docs/features.md) for current coverage. The user chose Android 12+ and the Niagara direction; use original code and assets under the Better Launcher name.
3. Identify the affected responsibility and read its source. Choose the relevant material:

| Change | Read |
| --- | --- |
| Build or dependencies | Environment/build sections in [README.md](README.md), root and app Gradle files, wrapper properties |
| Interface or gestures | [DESIGN.md](DESIGN.md), [docs/home-surface.md](docs/home-surface.md), MainActivity, LauncherViews, styles.xml |
| Persistence or backup | Storage section in [README.md](README.md), LauncherStore, LauncherIntegrationTest |
| Catalog, search or profiles | AppCatalog, AppListRules, both test files |
| System integration | AndroidManifest.xml and the relevant module: LauncherNotificationService, WidgetController, WeatherRepository, or calendar/MediaSession code in MainActivity |
| Verification or release | Verification/release sections in [README.md](README.md), [docs/verification.md](docs/verification.md), relevant release notes |

Check tool versions and Android parameters against the Gradle files, which are the source of truth. Local build outputs, old screenshots and HTML/CSS examples in `.impeccable/design.json` do not represent the current Android UI implementation.

## Implementation invariants

- Continue the current architecture: Kotlin, native Views, Material 3 and one `app` module. MainActivity coordinates screens; catalog, storage, widgets and networking have separate responsibilities. Place new pure logic where it can be tested without an Activity.
- Preserve `AppEntry.id` with the profile serial and ComponentName. Match notifications by both package and UserHandle; launch apps and shortcuts in their application profile.
- Populate the home screen from installed apps and system data. Use demonstration notifications, weather and events only in the test environment.
- Keep notification contents in NotificationHub memory. Request new permissions when the corresponding feature is enabled; run network and file reads off the main thread. Keep secrets, signing keys, SDK paths and user data outside Git.
- When adding settings, update the allowed keys and type validation in LauncherStore.import. Preserve validation of the complete backup before writing, format compatibility and local widget IDs; the backup format has its own version. Verify that an invalid import leaves previous settings intact.
- Preserve the AppWidgetHost ID and handling of incomplete binding, denial and configuration. Bind subscriptions, callbacks, timers and executors to the lifecycle; a HOME intent must return to the home screen. When updating notifications, account for an open dialog and preserve scroll position.
- Style every Better Launcher menu like the app long-press menu: a navy bottom sheet, pale blue labels and outline icons, rounded top corners, text rows that can grow and consistent animation. Use shared menu components; parameters and the reference are in [DESIGN.md](DESIGN.md). Android system menus and third-party widget content are styled by their owners.
- Use dp/sp, text rows that can grow, system/IME insets, separate footer space, and accessible keyboard actions. The alphabet must leave room for the system Back gesture; check large fonts and left-handed mode. Check dimensions, contrast and color roles against DESIGN.md.

## Checks by change type

| Change type | Required verification |
| --- | --- |
| Documentation only | Working links/paths, commands consistent with configuration, and `git diff --check`; APK builds and emulators are not required |
| Kotlin, resources, manifest or Gradle | `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` |
| Storage, import or catalog | Basic checks above and `./gradlew :app:connectedDebugAndroidTest` on a dedicated device; add a behavior or regression check |
| UI, navigation or system integration | Basic checks and a scenario on a dedicated emulator/phone; for the main UI, run `scripts/smoke-test.py` as described in README on Android 12 and the current target version, in portrait, at narrow width/font scale 1.3, and in landscape |

Behavior tests must verify user-visible results or data integrity. Reversible styling changes need appropriate visual verification. Report new lint warnings separately; the confirmed 0.1.0 baseline in docs/verification.md is not a reason to suppress new warnings.

Connected tests uninstall the tested app: reinstall the main APK afterwards and assign it as HOME on the test emulator. The UI script requires English Camera/Phone labels, initial favorites and the alphabet on the right. Use an explicit adb serial and only one UI script per device; restore size, font scale and orientation after temporary changes. An unavailable integration/device means the scenario is unverified, not passed.

## Documentation and completion

1. Update documentation made inaccurate by changed behavior: README for working commands/architecture, docs/features.md for coverage, DESIGN.md and its sidecar for visual rules. Preserve historical release notes and the 0.1.0 report as records of their respective releases.
2. Review the diff, appropriate check results and commit contents. Report the change, completed checks and actual limitations; claim full Niagara parity only after implementing and verifying the remaining features.
3. Commit, push and publish within the user's task. If `main` is explicitly requested, commit there and use a normal fast-forward push to the primary remote. If it diverges from the remote branch, fetch changes and reconcile history without a force push.
4. Before publishing any release candidate, merge the working branch into `main` and push `main` to the primary repository's `origin/main`. Build the final APK from a clean `main` commit already published in `origin/main`, and tag that commit. Immediately before publication, run `git fetch origin` and verify `git merge-base --is-ancestor <tag-commit> origin/main`; publication is allowed only after this check succeeds.
5. Publish only signed release APKs with R8 and unused-resource shrinking. Use debug builds for local verification and do not attach them to releases. Before publication, verify that the APK is not debuggable, its signature is compatible with the previous release, and its size/content have no unexplained growth.
6. For a new release, increment versionCode, update versionName, and prepare release notes, a new tag and a verified APK with SHA-256 as described in README. Verify that the remote commit/tag and published assets match the local files. Ordinary source or documentation changes do not replace the existing 0.1.0 release.

# Languages and regional formats

Better Launcher 0.3.0 includes complete English and Russian app-owned UI resources. English is the default and the fallback for unsupported languages; Russian is selected when the active Android app/system language is Russian. On Android 13 and newer, Android's per-app language settings expose English and Russian through `locales_config.xml`. On Android 12, the launcher follows the system language.

Menus, settings, errors, permission explanations, keyboard/accessibility action names, widget controls and empty states use Android string resources. Counts of notifications, widgets and hidden apps use plural resources; Russian includes one/few/many/other forms. String format arguments preserve provider and user labels without translating them.

Calendar dates use the active locale, and event times follow the user's 12/24-hour preference. All-day descriptions are translated. Weather condition labels use the active app resources; Open-Meteo city search requests Russian for Russian app locales and English otherwise. Existing saved city labels keep their saved text until the user selects a city again.

Installed app labels, shortcuts, notifications, calendar titles, folders, aliases and third-party widget content are supplied by their owners or the user. The launcher does not translate that content. Search remains case/diacritic-insensitive and treats Russian е/ё as equivalent. The rail remains A–Z plus #, with Russian ICU transliteration for Cyrillic section names; it is not a locale-specific alphabet. RTL layout support is partial and is not newly certified by this release.

Run `python3 scripts/check-localization.py` to verify that English/Russian resource keys and format arguments agree. The same check runs in GitHub Actions. `LocalizationTest` verifies language switching in the actual Activity, fallback resources, provider label preservation, English/Russian plurals, weather labels and calendar formatting on Android 12 and the current target Android version.

Repository documentation, Markdown and YAML prose, workflow labels and GitHub release descriptions are in English. Historical reports retain the original versions, checksums, measurements, failures and verification scope; translating them does not rerun those historical checks.

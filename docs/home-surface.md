# Home surface

Mode: Operate. Users repeatedly open an app, read a notification, or find an app with one hand.

## Direction contract
THESIS: Niagara's vertical favorites and alphabetical navigation, explicitly chosen by the user.
OWN-WORLD: Transparent launcher over the system wallpaper with user-controlled dimming from 0% to 100%, warm white text, native app icons, quiet native sheets. Long-press management uses a deep navy surface and pale blue contour icons matching the supplied reference, an app icon/title header, inline additional actions, and a separated settings entry.
STORY: Open an app directly, drag the alphabet to find another, swipe an app for its pop-up.
FIRST VIEWPORT: An unboxed vertical favorite list without a launcher clock or date; time stays in the Android status bar; a compact Latin alphabet at the lower right with a final # (16sp resting letters, 22dp letter step growing with font scale, and 24dp clearance above the footer; short windows cap letter size to the available step); search and settings at the bottom. Use real installed apps. The curved alphabet tracks every finger movement and highlights the selected section; navigation runs on section changes. The app list recycles visible native rows and jumps directly to the latest selected section. Cyrillic names share the first Latin letter of Android ICU Russian-Latin/BGN transliteration (Яндекс → Yandeks → Y); numbers and initials without Latin transliteration use the final #. Every window shows only populated Latin sections from visible apps, favorites, and #.
FORM: User-pinned Niagara composition; no concept roll replaces an explicit reference. Native implementation, code-led because composition is specified.
FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Widgets and navigation

The home screen hosts at most one widget. A rightward swipe on wallpaper or other free space opens a separate widget page physically to its left; a leftward swipe on that page returns home. App rows keep their pop-up swipe, hosted widgets keep their touch handling, and the outer 24dp system Back edge is excluded. The footer widget button provides a keyboard/accessibility alternative. The alphabet ends above the measured footer and respects system/IME insets, keeping every footer action reachable at large font sizes.

The widget page inherits the wallpaper, white type, and native controls. Its header contains Home, a wrapping title, and Add. Widgets form a vertically scrolling list with 28dp side margins and no alphabet overlay; a Home action stays outside the scroll. Empty state explains adding and choosing the home widget. Widget menus move between screens or delete. Selecting another home widget moves the previous one to the widget page. Back and HOME return home; rotation retains the widget page and its scroll. Page transitions use a short directional slide/fade and honor disabled system animations.

Releasing an active gesture below the letters selects the final section even without a final MOVE. After cancellation, a late release does not restart navigation.

Adding a widget opens a sheet with a fixed title/Close, a recycled list of widget labels, application names and 144dp provider previews, plus the shared alphabet control on the right. Provider preview layouts take precedence over images; missing previews are explicitly labeled. The star jumps to the start, Cyrillic labels share Latin sections, and # is last. The list reserves 72dp for the rail; every sheet shows only populated provider sections, with letter size capped to fit short windows. Previews are inert, rows are accessible choices, and browsing allocates no widget IDs. Dismissal cancels resource loading; choosing a row continues the existing binding/configuration flow.

## Compact alphabet review — 2026-10-04

Review verdict: ready for this layout change. Native captures confirm a compact bottom-aligned column, clear separation from footer actions, and preserved system Back space on dedicated Android 12/API 31 and Android 16/API 36 emulators. Portrait, 945×2100 with font scale 1.3, landscape, search/IME, Back, HOME and final-hash gestures passed; both full smoke runs passed without launcher crashes. Display size, font scale and rotation were restored. Captures and logs are local in `.impeccable/review/`.

Debug assembly and all 4 unit tests passed. The 3 instrumentation tests in `AlphabetBoundsTest` and `AppNavigationTest` passed on API 31, including both rail sides, section changes, cancellation and release below the alphabet. Lint completed with 0 errors and 27 warnings; it reports no findings on the changed source lines. The layout detector returned no findings.

## Larger populated alphabet review — 2026-10-05

Review verdict: ready for this change. Resting letters grow to 16sp with a 22dp preferred step; the rail stays bottom-aligned and shows only populated Latin sections in every window. Native captures on Android 12/API 31 and Android 16/API 36 confirm separation from footer actions in portrait, 945×2100/font scale 1.3, and landscape. Short-window letter sizing keeps the index within the available height. Search/IME, Back, HOME, and release below the letters passed on both; a left-handed release capture also passed on API 36. Device size, font scale, orientation, and the rail side were restored.

All 4 unit tests and the final full run of all 20 instrumentation tests passed on API 31, including filtering after hiding, restoring, and renaming apps. Signed release smoke passed on both Android versions. Debug/release lint completed with 0 errors and 27 existing warnings; there are no new findings on changed lines. The layout detector returned no findings. The first full instrumentation run failed the unrelated WidgetHostTest; its isolated retry and the repeated full run passed. Captures and logs are local in `.impeccable/review/rc18/`; release details and limitations are in [0.2.0-rc.19](releases/0.2.0-rc.19.md).

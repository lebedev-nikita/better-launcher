# Home surface

Mode: Operate. Users repeatedly open an app, read a notification, or find an app with one hand.

## Direction contract
THESIS: Niagara's vertical favorites and alphabetical navigation, explicitly chosen by the user.
OWN-WORLD: Transparent launcher over the system wallpaper with a readability scrim, warm white text, native app icons, quiet native sheets.
STORY: Open an app directly, drag the alphabet to find another, swipe an app for its pop-up.
FIRST VIEWPORT: An unboxed vertical favorite list without a launcher clock or date; time stays in the Android status bar; a narrow alphabet at the right; search and settings at the bottom. Use real installed apps. The curved alphabet tracks every finger movement and highlights the selected section; navigation runs on section changes. The app list recycles visible native rows and jumps directly to the latest selected section. Numbers and symbols (`#`) follow letters in both the list and rail; dragging below the final entry keeps the final available section.
FORM: User-pinned Niagara composition; no concept roll replaces an explicit reference. Native implementation, code-led because composition is specified.
FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Widgets and navigation

The home screen hosts at most one widget. A rightward swipe on wallpaper or other free space opens a separate widget page physically to its left; a leftward swipe on that page returns home. App rows keep their pop-up swipe, hosted widgets keep their touch handling, and the outer 24dp system Back edge is excluded. The footer widget button provides a keyboard/accessibility alternative. The alphabet ends above the measured footer and respects system/IME insets, keeping every footer action reachable at large font sizes.

The widget page inherits the wallpaper, white type, and native controls. Its header contains Home, a wrapping title, and Add. Widgets form a vertically scrolling list with 28dp side margins and no alphabet overlay; a Home action stays outside the scroll. Empty state explains adding and choosing the home widget. Widget menus move between screens or delete. Selecting another home widget moves the previous one to the widget page. Back and HOME return home; rotation retains the widget page and its scroll. Page transitions use a short directional slide/fade and honor disabled system animations.

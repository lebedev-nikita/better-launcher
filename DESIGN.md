---
name: Better Launcher
description: A Niagara-style native Android launcher with vertical app lists and quiet settings.
colors:
  primary: "#BCD7F3"
  on-primary: "#031629"
  secondary: "#BCD7F3"
  surface: "#031629"
  on-surface: "#BCD7F3"
  on-surface-variant: "#BCD7F3"
  outline: "#BCD7F3"
  touch-ripple: "#FFFFFF28"
  app-menu-surface: "#031629"
  app-menu-foreground: "#BCD7F3"
typography:
  app-label:
    fontFamily: "sans-serif"
    fontSize: "21sp"
    fontWeight: 400
  app-label-large:
    fontFamily: "sans-serif"
    fontSize: "25sp"
    fontWeight: 400
  title:
    fontFamily: "sans-serif"
    fontSize: "23sp"
    fontWeight: 400
  setting-title:
    fontFamily: "sans-serif"
    fontSize: "17sp"
    fontWeight: 400
  body:
    fontFamily: "sans-serif"
    fontSize: "16sp"
    fontWeight: 400
  label:
    fontFamily: "sans-serif"
    fontSize: "14sp"
    fontWeight: 400
  detail:
    fontFamily: "sans-serif"
    fontSize: "13sp"
    fontWeight: 400
rounded:
  message: "12dp"
  container: "16dp"
  sheet: "24dp"
spacing:
  tight: "4dp"
  small: "8dp"
  medium: "12dp"
  standard: "16dp"
  sheet-edge: "20dp"
  roomy: "24dp"
components:
  action:
    backgroundColor: "transparent"
    textColor: "#FFFFFF"
    typography: "{typography.body}"
    padding: "8dp 16dp"
  icon-button:
    backgroundColor: "transparent"
    textColor: "#FFFFFF"
    size: "48dp"
  app-row:
    backgroundColor: "transparent"
    textColor: "#FFFFFF"
    typography: "{typography.app-label}"
  setting-row:
    backgroundColor: "transparent"
    textColor: "{colors.on-surface}"
    typography: "{typography.setting-title}"
    padding: "10dp 0 10dp 16dp"
  search-field:
    backgroundColor: "transparent"
    textColor: "#FFFFFF"
    height: "56dp"
  bottom-sheet:
    backgroundColor: "{colors.app-menu-surface}"
    textColor: "{colors.app-menu-foreground}"
    rounded: "24dp 24dp 0 0"
    padding: "12dp 24dp 16dp + system insets"
  app-management-sheet:
    backgroundColor: "{colors.app-menu-surface}"
    textColor: "{colors.app-menu-foreground}"
    rounded: "24dp 24dp 0 0"
    padding: "12dp 24dp 16dp + system insets"
  alphabet-rail:
    backgroundColor: "transparent"
    textColor: "{colors.on-surface}"
    width: "92dp"
    letter-step: "22dp × font scale (minimum 1)"
    bottom-gap: "24dp"
  material-switch:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
---

# Design System: Better Launcher

## Overview

**Creative North Star: "Niagara-style native launcher"**

The user selected Niagara's vertical favorites and alphabetical navigation. The current implementation expresses that direction through native Android Views: an open wallpaper surface, white app names, installed app icons, a continuous alphabet control, and dark native sheets. Better Launcher uses original Kotlin source under its own product name and displays the system wallpaper.

Settings and all app-owned menus use the navy and pale blue Material 3 theme, while the launcher itself stays visually open. This document describes the implemented phone UI, not verified Niagara fidelity or full feature parity. The native source is authoritative: [MainActivity.kt](app/src/main/java/app/better/launcher/MainActivity.kt), [LauncherViews.kt](app/src/main/java/app/better/launcher/LauncherViews.kt), and [styles.xml](app/src/main/res/values/styles.xml). Review captures are stored locally in `.impeccable/review/`; the historical five material corrections for 0.1.0 have a scoped `ship` verdict in [verification.md](docs/verification.md).

**Key Characteristics:**

- Unboxed app lists over the system wallpaper.
- White app names over wallpaper; pale blue text, contour icons and controls on navy menu/settings surfaces.
- Native controls, content-driven row heights, and persistent bottom actions.
- A continuous alphabet rail with touch, keyboard, and accessibility alternatives.

## Colors

All app-owned menus share the long-press app menu palette. Settings and native controls use the same Material color roles.

- **Pale blue** (`primary`, `secondary`, `on-surface`, `on-surface-variant`, `outline`, `app-menu-foreground`, #BCD7F3): menu titles, body text, contour icons, input hints and selection controls.
- **Deep navy** (`surface`, `on-primary`, `app-menu-surface`, #031629): settings and every bottom sheet. Installed app labels on the wallpaper remain white.
- **Navy tonal container** (#112B42): notification detail inside a menu; provider previews preserve their original rendering.
- **White touch wash** (`touch-ripple`): native ripple feedback. The frontmatter uses CSS RGBA ordering; Kotlin uses Android ARGB ordering.

Android supplies the wallpaper behind the transparent launcher window, including live wallpapers. Better Launcher draws only a translucent black readability scrim; it has no built-in wallpaper artwork. The wallpaper settings row opens the system wallpaper picker directly. Legacy wallpaper selections in preferences or version 1 backups are accepted for compatibility and ignored when rendering.

**The Readable Wallpaper Rule.** A native Material slider controls black scrim opacity from 0% (fully visible wallpaper) to 100% (black background), in 1% steps. The default is 20%, with no mandatory contrast floor. The inline percentage updates and saves immediately; the slider supports keyboard and accessibility input. Lower dimming can reduce white-text contrast on bright wallpapers. Legacy enabled dimming maps to 20%, disabled dimming to 0%; an explicitly saved percentage takes precedence.

## Typography

**Body Font:** the selected Android family, initially `sans-serif`. The appearance selector offers `sans-serif`, `sans-serif-light`, `sans-serif-condensed`, and `serif`. Frontmatter records the default family; the shared text helper applies the selected family using `Typeface.NORMAL`.

**Character:** app names remain regular weight and prominent. Descriptions sit below their labels with a smaller size. The app uses native `sp` for text, `dp` for layout, and removes extra font padding from shared `TextView`s. There is no fixed modular type ratio or explicit shared line-height token.

### Hierarchy

- **Screen heading:** settings title (26sp); sheet titles use `title`.
- **App labels:** `app-label`, or `app-label-large` when the user enables larger app names. Labels wrap to two lines before ellipsizing.
- **Setting titles:** `setting-title`.
- **Body and actions:** `body`; explanatory copy also uses nearby sizes according to its component.
- **Secondary labels:** `label`, including section labels and supporting information.
- **Details:** `detail`, including settings descriptions and notification previews.
- **Search:** unboxed native `EditText` (19sp).
- **Alphabet:** native `sans-serif-medium`; resting letters (16sp) grow by up to (7sp) near the finger; letter sizes are capped by the available step in short windows, with a selected bubble label (27sp).

**The Grow With Text Rule.** App and settings rows use minimum heights, not fixed text containers; preserve wrapping when Android font scale grows. The fixed footer remains outside the scrolling home content.

## Layout

The phone UI uses one vertical content column. Home content and the app list reserve space for the alphabet: normal content margins are (28dp) at the start and (84dp) at the end; left-handed mode reverses these. The rail occupies a (92dp) overlay, while its resting letter center stays (32dp) inside the screen edge. Its touch corridor is (48dp), between (24dp) and (72dp) from the usable edge. Horizontal system-bar and cutout insets shift the rail inward, including side navigation bars in landscape.

The alphabet forms a compact column anchored 24dp above the bottom of its inset-safe area (above the home footer). Letter centers are 22dp apart at default font scale; the step grows with larger system fonts and shrinks only to fit short windows with 24dp top/bottom clearance. Drawing and touch selection share this geometry, on either side of the screen.

The home list sits in a `ScrollView` above a separate footer with a minimum height (66dp). All home content starts immediately at the inset-safe top, with zero decorative top padding, in portrait and short windows, with or without a widget. System bars and cutouts remain reserved. The home widget has a separate full-width region; its default centered width leaves approximately 36dp on each side, plus framework/provider padding. Favorites and information rows keep their alphabet margins. Below a viewport height (500dp), the optional information-to-list gap decreases. No width breakpoint or tablet layout is implemented.

Home has no built-in clock or date. When no weather, widget, or media content is present, favorites start directly at the inset-safe top; otherwise an (18dp) short-window or (30dp) normal gap separates that content from favorites. Calendar events remain available from settings.

Normal app rows have a minimum height (62dp); compact mode uses (54dp). Installed app icons are (38dp) with a trailing gap (16dp). Settings rows have a minimum height (76dp), grow with their text, and separate label from description with (5dp). The shared spacing steps in the frontmatter recur across actions, content, and containers; they do not imply an enforced grid.

Root content respects system bars, display cutouts, and the larger of navigation or keyboard bottom insets. Home and settings use independent scrolling containers; app results use a native `ListView` that recycles visible rows while keeping content-driven heights. Alphabet navigation selects the section position directly. Bottom sheets resize for the keyboard and cap their height at 85% of the screen, with scrolling content.

**The Separate Footer Rule.** Keep bottom navigation outside the scrolling favorite list so long lists and larger text cannot cover its actions.

## Elevation & Depth

Depth comes primarily from the system wallpaper, opaque navy settings, and bottom sheets over a native dimmed backdrop (0.30). App rows are transparent at rest and while favorites are dragged. There is no shared CSS shadow vocabulary. Transient messages alone set explicit native elevation (6dp); sheet and Material widget effects are resolved by the Android theme. Native ripples communicate touch state without introducing permanent row cards.

**The Open List Rule.** Keep launcher app rows unboxed at rest and throughout favorites dragging; reserve opaque containers for sheets, settings, notification detail, and transient feedback, as the current build does.

## Shapes

The main silhouettes are vertical lists and a narrow alphabet rail. Shared surfaces use soft corners: message (12dp), notification/media container (16dp), and bottom sheet (24dp). Material switches retain their native track and thumb shapes. App icon silhouettes come from the installed applications; the launcher does not restyle them into a new icon system.

The touch helper currently creates its ripple mask with a raw radius (24 device pixels), unlike the density-aware sheet radius. This implementation detail is not a reusable radius token; retain the distinction when reading source, and do not copy it as a layout convention.

## Components

### Buttons

Quiet touch targets on the existing surface. Text actions use `body`, horizontal padding (16dp), vertical padding (8dp), and a minimum height (52dp). Icon buttons occupy (48×48dp), expose a content description, and use vector paths drawn by `GlyphView`. Repeated clickable controls use the native white ripple wash; no bespoke hover animation or focus ring is defined.

### Cards / Containers

Bottom sheets have the shared dark surface, sheet radius, and sheet padding. A shared header uses a 23sp wrapping title with an optional original app/provider icon and a Close target for general sheets. All sheets use 24dp top corners, square bottom corners and inset-aware 12dp/24dp/16dp padding. Content scrolls within 85% of display height; list editors/picker keep the header and actions outside their inner list. Notification detail uses a darker tonal container with (16dp) corners, while the media strip uses a translucent brown container with the same radius. Transient messages use the message radius and native elevation; all messages, including undo, expire within (3000ms) without pausing on touch. A horizontal swipe in either direction dismisses the message without invoking undo; short/cancelled drags return it to its original position. The undo action remains clickable. New feedback replaces the previous message. Feedback appears above an open menu in the active window; transferring it between windows preserves its original deadline. System/cutout/IME insets are reserved, text wraps, and keyboard Escape/Delete and accessibility Dismiss provide alternatives to the gesture.

### App Management Menu

Long press opens a full-width navy sheet with rounded top corners and a continuous surface behind system navigation. The installed app icon (32dp artwork in a 48dp slot) precedes the 23sp title; tapping the title opens rename. The title has a 48dp minimum touch target, ripple, keyboard focus, and an accessible rename label; there is no close icon. Android Back, tapping outside, and HOME dismiss it. Action rows use authored contour glyphs in 48dp slots, a 16dp gap, 17sp wrapping labels, and a minimum height of 52dp plus content padding. The 24dp horizontal inset aligns the icon column and heading.

Favorites, app details, notifications/shortcuts, and uninstall remain directly available. Hidden apps remain discoverable by a nonblank search query, including original names and aliases; the empty list and alphabet exclude them. Their long-press menu exposes “Показывать снова” directly with an open-eye contour icon. Showing again saves immediately, preserves the search query/results and does not add a favorite. All actions form one flat list: attached apps and hide for visible apps appear directly, with no “Дополнительно” row or expansion state. A thin pale-blue divider separates “Настройки Better Launcher”. The sheet grows with its action list and caps at 85% of display height, scrolling for large fonts and short windows. System bars and cutouts are inset; commands retain keyboard focus and accessible names. The sheet enters from below its own height in 300ms with a decelerating curve (0.16, 1, 0.3, 1), and exits downward in 180ms. Entry translates the scroll surface after a pre-draw layout pass has settled system insets and window height, preventing a first-frame flash or jump. Native window animation timing drives dimming and the exit; entry uses the same window scale through a property animator, without per-frame layout changes. Android window animation scaling is respected; disabled animator scaling also skips the transition. No unsupported screen-time or category commands are shown.

### Favorites Order

Home has no reorder button below favorites. “Изменить порядок” in a favorite app’s long-press menu enables ordering. Ordering keeps the actual app/folder rows on home. One 48dp drag handle sits at the trailing edge of the title line, vertically centered with the name, including wrapped names and notification previews below. There is no second controls line or visible up/down button; popup/folder chevrons are hidden during ordering while row taps retain their actions. The handle remains inside the favorites column, clear of the alphabet and system Back edge. With keyboard focus, up/down keys move the item; named TalkBack actions expose valid upward/downward moves without extra buttons. Boundary actions are omitted. Drag/drop saves, cancellation restores positions, and edge scrolling reaches long lists. Done stays in the fixed footer; Back ends ordering and HOME restores home. Apps/folders share the saved full list, retaining unavailable IDs. Refresh waits while ordering. App selection and folder creation retain their management sheet.

### Inputs / Fields

App search uses a transparent, borderless, single-line native `EditText`, pale blue secondary hint, and white entered text. The toolbar combines back, search, and clear controls. Results filter as text changes, and the IME action launches the first result. Sheet forms retain native `EditText` styling and error behavior; they are not a custom shared outlined-field design. Name-editing sheets place a Save action with a check icon at the trailing edge (right in Russian), using the shared icon action with a (52dp) minimum row height, including when the alphabet is on the left.

### Settings and Switches

Settings are label-description rows on an opaque dark surface. Destination rows have a trailing chevron; toggle rows use `MaterialSwitch` from Material (1.13.0), with the theme deciding track, thumb, outline, and state treatments. Their minimum switch target is (56×48dp), and tapping the row also toggles the switch. Do not derive new switch colors from a screenshot: several states are library-resolved.

### Navigation

The footer provides icon-only app search on the left and settings on the right, with the widget-page button centered between them. All three targets are 48×48dp; equal flexible gaps and 22dp side padding keep the outer buttons symmetric. Search retains its accessible name without a visible label. The alphabet rail reserves system/IME insets and the measured home footer height so it cannot cover or intercept footer actions. The app list has back and clear controls; Android Back returns to home. Settings and the widget page hide the alphabet rail. Favorites navigation uses the star entry; the rail contains only populated A–Z sections, with a final hash for numbers, symbols, and initials without a Latin transliteration. Cyrillic app names use the first Latin letter from Android ICU Russian-Latin/BGN transliteration: B/Б, V/В, Y/Я/Ю/Й, Z/Ж. Multi-letter equivalents participate by their first letter (Яндекс → Yandeks → Y); hard/soft signs use the hash. The hash remains last in short windows and in the app list.

### Widgets

Home hosts at most one widget. A rightward swipe on home, including app and folder rows, opens the widget page to the left; a leftward swipe there returns home. The footer button offers the same destination without a gesture. Hosted widget content and ordering handles keep their existing interactions; the outer (24dp) system Back edge remains reserved.

The widget page uses the same wallpaper, type, and native actions. Its header has Home, a (26sp) wrapping title, a 48dp reorder action when at least two page widgets exist (Done while ordering), and Add; the list has (28dp) side margins and (24dp) gaps between widgets. A Home action stays below the scroll. Added widgets have no provider heading or persistent menu button. Holding anywhere on widget content opens a native management sheet; taps and scrolling keep the provider interaction, while moving beyond touch slop, cancellation and detachment cancel the hold. The focused wrapper exposes a named accessibility long-click action and Menu/Shift+F10. Menus offer moving/resizing with a four-direction arrow icon, reconfiguration (when available), placement on home from the widget page and deletion. The home menu has no transfer-to-page action. Width and height are edited directly through four edge handles with immediate saving and a bottom size/Done strip; the menu omits size/position reset. Only provider-supported directions are enabled, respecting provider min/max resize bounds and available window width. Local sizes survive navigation, rotation and backup import; width stays within the current window and narrower widgets start centered and can move horizontally inside their region. Home widgets can expand to the complete inset-safe width, beneath the alphabet overlay, while page widgets retain the 28dp column margins. Full width is an exact reachable endpoint even between 12dp grid points; provider max-width constraints still apply. The host reports measured dimensions through Android 12 widget-size options, excluding framework padding. Choosing a new home widget moves the old one left. A (180ms) directional slide/fade honors disabled Android animations.

The provider picker omits the redundant visible heading and separate Close button. Its only toolbar is a tonal navy-container search field with a leading contour magnifier, pale blue 17sp text/hint, a growing 56dp minimum height and 16dp corners. The trailing 48dp Clear target is visible only for a nonempty query; its space stays reserved to avoid moving the input text. The sheet retains an accessibility pane title. System Back and outside taps dismiss it; HOME returns home. Search matches all query words against widget and application names without case/diacritic sensitivity, using a background-prepared index. Empty/whitespace input restores the full catalog; no matches shows a polite accessible message with guidance to change or clear the query. The query also applies when entered before catalog loading completes. Keyboard opens on field tap; IME Done hides it without selecting a provider. Landscape may use the system keyboard’s fullscreen editor; Back/Done returns to the filtered list. Filter changes reset the list to its start and rebuild the right alphabet from results; no matches hides the rail. The search toolbar remains visible while results scroll and IME/system insets reduce the list. Each unboxed row has a wrapping 18sp widget label, a 13sp application name, and a 144dp preview area that fits images without cropping and scales static layouts proportionally. `previewLayout` takes precedence over `previewImage`; a missing or broken preview falls back to the image, then an application icon with the explicit “Превью недоступно” label. No widget IDs are allocated while browsing.

The picker reuses `AlphabetRail` on the right, reserves 72dp in the list, and leaves the outer system Back edge free. The star jumps to the start; Latin sections use the same Cyrillic transliteration and final hash as apps. Every sheet shows only populated sections plus star, with hash when populated; spacing adapts to font scale and available height. The selected letter follows the first visible row; keyboard and accessibility actions jump to sections. Preview content is inert and excluded from accessibility: the whole row announces adding the named widget and its application. Catalog/resource work uses a background executor, recycled rows cancel stale loads, and dismissal cancels all work. Selecting a row immediately starts the existing Android binding/configuration flow.

### App Rows and Alphabet Rail

App rows use installed icons and labels, optional single-line notification previews, and a chevron when pop-up content exists. Tap launches, holding the name opens management, and holding the icon opens notifications, shortcuts and attached apps. The 38dp icon sits inside a separate 48dp target; names also have at least 48dp height. Horizontal row swipes no longer open pop-ups; a rightward swipe on home or app-list rows opens widgets; search-field editing gestures remain local. A named accessibility action on the name opens quick actions even with hidden icons. Catalog and notification refreshes wait for the current touch to finish and any open sheet to close, preserving the home scroll position. The app pop-up contains notifications, shortcuts, attached apps and “Добавить приложения”; it has no “Открыть …” action for its owner. Empty states and weather failures display actual status instead of sample content.

The alphabet is one continuous native control, bottom-aligned (24dp) above the reserved footer area with a (22dp) letter step at font scale 1.0. The step grows with system font scale and shrinks to fit short windows. Home and the app list reserve the same measured footer height to keep letters under the finger when navigation changes screens; system/IME insets remain outside this area. Empty upper space does not intercept touches. Its curved displacement follows every finger movement immediately; navigation runs only when the selected letter changes (or a new touch starts on that letter). Only populated A–Z and # sections are displayed; ★ always returns home. Releasing below the alphabet commits the last populated section even if the last MOVE was omitted. A cancelled gesture does not reactivate on a late release. Selection grows and appears in a brown bubble while dragging, with a CLOCK_TICK haptic on section changes and exactly one identical tick when an active touch is released, even on the same section. Committing the final hash on release does not duplicate the tick; cancellation and inactive releases stay quiet. Native Android haptic settings remain respected. Accessibility exposes forward/backward scrolling, and keyboard up/down selects sections. The source reserves the outer system Back gesture edge.

The sidecar's self-contained HTML/CSS samples are **illustrative translations of native widgets**, using CSS pixels for preview only. They do not implement Android gestures, system font scaling, Material state resolution, dialogs, app data, or wallpaper rendering. Its synthesized tonal strips are panel metadata rather than runtime palette tokens.

## Do's and Don'ts

### Do:

- **Do** preserve the user's Niagara-style vertical lists and alphabetical navigation.
- **Do** keep app rows transparent and use installed app icons and actual system data.
- **Do** respect the selected wallpaper dimming percentage and use pale blue secondary text.
- **Do** keep scrolling home content separate from persistent footer actions.
- **Do** let text rows grow with font scale and preserve keyboard and accessibility alternatives.
- **Do** use native Material 3 controls within the existing dark theme.

### Don't:

- **Don't** replace the selected launcher composition with a card grid or a new brand metaphor.
- **Don't** copy the raw ripple-mask radius as a density-aware spacing or shape token.
- **Don't** treat panel CSS samples or synthesized tonal ramps as Android source values.
- **Don't** claim exact Niagara fidelity, full feature parity, tablet support, or physical-device validation from this phone build.

## Current candidate 0.2.0-rc.38

Only populated A–Z and # sections appear at every window height; ★ remains the home navigation entry. The resting type is 16sp with a 22dp step, capped to available height. The same footer reservation remains in home and apps. App management keeps a fixed full-height transparent window and bottom-anchored scrolling sheet; inline rename reserves IME space and aligns Save to the right. Favorites enter in-place ordering from the long-press app menu; there is no duplicate reorder button below favorites. Keyboard/accessibility moves remain on the drag handle. Widget touch wrappers observe long press without replacing child taps or scrolling; four-edge editing respects provider constraints, window width and local widget identity, with keyboard/accessibility alternatives and no reset controls over the widget.

## Widget edge editor

Widget management uses the navy app-menu surface and pale blue contour icons, with the provider icon and a wrapping title. The live widget stays on its original home/page surface during editing. Navy 2dp boundary lines and 36dp circular handles inside 48dp targets carry paired vertical or horizontal chevrons; there are no circular-arrow buttons. A bottom navy strip outside system bars shows the current width × height and Done. Provider content is inert while editing; taps outside finish. Narrow widths start centered and can be dragged in both axes on a 12dp grid; upper-edge edits preserve the bottom through a saved local top offset, and lower-edge edits fit above the bottom strip. Moving, cancellation, re-creation, keyboard arrows, range actions, Back/HOME and lifecycle cleanup preserve ordinary provider interaction outside editing. The management menu omits size/position reset. The widget menu omits “О приложении” and “Добавить виджет” on both home and the widget page. Adding remains available from the widget-page header/empty state and the home-widget settings row; provider app details remain in the app long-press menu.

### Widget edge editor

The live widget stays on its current screen. Navy 2dp lines mark its four edges; each enabled edge has a 36dp circular chevron control inside a 48dp touch target. Top/bottom handles change height and left/right handles change width around the saved horizontal center. Drag the widget body to move it in both axes within its own region and the visible area above the editor strip. Edited dimensions and movement snap to a 12dp grid; provider bounds take priority if their interval contains no grid point. Existing sizes remain compatible. Top-edge resizing preserves the bottom edge; expanding width clamps the saved horizontal offset to keep the widget inside the column. A navy pill above system navigation explains moving/resizing, reports dimensions and the grid step, and exposes Done. There are no reset arrows over the widget; the long-press menu omits size/position reset. Keyboard arrows and four named accessibility actions move the body, while edge handles resize. Cancellation restores the complete gesture snapshot. Back and outside taps finish editing, HOME returns home, and lifecycle cleanup removes the overlay. Provider content and the alphabet remain inert and excluded from accessibility during editing.


## Widget order

“Изменить порядок виджетов” enables ordering on the page with live hosted views. Each widget has one trailing 48dp drag handle below its content; there are no visible up/down buttons. The focused handle accepts keyboard up/down and named accessibility moves, omitting boundary actions. Dragging previews the real view and scrolls near the edge; dropping saves and cancellation restores positions. Duplicate providers have ordinal accessible labels for the session. Home identity, bindings, sizes and offsets remain attached to each ID. Done in the header and Back end the mode; HOME returns normal home. Empty/single-widget pages omit the entry.

## Widget resize feedback

Every applied width/height change requests one native CLOCK_TICK on all supported Android versions, including keyboard and accessibility input. The platform API exposes no amplitude parameter for CLOCK_TICK; strength follows the device implementation and system haptic settings. Repeated snapped dimensions, provider/window limits, movement without resizing, release without a new size and cancellation add no tick. Android haptic settings remain respected.


## Top edge controls

The widget editor reserves top system-bar/cutout insets for the complete 48dp touch target. When the widget starts at the inset-safe top, the upper handle is placed inside its bounds; the widget itself gains no layout padding. Saved local movement offsets still apply.

## Shared menus — 0.2.0-rc.32

Every Better Launcher menu follows the app long-press reference: deep navy surface, pale blue regular text and contour icons, rounded top corners and bottom anchoring. App/widget management, attached apps and notifications, folders, option selection, favorites management, widget picker, weather/calendar panels and text editors use the same body/header/action factories in MainActivity. Action rows grow with wrapping 17sp text, a 48dp icon slot and a 16dp gap; the shared header uses 23sp text. Settings use the same palette with their existing full-screen navigation.

All bottom sheets arrive with the same 300ms eased rise, native dimming and 180ms exit, respecting Android animation settings. A fixed transparent dialog window preserves the bottom anchor. System bars, cutouts and IME insets remain reserved; long panels scroll, while the widget picker retains its search toolbar above the list. HOME/Back and outside taps close the current sheet. System-owned permissions, widget configuration, file pickers and provider-rendered widget content retain their owner's appearance.

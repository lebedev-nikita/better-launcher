---
name: Better Launcher
description: A Niagara-style native Android launcher with vertical app lists and quiet settings.
colors:
  primary: "#E9CBB6"
  on-primary: "#352E2A"
  secondary: "#D4B69F"
  surface: "#292724"
  on-surface: "#FFFFFF"
  on-surface-variant: "#EEE5DE"
  outline: "#93877D"
  touch-ripple: "#FFFFFF28"
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
    textColor: "{colors.on-surface}"
    typography: "{typography.body}"
    padding: "8dp 16dp"
  icon-button:
    backgroundColor: "transparent"
    textColor: "{colors.on-surface}"
    size: "48dp"
  app-row:
    backgroundColor: "transparent"
    textColor: "{colors.on-surface}"
    typography: "{typography.app-label}"
  setting-row:
    backgroundColor: "transparent"
    textColor: "{colors.on-surface}"
    typography: "{typography.setting-title}"
    padding: "10dp 0 10dp 16dp"
  search-field:
    backgroundColor: "transparent"
    textColor: "{colors.on-surface}"
    height: "56dp"
  bottom-sheet:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.sheet}"
    padding: "8dp 20dp 24dp"
  alphabet-rail:
    backgroundColor: "transparent"
    textColor: "{colors.on-surface}"
    width: "92dp"
  material-switch:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
---

# Design System: Better Launcher

## Overview

**Creative North Star: "Niagara-style native launcher"**

The user selected Niagara's vertical favorites and alphabetical navigation. The current implementation expresses that direction through native Android Views: an open wallpaper surface, white app names, installed app icons, a continuous alphabet control, and dark native sheets. Better Launcher uses original Kotlin source and Canvas wallpaper artwork under its own product name.

Settings use a warm dark Material 3 theme, while the launcher itself stays visually open. This document describes the implemented phone UI, not verified Niagara fidelity or full feature parity. The native source is authoritative: [MainActivity.kt](app/src/main/java/app/better/launcher/MainActivity.kt), [LauncherViews.kt](app/src/main/java/app/better/launcher/LauncherViews.kt), and [styles.xml](app/src/main/res/values/styles.xml). Review evidence comprises the six current captures in `.impeccable/review/`; the five material corrections have a scoped `ship` verdict in [verification.md](docs/verification.md).

**Key Characteristics:**

- Unboxed app lists over selectable wallpaper.
- White foreground with warm secondary text and restrained peach settings accents.
- Native controls, content-driven row heights, and persistent bottom actions.
- A continuous alphabet rail with touch, keyboard, and accessibility alternatives.

## Colors

Warm dark neutrals support a small peach accent; wallpaper palettes supply the broad visual color.

### Primary

- **Peach accent** (`primary`): Material 3 control color, settings section labels, and selected app checkboxes.
- **Dark brown on accent** (`on-primary`): Material foreground on peach controls; the same brown also appears in the splash background and alphabet selection bubble.

### Secondary

- **Muted peach** (`secondary`): the theme's secondary Material role. It is distinct from the code property named `secondary`, which resolves to `on-surface-variant` for supporting text.

### Neutral

- **Warm charcoal** (`surface`): opaque settings background, bottom sheets, and transient messages.
- **White foreground** (`on-surface`): app names, headings, actions, and programmatically drawn vector icons.
- **Warm pale text** (`on-surface-variant`): settings descriptions, notification previews, date-adjacent information, search hints, and secondary actions.
- **Warm gray outline** (`outline`): outline role supplied to Material 3 controls.
- **White touch wash** (`touch-ripple`): repeated native `RippleDrawable` feedback on actions and clickable rows. The frontmatter uses CSS RGBA ordering; the Kotlin source uses Android ARGB ordering.

The built-in Dune, Forest, and Night wallpapers each draw a three-color gradient and two curved paths. These are original Canvas assets, not raster imports. System wallpaper mode leaves the artwork transparent and draws only its readability scrim. Wallpaper colors are local artwork palettes rather than a shared UI accent ramp.

**The Readable Wallpaper Rule.** Every wallpaper mode keeps the implemented base scrim; the optional dim setting adds darkness instead of removing that contrast floor. Base opacity is Dune (0.48), Forest (0.40), Night (0.08), and System (0.61), with an additional (0.06) when dimming is enabled. These are current rendering values, not proof of legibility for every wallpaper or device.

## Typography

**Body Font:** the selected Android family, initially `sans-serif`. The appearance selector offers `sans-serif`, `sans-serif-light`, `sans-serif-condensed`, and `serif`. Frontmatter records the default family; the shared text helper applies the selected family using `Typeface.NORMAL`.

**Character:** app names remain regular weight and prominent. Descriptions sit below their labels with a smaller size and warmer color. The app uses native `sp` for text, `dp` for layout, and removes extra font padding from shared `TextView`s. There is no fixed modular type ratio or explicit shared line-height token.

### Hierarchy

- **Clock:** native `sans-serif-light`, normal style, tight tracking (`-0.035em`); normal phone (76sp), small-clock preference (58sp), and short window (52sp). This is the current clock component, not a universal display-heading prescription.
- **Screen heading:** settings title (26sp); sheet titles use `title`.
- **App labels:** `app-label`, or `app-label-large` when the user enables larger app names. Labels wrap to two lines before ellipsizing.
- **Setting titles and date:** `setting-title`.
- **Body and actions:** `body`; explanatory copy also uses nearby sizes according to its component.
- **Secondary labels:** `label`, including section labels and footer text.
- **Details:** `detail`, including settings descriptions and notification previews.
- **Search:** unboxed native `EditText` (19sp).
- **Alphabet:** native `sans-serif-medium`; resting letters (11sp) grow by up to (7sp) near the finger, with a selected bubble label (27sp).

**The Grow With Text Rule.** App and settings rows use minimum heights, not fixed text containers; preserve wrapping when Android font scale grows. The fixed footer remains outside the scrolling home content.

## Layout

The phone UI uses one vertical content column. Home content and the app list reserve space for the alphabet: normal content margins are (28dp) at the start and (84dp) at the end; left-handed mode reverses these. The rail occupies a (92dp) overlay, while its resting letter center stays (32dp) inside the screen edge. Its touch corridor is (48dp), between (24dp) and (72dp) from the edge.

The home list sits in a `ScrollView` above a separate footer with a minimum height (66dp). Home top padding is 14% of viewport height, clamped to (36–126dp). Below a viewport height (500dp), it becomes (24dp), the clock shrinks, the clock-to-list gap decreases, and the alphabet shows only available app sections plus favorites. This is a height adaptation; no width breakpoint or tablet layout is implemented.

Normal app rows have a minimum height (62dp); compact mode uses (54dp). Installed app icons are (38dp) with a trailing gap (16dp). Settings rows have a minimum height (76dp), grow with their text, and separate label from description with (5dp). The shared spacing steps in the frontmatter recur across actions, content, and containers; they do not imply an enforced grid.

Root content respects system bars, display cutouts, and the larger of navigation or keyboard bottom insets. Home, app results, and settings scroll independently. Bottom sheets resize for the keyboard and cap their height at 85% of the screen, with scrolling content.

**The Separate Footer Rule.** Keep bottom navigation outside the scrolling favorite list so long lists and larger text cannot cover its actions.

## Elevation & Depth

Depth comes primarily from wallpaper artwork, opaque dark settings, and bottom sheets over a native dimmed backdrop (0.30). App rows are transparent at rest. There is no shared CSS shadow vocabulary. Transient messages alone set explicit native elevation (6dp); sheet and Material widget effects are resolved by the Android theme. Native ripples communicate touch state without introducing permanent row cards.

**The Open List Rule.** Keep launcher app rows unboxed at rest; reserve opaque containers for sheets, settings, notification detail, and transient feedback, as the current build does.

## Shapes

The main silhouettes are vertical lists and a narrow alphabet rail. Shared surfaces use soft corners: message (12dp), notification/media container (16dp), and bottom sheet (24dp). Material switches retain their native track and thumb shapes. App icon silhouettes come from the installed applications; the launcher does not restyle them into a new icon system.

The touch helper currently creates its ripple mask with a raw radius (24 device pixels), unlike the density-aware sheet radius. This implementation detail is not a reusable radius token; retain the distinction when reading source, and do not copy it as a layout convention.

## Components

### Buttons

Quiet touch targets on the existing surface. Text actions use `body`, horizontal padding (16dp), vertical padding (8dp), and a minimum height (52dp). Icon buttons occupy (48×48dp), expose a content description, and use vector paths drawn by `GlyphView`. Repeated clickable controls use the native white ripple wash; no bespoke hover animation or focus ring is defined.

### Cards / Containers

Bottom sheets have the shared dark surface, sheet radius, and sheet padding. A header combines a title with a close target; content scrolls within the screen-height limit. Notification detail uses a darker tonal container with (16dp) corners, while the media strip uses a translucent brown container with the same radius. Transient messages use the message radius and native elevation; optional undo increases dismissal time from (4000ms) to (6000ms).

### Inputs / Fields

App search uses a transparent, borderless, single-line native `EditText`, warm secondary hint, and white entered text. The toolbar combines back, search, and clear controls. Results filter as text changes, and the IME action launches the first result. Sheet forms retain native `EditText` styling and error behavior; they are not a custom shared outlined-field design.

### Settings and Switches

Settings are label-description rows on an opaque dark surface. Destination rows have a trailing chevron; toggle rows use `MaterialSwitch` from Material (1.13.0), with the theme deciding track, thumb, outline, and state treatments. Their minimum switch target is (56×48dp), and tapping the row also toggles the switch. Do not derive new switch colors from a screenshot: several states are library-resolved.

### Navigation

The footer provides app search on the left and settings on the right. The app list has back and clear controls; Android Back returns to home. Settings hide the alphabet rail. Favorites navigation uses the star entry; the normal rail contains Latin letters, available Cyrillic sections, and a final number/symbol section.

### App Rows and Alphabet Rail

App rows use installed icons and labels, optional single-line notification previews, and a chevron when pop-up content exists. Tap launches, long press opens management, and a horizontal swipe opens the app pop-up. Empty states and weather failures display actual status instead of sample content.

The alphabet is one continuous native control. Its curved displacement follows finger distance immediately; selection grows and appears in a brown bubble while dragging, with a haptic tick on section changes. Accessibility exposes forward/backward scrolling, and keyboard up/down selects sections. The source reserves the outer system Back gesture edge.

The sidecar's self-contained HTML/CSS samples are **illustrative translations of native widgets**, using CSS pixels for preview only. They do not implement Android gestures, system font scaling, Material state resolution, dialogs, app data, or wallpaper rendering. Its synthesized tonal strips are panel metadata rather than runtime palette tokens.

## Do's and Don'ts

### Do:

- **Do** preserve the user's Niagara-style vertical lists and alphabetical navigation.
- **Do** keep app rows transparent and use installed app icons and actual system data.
- **Do** retain wallpaper contrast floors and warm secondary text.
- **Do** keep scrolling home content separate from persistent footer actions.
- **Do** let text rows grow with font scale and preserve keyboard and accessibility alternatives.
- **Do** use native Material 3 controls within the existing dark theme.

### Don't:

- **Don't** replace the selected launcher composition with a card grid or a new brand metaphor.
- **Don't** copy the raw ripple-mask radius as a density-aware spacing or shape token.
- **Don't** treat panel CSS samples or synthesized tonal ramps as Android source values.
- **Don't** claim exact Niagara fidelity, full feature parity, tablet support, or physical-device validation from this phone build.

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
  app-management-sheet:
    backgroundColor: "{colors.app-menu-surface}"
    textColor: "{colors.app-menu-foreground}"
    rounded: "24dp 24dp 0 0"
    padding: "12dp 24dp 16dp + system insets"
  alphabet-rail:
    backgroundColor: "transparent"
    textColor: "{colors.on-surface}"
    width: "92dp"
    letter-step: "16dp × font scale (minimum 1)"
    bottom-gap: "24dp"
  material-switch:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
---

# Design System: Better Launcher

## Overview

**Creative North Star: "Niagara-style native launcher"**

The user selected Niagara's vertical favorites and alphabetical navigation. The current implementation expresses that direction through native Android Views: an open wallpaper surface, white app names, installed app icons, a continuous alphabet control, and dark native sheets. Better Launcher uses original Kotlin source under its own product name and displays the system wallpaper.

Settings use a warm dark Material 3 theme, while the launcher itself stays visually open. This document describes the implemented phone UI, not verified Niagara fidelity or full feature parity. The native source is authoritative: [MainActivity.kt](app/src/main/java/app/better/launcher/MainActivity.kt), [LauncherViews.kt](app/src/main/java/app/better/launcher/LauncherViews.kt), and [styles.xml](app/src/main/res/values/styles.xml). Review captures are stored locally in `.impeccable/review/`; the historical five material corrections for 0.1.0 have a scoped `ship` verdict in [verification.md](docs/verification.md).

**Key Characteristics:**

- Unboxed app lists over the system wallpaper.
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
- **Warm pale text** (`on-surface-variant`): settings descriptions, notification previews, weather information, search hints, and secondary actions.
- **Warm gray outline** (`outline`): outline role supplied to Material 3 controls.
- **Deep navy app menu** (`app-menu-surface`) and **pale blue foreground** (`app-menu-foreground`): long-press management sheet, matching the supplied Niagara reference. Settings and other sheets keep the warm Material palette.
- **White touch wash** (`touch-ripple`): repeated native `RippleDrawable` feedback on actions and clickable rows. The frontmatter uses CSS RGBA ordering; the Kotlin source uses Android ARGB ordering.

Android supplies the wallpaper behind the transparent launcher window, including live wallpapers. Better Launcher draws only a translucent black readability scrim; it has no built-in wallpaper artwork. The wallpaper settings row opens the system wallpaper picker directly. Legacy wallpaper selections in preferences or version 1 backups are accepted for compatibility and ignored when rendering.

**The Readable Wallpaper Rule.** A native Material slider controls black scrim opacity from 0% (fully visible wallpaper) to 100% (black background), in 1% steps. The default is 20%, with no mandatory contrast floor. The inline percentage updates and saves immediately; the slider supports keyboard and accessibility input. Lower dimming can reduce white-text contrast on bright wallpapers. Legacy enabled dimming maps to 20%, disabled dimming to 0%; an explicitly saved percentage takes precedence.

## Typography

**Body Font:** the selected Android family, initially `sans-serif`. The appearance selector offers `sans-serif`, `sans-serif-light`, `sans-serif-condensed`, and `serif`. Frontmatter records the default family; the shared text helper applies the selected family using `Typeface.NORMAL`.

**Character:** app names remain regular weight and prominent. Descriptions sit below their labels with a smaller size and warmer color. The app uses native `sp` for text, `dp` for layout, and removes extra font padding from shared `TextView`s. There is no fixed modular type ratio or explicit shared line-height token.

### Hierarchy

- **Screen heading:** settings title (26sp); sheet titles use `title`.
- **App labels:** `app-label`, or `app-label-large` when the user enables larger app names. Labels wrap to two lines before ellipsizing.
- **Setting titles:** `setting-title`.
- **Body and actions:** `body`; explanatory copy also uses nearby sizes according to its component.
- **Secondary labels:** `label`, including section labels and footer text.
- **Details:** `detail`, including settings descriptions and notification previews.
- **Search:** unboxed native `EditText` (19sp).
- **Alphabet:** native `sans-serif-medium`; resting letters (11sp) grow by up to (7sp) near the finger, with a selected bubble label (27sp).

**The Grow With Text Rule.** App and settings rows use minimum heights, not fixed text containers; preserve wrapping when Android font scale grows. The fixed footer remains outside the scrolling home content.

## Layout

The phone UI uses one vertical content column. Home content and the app list reserve space for the alphabet: normal content margins are (28dp) at the start and (84dp) at the end; left-handed mode reverses these. The rail occupies a (92dp) overlay, while its resting letter center stays (32dp) inside the screen edge. Its touch corridor is (48dp), between (24dp) and (72dp) from the usable edge. Horizontal system-bar and cutout insets shift the rail inward, including side navigation bars in landscape.


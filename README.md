# Thor Tools

Utilities for the **AYN Thor**, built as a fork of [OdinTools](https://github.com/langerhans/OdinTools) by
langerhans. Thor Tools keeps OdinTools' plain settings-list style and adds Thor-specific features on top.
It also runs on the Odin 2.

> Early development. Features marked *(planned)* aren't in the app yet.

## Works alongside OdinTools

Thor Tools has its own package name, so both apps can be installed at the same time. Some features change the same
system settings OdinTools does: per-app controller style, L2/R2, performance and fan; the external display style;
charge automation; and saturation or vibration re-applied at boot. **If OdinTools is installed, those features start
switched off in Thor Tools.** Each of them carries a small *OdinTools* tag in its menu, and the *OdinTools
coexistence* page under Setup & diagnostics explains each one.
You can still switch any of them on. Turn the matching feature off in OdinTools first, so the two apps don't undo
each other.

## Features

### From OdinTools
- Per-app overrides for controller style, L2/R2 mode, performance and fan
- Controller and L2/R2 style for external displays
- Quick settings tiles that cycle the controller style and L2/R2 mode
- Single-press Home
- Display saturation; on the Odin 2 also vibration strength, M1/M2 remapping and charge-limit automation

### New in Thor Tools
- OdinTools coexistence: overlapping features stay off while OdinTools is installed
- Diagnostics: device facts, a button tester, and a report you can copy or save to Downloads
- Actions for the quick panel's tiles: switch the Xbox / Standard layout, cycle controller, L2/R2, performance and
  fan modes, 60 / 120 Hz, swap screens, bottom screen on/off, per-screen brightness, volume, swipes, keep the screens
  on, close background apps or the other screen's app, AYN's drawer and more. Home goes home on the screen you're using
- Hotkeys: taps, double and triple taps and holds of Home, Back, AYN and the volume keys, and combos (hold one
  button, press another, including a D-pad direction or a stick flick) run any of the actions or open an app on the
  screen you choose. A single press of Home or Back still does its usual job, and game buttons are only used in
  combos, so games keep their controls. Record a hotkey by pressing it, start from ready-made suggestions, turn hotkeys
  off per app; each hotkey can show what it did on screen, and a controller move can also lock the controller there.
  Hotkeys keep working with the quick panel open
- Quick panel on the AYN button: by default a tap opens an overlay on the bottom screen with modes, screenshot,
  recents, volume and brightness, driven with the D-pad; holding AYN still opens AYN's own drawer
- Themes: Midnight, Graphite, Glacier, Ember, Forest, Daylight or Android's own colours, for the app and the panel
- Stays running: a quiet notification, a battery-optimisation exemption and a check after boot
- Permissions & access: every permission Thor Tools relies on, with its real state and a one-tap fix
- Charging stability alert: a notification when charging keeps flipping between fast, slow and not charging,
  which on the Thor usually means a reboot is needed
- Charging dashboard: live watts, 80 % limit, direct power, battery health *(planned)*
- Dual-screen tools: move or swap the app between screens, screen modes, bottom-screen auto-off *(planned)*
- Guided first-run setup *(planned)*

## Install

1. Download `ThorTools-<version>.apk` from the releases page, or build it yourself (below).
2. Copy it to the Thor and open it to install.
3. Open Thor Tools. It uses AYN's built-in system service, so there's no root, Shizuku or ADB to set up.
   AYN's **"Force SELinux"** option must be off, which is the factory default.

**Upgrading from 0.1.0:** uninstall it first. That build used OdinTools' package name, so it can't be updated in
place, and it would keep running next to the new one. Thor Tools shows a reminder while it is still installed.

## Build

Requirements: JDK 25 and the Android SDK (Android Studio installs it).

```
build-apk.cmd          lint, unit tests and a signed release APK in dist\
build-apk.cmd fast     APK only
build-apk.cmd clean    from a clean build directory
```

On the first run the script creates a personal signing key in `%USERPROFILE%\.thortools`. Back that folder up:
updates must be signed with the same key.

## Credits and license

Based on [OdinTools](https://github.com/langerhans/OdinTools) © 2024 Maximilian Keller (langerhans), MIT licensed.
Thor Tools changes and additions are also MIT. See [LICENSE](LICENSE).

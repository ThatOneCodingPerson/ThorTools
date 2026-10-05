# Thor Tools

Utilities for the **AYN Thor**, built as a fork of [OdinTools](https://github.com/langerhans/OdinTools) by
langerhans. Thor Tools keeps OdinTools' plain settings-list style and adds features made for the Thor's two screens,
its AYN button and its lid. It also runs on the Odin 2.

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

### Quick panel (AYN button)
- A tap of the AYN button opens a panel on the bottom screen; holding AYN still opens AYN's own drawer. Use it by
  touch or with the D-pad, and swipe or press L1 / R1 for more pages
- Every page is a grid of widgets, each in a choice of sizes:
  - **Icons**: switch the Xbox / Standard layout, cycle controller style, L2/R2, performance and fan, 60 / 120 Hz,
    swap screens, bottom screen on/off, screenshot, Recent apps, close the current app, AYN's drawer and much more
  - **Sliders**: volume and the brightness of each screen, standing or lying down
  - **Device stats**: refresh rate, CPU, GPU, power, memory and temperatures
  - **Now playing**: what's playing, with play, pause and skip
  - **Battery and charging**: level, watts in or out, time to full or time left
  - **Performance graph**: CPU, GPU and temperature over the last minute
  - **App shortcuts**: your apps, opened on the screen you choose
  - **Notes**: draw on it with a finger, or type a note in the app
- Arrange pages and widgets in the panel editor with a live preview; open it from the pencil in the panel
- By default only the AYN button (or the panel's close button) closes it, so you can use several tiles in a row

### Hotkeys
- Taps, double and triple taps and holds of Home, Back, AYN and the volume keys, and combos (hold one button, press
  another, including a D-pad direction or a stick flick) run any action or open an app on the screen you choose
- A single press of Home or Back still does its usual job, and game buttons are only used in combos, so games keep
  their controls
- Record a hotkey by pressing it, start from ready-made suggestions (the Thor Profile among them), turn hotkeys off
  per app; a hotkey can show what it did on screen, and a controller move can also lock the controller there

### Two screens
- Swap the apps between the screens, close the other screen's app, Home on the screen you're using or on both
- Move the controller to the top or bottom screen, and lock it there
- Brightness per screen

### Power management
- **When the lid closes**: pick what happens while it's closed: power-saving mode, close background apps, pause media,
  Wi-Fi, Bluetooth or airplane mode off, the AYN and volume buttons, the controller or the touchscreens turned off,
  and back to sleep after an accidental wake (plugging in the charger, say). Opening the lid puts everything back and
  checks that it did. Everything is off until you switch it on, and the power button always works
- **Charging stability alert**: a notification when charging keeps flipping between fast, slow and not charging,
  which on the Thor usually means a reboot is needed

### From OdinTools
- App profiles: per-app controller style, L2/R2 mode, performance and fan
- Controller and L2/R2 style for external displays
- Quick settings tiles that cycle the controller style and L2/R2 mode
- Display saturation; on the Odin 2 also vibration strength, M1/M2 remapping and charge-limit automation

### Look and setup
- Themes: Midnight, Graphite, Glacier, Ember, Forest, Daylight, Android's own colours or one you make yourself, for
  the app and the panel
- Permissions & access: every permission Thor Tools relies on, with its real state and a one-tap fix
- Diagnostics: device facts, a button tester, and a report you can copy or save to Downloads
- Stays running: a quiet notification, a battery-optimisation exemption and a check after boot

### Planned
- Charging dashboard: live watts, 80 % limit, direct power, battery health
- Screen modes and bottom-screen auto-off
- Guided first-run setup

## Install

1. Download `ThorTools-<version>.apk` from the [Releases](https://github.com/ThatOneCodingPerson/ThorTools/releases)
   page, or build it yourself (below).
2. Copy it to the Thor and open it to install.
3. Open Thor Tools and turn on its accessibility service when it asks; Setup & diagnostics shows anything else it
   needs. It uses AYN's built-in system service, so there's no root, Shizuku or ADB to set up. AYN's
   **"Force SELinux"** option must be off, which is the factory default.

To update, install the newer APK over the old one; your settings stay. Releases are signed with the project's key and
an APK you build yourself is signed with your own, so switching between the two means uninstalling first.

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

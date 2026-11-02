<p align="center">
  <img src="docs/icon.svg" height="128" alt="Doseful icon">
</p>

<h1 align="center">Doseful</h1>

<p align="center">
  <b>A private, offline medication reminder with Material 3 Expressive design.</b>
</p>

<p align="center">
  <a href="https://github.com/maxcodl/Doseful/releases/latest"><img src="https://img.shields.io/github/v/release/maxcodl/Doseful?label=Download&color=00796B&cacheSeconds=300&v=2" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Size-~2%20MB-00796B" alt="App size">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF" alt="Kotlin and Jetpack Compose">
  <img src="https://img.shields.io/badge/Network-none-blue" alt="No network permission">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-orange" alt="GPL-3.0"></a>
</p>

<p align="center">
  Track your medicines, get reminders on time, and keep an eye on stock.<br>
  No account. No cloud. No internet permission. Everything stays on your phone.
</p>

## Screenshots

| <img src="docs/screenshots/welcome.png" width="170"/> | <img src="docs/screenshots/today.png" width="170"/> | <img src="docs/screenshots/medicines.png" width="170"/> | <img src="docs/screenshots/history.png" width="170"/> | <img src="docs/screenshots/profile.png" width="170"/> |
| :--: | :--: | :--: | :--: | :--: |
| Welcome | Today | Medicines | History | Profile |

## Features

### Reminders you will not miss
- **Alarm-style reminders.** Rings like an alarm, with vibration, until you tap **Taken** or **Skip** right in the notification. Prefer a gentler sound? Switch to a normal notification, and use **Send test reminder** to hear either.
- **Exact timing.** Alarms are restored after a reboot, an app update or a timezone change, and onboarding asks for the permissions that keep them reliable: notifications, exact alarms and background activity.
- **Persistent "Today" notification.** A quiet summary of today's doses and the next one. It has its own channel, so you can hide it in your phone's notification settings.
- **Flexible schedules.** Several times per day, any days of the week, per medicine.
- **Stock and refills.** Taking a dose lowers your stock, you get a refill alert before a pack runs out, and expiry dates are always visible.

### Your health, in one place
- **Today.** A greeting with a large name, a hero card that counts up your doses, overview tiles and your schedule. Swipe a dose right for Taken or left for Skip.
- **Medical ID.** An ID card with a blood type badge, allergy and condition tags, and tap-to-call emergency and doctor contacts. A full-screen, large-text view is there for emergencies. Only what you fill in is shown.
- **Appointments.** Visits with the doctor, place, phone and notes, reminders a day and an hour before, and shortcuts to open the address in Maps, call, or add the visit to your calendar.
- **Health log.** Track weight, blood pressure, blood sugar, heart rate and temperature, each with a chart. A new weight updates your BMI.
- **Cycle tracker.** Opt in from Profile or setup. Log periods, flow and symptoms, see predictions and a calendar, cycle insights, irregularity warnings and remedies. Partner mode gives friendly tips and notifications on how to support her.
- **Adherence.** A 14-day ring, a streak counter, a bar chart and a day-by-day list, plus your stats on the Profile page.
- **App lock.** Optionally ask for your phone's PIN, pattern or fingerprint when Doseful opens.

### Made to feel good
- **Material 3 Expressive.** Morphing shapes, expressive motion, wavy progress indicators and Material You dynamic colour.
- **Swipe between pages.** A carousel of Today, Medicines, History and Profile. Tap a far tab and it jumps straight there.
- **Liquid Glass navigation bar.** An optional floating bar with four layouts, adjustable corner radius, five selection looks and a highlight that glides between items. A full glass editor lets you tune blur, refraction, tint, edge line, reflections and shadows.
- **Make it yours.** Light, dark or system theme, AMOLED black, an app-wide corner roundness slider and an animation speed slider.
- **Profile with photo.** Pick a picture from the system photo picker. It never leaves the device.

### Yours to keep
- **Backup and restore.** Export everything to a single JSON file and restore it later.
- **Try before you commit.** A demo mode in onboarding runs the whole app on fake data. Your own data is never touched.
- **Tiny.** The release APK is about 2 MB.

## Download

Get the latest APK from the **[Releases page](https://github.com/maxcodl/Doseful/releases/latest)** and install it. Android 8.0 (API 26) or newer.

> Releases are signed with the same key every time, so updates install over older versions.

## Privacy

Doseful declares **no `INTERNET` permission**, so it cannot send anything anywhere. There are no accounts, no analytics and no ads. Your medicines, history and profile live in the app's private storage. Backups are plain JSON files that you control.

## Roadmap

- **Package scanner:** fill the medicine form by photographing the box. A first version (on-device text recognition plus simple parsing) read too much noise to be useful, so it was removed. It will come back once it is accurate enough, as an optional add-on so the main app stays tiny and offline.

## Try it with sample data

`docs/sample-backup.json` contains a fake profile, six medicines with two weeks of history, appointments, a Medical ID and health readings. To load it:

1. Copy the file to your phone.
2. Open Doseful, then **Profile, Backup, Restore** and pick the file.

Regenerate it with fresh dates any time: `python docs/make_sample_backup.py`.

## Build from source

You need JDK 17 and the Android SDK (API 37).

```bash
git clone https://github.com/maxcodl/Doseful.git
cd Doseful
./gradlew assembleDebug      # app/build/outputs/apk/debug/
./gradlew assembleRelease    # app/build/outputs/apk/release/
```

Release and debug builds are signed with `key.properties` if it exists in the project root, so a debug build installs over a release one and keeps its data. Without it, they fall back to the shared Android debug key:

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

`key.properties` is git-ignored. Never commit it or your keystore.

### Automatic builds

GitHub Actions builds every push and pull request. Pushing a tag such as `v0.2.0` builds the signed release APK and publishes it as a GitHub Release, using that version's section of [CHANGELOG.md](CHANGELOG.md) as the release notes. See [`.github/workflows/build.yml`](.github/workflows/build.yml) for the secrets it needs.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for what changed in each version.

## Built with

Kotlin, Jetpack Compose, Material 3 Expressive, Room and AlarmManager. The glass bar uses the [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) library.

## Credits

- [Nunito](https://github.com/googlefonts/nunito) typeface, SIL Open Font License 1.1 (see [`licenses/`](licenses/)).
- [Material Symbols](https://fonts.google.com/icons) by Google, Apache License 2.0.
- [Backdrop (Liquid Glass)](https://github.com/Kyant0/AndroidLiquidGlass) by Kyant, Apache License 2.0.

## License

Doseful is free software, released under the **GNU General Public License v3.0**. See [LICENSE](LICENSE).

# Changelog

All notable changes to Doseful are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/), and versions follow [Semantic Versioning](https://semver.org/).

## [0.2.0] - 2026-11-01

A big update: a redesigned Today screen, a much fuller Profile, a floating Liquid Glass navigation bar you can restyle, and reminders that you can actually hear.

### Added

**Health and care**
- **Appointments.** Title, doctor, clinic or hospital, phone, notes, date and time. Reminders a day before and an hour before. Shortcuts to open the address in Maps, call, or add the visit to your calendar. Past appointments are kept.
- **Health log.** Track weight, blood pressure, blood sugar, heart rate and temperature, each with a chart and history. A new weight also updates your profile weight and BMI.
- **Medical ID card.** Blood type badge, allergy and condition tags, and emergency and doctor contact tiles with call buttons. A full-screen, large-text view is available for emergencies.
- **Profile stats.** Adherence over 14 days, current streak, and doses taken this month.
- **App lock.** Optional. Uses your phone's PIN, pattern or fingerprint and locks again after 30 seconds in the background. Doseful stores no password.

**Reminders**
- **Alarm-style reminders** (default). Rings like an alarm, with vibration, until you tap Taken or Skip (up to 5 minutes). A normal notification sound is available as an option, and **Send test reminder** lets you hear either.
- **Persistent "Today" notification.** A quiet summary of today's doses and the next one. It has its own channel, so you can hide it in your phone's notification settings.
- **Run in background permission.** Requested in onboarding and Settings so battery savers do not delay reminders.

**Look and feel**
- **Redesigned Today screen.** A random time-of-day greeting with a large name, a hero card with a counting-up total and progress bar, overview tiles with coloured shape icons, and schedule cards with status icons.
- **Floating Liquid Glass navigation bar** (optional), built on the [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) library. Four layouts (Pill, Icons, Labels, Dock), separate bar and selection corner radius, five selection looks, and a highlight that glides between items.
- **Glass editor.** Blur, refraction height and amount, colour saturation, tint, depth effect, colour fringing, edge line width and brightness, edge reflection style, outer shadow and inner glow, with reset.
- **Swipeable page carousel.** Swipe between Today, Medicines, History and Profile. Tapping a far tab jumps straight there instead of scrolling through the pages in between.
- **Swipe actions on cards.** Swipe a dose right for Taken or left for Skip. Swipe a medicine right to edit or left to delete.
- **Smooth animations** with an **Animation duration** setting (0 to 1.5, default 0.6).
- **Corner roundness** setting that scales the corners of every card, panel, field and dialog.
- **Predictive back** and edge-to-edge drawing.
- **Medicine list icons** and a pill icon for the Medicines tab.

**Settings and data**
- Settings moved to their own page with separate, collapsible cards: Theme, Navigation bar, Glass, Privacy, Reminders, Backup and About.
- **Contact developer** and a "Made with love and coffee" footer in About.
- Backups now include appointments, health readings and the Medical ID, and older backups still restore. The sample backup and demo mode show the new features.

### Changed
- **Typeface** is now Nunito (free, rounded), with its heaviest weight for big numbers.
- **Demo mode** runs the whole app on fake data, using a separate throwaway database and profile that are wiped on exit.
- Notification text reads "Time to take 1 tablet · 500 mg" and uses the app's pill icon.
- The Medical ID card shows only what you have filled in, with allergies and conditions side by side, and contact tiles only for contacts you have added.
- Switching tabs keeps every page loaded, so there is no blank or black page and scroll positions are remembered.
- The database upgraded to version 3 with an automatic migration. Existing data is kept.

### Fixed
- Dose reminders were silent by default. They now use a proper alarm channel with sound and vibration.
- Crash when opening the glass settings (the "%" in the edge radius label was read as a format code).
- Edge radius changed only the selection and not the bar itself.
- A square press ripple showed behind the rounded selection when switching tabs.
- The screen went black for a moment, and there was a delay, when switching tabs.
- The last card on a list could sit under the add button and could not be tapped.

### Removed
- The package scanner. Photographing a box and reading it with simple parsing was not accurate enough to be useful. It is on the roadmap as an optional add-on once it is reliable.

## [0.1.1] - 2026-11-01

### Fixed
- The scanner add-on crashed in release builds after taking a photo.

### Removed
- The scanner add-on and its onboarding page, which brought the app to about 1.6 MB.

## [0.1.0] - 2026-11-01

First public release.

- Medicine tracking with doses, strength, form, stock, refill threshold, expiry, batch and manufacturer.
- Daily schedules with several times per day and chosen days of the week.
- Exact-alarm reminders with Taken and Skip actions in the notification, restored after a reboot.
- Refill alerts, adherence history with a streak, profile with photo, JSON backup and restore.
- Material 3 Expressive design with dynamic colour, light, dark and AMOLED themes.
- Onboarding with a demo mode.
- No network permission, fully offline.

# Cycle tracker audit checklist

Source: code review of the cycle tracker work. Tick when fixed and verified.

## Bugs
- [x] 1. Memes replaced real PRE/PMS/day-1 notifications on every 4th day
- [x] 2. "Period started/ended today" button flipped back and created duplicate periods; future dates could be started
- [x] 3. Enabling the tracker during onboarding never armed the daily reminder
- [x] 4. Toggling the tracker rebuilt the Profile page (lost scroll and edits, flashed Cycle)
- [x] 5. Cycle tab kept yesterday's date after midnight
- [x] 6. Backup missed the reminder setting; old backups switched the tracker off
- [x] 7. "Period ongoing" defined three different ways (9 / 10 / 9 days)

## Cleanup
- [x] 8. Remove unused `dao` parameter from `scheduleCycle`; stop re-arming the alarm on every log edit
- [x] 9. Move JUnit to the version catalog

## UI
- [x] 10. Polish onboarding: updated feature list, dedicated Period tracker page (after "About you"), partner-aware copy, reminders and partner name

## Not started (needs a decision)
- [ ] 11. Honour the phone's 24-hour setting and locale for times (existing behaviour, not caused by this work)

## Final
- [x] Unit tests pass, release build installs
- [ ] Onboarding and fixes checked on device (needs a fresh install or cleared data)

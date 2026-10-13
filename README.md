# Noctra (NEET OS) - milestone 1: UI, planner, notifications, accounting

Native Android, Kotlin, Jetpack Compose, Material 3, Room, DataStore, Navigation Compose. minSdk 26, targetSdk 35.

Status: source written but NOT compiled or run. No Android SDK or network was available while writing it.
Expect to fix a few compile errors on first build. Send me the error output and I will fix them.

## Open and run
1. Install Android Studio (Ladybug or newer) with Android SDK 35.
2. File > Open > select this folder. Let Gradle sync (it downloads dependencies).
3. Run the `app` configuration on a phone or emulator (API 26+).
4. Tests: `./gradlew testDebugUnitTest` (JVM) and `./gradlew connectedDebugAndroidTest` (device).
   If `./gradlew` is missing, run `gradle wrapper --gradle-version 8.9` once, or just use Android Studio.
5. APK later: Build > Build APK(s). Output: app/build/outputs/apk/debug/app-debug.apk.

## Screens
Onboarding with permissions, Wake-up confirmation, Plan (home), Tasks, Add/Edit task, Hourly check-in, Daily report (review, correct, finalize), Insights and history, Profile/Settings.
Not in this milestone: AI coach chat, AI planning, AI review. No AI buttons exist yet.

## Notification behavior
- Wake alarm at the configured time (default 10:00). Actions: YES, I'M UP and NOT YET (asks again in 15 min).
- After confirmation, a check-in alarm fires every interval (default 60 min).
- Android shows at most 3 notification buttons, so the check-in notification has STILL WORKING, TAKING A BREAK and ANSWER IN APP. The in-app check-in has all five responses. COMPLETED, DISTRACTED and STUCK need your input (minutes, confirmation), so they live in the app.
- Check-ins are held during a break or while paused, and rescheduled when you end the break or resume.
- Unanswered check-ins stay recorded as unanswered. They are never turned into distraction.
- Alarms are rescheduled after reboot, app update, and time or timezone change.

## Time accounting
No timers. Study, distraction and break minutes are what you enter (verified, user-entered). Break start and end are timestamps. Unclassified = elapsed since wake minus the rest. Subject progress on Insights is labeled ESTIMATE.

## Known limits (be honest about these)
- Exact alarms need the "Alarms and reminders" permission on Android 12+. Without it, Android may delay reminders.
- Aggressive battery savers (some Xiaomi, Oppo, Vivo, Samsung modes) can still delay or kill alarms. Delivery cannot be guaranteed.
- Charts are simple Compose bars, not a chart library.
- Separate break table was merged into activity logs (kind = BREAK). Daily plan is derived from task plan dates.
- Export is CSV text via the share sheet.
- Database schema v1. Room schemas are exported to app/schemas for future migrations.

## Layout
app/src/main/java/com/noctra/app
- data: Room entities, DAOs, repository, settings (DataStore), stats calculator
- notify: notification builder, AlarmManager scheduler, receivers, permission helpers
- viewmodel: Home, Planner, TaskEdit, CheckIn, Wake, Insights, Report, Settings
- ui: theme, components, screens, navigation

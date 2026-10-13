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
Onboarding with permissions, Wake-up confirmation, Plan (home), Tasks, Add/Edit task, Hourly check-in, Daily report (review, correct, finalize), Insights and history, Settings.
Noc (AI coach) chat tab: talks to Google Gemini with your own API key, can add, edit, delete, start, complete and postpone tasks, logs study you already did, and logs lost time when you tell it you messed up. See "Noc" below.
Insights has LOG PAST STUDY: add study hours for any past day (date, hours/minutes, subject, note) and delete them again.

## Notification behavior
- Wake alarm at the configured time (default 10:00). Actions: YES, I'M UP and NOT YET (asks again in 15 min).
- After confirmation, a check-in alarm fires every interval (default 60 min).
- Android shows at most 3 notification buttons, so the check-in notification has STILL WORKING, TAKING A BREAK and ANSWER IN APP. The in-app check-in has all five responses. COMPLETED, DISTRACTED and STUCK need your input (minutes, confirmation), so they live in the app.
- Check-ins are held during a break or while paused, and rescheduled when you end the break or resume.
- Unanswered check-ins stay recorded as unanswered. They are never turned into distraction.
- Alarms are rescheduled after reboot, app update, and time or timezone change.

## Task timer
START on a task begins its timer and the button becomes PAUSE. Pausing asks why (taking a break, got distracted, stuck, something else):
- Taking a break starts a break (check-ins held), same as the check-in screen.
- Got distracted counts the time until you resume as distraction.
- Worked time is booked as study automatically, without double counting check-in minutes.
Only one task runs at a time; starting another parks the first. When a running task reaches its planned time (for example 2h) the app asks automatically: a notification (+15 MIN or ANSWER IN APP) and, if the app is open, the "Time's up" screen: done, need more time (+15/+30/+60) or pause with a reason.

## Noc (AI coach)
Add a Gemini API key in the Noc tab or in Settings (free key: aistudio.google.com/apikey). Model defaults to `gemini-flash-latest` and can be changed in Settings. The key is stored on the device in its own DataStore file, excluded from Android backups, and sent only to Google in a request header. Chat messages and your task list are sent to Google when you talk to Noc. The chat is saved on the phone (last 500 lines, Room table `noc_messages`) and comes back after restarting the app; the trash icon clears it. Failed requests are retried automatically (3 tries) and a TRY AGAIN button appears if they still fail and nothing had been changed yet. Tone (Brutal, Direct, Calm) in Settings changes how Noc talks.

## Backup and restore
Settings > Backup and restore: BACK UP NOW saves a JSON file (tasks, subtasks, days, logs, check-ins, reports, Noc chat) anywhere you pick, e.g. Drive. RESTORE FROM BACKUP replaces everything in the app with that file. App settings and the Gemini key are not included.

## Logging past study
Entries for past days go into a small closed day record of their own, so that day's real wake/break numbers are not touched. They count in the Insights charts, subject totals and CSV export (column `subject`). An entry for today, while you are awake, is added to today's numbers. Noc can do the same ("I studied 3 hours of physics yesterday"). Max 16 hours per entry, no future dates.

## Time accounting
Study, distraction and break minutes come from your check-in entries plus the task timer. Break start and end are timestamps. Unclassified = elapsed since wake minus the rest. Subject progress on Insights is labeled ESTIMATE.

## Known limits (be honest about these)
- Exact alarms need the "Alarms and reminders" permission on Android 12+. Without it, Android may delay reminders.
- Aggressive battery savers (some Xiaomi, Oppo, Vivo, Samsung modes) can still delay or kill alarms. Delivery cannot be guaranteed.
- Charts are simple Compose bars, not a chart library.
- Separate break table was merged into activity logs (kind = BREAK). Daily plan is derived from task plan dates.
- Export is CSV text via the share sheet.
- Database schema v3 (v2: task timer columns; v3: `activity_logs.subject` and the `noc_messages` table; migrations 1 to 2 and 2 to 3 included). Room schemas are exported to app/schemas.

## Layout
app/src/main/java/com/noctra/app
- data: Room entities, DAOs, repository, settings (DataStore), stats calculator
- notify: notification builder, AlarmManager scheduler, receivers, permission helpers
- viewmodel: Home, Planner, TaskEdit, CheckIn, Wake, Insights, Report, Settings
- ui: theme, components, screens, navigation

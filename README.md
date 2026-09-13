# MyHealth

Native Android app (Kotlin, Jetpack Compose, Room) for a Google Pixel 7A: a personal hub for sport performance, nutrition and health. Everything runs on the phone; there is no server.

## What it does

- **Garmin data via Health Connect** — Garmin Connect writes workouts, heart rate, steps, calories, sleep, weight and more into Health Connect; the app syncs incrementally (changes tokens), backfills history, and deduplicates against FIT/CSV imports.
- **Activities** — list and detail with HR zones, laps, TRIMP; **training load** (ATL/CTL/ACWR, monotony, strain), **recovery score** (sleep, resting HR, load, HRV), **running PRs** with best-split detection, Riegel predictions and VDOT.
- **Calendar** — soccer matches, trainings, races and other events (with weekly recurrence), linked to the matching Garmin activity automatically or by hand.
- **Nutrition** — ingredients entered by hand, scanned from a nutrition label (camera OCR, German + English), from a photo, or looked up by barcode (Open Food Facts); meal templates; a diary with per-slot logging and water; **adaptive daily targets** (calories and protein/carb/fat split) from weight, goal weight, activity level, measured expenditure and the day's context (rest, training, pre-match, match day).
- **Training plans** — goals (race time, weight, consistency), a week board, and rule-based **session suggestions** with rationale, driven by goals, load, recovery and the calendar (no hard sessions before a match, taper before races, rest days, sport caps).
- **Import / backup** — Garmin `.fit`, activity `.csv` and full export `.zip`; JSON backup export/import.
- **Cycle tracker** (female users, or anyone who enables it) — log period starts and ends, get averages-based forecasts of the next periods, ovulation and fertile windows on the calendar and Today, and cycle-aware training suggestions (moderate intensity on the first period days, strength/intervals favoured in the follicular phase, warm-up note around ovulation, recovery focus in the late luteal phase).
- Green Material 3 theme (wallpaper colours optional).

## Build and install

Requirements: JDK 21, Android SDK with platform 36 (see `docs/TOOLCHAIN.md` for the exact, verified toolchain).

```bash
export JAVA_HOME=/path/to/jdk21 ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug            # debug APK
./gradlew :app:assembleRelease          # minified release APK (debug-signed, for sideloading)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On first launch complete onboarding, then More → Integrations → Grant permissions (Health Connect) → Sync now. Make sure Garmin Connect is allowed to write to Health Connect on the phone.

## Verify

```bash
bash tools/verify.sh                     # assembleDebug + unit tests + lint (+ release)
./gradlew :app:connectedDebugAndroidTest # instrumented tests on a device/emulator
```

Emulator harness for runtime testing: `tools/emu.sh`, `tools/ui.sh`, and the Health Connect seeder app in `tools/hc-seeder/` (`seed.sh up && seed.sh seed 45`). Results are logged in `docs/VERIFICATION.md` with screenshots in `docs/screenshots/`.

## Layout

```
app/src/main/java/com/myhealth/
  domain/   pure Kotlin: models, engines (nutrition targets, TRIMP/ACWR, recovery, PRs, suggestions, label parser)
  data/     Room DB, repositories, Health Connect, FIT/CSV, OCR, Open Food Facts, backup
  sync/     WorkManager workers (HC sync, target/load recompute, import)
  ui/       Compose screens + ViewModels (no imports from data/)
  di/       AppGraph (manual DI)
docs/       BRIEF.md (requirements), PLAN.md (architecture + task plan), TOOLCHAIN.md, STATUS.md, VERIFICATION.md
```

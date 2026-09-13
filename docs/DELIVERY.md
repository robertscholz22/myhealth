# Delivery notes (2026-09-12)

## What you get
- Native Android app **MyHealth** (Kotlin / Jetpack Compose / Room), fully on-device. Repo: https://github.com/robertscholz22/myhealth
- Release APK (minified, 13.9 MB) from `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk` (debug-signed for sideloading; Play Store is out of scope).
- 691 unit tests, instrumented Compose/Room tests, and a runtime verification log (`docs/VERIFICATION.md`, screenshots in `docs/screenshots/`) from a Pixel-7a-profile emulator with 45 days of synthetic Garmin-style Health Connect data.

## Releases
- `v1.0.0` (2026-09-13, versionCode 100): first stable release, verified on the Pixel 7a; installed on the phone. Install only from tags from now on (see README → Releases).
- `v1.0.2` (2026-09-13, versionCode 102): Garmin CSV import reads German-header/English-number exports correctly (distances were ×100), maps all German sport names, reads avg/max HR; new "Undo import" in Import history; DB v4.
- `v1.0.1` (2026-09-13, versionCode 101): accepting a suggested week replaces unlocked planned sessions in its horizon; locale-aware number formatting and comma/dot decimal input; training-load rows start at the first activity; stale sync-error banner cleared on success.
- `v1.0.3` (2026-09-13, versionCode 103): BUG-12 hotfix — "Undo import" falls back to unstamped source records within ±15 minutes of the import when it wrote none (pre-DB-v4 imports); new Settings → Advanced → "Remove orphaned import data" removes remaining unstamped CSV/FIT source records from before DB v4 (Health Connect activities are kept).

## Install on the Pixel 7A
1. Enable developer options + USB debugging, connect the phone, `adb devices`.
2. `adb install -r app/build/outputs/apk/release/app-release.apk` (or the debug APK for logcat-friendly runs).
3. In **Garmin Connect** → Settings → Connected apps / Health Connect: allow writing all data types.
4. Open MyHealth → complete onboarding → More → Integrations → **Grant permissions** (allow all, then allow "past data" + "background") → **Start backfill** (e.g. 365 days) → **Sync now**.
5. Periodic sync runs every 6 h (Settings); targets and load recompute nightly and after every sync.

## Verified on the emulator (see VERIFICATION.md for every step)
Onboarding · Health Connect grant sheets · incremental sync + backfill + dedupe against re-seeded data · activities/detail/HR zones · load, recovery, PRs (incl. FIT best splits) · calendar events with recurrence, linking to activities (90 % auto-proposal) · adaptive nutrition targets (pre-match day: carbs ↑, fat ↓, protein ↑; Katch-McArdle when body fat exists) · ingredients by hand, by barcode (Open Food Facts), by label OCR from a photo (bundled-model test build) · meal templates, diary, water, item edit/delete · training plan, suggestions with rationale, accept, mark done, manual sessions · FIT + CSV import with checksum duplicate detection · JSON backup export/import · release build under R8.

## Added 2026-09-13: menstrual cycle tracker
More → Cycle (shown for female profiles or when enabled in Settings): log period start / period ended, forecast of the next six cycles from your average cycle and period length (28/5 until you have logged a few), ovulation and fertile window on the calendar and the Today card. The training suggester reads the phase per day and adapts intensity and rationale; nutrition shows a luteal-phase note. Verified on the emulator with a female profile (VERIFICATION.md session 6); 734 unit + 12 instrumented tests.

## Known limitations / follow-ups
- OCR from the live camera could not be exercised on the emulator (no ML Kit model download there); the same code path was verified with a photo and a bundled model. First use on the phone downloads the text/barcode models once.
- Garmin-only metrics (Body Battery, stress, training readiness) are not in Health Connect; the optional direct Garmin client (PLAN P9) was deliberately not built (brittle reverse-engineered login; see VERIFICATION.md).
- Polish ideas still open are listed as `NOTE-*` items in VERIFICATION.md (e.g. water total formatting, plurals).
- Not yet run on the physical Pixel 7A (no device was attached during the build); the smoke list in PLAN §6.5 is the checklist for that first run.

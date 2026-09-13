# Delivery notes (2026-09-12)

## What you get
- Native Android app **MyHealth** (Kotlin / Jetpack Compose / Room), fully on-device. Repo: https://github.com/robertscholz22/myhealth
- Release APK (minified, 13.9 MB) from `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk` (debug-signed for sideloading; Play Store is out of scope).
- 691 unit tests, instrumented Compose/Room tests, and a runtime verification log (`docs/VERIFICATION.md`, screenshots in `docs/screenshots/`) from a Pixel-7a-profile emulator with 45 days of synthetic Garmin-style Health Connect data.

## Releases
Renumbered on 2026-09-13 (owner decision: the first release should have been 0.1.0, not 1.0.0). Tags and GitHub releases now carry the new numbers; the APKs published before the renumbering keep their original internal `versionName` (shown in brackets). `versionCode` keeps counting upwards independently of the version name because Android refuses downgrades.
- `v0.1.0` (formerly 1.0.0; 2026-09-13, versionCode 100): first stable release, verified on the Pixel 7a; installed on the phone. Install only from tags from now on (see README → Releases).
- `v0.1.2` (formerly 1.0.2; 2026-09-13, versionCode 102): Garmin CSV import reads German-header/English-number exports correctly (distances were ×100), maps all German sport names, reads avg/max HR; new "Undo import" in Import history; DB v4.
- `v0.1.1` (formerly 1.0.1; 2026-09-13, versionCode 101): accepting a suggested week replaces unlocked planned sessions in its horizon; locale-aware number formatting and comma/dot decimal input; training-load rows start at the first activity; stale sync-error banner cleared on success.
- `v0.1.3` (formerly 1.0.3; 2026-09-13, versionCode 103): BUG-12 hotfix — "Undo import" falls back to unstamped source records within ±15 minutes of the import when it wrote none (pre-DB-v4 imports); new Settings → Advanced → "Remove orphaned import data" removes remaining unstamped CSV/FIT source records from before DB v4 (Health Connect activities are kept).

- `v0.2.0` (formerly 1.1.0; 2026-09-13, versionCode 110): **cycling workouts** (PLAN P12) — power fields and streams from Health Connect (`READ_POWER`, optional; pedalling cadence), FIT and the Garmin CSV; DB v5 (`ride_best`, profile FTP override + indoor trainer); FTP estimate (20-minute power × 0.95, 90 days; session NP fallback) with manual override; ride bests (5/20/60-min power, 10/20/40/100 km time); training load from power (TSS × 1.5) when a ride has no HR; Bike & power screen; bike goals (FTP, weekly hours, event) with progress; ride sessions in the suggestion engine (endurance ride, bike intervals, trainer session, recovery spin; second phase table for cycling goals; indoor season Nov–Mar with a trainer; ride sessions / week cap); Integrations re-reads the last 90 days of sessions when a new per-session permission is granted (BUG-13). Includes the 1.0.3 hotfix. 8xx unit tests, instrumented tests green, emulator session 10 in VERIFICATION.md.

- `v0.2.1` (formerly 1.1.1; 2026-09-13, versionCode 111): BUG-14 — a load recompute requested while a historical one was running cancelled it (`REPLACE` → `APPEND_OR_REPLACE`), leaving imported history without TRIMP; new Settings → Advanced → "Recompute training load". Also `tools/connected.sh` (instrumented tests only on a named emulator, after INCIDENT-1).

- `v0.3.0` (2026-09-13, versionCode 120): **active recovery on rest days** — every rest day except one true rest day per week gets an easy 30-minute recovery run or recovery spin (run when the run cap allows it, spin when the ride cap is above zero or an indoor trainer is available; alternating; spin preferred on early-menstrual and late-luteal days; the eve of a match/race and a strained today stay mobility-only). Fillers keep the day a rest day, cost no budget and no cap, and get mobility on top when enabled. Releases renumbered (1.x → 0.x).

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

# Runtime verification log (emulator myhealth_api35, Android 15)

Build under test is recorded per session. Findings are triaged as BUG (must fix), POLISH (nice to fix), NOTE (informational).

## Session 1 — 2026-09-12 (build after P6.1–P6.4, tests 554)

| Step | Result | Evidence |
|---|---|---|
| Install + first launch → Onboarding step 1 | PASS | screenshots/01_first_launch.png |
| Onboarding step 1 validation blocks Next without birth date | PASS | 02_onboarding_step2.png (stayed on step 1) |
| Date picker text-input mode, birth date accepted | PASS | 02_datepicker_input.png |
| Onboarding steps 2–3, Finish → Today | PASS | 03_step2_filled.png, 04_step3_filled.png, 05_today_first.png |
| Today: nutrition target computed (2320 kcal / 120 P / 265 C / 85 F for 76 kg, 180 cm, light active, maintain) | PASS (matches Mifflin: BMR 1720 × 1.35) | 05_today_first.png |
| Bottom tabs Calendar / Nutrition / Training / More | PASS (Training = placeholder until P6.6) | 06_tab_*.png |
| Nutrition diary: target header, water target 2.7 l, empty state | PASS | 06_tab_Nutrition.png |
| More → Integrations / Settings / Body / Goals / Ingredients / Meal Templates / Activities / Load & Recovery / Running PRs | PASS, no crashes | 07_more_*.png |
| Integrations: Health Connect "Available", all permissions granted | PASS (granted by `install -g`; real grant sheet retested later) | 07_more_Integrations.png |
| Green theme applied (light) | PASS | all screenshots |

Findings:
- POLISH-1: Ingredient editor shows validation warnings ("Fewer than 3 nutrient fields…", "No energy value…") on a completely empty new form. Show warnings only once any nutrient field has been touched.
- NOTE-1: uiautomator does not expose Compose text-field placeholders (e.g. "Birth date"); automation taps by coordinates there. Not a user-facing issue.
- BUG-1 (backend, Health Connect): with zero records in Health Connect, `aggregateGroupByPeriod(TotalCaloriesBurnedRecord.ENERGY_TOTAL)` still returns ~1564.5 kcal per day (the platform synthesises a basal-metabolic baseline). `HcAggregates`/`HcSyncService` stored 30 rows in `daily_health_summary` with `totalEnergyKcal = 1564.5`, source HEALTH_CONNECT, everything else null. `NutritionTargetEngine` then treated yesterday as "measured (Health Connect total)" → TDEE clamped to BMR (1720) → target 2060 with CLAMPED_TO_FLOOR instead of the 2320 estimate. On a phone this would under-feed every day Garmin did not write calories. **Fix:** in the daily aggregate reader, use `AggregationResult.dataOrigins`: when it is empty (or contains only the platform origin) treat every metric of that bucket as absent (null) and do not create a summary row for a day with no real data; keep the ≥ 0.9·BMR plausibility check in the engine as a second guard. Add a fake-reader test: bucket with a value but no data origins → no summary row. Evidence: DB dump session 1, snapshot day 20707.
- BUG-2 (UI/data, Ingredients list): a newly created ingredient does not appear in the Ingredients list (empty query) — only via search. Cause: `IngredientDao.observeRecent` filters `lastUsedAtMillis IS NOT NULL`, and `RoomIngredientRepository.search("")` delegates to it. **Fix:** empty query → all active ingredients ordered by `lastUsedAtMillis DESC NULLS LAST` (SQLite: `ORDER BY lastUsedAtMillis IS NULL, lastUsedAtMillis DESC, name ASC`), keep `observeRecent` (used-only) for the Add-food "Recents" tab. Add a fake-DAO test: never-used ingredient is listed for an empty query. Evidence: 12_after_save.png ("No ingredients yet") vs DB row id 1; 12_search_haf.png finds it.
- NOTE-2: `install -g` on the emulator pre-grants all health permissions, so the in-app "Grant permissions" button never showed; the real grant sheet is tested in a later session with a plain install.
| Ingredient editor: create by hand, decimals (13.5 / 58.7 / 0.02) persisted, edit + re-save | PASS | 09_ingredient_filled.png, DB row id 1 |
- POLISH-2 (UI, insets): `MealTemplateEditScreen` draws its top bar under the status bar (title "New template" overlaps the clock) — missing `Scaffold` content padding / `statusBarsPadding()`. The ingredient editor is correct. Audit every screen with its own top bar (templates list/edit, add-food, scan, OCR review, goal edit, planned-session edit, event edit, day detail, import, backup) and use the same Scaffold pattern as IngredientEditScreen. Evidence: 13_template_filled.png.
| Meal template: create with ingredient picker, quantity 80 g → totals 298 kcal / 11 P / 47 C / 6 F (matches MealMath), save | PASS | 13_template_filled.png, 14_templates_list.png |
- POLISH-3 (wording): the Water card's empty text "Nothing logged yet today." reads as if no meals were logged; change to "No water logged yet today."
| Template "Log now" → diary: meal under Lunch, item 80 g, totals 298/2320 kcal + macro bars updated | PASS | 16_diary_with_meal.png, 16_diary_meals.png |
| Add food: Recents tab lists used ingredient, quantity editor live preview (50 g → 187 kcal / 6.8 P), Add → Breakfast row in diary | PASS | 17_add_food.png, 17_quantity_editor.png, 18_diary_after_add.png |
| Water: +250 / +500 → 0.8 / 2.7 l (rounded from 0.75), entry list with time; header totals 485 kcal | PASS | 19_water.png |
- NOTE-3: water total is shown with one decimal (0.75 l → "0.8 l"); consider showing millilitres below 1 l or two decimals.
- BUG-4 (UI, all dropdowns): `ui/common/DropdownField.kt` puts no `Modifier.menuAnchor(...)` on the `OutlinedTextField` inside `ExposedDropdownMenuBox` and uses `DropdownMenu` instead of `ExposedDropdownMenu`, so tapping only focuses the field and the menu never opens. Affects every enum picker (onboarding sex/activity level, settings, ingredient basis, event type, meal slot, goal type, planned-session sport/type, quantity unit picker if it uses the same component). **Fix:** `modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()` (M3 1.4 API) and `ExposedDropdownMenu(expanded, onDismissRequest)`; then re-test one dropdown on the emulator. Evidence: 21_type_dropdown.png / 21_type_dropdown2.png (focused, no menu).
| Sync now with 45 seeded days: 38 activities (19 runs, 6 matches "Spiel"→SOCCER_MATCH, 6 trainings, 7 strength), HR streams on all, 45 sleep sessions with stages, 23 body rows, resting HR/HRV/SpO2 daily, 5k PR 20:29 + 10k PR, daily_load 29 rows, recovery 73 GOOD on Today | PASS (with BUG-5/6 below) | 24_today_after_sync.png, DB dump |
- BUG-5 (backend, load): after the initial sync only activities within the last 28 days got a TRIMP (24 of 38; `loadMethod` null on the 14 older ones) and `daily_load` starts 28 days back, so CTL warm-up ignores older history. `HealthSyncWorker`/`HcSyncService` trigger `requestLoadRecompute(today)` instead of the earliest ingested day. **Fix:** compute `minAffectedDay` from the ingested/changed records (and from backfill windows) and pass it; `LoadRecomputeService.recompute(fromDay)` must then TRIMP every activity ≥ fromDay − 28 (already does) — verify with a fake test where an activity 40 days old gets a TRIMP after sync.
- BUG-6 → NOTE-4 (resolved, emulator artifact): daily `steps/activeEnergy/floors/distance` were null after the first sync because the seeder had been granted via `adb pm grant`, which does not register it in Health Connect's data-source priority list, so aggregates excluded its data (raw reads still worked). After granting the seeder through the real Health Connect sheet and re-seeding, a second "Sync now" filled 29/30 days of steps, 28 active-kcal, 28 floors, 12 distance. On the phone Garmin Connect is granted through the sheet, so this does not occur; the raw-read fallback from fix batch 1 stays as a robustness measure.
| Clear + re-seed in Health Connect (new record ids) then Sync now: deletions + re-insertions handled, still 38 activities / 38 source records, no duplicates | PASS | DB dump |
| Activity detail (match "Spiel"): duration, calories, avg/max HR, TRIMP 226.2 (HR samples), time-in-zone table, linked-event card, title/notes edit | PASS | 27_activity_detail.png, 27_activity_detail2.png |
| Load & Recovery: ATL 65 / CTL 86 / ACWR 0.76 (Detraining) / TSB, daily TRIMP list, monotony/strain, flags with explanations, 28/90/365 d ranges | PASS | 28_load.png |
| Running PRs: 5 km 20:29, 10 km 56:30, Riegel predictions for all canonical distances, VDOT | PASS | 29_prs.png |
- POLISH-4 (running): Riegel/VDOT source effort = "largest distance ≤ 180 d" per §3.4, so a slow 10 km (56:30, VDOT 34.7) outranks a fast 5 km (20:29, VDOT ≈ 48). Prefer the effort with the highest VDOT among efforts ≥ 3 km.
| Calendar month with synced data; tap day 6 → Day detail shows the match activity (TRIMP 226) and sleep 7h14m with stages | PASS | 30_calendar_with_data.png, 31_day_detail_sep6.png |
| Body & Health: latest weight 76.0 kg (manual), last-90-days list with HC weights 77.4–78.0 kg and body-fat rows | PASS | 32_body.png |
- POLISH-5 (Body list): body-fat-only measurements render as "—, 17.4% fat"; show "Body fat 17.4 %" without the dash when weight is null.
| Calendar month: activity dots on training days, kcal bar + meal dot on today, event dot on tomorrow, today highlighted | PASS | 30_calendar_with_data.png |
| Integrations: per-channel last-sync times, "Start backfill" (365 d) completes → "Backfilled back to 2025-09-12", no errors | PASS | 33_integrations_bottom.png, 33_backfill_running.png |
- BUG-1 evidence 2: after the 365-day backfill `daily_health_summary` holds 366 rows although seeded data covers 45 days — every empty day got a row carrying only the platform's synthetic total-calorie baseline. Fix (a) in batch 1 (empty `dataOrigins` → no row) must also purge/avoid these rows; add a one-off cleanup on next sync: delete summaries whose only non-null value is `totalEnergyKcal` and whose day has no activity/steps/vitals.

## Fix batch 1 — 2026-09-12 (build after fixes, tests 579)

| Item | Status | Change | Evidence |
|---|---|---|---|
| BUG-4 (dropdowns never open) | FIXED | `ui/common/DropdownField.kt`: `Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()` on the `OutlinedTextField` + `ExposedDropdownMenu` instead of `DropdownMenu`. The only other `ExposedDropdownMenuBox` users were `OnboardingScreen`'s sex/NEAT pickers; both now delegate to `DropdownField`, so there is one anchor implementation left in the tree. | `screenshots/fix1_dropdown.png` (Calendar → New event → Type menu open, "Soccer match" selected → header shows it); meal-template "Default slot" menu lists all 9 slots |
| BUG-2 (new ingredient invisible) | FIXED | New `IngredientDao.observeAllActive(limit)` (`ORDER BY lastUsedAtMillis IS NULL, lastUsedAtMillis DESC, name ASC`); `RoomIngredientRepository.search("")` uses it, so the Ingredients list, the Add-food **Search** tab and the template-editor picker all list every active ingredient. `observeRecent` is untouched and still backs Add-food **Recents**. | `RoomIngredientRepositoryTest.search_with_a_blank_query_lists_a_never_used_ingredient`; `screenshots/fix1_ingredients.png` ("Haferflocken" with an empty query) |
| BUG-1 (synthetic basal baseline) | FIXED | `HcAggregates.totalsOf` returns empty totals when `AggregationResult.dataOrigins` is empty; `readDailyTotals` drops days whose five totals are all null (`HcDailyTotals.isAbsent()`), so no `daily_health_summary` row is written for them. | `HcAggregatesTest.bucket_with_a_value_but_no_data_origins_is_treated_as_absent`, `…bucket_with_a_data_origin_keeps_every_metric` |
| BUG-1 evidence 2 (366 rows after backfill) | FIXED | `HealthDao.deleteEmptySummaries()` deletes rows whose only non-null metric is `totalEnergyKcal`; exposed as `HealthRepository.deleteEmptySummaries()` and run once at the end of every `HcSyncService.syncIncremental()` (reported as `SyncSummary.emptyDaysRemoved`). | `HcSyncServiceTest.sync_deletes_daily_rows_that_only_carry_the_synthetic_energy_baseline` |
| BUG-6 → NOTE-4 (aggregates empty for an adb-granted source) | FIXED (robustness) | New `data/healthconnect/HcRawTotals.kt`: per metric, when any day's aggregate is null the raw records (`StepsRecord`, `TotalCaloriesBurnedRecord`, `ActiveCaloriesBurnedRecord`, `DistanceRecord`, `FloorsClimbedRecord`) are read once over the range and summed per local day. Dedup: one origin per day per metric (the one with the most records), and inside it whole-day records (span ≥ 20 h) win over the partial ones, so a source writing both session and day totals is not double counted. A metric the aggregates already cover is never read. | `HcAggregatesTest.raw_fallback_sums_two_step_records_of_the_same_day`, `…keeps_local_days_apart`, `a_whole_day_record_wins_over_the_partial_records_of_the_same_origin`, `only_the_origin_with_the_most_records_of_a_day_is_summed` |
| BUG-5 (old activities without TRIMP) | FIXED | `SyncSummary.minAffectedDay` / `BackfillResult.minAffectedDay` carry the earliest ingested or changed exercise day (first run, incremental changes and every backfill window); `HealthSyncWorker` passes `loadRecomputeDay(outcome, backfillFromDay, today)` to `requestLoadRecompute` instead of always `today`. `LoadRecomputeService.recompute(fromDay)` already widens by 28 days. | `HcSyncServiceTest.first_run_reports_the_oldest_ingested_activity_day_as_min_affected` (activity 40 days old → `today − 40`), `…sync_without_any_activity_reports_no_min_affected_day`; `HealthSyncWorkerTest.load_recompute_starts_at_the_oldest_ingested_activity_day`, `…falls_back_to_today_when_no_activity_was_touched`, `backfill_load_recompute_never_starts_later_than_its_own_start_day` |
| POLISH-1 (warnings on an empty form) | FIXED | `IngredientDraft.hasAnyNutrient()`; `IngredientEditViewModel.warningsFor` returns no warnings until at least one nutrient field carries a value. | `screenshots/fix1_ingredient_no_warnings.png` (new ingredient editor, no warning lines) |
| POLISH-2 (top bars and status-bar insets) | FIXED | Audit result: all 17 screens with their own top bar (templates list/edit, add-food, scan, OCR review, goal edit, planned-session edit, suggestion review, training, event edit, day detail, calendar, activity detail, ingredients list/edit, meal templates, goals, nutrition) already use the `IngredientEditScreen` pattern — `Scaffold(topBar = { TopAppBar(…) }) { innerPadding -> Body(Modifier.fillMaxSize().padding(innerPadding)) }` — and none ignores `innerPadding`. Body, Running PRs, Load, Settings, Integrations, Activities, Today, More have no top bar at all. The real defect was in the shell: `MyHealthNavHost`'s root `Scaffold` padded the `NavHost` with the system-bar insets but never consumed them, so every nested `TopAppBar` applied the status-bar inset a **second** time. Fixed with `Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)`. | `screenshots/fix1_template_insets.png` (title clear of the clock, no double gap), `fix1_template_keyboard.png`, `fix1_dropdown.png`, `fix1_ingredients.png` |
| POLISH-3 (water empty text) | FIXED | `WaterCard`: "No water logged yet today." | code |
| NOTE-3 (water total below 1 l) | FIXED | `waterAmountLabel(ml)` — millilitres under a litre, one decimal from a litre up; used by `waterLabel` and `waterRemainingLabel`. | `screenshots/fix1_water.png` ("750 ml / 2.7 l", "2.0 l left") |

Notes:
- `bash tools/verify.sh` → VERIFY OK, APK 84.6 MB, **579 unit tests**, 0 failures, lint clean.
- NOTE-5 (new, cosmetic): the original POLISH-2 evidence `13_template_filled.png` showed the top bar *overlapping* the clock because the window had panned for the soft keyboard (focused Quantity field far down the page), not because of the inset bug. With the keyboard on a field near the top the bar now stays put (`fix1_template_keyboard.png`). No `windowSoftInputMode` is declared, so Android still picks panning for deeply scrolled fields; switching the activity to `adjustResize` + `imePadding` would need its own pass over every screen and was left out of this batch.

## Session 2 — 2026-09-12 (build after fix batch 1, tests 579)

| Step | Result | Evidence |
|---|---|---|
| Training tab: week board with fixed sessions (synced activities), weekly load bar (Planned/Target/Actual), phase badge "Base", "Generate suggestions" | PASS | 40_training_empty.png |
| Suggestion review: Base phase, target 696 AU, 7 sessions (709 AU) each with rationale lines (phase, budget, recovery 67/100, sport cap), select all / accept / regenerate | PASS | 41_suggestion_review.png |
| Accept selected → Training shows "My plan", Planned 213 AU vs Target 696 vs Actual 505; Day detail 13 Sep lists planned "Strength full 54 min" | PASS | 42_training_after_accept.png |
- POLISH-6 (navigation): re-selecting the "Today" bottom tab restores the tab's saved stack (e.g. Load & Recovery opened from Today's card) instead of showing the dashboard. Make the Today tab pop to `TodayRoute` (no `restoreState` for the home tab, or pop-to-root on reselect) — the other tabs may keep restore semantics.
| Dropdowns after fix: event Type opens (7 options), "Soccer match" selected and saved; Day detail shows "90 min · Soccer Match" | PASS | 45_type_menu.png |
| Adaptive targets: match tomorrow → today's diary shows "Pre match", target 3180 kcal, carbs 500 g (6.5 g/kg), fat 71 g (20 %), protein 135 g (1.8 g/kg); BMR via Katch-McArdle (HC body fat); "Why this target?" explanation lists BMR/TDEE/day type/goal | PASS | 46_diary_prematch.png |
| New event on a past match day (date picker text mode, type Soccer match) → Day detail → "Link activity" sheet proposes the synced "Spiel" activity at 90 % | PASS | 48_link_sheet.png |
| Link accepted: event row shows "Linked to activity", snackbar "Activity linked.", DB `linkedActivityId = 11`, `linkMethod = AUTO_ACCEPTED` | PASS | 49_day6_linked.png |
- POLISH-7 (Day detail): linked-event label reads "Linked to activity #11"; show the activity's title/time (e.g. "Linked: Spiel, 15:00 · 1h 35m") instead of the id.
| Settings "Use wallpaper colours": on → dynamic palette (blue-grey on the emulator), off → green theme again; setting persists | PASS | 50_settings_dynamic_on.png, 51_today_dynamic.png, 52_today_green_again.png |
- BUG-8 (Goals): "Create goal" does nothing for a Race-time goal unless the Distance dropdown is re-selected: `GoalDraft.targetDistanceMeters` defaults to null while the picker displays "5 km", validation adds a DISTANCE error, and `GoalEditScreen` renders errors only for TITLE/TIME/WEIGHT/VALUE — so the failure is silent. **Fix:** initialise the draft with the displayed default (5000 m) whenever type = RACE_TIME (and on type change), render `errors[GoalField.DISTANCE]` under the picker, and show `saveError` prominently. Evidence: 53_goal_filled.png, DB empty until distance re-selected.
| Goal created after selecting distance: "5 km in 19:59 · Current best 20:29 · predicted 27:05 — behind", actions Make primary / Achieved / Abandon | PASS (see POLISH-4: the prediction comes from the slower 10 km effort and contradicts the 20:29 best) | 54_goals_list.png |
| Barcode lookup: enter EAN 3017620422003 → "Look up barcode" → Open Food Facts fills name "Nutella", 539 kcal, 6.3 P / 57.5 C / 30.9 F / 56.3 sugar per 100 g, hint "Filled in from Open Food Facts — check the values." | PASS | 55_barcode_lookup.png |
| Scan screen: camera preview, modes Nutrition label / Barcode, "Capture label" | PASS (opens; capture tested below) | 56_scan_screen.png |
| Capture label with the ML Kit model not yet downloaded → clear message "Text recognition model is still downloading, try again in a moment." + Retry, no crash | PASS | 57_after_capture.png |
- NOTE-6: on the emulator (google_apis image) the unbundled ML Kit text model download via Play Services is slow/uncertain; on the Pixel it downloads once on first use. OCR parsing itself is covered by 34 unit tests over label fixtures; a real-camera OCR run is re-attempted below and on the phone.
- NOTE-6 update: three retries over ~2 min still reported the model downloading → the google_apis emulator image cannot fetch the unbundled ML Kit text model. Plan: add "Scan from photo" (pick an image file → same recognition pipeline) — useful for users too — and verify the OCR path on the emulator with a one-off bundled-model test build against docs/testassets/label_haferflocken_de.png; the phone uses the unbundled model.
| Real Health Connect grant flow for MyHealth (permissions revoked first): Integrations shows ✕ per permission → "Grant permissions" → system sheet "Allow MyHealth to access Health Connect?" → Allow all → Allow → 17 record permissions granted | PASS | 60_integrations_revoked.png, 61_hc_sheet_myhealth.png, 62_integrations_granted.png |
| Health Connect second sheet "Allow additional access" (past data + background) is shown by the same request — the app asks for the history and background permissions correctly | PASS | 63_hc_additional.png |
| After Allow on both sheets: 19/19 health permissions granted (17 record types + history + background) | PASS | 64_integrations_all_granted.png |

## Session 3 — 2026-09-12 (build after P7, tests 620)

| Step | Result | Evidence |
|---|---|---|
| Import screen: pick `run_5k.fit` via the system document picker → "Import finished · 1 parsed · 1 saved · 0 duplicates · 0 errors", history row; DB: RUN_OUTDOOR 5000 m / 1500 s with streams, 2 laps, TRIMP from HR samples, best splits 1 km / 1 mile / 3 km / 5 km (BEST_SPLIT) | PASS | 65_import_screen.png |
| Today's plan card: accepted "Long run · 77 min · 116 AU · Planned" with Done / Plan actions | PASS | 69_after_start.png |
| Today plan card "Done" → session shows "Completed" | PASS | — |
| Running PRs after FIT import: best splits appear (1 km 5:00, 1 mile 8:03, 3 km 15:00 from the 5 km FIT stream) alongside the HC-derived 5 km 20:29 / 10 km 56:30 | PASS | 67_prs_after_fit.png |
| CSV import via the document picker (German-locale Garmin export fixture): "3 parsed · 3 saved · 0 duplicates", rows RUN 10.52 km / CYCLING 30.25 km / STRENGTH, history shows both imports | PASS | 70_csv_import.png |
- NOTE-7: sending a documents-provider URI to the app from the adb shell fails with a SecurityException in the shell (uid 2000 has no grant) — a shell limitation, not an app issue; the share-sheet path is verified on the phone.
| Re-import of the same CSV → "Already imported" (checksum short-circuit), no new rows | PASS | 71_reimport.png |
| Planned-session editor opens from Training "+ Session" (sport, type, intensity, targets, date) | PASS | 72_planned_session_edit.png |
| Manual planned session (Run outdoor / Easy run / Low / 45 min) saved from the editor; weekly Planned AU updates (213 → 151 after the long run moved to Completed and +54 AU easy run) | PASS | 73_training_after_manual.png |
- POLISH-8 (suggestions): the generated week placed "Strength full" on two consecutive days (13/14 Sep) and "Soccer training" on 17/18 Sep; §3.5.3 C11 only spaces STRENGTH_LOWER/HIGH runs/long runs. Add a rule: no identical session type on consecutive days and ≥ 48 h between any two strength sessions. Also: the batch was generated while the 13 Sep event was still an Appointment; after an event's type/date changes, mark the PROPOSED batch stale and show a "Regenerate" hint on Training/Today.
- POLISH-9 (suggestions): `suggestion_batch.status` stays PROPOSED after "Accept selected"; set it to ACCEPTED (or SUPERSEDED) so the review screen and the Today card do not keep offering the same batch.
| Diary item edit: tap quantity "80 g" → dialog (Quantity/Unit) → 100 → Save → row shows "100 g · 373 kcal · 14 g P" (snapshot recomputed, DB kcal 373) | PASS | 76_qty_tap.png, 77_diary_after_edits.png |
| Diary item delete via "Item actions" → Delete: breakfast meal removed, slot shows "Nothing logged.", DB row gone | PASS | 77_diary_after_edits.png |

## Fix batch 2 — 2026-09-12 (build after the fixes + P8.4/P8.5, tests 643)

| Item | Status | Change | Evidence |
|---|---|---|---|
| BUG-8 (race goal could not be created) | FIXED | `GoalDraft.targetDistanceMeters` now defaults to `CanonicalDistances.FIVE_KM` — the value the picker already displayed — and the new `GoalDraft.withType(type)` restores it whenever the type changes back to `RACE_TIME`. `GoalEditScreen` renders `errors[GoalField.DISTANCE]` in the error colour directly under the distance picker, and `saveError`/`loadError` are now `ErrorBanner`s at the top of the form instead of an unstyled line below the fold. | `GoalDraftTest.a_fresh_race_draft_with_a_time_validates_without_touching_the_distance_picker`, `…switching_back_to_a_race_goal_restores_the_default_distance`; emulator: Goals → New goal → title "Sub19 5k", 18/59, **Create goal** → goal saved without ever opening the Distance menu (`screenshots/fix2_goal.png`, `fix2_goal_created.png` shows "5 km in 18:59") |
| POLISH-4 (Riegel/VDOT source effort) | FIXED | `RiegelPredictor.pickSource` now ranks the qualifying efforts (≥ 3 km, ≤ 180 d) by **VDOT** — `maxWithOrNull(compareBy { vdotOf(it) }.thenBy { it.distanceMeters })`, so a tie goes to the longer distance — instead of "largest distance, then fastest time". `RunningPrsViewModel` (predictions + VDOT) and `GoalProgress.predictedSec` (the on-track check) both go through it, so both were fixed by the one change. `pr10` is untouched: `predictSec` still gives 5 k → 10 k = 2501.9 s. | `RiegelPredictorTest.pr13_source_effort_prefers_best_vdot` (20:29 5 km beats 56:30 10 km, and the 10 km prediction drops below 56:00), `…a_stale_effort_is_never_the_source_even_when_it_is_the_best`, `pr10_riegel_5k_to_10k` unchanged; emulator: the 5 km goal now reads "Current best 20:29 · predicted 20:29 — behind" instead of the self-contradicting "predicted 27:05" |
| POLISH-5 (body-fat-only rows) | FIXED | New pure `measurementValueLabel(weightKg, bodyFatPercent)` in `ui/body/BodyUiState.kt`: weight + fat → "78.0 kg, 17.4 % fat", weight only → "78.0 kg", fat only → "Body fat 17.4 %", neither → "—". `BodyScreen.MeasurementRow` delegates to it. | `screenshots/fix2_body.png` — the last-90-days list shows "Body fat 17.4 / 17.5 / 18.0 %" with no leading dash |
| POLISH-7 (linked-event label) | FIXED | New pure `linkedActivityLabel(activityId, activities, zone)` + `shortDuration(seconds)` in `ui/calendar/DayDetailUiState.kt`; `EventsSection`/`eventSubtitle` take the day's `CalendarDay.activities` (already loaded) and resolve the link to title + start time + duration, falling back to `#id` only when the activity is not on that day. | `screenshots/fix2_daydetail.png` — 6 Sep event subtitle reads "Soccer Match · Linked: Spiel, 15:00 · 1h 35m" (was "Linked to activity #11") |
| POLISH-6 (Today tab did not show the dashboard) | FIXED | `navigateToBottomDestination` keeps `popUpTo(TodayRoute) { saveState = true }` and `launchSingleTop`, but uses `restoreState = !isHome` — the Today tab never restores its own saved stack, the other four tabs still do. `MyHealthBottomBar` now also handles a tap on the already-selected tab: `popToBottomRoot(destination)` (`popBackStack(route, inclusive = false)`). ArchitectureTest still green. | emulator: Today → "Recovery" card → Load & Recovery → Calendar → **Today** → dashboard (`screenshots/fix2_today_tab.png`); before the fix the Load screen came back |
| NOTE-6 (Scan from photo) | DONE | `ScanSources` gained `recognizeLabel(uri, cacheDir)` and `scanBarcode(uri)`; `MlKitScanSources` now holds the app `Context` and uses `InputImage.fromFilePath(context, uri)` (EXIF rotation included), with `FrameImages.copyToCache(context, uri, dir)` keeping a copy for the OCR review screen. `ui/` still imports no `com.myhealth.data.*` — the picked `android.net.Uri` crosses the same `di/` seam the `ImageProxy` does. `ScanScreen` offers "From photo" twice (a top-bar `PhotoLibrary` action, always enabled — it needs no permission, so it works even when the camera was denied — and an outlined button next to "Capture label"), in **both** modes. `ScanViewModel.onPhotoPicked(uri)` runs the same pipeline: LABEL → recognise → `NutritionLabelParser` → `DraftStore` → `OcrReviewRoute`; BARCODE → scan → OFF lookup → ingredient editor, with "Enter it manually" on a miss. The model-download message is the existing `ScanMessages.of(MODEL_NOT_READY)` text. | `ScanUiStateTest` (5 cases over the pure `photoStarted/photoCancelled/photoFailed/reviewReady` transitions); emulator: `screenshots/fix2_scan_photo.png` (both actions visible), picker opens, the label PNG is accepted and the pipeline reports **"Text recognition model is still downloading, try again in a moment."** with a Retry (`fix2_scan_model.png`) — i.e. the from-photo path is wired end to end and fails only at ML Kit itself |
| NOTE-6 follow-up | OPEN | Four more attempts over ~2 min, in both LABEL and BARCODE mode, still returned `MODEL_NOT_READY`: the `google_apis` emulator image genuinely cannot fetch either unbundled ML Kit model, exactly as session 2 concluded. OCR parsing stays covered by the 34 label-fixture unit tests; a real recognition run is a **phone-only** check. Cosmetic: in BARCODE mode the shared message still says "Text recognition model", left unchanged so the session-2 evidence string stays stable. | — |
| P8.4 JSON backup (new) | DONE | See `docs/STATUS.md` P8.4. | Emulator: Backup → **Export backup** → picker (Downloads, typed name kept) → SAVE → "1005 rows over 25 tables written", `/sdcard/Download/myhealth-backup-2026-09-12.json` = 1 099 978 B, valid JSON, `schemaVersion 2`, `appVersion 0.1.0`, no settings/credential keys (`fix2_backup_export.png`). Then **Import backup** in **Merge** mode on the same file → "1005 rows … in the file · **0 merged**" (`fix2_backup_import.png`); `run-as com.myhealth sqlite3 databases/myhealth.db` before/after: `activity_session` 42 → 42, `ingredient` 1 → 1, `goal` 2 → 2, `body_measurement` 24 → 24, `meal_log_item` 1 → 1 — no duplicates. |
| P8.5 ingredient FTS (new) | DONE | See `docs/STATUS.md` P8.5. | Installed over the existing v1 database **without clearing app data**: `pragma user_version` 1 → 2, `activity_session` still 42, `sqlite_master` holds `ingredient_fts` + its 4 shadow tables + the 4 `room_fts_content_sync_*` triggers, `select count(*) from ingredient_fts` = 1 and `MATCH 'hafe*'` returns "Haferflocken" (the rebuild worked on pre-existing rows). Logcat shows no migration or SQLite error. In the app: More → Ingredients lists "Haferflocken", and typing "hafe" (≥ 3 chars → the FTS path) still finds it. |

Notes:
- `bash tools/verify.sh` → **VERIFY OK**, APK 86.1 MB, **643 unit tests**, 0 failures, lint clean; `./gradlew :app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL.
- NOTE-7 (new, informational): `sync_state` is part of a backup, so a REPLACE restore also restores the Health Connect `changesToken`. On a different device that token is rejected as expired and `HcSyncService` falls back to one full 30-day re-read plus a fresh token (amendment A6), so this is safe — but it is the one row in a backup that is device-specific.

## Session 4 — 2026-09-12 (bundled-ML-Kit test build of the fix-batch-2 tree, on the emulator)

| Step | Result | Evidence |
|---|---|---|
| Scan → "From photo" → Android photo picker → label_haferflocken_de.png → ML Kit text recognition → parser → "Check the scan": Calories 373, Fat 7.0, saturates 1.2, Carbs 58.7, sugars 1.1, Fibre 10.0, Protein 13.5, "Values are per 100 g", per-serving column detected (toggle offered), one field flagged "check this value" | PASS | 82_ocr_review.png, 83_ocr_review_values.png |
| Accept → Ingredient editor prefilled with the recognised values (name left for the user) | PASS | 84_editor_prefilled.png |
- NOTE-8: this run used a one-off build with the bundled `com.google.mlkit:text-recognition` model because the emulator cannot download the unbundled model; the shipped build uses the unbundled Play-Services model (same API), which downloads once on the Pixel. Camera capture on the phone follows the same code path as "From photo".

## Charts — 2026-09-12 (P8.2 + P8.3, build after the chart layer, tests 684)

`bash tools/verify.sh` → **VERIFY OK**, APK 86.1 MB, **684 unit tests**, 0 failures, lint clean.
Installed with `bash tools/emu.sh install` over the existing `emulator-5554` data (no clear, no
reboot) and driven through the UI; every chart below is drawn with Compose `Canvas` only
(amendment A2 — no chart library) and takes all of its colours from `MaterialTheme.colorScheme`.

| Screen | What the screenshot shows | Status | Evidence |
|---|---|---|---|
| Body & Health | 30/90/365 d selector (90 d); **Weight** card with the weight line, the dashed 7-day average and the legend — y ticks 76.0…80.0, x "15 Jun / 29 Jul / 12 Sep"; **Body fat** line 17.0–19.0 % | PASS | `p8_body_chart.png` |
| Body & Health (scrolled) | **Resting heart rate** line 50–60 bpm over the same 90-day x domain; **Sleep (last 14 nights)** bars with per-bar value labels (7.1 … 8.3 h, ≤ 14 bars so the labels are drawn) and `30/8 · 5/9 · 12/9` x labels; the range-aware "Last 90 days" list below | PASS | `p8_body_chart_2.png` |
| Load & Recovery | **Acute vs chronic load**: ATL (primary) and CTL (tertiary) lines with the legend, ticks 50/100/150 AU; the ATL/CTL/ACWR/TSB tiles above are unchanged | PASS | `p8_load_chart.png` |
| Load & Recovery (scrolled) | **ACWR** with the §3.2.3 zones shaded — 0.8–1.3 green tint, 1.3–1.5 amber (the > 1.5 error band is off-scale because the data tops out at 1.4); **Daily TRIMP** 28 bars (no value labels above 14 bars, today highlighted); **Recovery score** line 60–100 | PASS | `p8_load_chart_2.png` |
| Activity detail (run "Lauf", 12.36 km) | **Heart rate over time** 120–180 bpm against elapsed minutes (`0:00 / 34:00 / 68:00`); the existing HR min/avg/max + time-in-zone table is untouched below it | PASS | `p8_activity_chart.png` |
| Activity detail (scrolled) | **Pace over time** on an inverted axis — 5:00 at the top, 6:20 at the bottom, so a faster kilometre sits higher | PASS | `p8_activity_chart_2.png` |
| Running PRs | **PR progression**: one line per canonical distance with ≥ 2 efforts (here 5 km: 25:00 → 20:29 → 20:43), y labels formatted as race times, x as months, legend forced on so the single distance is named | PASS | `p8_prs_chart.png` |

Fixes made while reviewing the screenshots:

- Body weight/body-fat lines were a dot cloud: both quantities are *sampled* irregularly, so a day
  without a reading is "not weighed", not "nothing happened". The daily grid is still used to
  compute the 7-day average, then `dropGaps()` joins the sampled points. Sensor streams (HR, pace)
  keep the gap semantics.
- A Health Connect run rendered "Speed over time" instead of pace: HC exercise sessions carry
  `speedMps` but no cumulative distance stream, so `paceSeriesFromSpeed` was added as the fallback
  (FIT imports still use the distance channel).
- The single-series PR chart had no legend and the title does not name the distance →
  `LineChartCard(alwaysShowLegend = true)`.

Notes:

- NOTE-9: PLAN P8.3 says "ATL/CTL lines with ACWR zone shading". ATL/CTL are in AU (50–150 here)
  and ACWR is a ratio around 1.0, so shading ACWR zones on the AU axis would be meaningless; the
  zones are shaded on their own ACWR chart instead and ATL/CTL get a plain two-series chart.
- NOTE-10: PR progression plots absolute finishing time, as the plan specifies. With efforts at
  very different distances (1 km and a marathon) the short-distance line would flatten against the
  bottom of the axis; a per-distance y axis or a pace axis would be the fix if that ever happens.
- Navigation note for future runtime checks: the More tab remembers its own back stack, so tapping
  "More" while already on a More sub-screen does nothing. Press Back once to return to the More hub
  (this is safe — Body/Load/PRs are nested destinations, not the app root).

## Polish batch — 2026-09-12 (P8.1 + P8.6 + P8.7 + POLISH-8 + POLISH-9)

`bash tools/verify.sh` → **VERIFY OK**: **691 unit tests**, 0 failures, lint clean with
`HardcodedText` as an **error**, and `:app:assembleRelease` now part of the script.

| Artifact | Size |
|---|---|
| Debug APK (unminified) | **87.2 MB** (91,440,511 bytes) |
| Release APK (R8 + `shrinkResources`) | **13.9 MB** (14,569,988 bytes) — **6.3× smaller**, well under the ≤ 40 MB budget of §6.6 |

### POLISH-8 — `C13` and the staleness hint

- `Constraints.C13` (`domain/engine/suggest/Constraints.kt`): no identical `SessionType` on two
  adjacent grid days, and ≥ 48 h between any two `STRENGTH_*` sessions (the three variants are
  spaced as one family, so `STRENGTH_UPPER` the day after `STRENGTH_FULL` is now rejected). Both
  halves look at **every** grid item, fixed or suggested, so a soccer training already on the
  calendar blocks a suggested one the next day — the exact 13/14 and 17/18 Sep case from session 3.
  `MOBILITY` is exempt from the same-type half, because post-pass 7c deliberately puts one on every
  rest day (`sug18`).
- Tests `c13_no_same_session_type_on_consecutive_days` and `c13b_strength_sessions_48h_apart` in
  `ConstraintsTest`. **No `sug01…sug20` fixture needed changing** — C13 only removes candidates the
  greedy loop could have chosen, and every named case still asserts the same outcome.
- Staleness: `SuggestionRepository` gained `markProposedStale()` / `observeStale()`.
  `RoomCalendarRepository` takes an `onPlanChanged: suspend () -> Unit` hook, fired from
  `upsertEvent` / `deleteEvent` / `upsertOverride` / `deleteOverride`; `AppGraph` wires it to
  `suggestionRepo.markProposedStale()`. The flag is `AppSettings.suggestionsStale` in DataStore —
  **no schema change**, as the brief requires — and `observeStale()` only reports `true` while the
  latest batch is still `PROPOSED`. `generate()` and a completed review clear it.
- Tests: `RoomCalendarRepositoryTest.every_event_write_notifies_the_plan_changed_hook`,
  `RoomSuggestionRepositoryTest.a_calendar_change_marks_an_open_proposed_batch_stale_until_it_is_regenerated`
  and `…marking_stale_does_nothing_when_no_batch_is_awaiting_review`.

### POLISH-9 — the batch closes when the review is saved

`RoomSuggestionRepository.closeBatches()` runs after `accept`/`reject`: a batch with at least one
`ACCEPTED` session becomes `ACCEPTED`, one whose sessions are all `REJECTED` becomes `REJECTED`, and
a partly-reviewed batch (still holding `PROPOSED` rows) is left alone so the rest can be reviewed.
`observeLatestBatch()` still returns the row — the Training phase badge reads it — but
`SuggestionReviewViewModel` and `TodayViewModel` now only propose from a `PROPOSED` batch. Tests:
`a_partly_reviewed_batch_stays_proposed_until_every_session_is_decided`,
`a_batch_whose_sessions_are_all_rejected_becomes_rejected`, and the extended
`accept_copies_suggestions_into_planned_sessions_and_creates_the_default_plan`.

### Release smoke test (R8 build on `emulator-5554`, existing data kept)

`adb install -r -g app/build/outputs/apk/release/app-release.apk` over the debug install (same debug
signature ⇒ no data loss, no `pm clear`, no reboot).

| Step | Result | Evidence |
|---|---|---|
| Launch → Today: nutrition card, plan card, recovery 70/100, "Last synced 19:38" | PASS | `screenshots/rel_today.png` |
| Calendar (month grid), Nutrition (diary with meals + water 750 ml / 3.2 l), Training (week board, 7–13 Sep, 505/696 AU) | PASS | — |
| More → Activities (6 rows, source badges) → activity "Lauf" detail: 12.36 km, HR/pace charts, laps | PASS | `screenshots/rel_activity.png` |
| More → Ingredients, Import (history with 2 records), Backup, Integrations (all HC permissions listed) | PASS | — |
| Integrations → **Sync now**: `HealthSyncWorker` → SUCCESS, `LoadRecomputeWorker` → SUCCESS, every channel timestamp advanced to 21:51 | PASS | logcat `WM-WorkerWrapper` |
| Training → Generate suggestions → review (6 sessions, 395 AU, "In season · target 600 AU") | PASS | `screenshots/rel_suggestions.png` |
| **C13 in the field**: the generated week places Soccer training on Wed **and Fri** with a mobility day between them — previously two in a row | PASS | `rel_suggestions.png` |
| **POLISH-8 in the field**: creating a calendar event under the open batch → "Calendar changed — regenerate" on both Training and the Today card; "Regenerate" reruns the suggester and clears it | PASS | `screenshots/rel_today_stale.png`, `rel_training_stale.png` |
| **POLISH-9 in the field**: "Accept selected" → the Today card stops offering the batch and shows the accepted sessions instead, while the Training phase badge still reads "In season" | PASS | — |
| `adb logcat -d \| grep -E "FATAL\|AndroidRuntime"` after the whole walkthrough | **clean** — 0 `FATAL`, no `ClassNotFoundException` / `NoSuchMethodException` / serialization errors | — |

No keep rule had to be added after the fact: `app/proguard-rules.pro` was written up front for
kotlinx-serialization (`@Serializable` classes + `$$serializer` + `Companion.serializer()`), Room
(`*_Impl`, entities, converters), the type-safe navigation routes, DataStore's protobuf,
`ListenableWorker` subclasses, Health Connect, ML Kit / Play Services, CameraX, `com.garmin.fit.**`
and OkHttp. The debug APK was reinstalled afterwards (`bash tools/emu.sh install`).

### P8.1 — string extraction

`res/values/strings.xml` went from **43** to **778** `<string>` entries; `HardcodedText` is now
`error` in `app/lint.xml` and `lintDebug` is clean. ViewModel/UiState text that used to be a `String`
now travels as `com.myhealth.ui.common.UiMessage` (`@StringRes` + args) and is resolved in the
composable, so `domain/` stays Android-free and no ViewModel holds a `Context`.

**Deliberately left as literals (68 strings, listed in the run's `skipped_*.txt` notes):** the pure,
non-`@Composable` label helpers that unit tests assert verbatim — `goalTypeLabel`/`goalStatusLabel`/
`goalHeadline` (`GoalDraftTest`), `permissionLabel`/`syncChannelLabel` (`IntegrationsUiStateTest`),
`flagExplanation`/`recoveryBandLabel` (`LoadUiStateTest`), `distanceLabel` (`RunningPrsUiStateTest`),
`validatePlannedSession` (`PlannedSessionDraftTest`), `validateTemplate` (`MealTemplateDraftTest`),
`OcrReviewUiState.basisNote` (`OcrReviewUiStateTest`), the `targetProgressRows` macro labels and
`formatSleepDuration` (`DayDetailUiStateTest`), and the number-glued unit suffixes (`" min"`,
`" AU"`, `" g"`) and `" · "` separators inside those same helpers. Moving them would mean rewriting
the §3/§4 named tests that pin their exact output, which R8 (rule R8 of §0.1) forbids doing
casually. They are single-language English constants in an English-only app, so nothing is lost
today; a follow-up could convert them together with their tests.

### P8.6 — visual polish

- New `ui/common/Dimens.kt` (`SCREEN_PADDING` 16 dp, `CARD_CORNER_RADIUS` 12 dp) and
  `ui/common/LoadingBox.kt`; every screen root list now uses `PaddingValues(SCREEN_PADDING)` and
  every card surface `RoundedCornerShape(CARD_CORNER_RADIUS)`.
- `PullToRefreshBox` (M3 1.4) on **Today** and **Activities**, both triggering `syncNow()`; the
  spinner is driven by the real `SyncScheduler.observeState()`, and Activities gained a retryable
  `ErrorBanner` for a failed sync plus a `LoadingBox` for its first load.
- Body & Health's history list gained the standard icon + title + message + action empty state
  (`EmptyState`), and its "Last N days" header/empty text now follows the 30/90/365-day selector
  instead of being hard-coded to 90.
- Launcher icon: the P0 flat square is replaced by a diagonal green gradient background, a white
  heart carrying a pulse trace with a leaf on its top-right lobe (`ic_launcher_foreground.xml`), and
  a dedicated `ic_launcher_monochrome.xml` (heart + pulse as one `evenOdd` path) for themed icons.
  All vector, no external assets.
- `TodayScreen.kt` (415 lines) and `ActivityDetailScreen.kt` (402) were split by moving their
  `@Preview`s into `TodayScreenPreviews.kt` / `ActivityDetailPreviews.kt`, back inside R10's budget.

Notes:

- NOTE-11: lint now reports 21 `PluralsCandidate` warnings against the new strings (e.g. "%1$d
  logged"). English-only app, so they stay warnings; converting them to `<plurals>` is a tidy-up for
  whenever a second language appears.
- NOTE-12: P8.8 (Glance home-screen widget) is **skipped** — it is the one task PLAN §5 marks
  optional, and Glance would need a dependency addition that R4/R5 forbid outside a task that lists it.

## Session 5 — 2026-09-12 (final debug build e461e57, tests 691)

| Step | Result | Evidence |
|---|---|---|
| Regression pass: Today (plan card with completed + planned sessions, recovery 70, targets), Training (phase "In season" now that the match event is typed, planned/target/actual bar), Nutrition diary, Ingredients (empty query lists the ingredient — BUG-2 fixed), Body charts; no crashes in logcat | PASS | 90_final_*.png |

## Instrumented tests (PLAN P10.2)

`JAVA_HOME=/home/robert/jdk/current ANDROID_HOME=/home/robert/android-sdk ./gradlew :app:connectedDebugAndroidTest`
on `emulator-5554` (Android 15, `myhealth_api35`): **10/10 pass** —
`MyHealthDatabaseTest` (4), `BottomNavTest`, `CalendarEventTest`, `IngredientTest`,
`MealTemplateAndDiaryTest`, `OnboardingFlowTest`, `SettingsPersistenceTest`. Two consecutive full
runs were both 10/10 green (no flakiness observed); no test was `@Ignore`d.

Starting state was 6/10 (the same 4 UI tests failing); all 4 failures were test-code bugs, not
product bugs, and were fixed without touching production code except reading the two pre-existing
`Modifier.testTag("settings_dynamic_color_switch")` uses already in `SettingsSections.kt` — no new
`testTag` was added anywhere.

- **`IngredientTest`** ("Calories" not found) and **`MealTemplateAndDiaryTest`** ("Save template"
  not found): both screens' energy/macro fields and the Save button sit in `item {}`s below the
  fold of a `LazyColumn`. A `LazyColumn` only composes items near the viewport, so a plain
  `performScrollTo()` fails outright (there is no node yet to scroll to) — fixed with
  `onNode(hasScrollAction()).performScrollToNode(hasText(label))`, which scrolls the list by index
  until the target composes, then acts on it. `MealTemplateAndDiaryTest` additionally needed
  `Espresso.closeSoftKeyboard()` after the quantity-field edit so the keyboard did not intercept
  the Save click.
- **`OnboardingFlowTest`** ("found 2 nodes that satisfy SetText"): `onNode(hasSetTextAction())`
  matched both the read-only birth-date `OutlinedTextField` (which still carries a SetText
  semantics action despite `readOnly = true`) and the `DatePickerDialog`'s real text-input field.
  Scoped the matcher to `hasSetTextAction() and hasAnyAncestor(isDialog())`.
- **`SettingsPersistenceTest`** (10 s `waitUntil` timeout): a `printToLog` semantics-tree dump
  showed two separate issues. First, the same below-the-fold problem — `ProfileSection` (the
  Settings `LazyColumn`'s first item) is by itself taller than the viewport, so
  `AppPreferencesSection` (item 2, holding the "Use wallpaper colours" switch) never composes from
  a plain `waitUntilTextExists` on its label; fixed with the same `performScrollToNode` pattern,
  scrolling to the switch's existing test tag. Second, the dump showed that after
  `scenario.recreate()` (which restores the NavController's saved back stack straight back onto
  Settings, not Today), re-tapping the bottom-nav "More" tab is a **dead click**: Settings is
  reached from the More list by a plain `navigate()` push, not through the bottom bar's own
  save/restore machinery, and tapping "More" from it left the app on Settings with no navigation
  at all. The fix does not re-navigate after the second `recreate()` — it scrolls and asserts on
  the screen that is already showing.
- **`MealTemplateAndDiaryTest`** (diary total right, but the logged item never found): the new
  template is created with no default slot, so `defaultSlotFor` falls back to `MealSlot.LUNCH`;
  the diary's `LazyColumn` renders "Breakfast" first, so the logged item row is further down the
  list than the fold — same `performScrollToNode` fix, applied to the diary screen.

## Scope decision — P9 (direct Garmin Connect client)

Not built, deliberately. Garmin offers no personal API; the only route is the reverse-engineered SSO flow used by community Python libraries, which Garmin broke in March 2026 and can break again at any time, and which requires storing the Garmin password on the device. Everything the owner asked for (activities, HR, sleep, steps, calories, weight, body fat, HRV, VO2max) arrives through Health Connect, which is the supported path. The isolation seam (`GarminMetricsProvider`, PLAN P9.1) remains available should Garmin's Body Battery / stress / training readiness ever be wanted; it would be a self-contained, optional, default-off module.

## Session 6 — 2026-09-13 (P11 cycle tracker, build ef19d44, 734 unit + 12 instrumented tests)

| Step | Result | Evidence |
|---|---|---|
| Onboarding as FEMALE: step 3 shows "Track menstrual cycle" (default on) | PASS | c01_onboarding_step3.png |
| Cycle screen (More → Cycle): empty state → "Log period start" (date picker, default today) → status "Menstrual · Day 1 of ~28 · Next period in 28 days (11 Oct) · Ovulation ~27 Sep · Fertile window 22–28 Sep · default-cycle confidence note", forecast of 6 cycles with period ranges and ovulation dates, history row "13 Sep 2026 · Ongoing", "Period ended" action | PASS | c03_cycle_empty.png, c05_cycle_status.png, c06_cycle_forecast.png |
| Today: "Cycle" card (Menstrual · Day 1 of ~28 · next period) | PASS | c07_today_scrolled.png |
| Calendar: period days 13–17 (red), fertile window 22–24/28 (olive), ovulation window 25–27 (rings), predicted next period 11 Oct (light red) | PASS | c08_calendar_markers.png |
- POLISH-10 (suggestions, new users): right after the first sync (before `daily_load` exists) or for a user with no history, `weeklyTarget = min(ctl·7·factor, max(lastWeek·1.25, 150))` is 0, so the generated week contains only mobility. Use a starter target (150 AU) when CTL < 5 and there is no last-week load, and label it "starter week" in the rationale.
| Cycle-aware suggestions (after sync, CTL 83, target 473 AU, 11 sessions): rationale lines per day — day 1/2 "keeping the intensity moderate for the first two days", day 3–5 "quality work is fine now, rated a little more cautiously", follicular "intervals and strength work are usually best tolerated now", each with "Based on a default 28-day cycle — log your period to improve this" | PASS | c13_review_1.png … c13_review_6.png |
- POLISH-11 (onboarding): the "sessions / week" fields default to 0, so a user who skips them gets sport caps of 0 and the suggester can only propose cross-training/mobility. Default to Run 2 / Strength 2 / Soccer 1 and treat 0 as "no cap" only when all three are 0.

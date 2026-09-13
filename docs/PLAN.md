# MyHealth — Implementation Plan

Status: v1.0 · Date 2026-09-12 · Author: planning agent (Opus) · Source of truth: `docs/BRIEF.md`
Target: single-user Android app, Pixel 7A, sideloaded. Offline-first. No Play Store.

This document is **executable**: every task in section 5 is written so that one coding agent can complete it
in a single run, and the lead can verify it with the exact commands given. Read sections 0–4 before
executing any task; they are the contract every task must respect.

---

## 0. Execution protocol (read first, every agent)

### 0.0 Lead review amendments (2026-09-12, Fable 5.1) — these override anything below that contradicts them

| # | Amendment | Where applied |
|---|---|---|
| A1 | **Toolchain is the one in `docs/TOOLCHAIN.md`** (verified by an actual build+test+lint on this machine): AGP 8.13.2, Gradle wrapper **8.14.5**, Kotlin **2.3.21**, KSP 2.3.12, Compose BOM **2026.06.01**, **compileSdk = targetSdk = 36**, minSdk 34, core-ktx 1.17.0, activity-compose 1.12.4, lifecycle 2.9.4, navigation-compose 2.9.8, okhttp 5.1.0, Room 2.8.5, HC 1.1.0, CameraX 1.6.2, FIT `com.garmin:fit:21.214.0`. The newest androidx/Compose releases (BOM 2026.09, core 1.19, nav 2.10, lifecycle 2.11, Compose 1.12) **require AGP 9.1 + compileSdk 37 and fail here** — never bump to them. No `jvmToolchain(17)` (only JDK 21 exists): use `compileOptions` 17 + `kotlin.compilerOptions.jvmTarget = JVM_17`. | §0.3, P0.1, App. B |
| A2 | **Vico is removed from the project** (both 2.5.x and 3.x require compileSdk 37). Charts are drawn with Compose `Canvas` in `ui/common/charts/` (P8.2 rewritten). | §4.2, §4.3, P8.2, R15, B8 |
| A3 | **ML Kit uses the unbundled Play-Services artifacts** `com.google.android.gms:play-services-mlkit-text-recognition:19.0.1` and `play-services-mlkit-barcode-scanning:18.3.1` (Pixel has Play Services; models download once, then work offline). Measured debug APK with the full dependency set: ~81 MB unbundled vs ~149 MB bundled. | P4.8, R11, §6.6 |
| A4 | **Rounding is half-up**, not `kotlin.math.round` (which is half-to-even and would make `nut01` 2220 instead of 2230): `roundTo(v, step) = floor(v / step + 0.5) * step`. | §3 preamble |
| A5 | Corrected expected values (recomputed): `load01` = **108.1 ± 0.5** (rate 1.8016), `load02` = **90.4 ± 0.5**, `load13` sd = **42.905**, monotony = **1.3652 ± 0.001**, strain = **559.7 ± 0.5**; `pr10` = **2501.9 ± 1**. | §3.2.4, §3.4 |
| A6 | Health Connect changes API: `getChangesToken(ChangesTokenRequest(recordTypes))` and `getChanges(token)` returning `ChangesResponse(changes, nextChangesToken, hasMore, changesTokenExpired)`; loop while `hasMore`; on `changesTokenExpired` do exactly one full 30-day re-read and request a fresh token. Exercise routes are **not** read from HC (drop `READ_EXERCISE_ROUTE`; GPS comes from FIT import). | P2.1, P2.6 |
| A7 | `gradle.properties`: no `org.gradle.configuration-cache` (not needed, one less thing to break). `robolectric` is not a dependency. | P0.1 |
| A8 | P0 is executed as **one task by one Opus agent** (P0.1–P0.5 together), starting from the verified template at the path given in the task prompt; the fallback ladder is replaced by "if the verified set fails, stop and report". | P0 |
| A9 | Calorie clamp ordering: `target = max(floor, min(raw, ceil))` — the floor wins if the two ever cross. | §3.1.4 |
| A10 | A working tree that builds is the only checkpoint mechanism (no git commits unless the owner asks); every agent must leave the tree green. | §0.1 |


### 0.1 Ground rules for coding agents

| # | Rule |
|---|---|
| R1 | Do exactly one task per run. Do not start the next task. Do not "improve" files outside the task's file list. |
| R2 | Before writing code, read `docs/PLAN.md` §1 (architecture), §2 (data model) and the §3 sub-section for any engine you touch. These are specifications, not suggestions. Where the plan gives a formula, implement that formula. |
| R3 | Every task ends by running its acceptance commands verbatim and pasting the tail of the output into the task report. A task is not done until they pass. |
| R4 | Never change `gradle/libs.versions.toml` versions except in a task that explicitly says so. If a build fails due to a version, stop and report — do not bump. |
| R5 | No new Gradle modules. No Hilt/Dagger/Koin. No Retrofit. No RxJava. No new third-party libs unless the task lists them. |
| R6 | `domain/` must not import anything from `android.*`, `androidx.*`, `kotlinx.coroutines.android`, or Room. Enforced by test `ArchitectureTest` (P1.8). |
| R7 | Never commit secrets. Garmin credentials only via `EncryptedSharedPreferences` (P9). |
| R8 | When a task says "add tests", tests must actually assert values, not just `assertNotNull`. Each named test case in §3 maps to one `@Test` function with that exact name. |
| R9 | If the task cannot be completed as specified (API differs from plan, library missing), stop, leave the tree compiling, and report the discrepancy with evidence. Do not improvise an alternative architecture. |
| R10 | Keep functions ≤ ~60 lines, files ≤ ~400 lines. Split by responsibility, not by line count. |
| R11 | Never write `/*` inside a KDoc or comment (e.g. `mapper/*Mappers.kt`) — Kotlin nests block comments and the rest of the file is swallowed. Write `mapper/…Mappers.kt` instead. |
| R12 | Gradle runs unit tests with `user.dir` = `app/`; tests that read source or fixture files must resolve paths from both `app/` and the project root. |

### 0.2 Standard command set

Run from `/home/robert/my_health`. `JAVA_HOME=/home/robert/jdk/current`, `ANDROID_HOME=/home/robert/android-sdk`.

| Alias | Command |
|---|---|
| `BUILD` | `./gradlew :app:assembleDebug` |
| `TEST` | `./gradlew :app:testDebugUnitTest` |
| `LINT` | `./gradlew :app:lintDebug` |
| `ALL` | `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` |
| `CLEANALL` | `./gradlew clean :app:assembleDebug :app:testDebugUnitTest` |
| `APKSIZE` | `ls -l app/build/outputs/apk/debug/app-debug.apk \| awk '{print $5}'` |
| `SCHEMA` | `ls app/schemas/com.myhealth.data.db.MyHealthDatabase/` |

Add `--offline` never. Add `--no-daemon` only when a task says so (daemon reuse is faster and is fine here).

### 0.3 Environment facts verified on this machine (2026-09-12)

| Fact | Value |
|---|---|
| JDK | Temurin 21.0.12.1 at `/home/robert/jdk/current` |
| Android SDK | `/home/robert/android-sdk`; platforms: **android-35 and android-36** (android-37 is not available in the SDK repo); build-tools 34.0.0, 35.0.0, 36.0.0; platform-tools 37.0.1; working sdkmanager at `cmdline-tools/latest-2/bin/sdkmanager` (the one in `latest` is too old) |
| Emulator | **None installed, no system images** → no instrumented tests in CI-equivalent runs. `androidTest` sources may exist but are never required to pass. |
| Device | None attached. `adb` exists at `$ANDROID_HOME/platform-tools/adb`. |
| Gradle on PATH | **None.** The verified template (see P0) already contains a working `gradlew` + wrapper jar for Gradle 8.14.5; Gradle 8.11.1 is also cached under `~/.gradle/wrapper/dists` if a wrapper ever needs regenerating. |
| Network | Maven Central + Google Maven reachable. |
| CPU/RAM | 12 cores / 14 GB. Gradle JVM heap 3 GB, Kotlin daemon 2 GB (see P0.1). |

### 0.4 Task report format (what each agent returns)

```
TASK: P<phase>.<n> <title>
FILES: <created|modified list>
COMMANDS:
  <command> -> PASS/FAIL (tail of output)
NOTES: <deviations, discoveries, follow-ups>
```

---

## 1. Architecture

### 1.1 Module & package layout

Single Gradle module `:app`, application id `com.myhealth`, namespace `com.myhealth`.

```
app/src/main/java/com/myhealth/
├─ MyHealthApp.kt                 # Application; creates AppGraph; WorkManager config
├─ di/
│   ├─ AppGraph.kt                # the container: lazy vals for every dependency
│   ├─ AppGraphHolder.kt          # LocalAppGraph CompositionLocal + appGraph() accessor
│   └─ ViewModelFactories.kt      # viewModelFactory { } helpers
├─ domain/                        # PURE KOTLIN. No android.*, no androidx.*, no Room.
│   ├─ model/                     # data classes + enums (Profile, ActivitySession, …)
│   ├─ engine/
│   │   ├─ nutrition/             # NutritionTargetEngine, MacroSplitter, MetTable
│   │   ├─ load/                  # TrimpCalculator, LoadSeriesEngine, RecoveryEngine
│   │   ├─ running/               # RunningBestEngine, RiegelPredictor, VdotCalculator
│   │   ├─ suggest/               # SuggestionEngine, Periodization, ConstraintSet, Scorer
│   │   └─ label/                 # NutritionLabelParser, Lexicon, NumberTokenizer
│   ├─ repository/                # repository INTERFACES only
│   └─ util/                      # DateRange, LocalDate helpers, Result/AppError
├─ data/
│   ├─ db/                        # MyHealthDatabase, DAOs, entities, converters, migrations
│   │   ├─ entity/  dao/  converter/  migration/
│   ├─ repository/                # repository IMPLEMENTATIONS (entity <-> domain mapping)
│   ├─ mapper/                    # Entity <-> domain mappers (pure functions, unit-tested)
│   ├─ healthconnect/             # HealthConnectClientProvider, HcReader, HcMapper, HcSync
│   ├─ fit/                       # FitDecoder wrapper, FitToDomainMapper, CSV parser
│   ├─ ocr/                       # MlKitTextSource -> OcrLine mapping (Android side)
│   ├─ off/                       # Open Food Facts OkHttp client + DTOs
│   ├─ garmin/                    # P9 only. Direct Garmin client, isolated.
│   ├─ prefs/                     # DataStore settings
│   └─ backup/                    # JSON export/import
├─ sync/                          # WorkManager workers + scheduling
├─ ui/
│   ├─ theme/                     # Color.kt, Type.kt, Theme.kt (M3, dynamic color)
│   ├─ common/                    # shared composables: SectionCard, StatTile, NumberField,
│   │                             #   MacroBar, EmptyState, ErrorBanner, LoadingBox, DatePickerField
│   ├─ nav/                       # Routes.kt (@Serializable), MyHealthNavHost.kt, BottomBar.kt
│   ├─ today/ calendar/ training/ activities/ nutrition/ ingredients/ meals/
│   ├─ camera/ body/ goals/ settings/ imports/ onboarding/
│   └─ … each feature folder: <Feature>Screen.kt, <Feature>ViewModel.kt, <Feature>UiState.kt
└─ res/                           # strings.xml (English only), themes, icons
```

Test roots:

| Kind | Location | Runs in CI |
|---|---|---|
| Domain + mapper + parser unit tests | `app/src/test/java/com/myhealth/…` (mirror main package) | **yes** (`TEST`) |
| Fixtures (OCR text, CSV, JSON) | `app/src/test/resources/fixtures/…` | yes |
| Room migration + DAO tests | `app/src/androidTest/java/com/myhealth/data/db/…` | **no** (no emulator) — written but only run when a device is attached |
| Compose UI tests | `app/src/androidTest/…` | no; keep to ≤ 3 smoke tests |

### 1.2 Layering rules

```
ui  ──▶ domain (models, engines, repository interfaces)
ui  ──▶ di (AppGraph)   [only to obtain ViewModels]
data ──▶ domain (implements repository interfaces, maps to domain models)
sync ──▶ data + domain
domain ──▶ nothing
```

- UI **never** imports `com.myhealth.data.*`. ViewModels depend on repository interfaces from `domain/repository`.
- Entities never leave `data/`. Mappers in `data/mapper` convert both ways.
- One exception, explicitly allowed: `data/db/entity` classes may be used directly inside `data/repository` and `data/mapper`.

### 1.3 Dependency injection — `AppGraph`

No annotation processors for DI. `AppGraph` is a plain class with `by lazy` properties, constructed once in `MyHealthApp.onCreate()`.

```kotlin
class AppGraph(private val app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock: Clock by lazy { Clock.systemDefaultZone() }
    val db: MyHealthDatabase by lazy { MyHealthDatabase.build(app) }
    val settings: SettingsRepository by lazy { DataStoreSettingsRepository(app) }
    val profileRepo: ProfileRepository by lazy { RoomProfileRepository(db.profileDao(), db.bodyDao()) }
    // … one lazy val per repository / engine / worker helper
    val nutritionTargetEngine: NutritionTargetEngine by lazy { NutritionTargetEngine(clock) }
}
```

Access from Compose:

```kotlin
val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph missing") }
@Composable fun appGraph(): AppGraph = LocalAppGraph.current
```

ViewModels are obtained with an explicit factory — no reflection:

```kotlin
@Composable
inline fun <reified VM : ViewModel> rememberVm(noinline create: (AppGraph) -> VM): VM {
    val graph = appGraph()
    return viewModel(factory = viewModelFactory { initializer { create(graph) } })
}
```

Workers get the graph through `(applicationContext as MyHealthApp).graph` — a `WorkerFactory` is **not** used.

Rules:
- `AppGraph` holds no Activity/Compose references.
- Anything a test needs must be constructible without `AppGraph` (engines take plain constructor params).
- Engines are **stateless**; they take all inputs as parameters. `Clock` is injected so tests can pin "today".

### 1.4 Threading & coroutines

| Concern | Convention |
|---|---|
| Repos expose reads as `Flow<T>` (Room `Flow` DAOs), writes as `suspend fun` | always |
| Dispatcher for DB/IO | Room handles its own; explicit IO uses `withContext(Dispatchers.IO)` inside the repository impl, **never** in the ViewModel |
| Engines (CPU) | called via `withContext(Dispatchers.Default)` when operating on > ~1 000 items; otherwise inline |
| ViewModel state | `StateFlow<UiState>` via `MutableStateFlow`, exposed with `.asStateFlow()`; derived flows via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)` |
| One-shot events | `Channel<UiEvent>(Channel.BUFFERED).receiveAsFlow()` — snackbars, navigation |
| Cancellation | never use `GlobalScope`; long background work is WorkManager, not `appScope`, except fire-and-forget cache warming |
| Compose collection | `collectAsStateWithLifecycle()` (lifecycle-runtime-compose) |

### 1.5 Error handling

Single result type in `domain/util`:

```kotlin
sealed interface AppError {
    data class Storage(val cause: Throwable) : AppError
    data class Network(val code: Int?, val cause: Throwable?) : AppError
    data object HealthConnectUnavailable : AppError
    data object HealthConnectUpdateRequired : AppError
    data object HealthConnectPermissionDenied : AppError
    data class Parse(val what: String, val detail: String) : AppError
    data class Validation(val field: String, val message: String) : AppError
    data class Unexpected(val cause: Throwable) : AppError
}
sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Err(val error: AppError) : Outcome<Nothing>
}
inline fun <T> runCatchingApp(block: () -> T): Outcome<T>
```

- Repository methods that can fail return `Outcome<T>`. Flows of cached data never fail; they emit last-known data and expose a separate `syncStatus: StateFlow<SyncStatus>`.
- ViewModels convert `AppError` → a user string via `AppError.toMessage()` (in `ui/common`). Domain never formats user strings.
- Engines never throw for bad input: they return a result with `warnings: List<EngineWarning>` and clamp inputs. Only programmer errors (`require` on invariants the caller controls) throw.
- Logging: `android.util.Log` wrapped in `ui/common/Logx.kt` with tag `MyHealth`; debug-only verbose.

### 1.6 Naming conventions

| Thing | Convention | Example |
|---|---|---|
| Room entity | `<Name>Entity`, table `snake_case` | `ActivitySessionEntity` / `activity_session` |
| DAO | `<Name>Dao` | `ActivityDao` |
| Repository interface | `<Name>Repository` in `domain/repository` | `ActivityRepository` |
| Repository impl | `Room<Name>Repository` / `Hc<Name>Source` | `RoomActivityRepository` |
| Mapper | `<Name>Mappers.kt` with `fun ActivitySessionEntity.toDomain()` / `fun ActivitySession.toEntity()` | |
| ViewModel | `<Screen>ViewModel`, state `<Screen>UiState` | `TodayViewModel`, `TodayUiState` |
| Screen composable | `<Screen>Screen(vm, onNavigate…)` + private `<Screen>Content(state, actions)` (stateless, previewable) | |
| Route | `@Serializable data object/class <Name>Route` | `CalendarRoute` |
| Engine | `<Domain>Engine`, main entry `fun compute(...)` | `NutritionTargetEngine.compute(...)` |
| Test | `<Class>Test`; test fn names = the exact case IDs in §3 | `nut01_male30y180cm80kg_maintain_restday` |
| Constants | `object Defaults { … }` per engine package | `NutritionDefaults` |

All dates/times: `java.time` with **desugaring off** (minSdk 34 ⇒ `java.time` is native). Store instants as epoch millis (`Long`), local dates as **epoch day** (`Long`) or ISO `yyyy-MM-dd` `String`? → **epoch day `Long`** in Room (sortable, indexable, no locale issues), converted at the boundary. Zone: `ZoneId.systemDefault()` captured once in `AppClock`; all "day" boundaries are local days. ISO week starts Monday (`WeekFields.ISO`).

### 1.7 Compose conventions

- Material 3 (`androidx.compose.material3`), dynamic color on Android 12+, dark theme follows system.
- `Scaffold` + `NavigationBar` at the root; nested `NavHost`s are not used — one graph.
- Each screen file ≤ ~350 lines; extract row/card composables to the same file or `ui/common`.
- `@Preview` on the stateless `…Content` composable of every screen with a `previewState` fixture. Previews must compile (they are part of `assembleDebug`).
- No custom design system, no custom fonts. Spacing scale: 4/8/12/16/24 dp. Corner radius 12 dp for cards.
- Strings in `res/values/strings.xml`. No hard-coded user-facing strings in composables (lint `HardcodedText` is enabled as **warning**, not error — do not fight it in P0–P2, fix in P8.1).

---

## 2. Data model

### 2.1 Enums (all in `domain/model/Enums.kt`; stored in Room as `TEXT` = `name()`)

Converter rule: unknown strings decode to the enum's `UNKNOWN`/last-resort member and log a warning — never crash on forward-incompatible data.

| Enum | Members |
|---|---|
| `SportType` | `SOCCER_MATCH, SOCCER_TRAINING, RUN_OUTDOOR, RUN_TREADMILL, RUN_TRACK, RUN_TRAIL, WALK, HIKE, CYCLING, CYCLING_INDOOR, STRENGTH, HIIT, MOBILITY, SWIM, ROWING, OTHER, UNKNOWN` |
| `SportGroup` | `SOCCER, RUN, STRENGTH, CYCLE, WALK, SWIM, OTHER` — `SportType.group: SportGroup` is a property, used by the de-dup key and by load rules |
| `ActivitySource` | `HEALTH_CONNECT, FIT_IMPORT, CSV_IMPORT, GARMIN_API, MANUAL` |
| `EventType` | `SOCCER_MATCH, SOCCER_TRAINING, RACE, APPOINTMENT, BLOCKED, NOTE, OTHER` |
| `MealSlot` | `BREAKFAST, MORNING_SNACK, LUNCH, AFTERNOON_SNACK, DINNER, EVENING_SNACK, PRE_WORKOUT, POST_WORKOUT` |
| `MeasureBasis` | `PER_100G, PER_100ML, PER_PIECE` (how an ingredient's nutrition values are expressed) |
| `QuantityUnit` | `G, ML, PIECE, SERVING` |
| `Sex` | `MALE, FEMALE, OTHER` |
| `NeatLevel` | `DESK(1.25), LIGHT_ACTIVE(1.35), ACTIVE(1.45), PHYSICAL_JOB(1.60)` — carries `factor: Double` |
| `Intensity` | `RECOVERY, LOW, MODERATE, HIGH, MAX` |
| `SessionType` | `EASY_RUN, LONG_RUN, TEMPO_RUN, INTERVAL_RUN, RECOVERY_RUN, STRENGTH_FULL, STRENGTH_UPPER, STRENGTH_LOWER, SOCCER_TRAINING, SOCCER_MATCH, MOBILITY, CROSS_TRAINING, REST` |
| `GoalType` | `RACE_TIME, BODY_WEIGHT, STRENGTH_LIFT, CONSISTENCY, SOCCER_AVAILABILITY` |
| `GoalStatus` | `ACTIVE, ACHIEVED, ABANDONED, EXPIRED` |
| `TrainingPhase` | `BASE, BUILD, PEAK, TAPER, RACE_WEEK, IN_SEASON, OFF_SEASON, RECOVERY_WEEK` |
| `RecoveryBand` | `FRESH, GOOD, MODERATE, FATIGUED, STRAINED` |
| `LinkMethod` | `MANUAL, AUTO_TIME_OVERLAP, AUTO_ACCEPTED` |
| `SleepStage` | `UNKNOWN, AWAKE, AWAKE_IN_BED, OUT_OF_BED, SLEEPING, LIGHT, DEEP, REM` |
| `DayType` | `REST, TRAINING, HARD_TRAINING, MATCH_DAY, PRE_MATCH, RACE_DAY, PRE_RACE, RECOVERY` (computed, not stored except in the target snapshot) |
| `LoadMethod` | `HR_SAMPLES, HR_AVERAGE, RPE_ESTIMATE, DURATION_ONLY` |
| `ImportKind` | `FIT_FILE, GARMIN_CSV, GARMIN_ZIP, JSON_BACKUP` |
| `PlanStatus` | `DRAFT, ACTIVE, ARCHIVED` |
| `PlannedStatus` | `PLANNED, COMPLETED, SKIPPED, MOVED` |
| `SuggestionStatus` | `PROPOSED, ACCEPTED, REJECTED, SUPERSEDED` |
| `EngineWarningCode` | `MISSING_WEIGHT, MISSING_HR, ESTIMATED_LOAD, INSUFFICIENT_HISTORY, CLAMPED_TO_FLOOR, CLAMPED_TO_CEILING, ENERGY_MISMATCH, IMPLAUSIBLE_VALUE, COLUMN_AMBIGUOUS, NO_NUTRIENTS_FOUND, LOW_CONFIDENCE` |

`SportType.group` mapping: `SOCCER_MATCH,SOCCER_TRAINING → SOCCER`; `RUN_* → RUN`; `STRENGTH,HIIT → STRENGTH`; `CYCLING,CYCLING_INDOOR → CYCLE`; `WALK,HIKE → WALK`; `SWIM → SWIM`; rest `OTHER`.

### 2.2 Room database

`MyHealthDatabase`, `version = 1` at P1, incremented per schema change; `exportSchema = true`, schema dir `app/schemas`. Every version bump ships an explicit `Migration` object in `data/db/migration/` plus a row in the migration table in `docs/PLAN.md` §6.4. **`fallbackToDestructiveMigration` is forbidden** except behind `BuildConfig.DEBUG && settings.allowDestructiveMigration` (default false).

Common column conventions:
- `id: Long` primary key, `autoGenerate = true`, unless stated.
- Instants: `*AtMillis: Long` (UTC epoch millis). Local days: `*Day: Long` (epoch day). Local times: `*MinuteOfDay: Int`.
- All nullable-in-reality numeric fields are nullable Kotlin types (`Double?`, `Int?`) — never sentinel `-1`.
- `createdAtMillis`, `updatedAtMillis` on every user-editable entity.
- Foreign keys: `onDelete = CASCADE` for owned children, `SET_NULL` for soft references (e.g. event → activity link).

#### 2.2.1 Profile & body

**`profile`** (single row, `id = 1L`)

| Column | Type | Notes |
|---|---|---|
| `id` | `Long` PK | always 1 |
| `displayName` | `String` | |
| `sex` | `Sex` | |
| `birthDay` | `Long` | epoch day |
| `heightCm` | `Double` | |
| `neatLevel` | `NeatLevel` | default `LIGHT_ACTIVE` |
| `goalWeightKg` | `Double?` | |
| `goalPaceKgPerWeek` | `Double` | default 0.0; UI clamps to [-1.0, +0.5] |
| `restingHrManual` | `Int?` | fallback when HC has none |
| `maxHrManual` | `Int?` | overrides Tanaka estimate |
| `fallbackWeightKg` | `Double?` | used when no `body_measurement` exists |
| `sleepTargetHours` | `Double` | default 8.0 |
| `preferredSportsJson` | `String` | JSON `{"RUN":3,"STRENGTH":2,"SOCCER":2}` sessions/week caps |
| `mobilityOnRestDays` | `Boolean` | default true |
| `createdAtMillis`,`updatedAtMillis` | `Long` | |

**`body_measurement`**

| Column | Type | Notes |
|---|---|---|
| `id` | `Long` PK | |
| `measuredAtMillis` | `Long` | index |
| `day` | `Long` | epoch day, index |
| `weightKg` | `Double?` | |
| `bodyFatPercent` | `Double?` | 0–100 |
| `muscleMassKg` | `Double?` | |
| `boneMassKg`,`bodyWaterPercent` | `Double?` | |
| `source` | `ActivitySource` | |
| `externalId` | `String?` | HC record id |
| `note` | `String?` | |

Indices: `idx_body_day (day)`, unique `uq_body_source_ext (source, externalId)` where `externalId IS NOT NULL` (Room: `@Index(value=["source","externalId"], unique=true)`; nulls are distinct in SQLite so this is safe).

#### 2.2.2 Activities

**`activity_session`** — the *canonical, merged* activity.

| Column | Type | Notes |
|---|---|---|
| `id` | `Long` PK | |
| `startAtMillis` | `Long` | index |
| `endAtMillis` | `Long` | |
| `day` | `Long` | local epoch day of `startAtMillis`, index |
| `sportType` | `SportType` | |
| `sportGroup` | `SportGroup` | denormalized for the dedupe bucket + queries, index |
| `title` | `String?` | |
| `durationSec` | `Int` | moving/timer time if known, else elapsed |
| `elapsedSec` | `Int` | |
| `distanceMeters` | `Double?` | |
| `activeEnergyKcal` | `Double?` | |
| `totalEnergyKcal` | `Double?` | |
| `avgHr`,`maxHr` | `Int?` | |
| `avgSpeedMps`,`maxSpeedMps` | `Double?` | |
| `avgCadenceSpm` | `Double?` | |
| `elevationGainM` | `Double?` | |
| `trimp` | `Double?` | computed by the load engine, cached |
| `loadMethod` | `LoadMethod?` | |
| `rpe` | `Int?` | 1–10, user-entered |
| `note` | `String?` | |
| `primarySource` | `ActivitySource` | source of the winning field set |
| `mergedSourcesCsv` | `String` | e.g. `HEALTH_CONNECT,FIT_IMPORT` |
| `dedupeBucket` | `String` | `"${sportGroup}|${startAtMillis/300_000}"`, index |
| `userEditedFieldsCsv` | `String` | field names the user manually overrode; merger must not clobber them |
| `hasStreams` | `Boolean` | |
| `createdAtMillis`,`updatedAtMillis` | `Long` | |

Indices: `idx_act_start (startAtMillis)`, `idx_act_day (day)`, `idx_act_bucket (dedupeBucket)`, `idx_act_group_start (sportGroup, startAtMillis)`.

**`activity_source_record`** — one row per arrival from each source; the merger reads these.

| Column | Type | Notes |
|---|---|---|
| `id` | `Long` PK | |
| `activityId` | `Long?` | FK → `activity_session.id`, `SET_NULL`, index |
| `source` | `ActivitySource` | |
| `externalId` | `String` | HC `metadata.id`, FIT `file_id` hash, CSV row hash |
| `payloadJson` | `String` | normalized snapshot of the source's fields |
| `receivedAtMillis` | `Long` | |

Unique index `uq_asr (source, externalId)` — this is the **idempotency key for sync/import**.

**`activity_stream`** — one row per activity; big series stored as JSON arrays (personal-scale data; avoids 10k-row tables).

| Column | Type | Notes |
|---|---|---|
| `activityId` | `Long` PK | FK → `activity_session`, `CASCADE` |
| `sampleOffsetsSecJson` | `String` | `[0,1,2,…]` seconds from `startAtMillis`; the shared time axis |
| `hrJson` | `String?` | `[null,132,133,…]` bpm |
| `distanceMetersJson` | `String?` | cumulative |
| `speedMpsJson` | `String?` | |
| `cadenceSpmJson` | `String?` | |
| `altitudeMJson` | `String?` | |
| `latLngE7Json` | `String?` | `[[lat1e7,lng1e7],…]` |
| `sampleCount` | `Int` | |
| `medianIntervalSec` | `Double` | used to decide `estimated` for PR splits |

**`activity_lap`** (from FIT)

| Column | Type |
|---|---|
| `id` `Long` PK · `activityId` `Long` FK CASCADE index · `lapIndex` `Int` · `startAtMillis` `Long` · `durationSec` `Int` · `distanceMeters` `Double?` · `avgHr` `Int?` · `maxHr` `Int?` · `avgSpeedMps` `Double?` · `energyKcal` `Double?` |

#### 2.2.3 Daily health

**`daily_health_summary`** (PK `day: Long`)

| Column | Type | Notes |
|---|---|---|
| `day` | `Long` PK | |
| `steps` | `Int?` | |
| `totalEnergyKcal` | `Double?` | HC `TotalCaloriesBurnedRecord` daily aggregate |
| `activeEnergyKcal` | `Double?` | HC `ActiveCaloriesBurnedRecord` |
| `restingHr` | `Int?` | |
| `distanceMeters` | `Double?` | |
| `floors` | `Double?` | |
| `avgSpo2Percent` | `Double?` | |
| `avgRespiratoryRate` | `Double?` | |
| `hrvRmssdMs` | `Double?` | HC `HeartRateVariabilityRmssdRecord` if present |
| `vo2Max` | `Double?` | HC `Vo2MaxRecord` if present |
| `bodyBattery`,`stressAvg`,`trainingReadiness` | `Int?` | **only** populated by Tier-3 Garmin client (P9); null otherwise |
| `source` | `ActivitySource` | |
| `updatedAtMillis` | `Long` | |

**`sleep_session`**

| Column | Type | Notes |
|---|---|---|
| `id` `Long` PK · `startAtMillis` `Long` index · `endAtMillis` `Long` · `night` `Long` (epoch day the sleep is *attributed* to = local date of `endAtMillis`; **unique index**) · `totalSleepMin` `Int` · `lightMin`,`deepMin`,`remMin`,`awakeMin` `Int?` · `stagesJson` `String?` (`[{s,e,stage}]`) · `source` `ActivitySource` · `externalId` `String?` · `sleepScore` `Int?` |

Unique index `uq_sleep_source_ext (source, externalId)`; index `idx_sleep_night (night)`.
Rule: if two sleep sessions map to the same `night`, **merge** them (union of intervals, stages concatenated) in the mapper before insert; the DAO upsert is by `night`.

**`sync_state`** (PK `key: String`) — `changesToken: String?`, `lastSuccessAtMillis: Long?`, `lastErrorAtMillis: Long?`, `lastError: String?`, `backfillCompleteDay: Long?`. Keys: `hc.exercise`, `hc.daily`, `hc.sleep`, `hc.body`, `targets.recompute`, `load.recompute`.

#### 2.2.4 Calendar & plan

**`calendar_event`**

| Column | Type | Notes |
|---|---|---|
| `id` | `Long` PK | |
| `type` | `EventType` | |
| `title` | `String` | |
| `startDay` | `Long` | epoch day, index |
| `startMinuteOfDay` | `Int?` | null = all-day |
| `durationMin` | `Int?` | |
| `location` | `String?` | |
| `sportType` | `SportType?` | for matches/races/trainings |
| `targetDistanceMeters` | `Double?` | for races |
| `isKeyEvent` | `Boolean` | drives periodization (A-race / important match) |
| `recurrenceRule` | `String?` | subset of RFC5545: `FREQ=WEEKLY;BYDAY=TU,TH;INTERVAL=1;UNTIL=yyyymmdd` |
| `recurrenceUntilDay` | `Long?` | denormalized for range queries |
| `parentEventId` | `Long?` | set on materialized overrides |
| `linkedActivityId` | `Long?` | FK → `activity_session`, `SET_NULL`, index |
| `linkMethod` | `LinkMethod?` | |
| `notes` | `String?` | |
| `createdAtMillis`,`updatedAtMillis` | `Long` | |

**`event_override`** — exceptions for recurring series: `id`, `eventId` FK CASCADE, `occurrenceDay: Long`, `action: String` (`SKIP`/`MOVE`/`EDIT`), `newStartDay: Long?`, `newStartMinuteOfDay: Int?`, `newDurationMin: Int?`, `newTitle: String?`. Unique `(eventId, occurrenceDay)`.

Occurrence expansion happens in `domain/engine/calendar/RecurrenceExpander.kt` (pure, unit-tested), not in SQL.

**`training_plan`** — `id`, `name`, `startDay`, `endDay`, `status: PlanStatus`, `primaryGoalId: Long?`, `notes`, timestamps. At most one `ACTIVE` plan (enforced in repository).

**`planned_session`**

| Column | Type | Notes |
|---|---|---|
| `id` `Long` PK · `planId` `Long?` FK SET_NULL index · `day` `Long` index · `startMinuteOfDay` `Int?` · `sportType` `SportType` · `sessionType` `SessionType` · `intensity` `Intensity` · `targetDurationMin` `Int?` · `targetDistanceMeters` `Double?` · `targetPaceSecPerKm` `Int?` · `estimatedTrimp` `Double?` · `description` `String?` · `rationale` `String?` · `status` `PlannedStatus` · `locked` `Boolean` (user-pinned; the suggester must not move it) · `linkedActivityId` `Long?` FK SET_NULL · `sourceSuggestionId` `Long?` · timestamps |

**`suggested_session`** — output of the suggestion engine, kept so the review screen survives process death.

`id`, `batchId: Long` (index), `day`, `sportType`, `sessionType`, `intensity`, `targetDurationMin`, `targetDistanceMeters?`, `estimatedTrimp`, `score: Double`, `rationaleJson: String` (list of `{ruleId, text}`), `status: SuggestionStatus`.
**`suggestion_batch`** — `id`, `generatedAtMillis`, `horizonStartDay`, `horizonEndDay`, `phase: TrainingPhase`, `weeklyLoadTarget: Double`, `inputsHash: String`, `status`.

**`goal`**

`id`, `type: GoalType`, `title`, `targetDay: Long?`, `targetDistanceMeters: Double?`, `targetTimeSec: Int?`, `targetWeightKg: Double?`, `targetValue: Double?`, `priority: Int` (1 = primary), `status: GoalStatus`, `linkedEventId: Long?`, `notes`, timestamps. Index `(status, priority)`.

#### 2.2.5 Nutrition

**`ingredient`**

| Column | Type | Notes |
|---|---|---|
| `id` | `Long` PK | |
| `name` | `String` | index (for search, plus FTS in P8) |
| `brand` | `String?` | |
| `barcode` | `String?` | unique index where not null |
| `basis` | `MeasureBasis` | `PER_100G` / `PER_100ML` / `PER_PIECE` |
| `pieceGrams` | `Double?` | mass of one piece, required if `basis = PER_PIECE` or if pieces are usable |
| `servingGrams` | `Double?` | default serving size for quick entry |
| `servingLabel` | `String?` | e.g. "1 Scheibe (30 g)" |
| `kcal` | `Double` | per basis unit |
| `proteinG`,`carbsG`,`sugarG`,`fatG`,`satFatG`,`fiberG`,`saltG` | `Double?` | per basis unit; `kcal` and `proteinG/carbsG/fatG` effectively required (validated) |
| `sodiumG` | `Double?` | derived/stored |
| `isFavorite` | `Boolean` | |
| `source` | `String` | `MANUAL` / `OCR` / `OFF` |
| `offProductJson` | `String?` | raw OFF payload for provenance |
| `lastUsedAtMillis` | `Long?` | index desc for "recent" list |
| `useCount` | `Int` | |
| `archived` | `Boolean` | |
| `createdAtMillis`,`updatedAtMillis` | `Long` | |

**`meal_template`** — `id`, `name`, `defaultSlot: MealSlot?`, `note`, `isFavorite`, `useCount`, `lastUsedAtMillis`, `archived`, timestamps.
**`meal_template_item`** — `id`, `templateId` FK CASCADE index, `ingredientId` FK RESTRICT index, `quantity: Double`, `unit: QuantityUnit`, `sortOrder: Int`.

**`meal_log`** — `id`, `day: Long` index, `atMinuteOfDay: Int?`, `slot: MealSlot`, `name: String?`, `templateId: Long?` (SET_NULL, provenance only — items are copied), `note`, timestamps.
**`meal_log_item`** — `id`, `mealLogId` FK CASCADE index, `ingredientId: Long?` FK SET_NULL, `nameSnapshot: String`, `quantity: Double`, `unit: QuantityUnit`, plus **denormalized snapshots** `kcal, proteinG, carbsG, sugarG, fatG, satFatG, fiberG, saltG` (absolute values for the logged quantity, computed at log time). Rationale: editing an ingredient later must not rewrite history.

**`nutrition_target_snapshot`** (PK `day: Long`) — cached engine output so the UI and widgets are instant and history is auditable.
`day`, `kcal: Int`, `proteinG: Int`, `carbsG: Int`, `fatG: Int`, `fiberG: Int`, `sugarCapG: Int`, `satFatCapG: Int`, `saltG: Double`, `waterMl: Int`, `bmrKcal: Int`, `tdeeKcal: Int`, `dayType: DayType`, `explanation: String`, `warningsCsv: String`, `inputsHash: String`, `computedAtMillis: Long`.
Recompute trigger: inputs hash change (weight, profile, plan for the day, actual TDEE) — worker `TargetRecomputeWorker`.

**`water_log`** — `id`, `day` index, `atMinuteOfDay`, `ml: Int`.

#### 2.2.6 Derived / cache

**`daily_load`** (PK `day: Long`) — `trimp: Double`, `sessionCount: Int`, `atl: Double`, `ctl: Double`, `acwr: Double?`, `tsb: Double`, `monotony: Double?`, `strain: Double?`, `recoveryScore: Int?`, `recoveryBand: RecoveryBand?`, `recoveryConfidence: Double`, `flagsCsv: String`, `computedAtMillis: Long`.

**`running_best`** — `id`, `distanceMeters: Double` (canonical: 1000, 1609.34, 3000, 5000, 10000, 15000, 21097.5, 42195), `timeSec: Int`, `activityId: Long?` FK SET_NULL, `day: Long`, `method: String` (`FULL_ACTIVITY`/`BEST_SPLIT`/`MANUAL`), `isEstimated: Boolean`, `paceSecPerKm: Int`, `createdAtMillis`. Index `(distanceMeters, timeSec)`. Keep **all** qualifying efforts; "the PR" is `MIN(timeSec)` per distance via a DAO query. A partial unique index is not used; duplicates per activity are prevented by unique `(activityId, distanceMeters)`.

**`import_record`** — `id`, `kind: ImportKind`, `fileName`, `fileHashSha256` (unique index), `importedAtMillis`, `itemsParsed: Int`, `itemsInserted: Int`, `itemsDuplicate: Int`, `errorsJson: String?`.

### 2.3 Domain models that differ from entities

| Domain model | Difference |
|---|---|
| `ActivitySession` | carries `streams: ActivityStreams?` (decoded `DoubleArray`/`IntArray`, not JSON) and `laps: List<Lap>` when loaded "full"; the list query returns a lighter `ActivitySummary`. |
| `CalendarDay` | aggregate view object built by `CalendarRepository`: `day`, `events: List<EventOccurrence>`, `planned: List<PlannedSession>`, `activities: List<ActivitySummary>`, `meals: List<MealLogSummary>`, `target: NutritionTarget?`, `intake: MacroTotals`, `load: DailyLoad?`. Never a table. |
| `EventOccurrence` | expanded instance of a recurring `calendar_event`: `(eventId, occurrenceDay, effective start/duration/title, isOverride, linkedActivityId)`. Not stored. |
| `NutritionFacts` / `NutritionFactsDraft` | draft carries `ParsedValue(value: Double?, confidence: Double, sourceLineIndex: Int?)` per field + `warnings`. |
| `MacroTotals` | `kcal, proteinG, carbsG, fatG, fiberG, sugarG, satFatG, saltG` as `Double`; has `operator fun plus`. |
| `DailyLoad` / `RecoveryState` | computed values, mirrored into `daily_load` as a cache but the engines return richer objects with `components` and `flags`. |
| `Profile` | exposes computed `ageYears(on: LocalDate)`, `estimatedMaxHr`, `effectiveWeightKg(latest: BodyMeasurement?)`. |

### 2.4 Activity de-duplication (normative)

Two source records describe the **same real-world activity** iff **all** of:

1. `a.sportGroup == b.sportGroup`, **and**
2. `|a.startAtMillis - b.startAtMillis| <= 180_000` (3 min), **and**
3. `|a.durationSec - b.durationSec| <= max(60, 0.05 * max(a.durationSec, b.durationSec))`, **and**
4. distance agrees: both null, **or** one null, **or** `|a.dist - b.dist| <= max(100.0, 0.02 * max(a.dist, b.dist))`.

Implementation:
- `dedupeBucket = "${sportGroup}|${startAtMillis / 300_000}"` (5-minute buckets). Candidate lookup queries buckets `n-1, n, n+1`, then applies the predicate above. Pure function `ActivityMatcher.matches(a, b): Boolean` in `domain/engine/` — unit-tested.
- **Idempotency**: `(source, externalId)` unique on `activity_source_record`. Re-running sync/import never creates duplicates.
- **Field-level merge precedence** when several sources map to one canonical row:

| Field group | Precedence (highest first) |
|---|---|
| streams (HR/GPS/cadence), laps | `FIT_IMPORT` > `GARMIN_API` > `HEALTH_CONNECT` |
| distance, duration, speed | `FIT_IMPORT` > `HEALTH_CONNECT` > `GARMIN_API` > `CSV_IMPORT` |
| calories | `HEALTH_CONNECT` > `FIT_IMPORT` > `GARMIN_API` > `CSV_IMPORT` (HC carries Garmin's own kcal, already device-calibrated) |
| title, sportType | `FIT_IMPORT` > `CSV_IMPORT` > `GARMIN_API` > `HEALTH_CONNECT` (HC types are coarse) |
| `rpe`, `note` | `MANUAL` always wins |

- Any field listed in `userEditedFieldsCsv` is **never** overwritten by a merge.
- `externalId` derivation: HC → `metadata.id`; FIT → `sha256(fileIdSerial + fileIdTimeCreated + startTime)` hex, first 32 chars; CSV → `sha256(rawRowText)`; Garmin API → `activityId`.
- Merge is a pure function `ActivityMerger.merge(records: List<SourceRecord>, existing: ActivitySession?): ActivitySession` — unit-tested with cases T-DEDUP-01..08.

---

## 3. Domain engines (normative specifications)

All engines live in `domain/engine/`, are pure Kotlin, stateless, and take a `Clock`/`LocalDate` explicitly.
All return a result object containing `warnings: List<EngineWarning>` instead of throwing.
**Rounding rule everywhere:** half-up. `roundTo(v, step) = floor(v / step + 0.5) * step` (do **not** use `kotlin.math.round`, which is half-to-even).
All test functions listed with an ID must exist with exactly that function name.

### 3.1 Nutrition target engine

**Location** `domain/engine/nutrition/NutritionTargetEngine.kt` (+ `MetTable.kt`, `NutritionDefaults.kt`, `MacroSplitter.kt`)

**Entry point**

```kotlin
data class NutritionTargetInput(
    val date: LocalDate,
    val profile: Profile,
    val latestWeight: BodyMeasurement?,      // most recent within 30 days
    val latestBodyFat: BodyMeasurement?,     // most recent with bodyFatPercent within 30 days
    val actualDailySummary: DailyHealthSummary?, // HC totals for `date`
    val completedSessions: List<ActivitySummary>, // activities already recorded on `date`
    val plannedSessions: List<PlannedSession>,    // remaining planned for `date`
    val dayType: DayType,                    // from DayTypeResolver (below)
    val isDayComplete: Boolean               // date < today
)
fun compute(input: NutritionTargetInput): NutritionTarget
```

#### 3.1.1 BMR

Let `W` = effective weight kg, `H` = height cm, `A` = age in whole years on `date`.

1. **Mifflin–St Jeor** (default):
   - `MALE:   BMR = 10*W + 6.25*H - 5*A + 5`
   - `FEMALE: BMR = 10*W + 6.25*H - 5*A - 161`
   - `OTHER:  BMR = 10*W + 6.25*H - 5*A - 78`  (mean of the two constants)
2. **Katch–McArdle** is used instead **iff** a body-fat measurement exists within 30 days and `3.0 <= bf% <= 60.0`:
   - `LBM = W * (1 - bf/100)`; `BMR = 370 + 21.6 * LBM`
3. Weight resolution ladder: `latestWeight.weightKg` (≤ 30 days old) → `profile.fallbackWeightKg` (+ warning `MISSING_WEIGHT`) → `profile.goalWeightKg` (+ warning) → **fail-safe** 75.0 (+ warning).
4. Clamp `BMR` to `[1000.0, 3500.0]`; if clamped add `CLAMPED_*`.

#### 3.1.2 Training energy for the day

`activeKcal(session)` ladder:
1. `session.activeEnergyKcal` if present and `> 0`.
2. Else MET estimate: `kcal = (MET - 1.0) * W * durationHours`. The `-1.0` removes the resting component already counted in BMR.

MET table (`MetTable.kt`):

| SportType | MET |
|---|---|
| `SOCCER_MATCH` | 10.0 |
| `SOCCER_TRAINING` | 7.0 |
| `RUN_*` | `0.952 * speedKmh + 1.0`, clamped `[6.0, 20.0]`; if speed unknown use 9.8 (≈ 9.2 km/h) |
| `WALK` | 3.5 · `HIKE` 6.0 |
| `CYCLING` | 8.0 · `CYCLING_INDOOR` 7.0 |
| `STRENGTH` | 5.0 · `HIIT` 8.0 |
| `MOBILITY` | 2.5 · `SWIM` 8.0 · `ROWING` 7.0 |
| `OTHER`/`UNKNOWN` | 6.0 |

Derivation of the run MET: ACSM running VO₂ = `0.2 * v[m/min] + 3.5`; `MET = VO2/3.5` ⇒ `MET = 0.0571*v[m/min] + 1 = 0.952*v[km/h] + 1`.

`trainingKcal(date) = Σ activeKcal(completedSessions) + Σ activeKcal(plannedSessions)` where planned sessions use `targetDurationMin` and, for runs, `targetPaceSecPerKm` → speed.
If a planned session and a completed session overlap in time by > 50 %, count **only** the completed one (the plan was executed).

#### 3.1.3 TDEE ladder (first match wins)

| # | Condition | TDEE |
|---|---|---|
| 1 | `isDayComplete` && `summary.totalEnergyKcal != null` && `total >= 0.9*BMR` | `total` |
| 2 | `isDayComplete` && `summary.activeEnergyKcal != null` | `BMR + activeEnergyKcal` |
| 3 | otherwise | `BMR * profile.neatLevel.factor + trainingKcal(date)` |

Clamp TDEE to `[BMR, BMR * 3.0]`. Record which rung was used in the explanation.

#### 3.1.4 Calorie target

```
goalDelta:
  if profile.goalWeightKg == null                      -> 0.0
  else if |W - goalWeight| <= 1.0                      -> 0.0            (MAINTAIN)
  else sign = if (goalWeight < W) -1 else +1
       pace = min(|profile.goalPaceKgPerWeek|, if (sign<0) 1.0 else 0.5)
       goalDelta = sign * pace * 7700.0 / 7.0          (= sign * pace * 1100 kcal/day)

matchDayOverride: if dayType in {MATCH_DAY, PRE_MATCH, RACE_DAY, PRE_RACE}
       goalDelta = max(goalDelta, 0.0)                 (never diet into a match/race)

raw = TDEE + goalDelta

floor  = max( 1.2 * BMR,
              absoluteFloor,                           // MALE/OTHER 1500, FEMALE 1200
              BMR + 0.5 * trainingKcal,
              TDEE - 1000.0 )
ceil   = min( TDEE + 700.0, 5000.0 )

target = roundTo(max(floor, min(raw, ceil)), 10.0)     // floor wins if floor > ceil
```
If `raw < floor` add warning `CLAMPED_TO_FLOOR`; if `raw > ceil` add `CLAMPED_TO_CEILING`.

#### 3.1.5 Macros (`MacroSplitter`, deterministic ordering)

`refWeight` (protein/fat reference): if `W > goalWeight` then `goalWeight + 0.25*(W - goalWeight)` else `W`. If `goalWeight == null`, `refWeight = W`.

**Protein g/kg**
```
p = 1.6
if (goalDelta < 0)                       p += 0.2     // deficit spares lean mass
if (trainingKcal >= 500 || dayType in {HARD_TRAINING, MATCH_DAY, RACE_DAY}) p += 0.2
if (anySession.sportType == STRENGTH)    p += 0.2
if (ageYears >= 50)                      p += 0.1
p = clamp(p, 1.4, 2.4)
proteinG = refWeight * p
proteinG = min(proteinG, 0.35 * target / 4.0)          // never > 35 % of energy
```

**Fat target percentage by day type**

| DayType | fat % of kcal | carb floor g/kg |
|---|---|---|
| `REST` | 0.33 | 2.5 |
| `RECOVERY` | 0.30 | 3.0 |
| `TRAINING` | 0.28 | 3.5 |
| `HARD_TRAINING` | 0.25 | 5.0 |
| `MATCH_DAY` | 0.22 | 6.0 |
| `PRE_MATCH` | 0.20 | 6.5 |
| `RACE_DAY` | 0.20 | 6.0 |
| `PRE_RACE` | 0.20 | 7.0 |

```
fatFloorG  = max(0.8 * refWeight, 0.20 * target / 9.0)
fatCeilG   = 0.35 * target / 9.0
fatG       = clamp(fatPct * target / 9.0, fatFloorG, fatCeilG)
carbG      = (target - 4*proteinG - 9*fatG) / 4.0
carbFloorG = carbFloorPerKg * refWeight

if (carbG < carbFloorG) {
    // 1) take from fat down to its floor
    needKcal   = (carbFloorG - carbG) * 4.0
    availKcal  = (fatG - fatFloorG) * 9.0
    take       = min(needKcal, availKcal)
    fatG      -= take / 9.0
    carbG     += take / 4.0
    // 2) on carb-priority days only, raise the calorie target by up to 10 %
    if (carbG < carbFloorG && dayType in {PRE_MATCH, MATCH_DAY, PRE_RACE, RACE_DAY}) {
        extra   = min((carbFloorG - carbG) * 4.0, 0.10 * target)
        target += extra; carbG += extra / 4.0
        target  = roundTo(target, 10.0)
    }
    if (carbG < carbFloorG) warn(CLAMPED_TO_FLOOR, "carb floor not reachable within calorie target")
}
proteinG = roundTo(proteinG, 5.0); carbG = roundTo(carbG, 5.0); fatG = roundTo(fatG, 1.0)
```
Post-condition (asserted in tests): `|4*proteinG + 4*carbG + 9*fatG - target| <= 30`.

**Other targets**
| Field | Formula |
|---|---|
| `fiberG` | `clamp(14.0 * target / 1000.0, 25.0, 45.0)`, round to 1 |
| `sugarCapG` | `0.10 * target / 4.0`, round to 1 |
| `satFatCapG` | `0.10 * target / 9.0`, round to 1 |
| `saltG` | `5.0`, `+1.0` if total training duration ≥ 90 min; cap 6.0 |
| `waterMl` | `roundTo(35.0 * W + 750.0 * trainingHours, 100.0)`, clamp `[2000, 6000]` |

#### 3.1.6 `DayTypeResolver` (`domain/engine/nutrition/DayTypeResolver.kt`)

Evaluated in order; first match wins. `events` = expanded occurrences for `date`, `date+1`.

| Order | Condition | DayType |
|---|---|---|
| 1 | an event on `date` of type `RACE` | `RACE_DAY` |
| 2 | an event on `date` of type `SOCCER_MATCH` | `MATCH_DAY` |
| 3 | an event on `date+1` of type `RACE` with `targetDistanceMeters >= 10000` | `PRE_RACE` |
| 4 | an event on `date+1` of type `SOCCER_MATCH` | `PRE_MATCH` |
| 5 | yesterday had a `MATCH_DAY`/`RACE_DAY`, or today's planned session type is `RECOVERY_RUN`/`MOBILITY` only | `RECOVERY` |
| 6 | today's planned/completed load (estimated TRIMP) ≥ 120 **or** total planned duration ≥ 100 min **or** any `INTERVAL_RUN`/`TEMPO_RUN`/`STRENGTH_LOWER`/`SOCCER_TRAINING` | `HARD_TRAINING` |
| 7 | any planned or completed session exists | `TRAINING` |
| 8 | else | `REST` |

#### 3.1.7 Explanation string

Multi-line, built from a fixed template (tested by substring assertions):
```
BMR {bmr} kcal (Mifflin-St Jeor, {W} kg, {H} cm, {A} y)
TDEE {tdee} kcal ({tdeeSourceLabel})
Day type: {dayType}{eventSuffix}
Goal: {goalLabel} ({pace} kg/week) -> {delta:+} kcal/day
Target: {target} kcal{clampNote}
Protein {p} g ({gPerKg} g/kg{proteinReasons}) · Carbs {c} g · Fat {f} g ({fatPct} %)
```
`tdeeSourceLabel` ∈ `"measured (Health Connect total)"`, `"BMR + measured active kcal"`, `"estimated (BMR x NEAT + training)"`.

#### 3.1.8 Named test cases — `NutritionTargetEngineTest`

| ID / test fn | Scenario & assertion |
|---|---|
| `nut01_male30y_180cm_80kg_maintain_restday` | goalWeight = 80, REST, DESK, no HC → BMR = 1780, TDEE = 2225, target = 2230 (±10), protein = 130 g (1.6 g/kg → 128 → round 5 = 130) |
| `nut02_female_uses_minus161_constant` | same anthropometrics, FEMALE → BMR = 1614 |
| `nut03_other_sex_uses_minus78_constant` | BMR = 1697 |
| `nut04_katch_mcardle_used_when_bodyfat_present` | 80 kg, 15 % bf → LBM = 68 → BMR = 1838.8; assert method label in explanation |
| `nut05_bodyfat_out_of_range_falls_back_to_mifflin` | bf = 1.0 → Mifflin used, no crash |
| `nut06_deficit_pace_0_5_reduces_target_by_550` | pace −0.5 kg/wk → delta = −550 |
| `nut07_aggressive_deficit_clamped_by_bmr_floor` | pace −1.0, low TDEE → target == `1.2*BMR` rounded, warning `CLAMPED_TO_FLOOR` |
| `nut08_surplus_capped_at_plus_700` | goal gain, high pace → target ≤ TDEE + 700, warning `CLAMPED_TO_CEILING` |
| `nut09_match_day_removes_deficit` | pace −0.5, `MATCH_DAY` → target ≥ TDEE |
| `nut10_pre_match_carb_load_raises_carbs_and_lowers_fat` | `PRE_MATCH`, 80 kg → carbs ≥ 520 g (6.5 g/kg), fat at floor |
| `nut11_hard_training_day_protein_at_least_2_0_g_per_kg` | deficit + strength + 600 kcal training → p = 2.2 (1.6+0.2+0.2+0.2) |
| `nut12_protein_capped_at_35_percent_of_energy` | very low target, high refWeight → protein·4 ≤ 0.35·target |
| `nut13_macros_sum_within_30_kcal_of_target` | loop over a 24-case matrix (3 sexes × 4 dayTypes × 2 goals) |
| `nut14_missing_weight_uses_fallback_and_warns` | no measurement → warning `MISSING_WEIGHT`, uses `fallbackWeightKg` |
| `nut15_tdee_prefers_health_connect_total_on_complete_day` | summary.total = 3100 → TDEE = 3100, label "measured" |
| `nut16_tdee_ignores_implausibly_low_hc_total` | total = 900 < 0.9·BMR → rung 2/3 used |
| `nut17_met_estimate_for_run_matches_acsm` | 10 km/h, 60 min, 80 kg → MET = 10.52, kcal = (10.52−1)·80·1 = 761.6 ±1 |
| `nut18_fiber_sugar_salt_water_bounds` | 90 min training → salt = 6.0, water within [2000,6000] and multiple of 100 |
| `nut19_explanation_contains_bmr_tdee_daytype_and_goal` | substring assertions |
| `nut20_rest_day_fat_share_is_33_percent` | REST → `9*fatG/target` ∈ [0.30, 0.35] |

### 3.2 Training-load engine

**Location** `domain/engine/load/` — `TrimpCalculator.kt`, `LoadSeriesEngine.kt`, `HrBounds.kt`.

#### 3.2.1 HR bounds

```
hrMax = profile.maxHrManual
      ?: max(round(208 - 0.7 * age), observedMaxHrLast365d ?: 0)      // Tanaka
      ; clamp [150, 220]
hrRest = median(restingHr over last 7 days with data)
      ?: profile.restingHrManual ?: 60 ; clamp [30, 90]
require(hrMax - hrRest >= 30) else hrMax = hrRest + 30
```

#### 3.2.2 Session TRIMP (Banister)

`y = 1.92` (MALE/OTHER), `1.67` (FEMALE). `hrr(hr) = clamp((hr - hrRest) / (hrMax - hrRest), 0.0, 1.0)`.
`trimpRate(hr) = hrr * 0.64 * exp(y * hrr)`  [AU per minute]

**Method ladder**

| Method | Condition | Formula |
|---|---|---|
| `HR_SAMPLES` | stream has ≥ 10 HR samples | `Σ_i dt_i * trimpRate(meanHr_i)` where `dt_i = min(t_{i+1} - t_i, 60s) / 60.0` min and `meanHr_i = (hr_i + hr_{i+1}) / 2`; samples with null HR skipped and their interval excluded |
| `HR_AVERAGE` | `avgHr != null` | `durationMin * trimpRate(avgHr)` |
| `RPE_ESTIMATE` | `rpe != null` or a sport default exists | `0.30 * rpe * durationMin` |
| `DURATION_ONLY` | nothing else | `0.30 * 5 * durationMin` and warning `ESTIMATED_LOAD` |

Default RPE per sport when `rpe == null`: `SOCCER_MATCH 8.5`, `SOCCER_TRAINING 6.5`, `HIIT 8.0`, `RUN_* 6.0`, `STRENGTH 6.0`, `CYCLING 5.0`, `SWIM 6.0`, `WALK 2.0`, `HIKE 4.0`, `MOBILITY 2.0`, other 5.0.
Calibration note for the 0.30 factor: a 60-min session at `hrr = 0.75` gives `60·0.75·0.64·e^1.44 ≈ 121.6` AU; sRPE for the same session ≈ `7·60 = 420` ⇒ `k ≈ 0.29`. Use `0.30` (constant `TrimpDefaults.RPE_TO_TRIMP = 0.30`).
`trimp` is clamped to `[0.0, 600.0]`; anything above adds `IMPLAUSIBLE_VALUE`.

#### 3.2.3 Daily & rolling load (`LoadSeriesEngine`)

Input: a contiguous list of `(day, dailyTrimp)` for a window ending at `today` (missing days = 0.0).

```
// EWMA (primary)
λa = 2/(7+1)  = 0.25
λc = 2/(28+1) ≈ 0.0689655
ATL_d = ATL_{d-1} + λa * (load_d - ATL_{d-1});  ATL_{-1} = 0
CTL_d = CTL_{d-1} + λc * (load_d - CTL_{d-1});  CTL_{-1} = 0
ACWR_d = if (CTL_d > 1.0) ATL_d / CTL_d else null
TSB_d  = CTL_{d-1} - ATL_{d-1}                 // "form", uses yesterday's values

// Rolling (secondary, also exposed)
ATL7_d  = mean(load over days d-6..d)
CTL28_d = mean(load over days d-27..d)
ACWR_rolling_d = if (CTL28_d > 1.0) ATL7_d / CTL28_d else null
```
`reliable = (number of days with any data in the last 28) >= 21`; otherwise warning `INSUFFICIENT_HISTORY` and ACWR is reported but flagged.

**Foster monotony & strain** over days `d-6..d` (7 values including zeros):
```
mean = Σ/7
sd   = sqrt(Σ(x - mean)^2 / 7)          // population sd, n = 7
monotony = when { mean < 1e-6 -> 0.0; sd < 1e-6 -> 3.0; else -> min(mean / sd, 3.0) }
weeklyLoad = Σ(7 days)
strain = weeklyLoad * monotony
```

**Zones / flags**

| Metric | Thresholds |
|---|---|
| ACWR | `< 0.80` `DETRAINING` · `0.80–1.30` `OPTIMAL` · `1.30–1.50` `CAUTION` · `> 1.50` `HIGH_RISK` |
| Weekly ramp | `weeklyLoad_d / weeklyLoad_{d-7} > 1.15` → flag `RAMP_HIGH` |
| Monotony | `> 2.0` → flag `HIGH_MONOTONY` |
| Strain | `> 6000` → flag `HIGH_STRAIN` |
| Rest days | zero sessions on 0 of the last 7 days → flag `NO_REST_DAY_7D` |

#### 3.2.4 Named test cases — `TrimpCalculatorTest` / `LoadSeriesEngineTest`

| ID / test fn | Assertion |
|---|---|
| `load01_banister_male_60min_at_150bpm` | hrMax = 190, hrRest = 50 → hrr = 0.7143; rate = 0.7143·0.64·e^(1.3714) = 1.8016; TRIMP = 108.1 ± 0.5 |
| `load02_banister_female_uses_y_1_67` | same inputs, FEMALE → rate 1.5070; TRIMP = 90.4 ± 0.5 |
| `load03_hr_samples_beat_average_when_available` | stepped HR stream → method `HR_SAMPLES`, value ≠ the HR_AVERAGE value |
| `load04_sample_gap_capped_at_60s` | two samples 600 s apart → contributes at most 1 min |
| `load05_null_hr_samples_are_skipped` | stream with nulls → no NaN |
| `load06_rpe_fallback_uses_0_30_factor` | rpe = 7, 60 min → 126.0 |
| `load07_sport_default_rpe_used_when_none_given` | SOCCER_MATCH, 90 min → 0.30·8.5·90 = 229.5 |
| `load08_hrr_clamped_at_zero_and_one` | hr below rest and above max → no negative / no > 1 |
| `load09_ewma_atl_ctl_converge_on_constant_load` | 100 AU/day for 200 days → ATL ≈ CTL ≈ 100 (±0.5), ACWR ≈ 1.0 |
| `load10_acwr_null_when_ctl_below_one` | fresh series → null |
| `load11_monotony_zero_when_no_load` | all zeros → monotony 0, strain 0 |
| `load12_monotony_capped_at_three_for_constant_load` | 7× 100 AU → sd = 0 → monotony = 3.0, strain = 2100 |
| `load13_monotony_known_value` | loads `[100,0,50,80,0,120,60]` → mean = 58.571, sd = 42.905 (population), monotony = 1.3652 ± 0.001, strain = 559.7 ± 0.5 |
| `load14_acwr_zones_boundaries` | 0.79/0.80/1.30/1.31/1.50/1.51 map to the right zone |
| `load15_no_rest_day_flag` | 7 consecutive days with load → `NO_REST_DAY_7D` |
| `load16_ramp_high_flag` | week 2 = 1.20 × week 1 → `RAMP_HIGH` |
| `load17_insufficient_history_warning` | 10 days of data → `INSUFFICIENT_HISTORY` |
| `load18_tanaka_hrmax_and_observed_override` | age 30 → 187; observed 195 → hrMax = 195 |

### 3.3 Recovery-state engine

**Location** `domain/engine/load/RecoveryEngine.kt`

Composite score 0–100 from four weighted components. Any component whose inputs are missing is **dropped** and the remaining weights are renormalised to 100; `confidence = availableWeight / 100`.

| Component | Weight | Formula |
|---|---|---|
| Sleep | 40 | `durationPts (0–25) + qualityPts (0–10) + consistencyPts (0–5)` |
| Resting HR | 25 | see below |
| Load | 25 | see below |
| HRV | 10 | see below (only if `hrvRmssdMs` available today **and** a ≥ 7-day baseline exists) |

**Sleep (needs last night's `sleep_session`)**
```
h = totalSleepMin / 60.0
durationPts = when {
    h < 4.0  -> 0.0
    h < 7.0  -> 20.0 * (h - 4.0) / 3.0
    h <= 8.5 -> 20.0 + 5.0 * (h - 7.0) / 1.5
    else     -> max(20.0, 25.0 - 2.0 * (h - 8.5))
}
band(x, lo, hi) = when { x in lo..hi -> 1.0
                         x < lo      -> max(0.0, 1.0 - (lo - x) / lo)
                         else        -> max(0.0, 1.0 - (x - hi) / hi) }
qualityPts = 5.0*band(deepMin/totalSleepMin, 0.13, 0.23) + 5.0*band(remMin/totalSleepMin, 0.20, 0.25)
             // if stages are absent: qualityPts = 5.0 and warning MISSING_HR-style `LOW_CONFIDENCE`
consistencyPts: dev = |bedtimeMinuteOfDay - median(bedtime over last 14 nights)| (circular, ≤ 720)
                = if (dev <= 30) 5.0 else max(0.0, 5.0 * (1.0 - (dev - 30) / 90.0))
```

**Resting HR** (needs today's or yesterday's `restingHr` and ≥ 7 values in the last 30 days)
```
base  = median(restingHr, last 30 days, excluding today)
delta = todayRhr - base
rhrPts = when { delta <= -1 -> 25.0
                delta >= 7  -> 0.0
                else        -> 25.0 * (7 - delta) / 8.0 }
```

**Load** (needs `DailyLoad` for today with `acwr != null`)
```
acwrPts = when {
    acwr < 0.50 -> 15.0
    acwr < 0.80 -> 15.0 + 10.0 * (acwr - 0.50) / 0.30
    acwr <= 1.20 -> 25.0
    acwr <= 1.60 -> 25.0 - 20.0 * (acwr - 1.20) / 0.40
    else -> 5.0
}
tsbAdj = clamp(tsb / 10.0, -5.0, +5.0)
loadPts = clamp(acwrPts + tsbAdj, 0.0, 25.0)
if (monotony > 2.0) loadPts *= 0.85
```

**HRV**
```
base = mean(hrvRmssdMs over last 7 days excluding today)
r = today / base
hrvPts = when { r >= 1.0 -> 10.0; r <= 0.75 -> 0.0; else -> 10.0 * (r - 0.75) / 0.25 }
```

**Score & band**
```
score = round(100 * Σ(componentPts) / Σ(componentMaxWeights present))
band  = when { score >= 80 -> FRESH; >= 65 -> GOOD; >= 50 -> MODERATE; >= 35 -> FATIGUED; else -> STRAINED }
```
Additional flags copied through from the load engine + `SLEEP_DEBT` when `Σ sleep(last 7 nights) < 7*profile.sleepTargetHours - 5.0` hours.
If **no** component is available → `RecoveryState(score = null, band = null, confidence = 0.0, warnings += INSUFFICIENT_HISTORY)`; consumers must handle `null`.

**Named tests — `RecoveryEngineTest`**

| ID | Assertion |
|---|---|
| `rec01_perfect_inputs_score_fresh` | 8 h sleep (deep 18 %, rem 22 %), rhr −2 vs base, acwr 1.0, tsb 0, hrv 1.05× → score ≥ 90, band `FRESH` |
| `rec02_missing_sleep_renormalises_weights` | no sleep data → confidence = 0.60, score still 0–100 |
| `rec03_all_missing_returns_null_score` | → score null, confidence 0.0 |
| `rec04_elevated_rhr_drops_score` | delta +5 → rhrPts = 6.25 |
| `rec05_high_acwr_drops_load_points` | acwr 1.6 → acwrPts = 5.0 |
| `rec06_short_sleep_four_hours_scores_zero_duration` | h = 4.0 → durationPts 0.0 |
| `rec07_oversleep_ten_hours_penalised_mildly` | h = 10 → durationPts = 22.0 |
| `rec08_sleep_debt_flag` | 5 h × 7 nights, target 8 → `SLEEP_DEBT` |
| `rec09_band_boundaries` | 80/79/65/64/50/49/35/34 map correctly |
| `rec10_high_monotony_scales_load_points` | monotony 2.5 → loadPts ×0.85 |

### 3.4 Running PR / best-effort engine

**Location** `domain/engine/running/` — `RunningBestEngine.kt`, `RiegelPredictor.kt`, `VdotCalculator.kt`.

**Canonical distances (m):** `1000, 1609.34, 3000, 5000, 10000, 15000, 21097.5, 42195`.
**Eligible sports:** `RUN_OUTDOOR, RUN_TRACK, RUN_TRAIL`; `RUN_TREADMILL` only if `settings.includeTreadmillInPrs` (default **false**).

**Method A — full-activity PR.** If `|totalDistance - D| <= max(0.01*D, 50.0)` then
`timeSec = durationSec * D / totalDistance`, `method = FULL_ACTIVITY`, `isEstimated = (|dist - D| > 5.0)`.

**Method B — best split from the stream.** Given `offsets[i]` (s) and cumulative `distance[i]` (m):
```
two-pointer: for each j (right edge), advance i while distance[j] - distance[i+1] >= D
  interpolate left edge: tLeft = linear interpolation of time at distance (distance[j] - D)
  candidate = offsets[j] - tLeft
best = min(candidate)
```
Also interpolate the right edge symmetrically when the exact `D` falls between samples. Complexity O(n).
Quality gate: `medianIntervalSec <= 10.0` and `distance[last] >= D`; otherwise `isEstimated = true`.
`method = BEST_SPLIT`. Sanity: reject any result with pace faster than `140 s/km` or slower than `900 s/km`.

**Persistence:** for each activity and each canonical `D <= totalDistance`, insert/replace one `running_best` row (unique `(activityId, distanceMeters)`). PR per distance = `MIN(timeSec)`.

**Riegel prediction:** `T2 = T1 * (D2 / D1) ^ 1.06`. Source effort = best effort of the last 180 days with `D >= 3000`, preferring the largest `D`.

**Daniels VDOT** (`VdotCalculator`), `v` in m/min, `t` in minutes:
```
percentMax(t) = 0.8 + 0.1894393 * exp(-0.012778 * t) + 0.2989558 * exp(-0.1932605 * t)
vo2(v)        = -4.60 + 0.182258 * v + 0.000104 * v * v
vdot          = vo2(v) / percentMax(t)
```

**Named tests — `RunningBestEngineTest` / `RiegelPredictorTest` / `VdotCalculatorTest`**

| ID | Assertion |
|---|---|
| `pr01_full_activity_exact_5k` | 5000 m in 1200 s → 1200 s, `FULL_ACTIVITY`, not estimated |
| `pr02_full_activity_scaled_within_one_percent` | 5040 m in 1210 s → 1200.4 s, `isEstimated = true` |
| `pr03_full_activity_rejected_beyond_tolerance` | 5300 m → no `FULL_ACTIVITY` row for 5 km |
| `pr04_best_split_finds_fastest_1k_in_10k` | synthetic stream with one fast km → that km's time |
| `pr05_best_split_interpolates_between_samples` | 5 s sampling → result not snapped to sample boundaries |
| `pr06_best_split_marks_estimated_on_sparse_samples` | 30 s sampling → `isEstimated = true` |
| `pr07_implausible_pace_rejected` | 1 km in 100 s → dropped |
| `pr08_treadmill_excluded_by_default` | treadmill run → no rows |
| `pr09_pr_is_minimum_per_distance` | three 5 k efforts → PR = fastest |
| `pr10_riegel_5k_to_10k` | 20:00 (1200 s) 5 k → 10 k = `1200 * 2^1.06` = 2501.9 s ± 1 |
| `pr11_vdot_for_5k_in_20min` | v = 250 m/min, t = 20 → VDOT = 49.8 ± 0.3 |
| `pr12_empty_stream_returns_no_bests` | no crash |

### 3.5 Training-suggestion engine

**Location** `domain/engine/suggest/` — `SuggestionEngine.kt`, `Periodization.kt`, `Constraints.kt`, `SessionCatalog.kt`, `Scorer.kt`.
Deterministic and offline. Same inputs ⇒ byte-identical output (`inputsHash` proves it).

#### 3.5.1 Inputs

```kotlin
data class SuggestionInput(
    val today: LocalDate,
    val horizonDays: Int,                     // default 7
    val profile: Profile,
    val goals: List<Goal>,                    // ACTIVE, sorted by priority
    val events: List<EventOccurrence>,        // expanded, horizon + 3 days
    val lockedPlanned: List<PlannedSession>,  // locked == true
    val recentLoad: List<DailyLoad>,          // last 42 days
    val recovery: RecoveryState?,
    val recentActivities: List<ActivitySummary> // last 14 days
)
```

#### 3.5.2 Periodization (`Periodization.kt`)

Primary goal = `goals.firstOrNull { it.type == RACE_TIME && it.targetDay != null }` (lowest `priority`).
Let `R` = target day, `d` = days from `today` to `R`.

| Condition | Phase |
|---|---|
| `R == null` and a `SOCCER_MATCH` occurs within the next 21 days | `IN_SEASON` |
| `R == null` otherwise | `BASE` |
| `d <= 7` | `RACE_WEEK` |
| `d <= 10` | `TAPER` |
| `d <= 35` | `PEAK` |
| `d <= 77` | `BUILD` |
| else | `BASE` |
| override: `(weeksSincePlanStart % 4) == 3` and phase ∉ {`TAPER`,`RACE_WEEK`} | `RECOVERY_WEEK` |

Weekly load target:
```
ctl = recentLoad.last().ctl
baseWeekly = ctl * 7.0
factor = when (phase) { BASE -> 1.05; BUILD -> 1.10; PEAK -> 1.05; TAPER -> 0.60;
                        RACE_WEEK -> 0.45; IN_SEASON -> 1.00; OFF_SEASON -> 0.80; RECOVERY_WEEK -> 0.65 }
weeklyTarget = baseWeekly * factor
lastWeekActual = Σ dailyTrimp over the previous 7 days
weeklyTarget = min(weeklyTarget, max(lastWeekActual * 1.25, 150.0))       // never ramp > 25 %
if (acwr != null && acwr > 1.5) weeklyTarget *= 0.75
if (recovery?.band == FATIGUED) weeklyTarget *= 0.85
if (recovery?.band == STRAINED) weeklyTarget *= 0.60
weeklyTarget = max(weeklyTarget, 0.0)
```

#### 3.5.3 Hard constraints (`Constraints.kt`) — a candidate violating any is discarded

| ID | Rule |
|---|---|
| `C1` | No `HIGH`/`MAX` intensity session within 48 h **before** a `SOCCER_MATCH` or `RACE`. |
| `C2` | No `STRENGTH_LOWER` / `STRENGTH_FULL` within 48 h before a `SOCCER_MATCH` or `RACE`. |
| `C3` | ≥ 1 fully free day in every rolling 7-day window of the horizon. |
| `C4` | The day **after** a `SOCCER_MATCH`, `RACE`, or an activity with TRIMP ≥ 200: only `RECOVERY_RUN`, `MOBILITY`, or `REST`. |
| `C5` | ≤ 2 suggested `HIGH`/`MAX` sessions per rolling 7 days; a match or race counts as one. |
| `C6` | No suggestion on a day carrying a `BLOCKED` event, and none overlapping a locked planned session. |
| `C7` | ≤ 1 non-`MOBILITY` suggested session per day (a `MOBILITY` session may be added as a second). |
| `C8` | If `recovery.band == FATIGUED` → today may only get `RECOVERY_RUN`/`MOBILITY`; if `STRAINED` → today gets nothing (rest). |
| `C9` | If `acwr > 1.5` → no `HIGH`/`MAX` candidates anywhere in the horizon. |
| `C10` | Per-sport weekly caps from `profile.preferredSportsJson` (fixed calendar sessions count toward the cap). |
| `C11` | Minimum spacing: two `HIGH` run sessions ≥ 72 h apart; two `STRENGTH_LOWER` ≥ 72 h apart; `LONG_RUN` ≥ 5 days apart. |
| `C12` | `LONG_RUN` only on days where `profile.preferredSportsJson.longRunWeekday` matches, or on Sat/Sun when unset. |

#### 3.5.4 Session catalog (`SessionCatalog.kt`)

| SessionType | Sport | Intensity | Default min | RPE | est. TRIMP |
|---|---|---|---|---|---|
| `RECOVERY_RUN` | `RUN_OUTDOOR` | `RECOVERY` | 30 | 3 | 27 |
| `EASY_RUN` | `RUN_OUTDOOR` | `LOW` | 45 | 4 | 54 |
| `LONG_RUN` | `RUN_OUTDOOR` | `MODERATE` | 80 | 5 | 120 |
| `TEMPO_RUN` | `RUN_OUTDOOR` | `HIGH` | 50 | 7 | 105 |
| `INTERVAL_RUN` | `RUN_OUTDOOR` | `HIGH` | 55 | 8 | 132 |
| `STRENGTH_FULL` | `STRENGTH` | `MODERATE` | 55 | 6 | 99 |
| `STRENGTH_UPPER` | `STRENGTH` | `MODERATE` | 45 | 6 | 81 |
| `STRENGTH_LOWER` | `STRENGTH` | `HIGH` | 55 | 7 | 115 |
| `MOBILITY` | `MOBILITY` | `RECOVERY` | 20 | 2 | 12 |
| `CROSS_TRAINING` | `CYCLING` | `LOW` | 60 | 4 | 72 |
| `SOCCER_TRAINING` | `SOCCER_TRAINING` | `MODERATE` | 90 | 6.5 | 176 |
| `SOCCER_MATCH` | `SOCCER_MATCH` | `HIGH` | 90 | 8.5 | 230 |

est. TRIMP = `0.30 * RPE * minutes` (same constant as §3.2.2), so the suggester's budget arithmetic is consistent with the load engine.
Durations are scaled at placement: `actualMin = round(defaultMin * durationScale)` where `durationScale = clamp(remainingBudget / Σ(remaining planned defaults), 0.7, 1.3)`.

#### 3.5.5 Scoring (`Scorer.kt`)

For candidate `c` on day `d`, all terms in `[0,1]`:

| Term | Weight | Definition |
|---|---|---|
| `goalFit` | 0.35 | 1.0 if `c.sport` is the primary goal's sport **and** `c.sessionType` ∈ the phase's preferred types; 0.6 if sport matches only; 0.3 if a secondary goal's sport; 0.1 otherwise |
| `loadFit` | 0.25 | `1.0 - clamp(|remainingBudget - c.estTrimp| / max(remainingBudget, 1.0), 0.0, 1.0)`; 0 if `c.estTrimp > remainingBudget * 1.4` |
| `recoveryFit` | 0.20 | from `recovery.band` × `c.intensity`: matrix below |
| `spacingFit` | 0.10 | `1.0` if the gap to the nearest same-intensity session ≥ ideal (72 h for HIGH, 24 h otherwise), linearly → 0 at half the ideal |
| `prefFit` | 0.10 | `1.0 - (sessionsThisWeekForSport / cap)`; 0 when the cap is reached |

`recoveryFit` matrix (rows = band, cols = intensity `RECOVERY/LOW/MODERATE/HIGH/MAX`):

| | REC | LOW | MOD | HIGH | MAX |
|---|---|---|---|---|---|
| `FRESH` | 0.4 | 0.7 | 0.9 | 1.0 | 1.0 |
| `GOOD` | 0.5 | 0.8 | 1.0 | 0.9 | 0.8 |
| `MODERATE` | 0.7 | 1.0 | 0.8 | 0.5 | 0.3 |
| `FATIGUED` | 1.0 | 0.8 | 0.3 | 0.0 | 0.0 |
| `STRAINED` | 1.0 | 0.3 | 0.0 | 0.0 | 0.0 |
| `null` | 0.6 | 0.9 | 0.9 | 0.7 | 0.5 |

Phase → preferred session types:

| Phase | Preferred |
|---|---|
| `BASE` | `EASY_RUN`, `LONG_RUN`, `STRENGTH_FULL` |
| `BUILD` | `TEMPO_RUN`, `LONG_RUN`, `STRENGTH_LOWER` |
| `PEAK` | `INTERVAL_RUN`, `TEMPO_RUN`, `LONG_RUN` |
| `TAPER` | `EASY_RUN`, short `INTERVAL_RUN` (duration ×0.6) |
| `RACE_WEEK` | `RECOVERY_RUN`, `EASY_RUN`, `MOBILITY` |
| `IN_SEASON` | `EASY_RUN`, `STRENGTH_UPPER`, `MOBILITY` |
| `RECOVERY_WEEK` | `RECOVERY_RUN`, `EASY_RUN`, `MOBILITY` |

`score = 0.35*goalFit + 0.25*loadFit + 0.20*recoveryFit + 0.10*spacingFit + 0.10*prefFit`

#### 3.5.6 Algorithm

1. Build a `DayPlan` grid over `[today, today + horizonDays)`. Seed each day with fixed items: `EventOccurrence`s of type `SOCCER_MATCH`/`SOCCER_TRAINING`/`RACE` (as `SessionCatalog` entries), locked planned sessions, and `BLOCKED` markers.
2. `fixedLoad = Σ estTrimp(fixed items)`; `remainingBudget = max(weeklyTarget - fixedLoad, 0.0)`.
3. Compute `phase` and preferred types.
4. Generate candidates: for every free day × every `SessionType` in the catalog allowed for that day, build a candidate; drop those failing `C1..C12` **against the grid as it currently stands**.
5. Score all candidates; sort by `(score desc, day asc, sessionType.ordinal asc)` — fully deterministic tie-break.
6. Greedy placement loop: take the top candidate; if `score < 0.35` or `remainingBudget < 0.10 * weeklyTarget` stop; place it; subtract `estTrimp`; re-evaluate constraints for all remaining candidates; repeat (max 20 iterations).
7. Post-passes, in order:
   a. If `C3` (rest day) is violated, remove the lowest-scoring placed session in the offending window.
   b. If the day before a match/race carries anything above `LOW`, downgrade it to `EASY_RUN` (or remove it).
   c. If `profile.mobilityOnRestDays`, add a `MOBILITY` session to each fully free day (does not consume budget accounting for `C3`; a day with only `MOBILITY` still counts as a rest day).
8. Build `rationale` per session: ordered list of `{ruleId, text}` from the rules that fired, e.g.
   `PHASE_BUILD` "Build phase: tempo work develops threshold for your 5k goal",
   `BUDGET` "Weekly load target 620 AU; 180 AU still unallocated",
   `C1_RESPECTED` "Kept easy — match in 36 h",
   `RECOVERY_GOOD` "Recovery 72/100, you can absorb a quality session".
9. `inputsHash = sha256` of a canonical serialization of `SuggestionInput` (for `suggestion_batch.inputsHash`).

#### 3.5.7 Named tests — `SuggestionEngineTest` (+ `PeriodizationTest`, `ConstraintsTest`)

| ID | Assertion |
|---|---|
| `sug01_no_hard_session_within_48h_before_match` | match on day +3 → no HIGH candidate on days +1, +2, +3 |
| `sug02_no_lower_body_strength_before_match` | as above for `STRENGTH_LOWER` |
| `sug03_day_after_match_is_recovery_or_rest` | match on day 0 → day +1 ∈ {REST, RECOVERY_RUN, MOBILITY} |
| `sug04_at_least_one_rest_day_per_week` | dense 7-day horizon → ≥ 1 day with no non-mobility session |
| `sug05_max_two_high_sessions_per_week` | count HIGH ≤ 2 |
| `sug06_strained_recovery_yields_rest_today` | band STRAINED → no session on day 0 |
| `sug07_high_acwr_suppresses_high_intensity` | acwr 1.7 → zero HIGH sessions |
| `sug08_blocked_day_gets_nothing` | BLOCKED event → empty day |
| `sug09_taper_reduces_weekly_target_to_60_percent` | `Periodization` unit test |
| `sug10_race_week_phase_detected_at_seven_days` | d = 7 → `RACE_WEEK`; d = 8 → `TAPER` |
| `sug11_recovery_week_every_fourth_week` | weeksSincePlanStart = 3 → `RECOVERY_WEEK` |
| `sug12_weekly_target_never_ramps_more_than_25_percent` | lastWeek 400 → target ≤ 500 |
| `sug13_sport_cap_respected` | cap RUN = 2 → ≤ 2 run suggestions |
| `sug14_deterministic_same_input_same_output` | run twice, assert equal lists and equal `inputsHash` |
| `sug15_every_session_has_non_empty_rationale` | all sessions |
| `sug16_total_estimated_load_within_15_percent_of_target` | when the budget is achievable |
| `sug17_in_season_phase_when_match_within_21_days` | `Periodization` |
| `sug18_mobility_added_to_rest_days_when_enabled` | |
| `sug19_long_run_spacing_at_least_five_days` | 14-day horizon → ≤ 3 long runs, spaced |
| `sug20_empty_goals_still_produces_a_sane_week` | no goals → BASE phase, sessions exist, no crash |

### 3.6 Nutrition label parser

**Location** `domain/engine/label/` — `NutritionLabelParser.kt`, `Lexicon.kt`, `NumberTokenizer.kt`, `LabelValidator.kt`.
Input is **Android-free**:

```kotlin
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerX get() = (left + right) / 2
}
fun parse(lines: List<OcrLine>): LabelParseResult
sealed interface LabelParseResult {
    data class Success(val draft: NutritionFactsDraft, val warnings: List<EngineWarning>) : LabelParseResult
    data class Failed(val reason: EngineWarningCode) : LabelParseResult
}
```

#### 3.6.1 Pipeline

1. **Normalise** each line: NFKD, lowercase, map `ß→ss`, strip diacritics (`ä→a`, `ö→o`, `ü→u`, `é→e`), replace `·•|`→space, collapse whitespace, map unicode minus/en-dash to `-`.
2. **Tokenise numbers** with
   `Regex("""(?<lt>[<≤]\s*)?(?<num>\d{1,4}(?:[.,]\d{1,3})?)\s*(?<unit>kj|kcal|cal|mg|µg|ug|g|ml)?""")`
   Decimal comma → dot. Reject numbers with > 4 integer digits. `lt` present ⇒ `isUpperBound = true`.
   **Thousands guard:** a match like `1.234` followed by `kj` is read as 1234 when the fractional part has exactly 3 digits **and** the unit is `kj`.
3. **Match the nutrient keyword** per line, **longest keyword first** (so `davon gesattigte fettsauren` beats `fett`). Fuzzy: Levenshtein ≤ 1 for keywords ≥ 6 characters (confidence ×0.8).

| Field | German keys | English keys |
|---|---|---|
| `energyKj` | `brennwert`, `energie`, `energiewert` (with a `kj` number) | `energy` (with a `kj` number) |
| `energyKcal` | `brennwert`, `energie`, `kalorien`, `kcal` | `energy`, `calories`, `kcal` |
| `fat` | `fett` | `fat`, `total fat` |
| `satFat` | `davon gesattigte fettsauren`, `gesattigte fettsauren`, `davon gesattigte`, `gesattigte` | `of which saturates`, `saturated fat`, `saturates`, `of which saturated fatty acids` |
| `carbs` | `kohlenhydrate` | `carbohydrate`, `carbohydrates`, `total carbohydrate` |
| `sugar` | `davon zucker`, `zucker` | `of which sugars`, `total sugars`, `sugars`, `sugar` |
| `fiber` | `ballaststoffe` | `fibre`, `fiber`, `dietary fiber`, `dietary fibre` |
| `protein` | `eiweiss`, `eiweiß`, `protein` | `protein` |
| `salt` | `salz` | `salt` |
| `sodium` | `natrium` | `sodium` |

4. **Column detection.** Header line = one containing ≥ 1 of `pro 100 g`, `je 100 g`, `per 100 g`, `/100g`, `100 ml`, `pro 100 ml`, **and optionally** one of `pro portion`, `je portion`, `per serving`, `pro stuck`, `per portion`. Compute each header token's `centerX`. For a nutrient line with *k* numbers, assign number *i* to the header whose `centerX` is nearest to the number's estimated x-position (`left + (right-left) * charOffset / text.length`).
   - No header found → treat the first number on each line as per-100; `basis = PER_100ML` if any line matches `Regex("""\b100\s*ml\b""")` or `getrank|beverage|drink`, else `PER_100G`; warning `COLUMN_AMBIGUOUS` if more than one number was present on ≥ 2 lines.
   - Serving column values are captured into `draft.perServing` and `servingLabel` (text inside the serving header's parentheses, e.g. `(30 g)` → `servingGrams = 30.0`).
5. **Unit normalisation.** `mg → g / 1000`; `µg/ug → g / 1_000_000`; `kJ → kcal = kj / 4.184` (round to whole kcal). If both kJ and kcal exist, keep both and prefer `kcal`. `sodium → salt = sodium * 2.5` if `salt` is absent; `salt → sodium = salt / 2.5` if `sodium` is absent.
6. **Upper bounds.** `< 0,5 g` → `value = 0.5`, `isUpperBound = true` (UI shows "< 0.5").
7. **Validation** (`LabelValidator`) — produces warnings, never failures:

| Check | Warning |
|---|---|
| `satFat > fat + 0.1` | `IMPLAUSIBLE_VALUE` |
| `sugar > carbs + 0.1` | `IMPLAUSIBLE_VALUE` |
| any per-100 field `> 100.0` (except energy) | `IMPLAUSIBLE_VALUE` |
| Atwater: `computed = 4*protein + 4*carbs + 9*fat + 2*fiber`; `|computed - kcal| > max(30.0, 0.25*kcal)` | `ENERGY_MISMATCH` |
| `kcal == null && kj == null` | `LOW_CONFIDENCE` |
| fewer than 3 recognised fields | `LOW_CONFIDENCE` |
| zero recognised fields | ⇒ `Failed(NO_NUTRIENTS_FOUND)` |

8. **Confidence** per field: `1.0` keyword + number on the same line; `0.75` number taken from the following line (keyword line had none); `×0.8` fuzzy keyword; `×0.9` when column assignment was ambiguous; `×0.85` when derived (kJ→kcal, sodium→salt). Final field confidence is clamped to `[0.0, 1.0]`.
9. **Output is always a draft shown in an editable form.** The OCR path must never write an `ingredient` row without user confirmation (brief §2.1).

#### 3.6.2 Fixtures — `app/src/test/resources/fixtures/ocr/`

`de_milch_100ml.txt`, `de_haferflocken_100g.txt`, `de_skyr_two_columns.txt`, `de_kj_only.txt`, `de_lower_bound.txt`, `de_umlaut_ocr_noise.txt`, `en_uk_oats.txt`, `en_us_serving_only.txt`, `en_two_columns.txt`, `garbage_no_nutrients.txt`.
Format: one line per OCR line, `text|left|top|right|bottom`. A test helper `loadOcrFixture(name): List<OcrLine>` parses this.

#### 3.6.3 Named tests — `NutritionLabelParserTest`

| ID | Assertion |
|---|---|
| `ocr01_german_basic_per_100g` | `de_haferflocken_100g` → kcal 370, protein 13.5, carbs 58.7, fat 7.0, fiber 10.0, salt 0.02 |
| `ocr02_german_comma_decimals_parsed` | `13,5` → 13.5 |
| `ocr03_kj_only_converts_to_kcal` | 1560 kJ → 373 kcal |
| `ocr04_of_which_lines_matched_before_parent` | `davon zucker` not matched as `zucker`-only after `kohlenhydrate` |
| `ocr05_saturated_fat_german_long_form` | `davon gesättigte Fettsäuren 1,2 g` → 1.2 |
| `ocr06_two_columns_prefers_per_100g` | `de_skyr_two_columns` → per-100 values, `perServing` populated, `servingGrams = 150` |
| `ocr07_english_uk_labels` | `en_uk_oats` → `of which saturates`, `fibre` recognised |
| `ocr08_english_us_serving_only_sets_basis` | serving-only label → `perServing` filled, warning `COLUMN_AMBIGUOUS` |
| `ocr09_lower_bound_value` | `< 0,5 g` → 0.5 with `isUpperBound` |
| `ocr10_sodium_converted_to_salt` | sodium 0.2 g → salt 0.5 g, confidence ×0.85 |
| `ocr11_per_100ml_detected_for_beverage` | `de_milch_100ml` → `basis = PER_100ML` |
| `ocr12_energy_mismatch_warning` | doctored fixture → `ENERGY_MISMATCH` |
| `ocr13_sugar_greater_than_carbs_warning` | → `IMPLAUSIBLE_VALUE` |
| `ocr14_umlaut_and_noise_tolerated` | `de_umlaut_ocr_noise` (e.g. `EiweiB`, `Kohlenhydrafe`) → ≥ 4 fields found |
| `ocr15_garbage_returns_failed` | → `Failed(NO_NUTRIENTS_FOUND)` |
| `ocr16_thousands_separator_in_kj` | `1.560 kJ` → 1560 |
| `ocr17_confidence_lower_for_next_line_number` | keyword line without a number → confidence 0.75 |

### 3.7 Meal / ingredient math

**Location** `domain/engine/nutrition/MealMath.kt` (pure).

```
gramsOf(item): Double = when (item.unit) {
    G, ML     -> item.quantity
    PIECE     -> item.quantity * (ingredient.pieceGrams ?: error -> warning MISSING, treat as 0)
    SERVING   -> item.quantity * (ingredient.servingGrams ?: ingredient.pieceGrams ?: 100.0)
}
factor(ingredient, grams) = when (ingredient.basis) {
    PER_100G, PER_100ML -> grams / 100.0
    PER_PIECE           -> grams / (ingredient.pieceGrams ?: 100.0)
}
nutrient(item, field) = ingredient[field] * factor
MacroTotals for a meal = Σ over items; for a day = Σ over meals.
```
Rounding: totals are displayed with `kcal` as `Int` (round half-up) and macros to 1 decimal; **stored** unrounded in `meal_log_item`.

Named tests — `MealMathTest`: `meal01_per100g_scaling`, `meal02_piece_based_ingredient`, `meal03_serving_unit_falls_back_to_100g`, `meal04_ml_treated_as_grams_for_per_100ml`, `meal05_totals_sum_across_items`, `meal06_missing_piece_grams_warns_and_contributes_zero`, `meal07_editing_ingredient_does_not_change_past_logs` (repository-level test with fakes).

---

## 4. Screens & navigation

### 4.1 Navigation graph

Type-safe routes with `kotlinx.serialization` (`androidx.navigation:navigation-compose` 2.10.x supports `@Serializable` route objects). All in `ui/nav/Routes.kt`.

```kotlin
@Serializable data object TodayRoute
@Serializable data object CalendarRoute
@Serializable data class DayDetailRoute(val epochDay: Long)
@Serializable data object TrainingRoute                      // plan week view
@Serializable data object SuggestionReviewRoute
@Serializable data class PlannedSessionEditRoute(val id: Long = -1, val epochDay: Long = -1)
@Serializable data object ActivitiesRoute
@Serializable data class ActivityDetailRoute(val id: Long)
@Serializable data object NutritionRoute                     // diary, defaults to today
@Serializable data class NutritionDayRoute(val epochDay: Long)
@Serializable data class AddFoodRoute(val epochDay: Long, val slot: String)
@Serializable data object IngredientsRoute
@Serializable data class IngredientEditRoute(val id: Long = -1, val barcode: String? = null)
@Serializable data object ScanRoute                          // camera: OCR + barcode in one screen
@Serializable data object OcrReviewRoute                     // consumes a shared draft from the VM store
@Serializable data object MealTemplatesRoute
@Serializable data class MealTemplateEditRoute(val id: Long = -1)
@Serializable data class EventEditRoute(val id: Long = -1, val epochDay: Long = -1)
@Serializable data object BodyRoute
@Serializable data object LoadRoute                          // load / recovery detail
@Serializable data object RunningPrsRoute
@Serializable data object GoalsRoute
@Serializable data class GoalEditRoute(val id: Long = -1)
@Serializable data object SettingsRoute
@Serializable data object IntegrationsRoute                  // Health Connect status + permissions
@Serializable data object ImportRoute                        // FIT / CSV / ZIP
@Serializable data object BackupRoute
@Serializable data object OnboardingRoute
@Serializable data object GarminDirectRoute                  // P9 only
@Serializable data object MoreRoute
```

**Bottom navigation (5 items):** `Today` · `Calendar` · `Nutrition` · `Training` · `More`.
`More` is a simple list screen linking to Activities, Body & Health, Running PRs, Load & Recovery, Ingredients, Meal Templates, Goals, Import, Backup, Settings, Integrations.

Start destination: `OnboardingRoute` when `profile` row is missing, else `TodayRoute`.
Back behaviour: bottom-nav destinations use `popUpTo(TodayRoute) { saveState = true }`, `launchSingleTop = true`, `restoreState = true`.

```
TodayRoute ──▶ DayDetailRoute, NutritionDayRoute, LoadRoute, ActivityDetailRoute, SuggestionReviewRoute
CalendarRoute ──▶ DayDetailRoute ──▶ EventEditRoute, ActivityDetailRoute, NutritionDayRoute, PlannedSessionEditRoute
TrainingRoute ──▶ SuggestionReviewRoute, PlannedSessionEditRoute, GoalsRoute, ActivityDetailRoute
NutritionRoute ──▶ AddFoodRoute ──▶ ScanRoute ──▶ OcrReviewRoute ──▶ IngredientEditRoute
                └▶ MealTemplatesRoute ──▶ MealTemplateEditRoute
MoreRoute ──▶ everything else
```

### 4.2 Screen catalogue

| Screen | Purpose | Main state (`…UiState`) | Key actions |
|---|---|---|---|
| **Onboarding** | First-run profile setup (name, sex, birth date, height, weight, goal weight, pace, NEAT level, sport preferences), then Health Connect permission prompt | `step: Int`, draft profile fields, `canContinue` | save profile → request HC permissions → Today |
| **Today (Home)** | The dashboard. Sections: (1) nutrition ring — kcal in/target, macro bars; (2) today's plan / suggested session with rationale; (3) recovery card — score, band, top flag; (4) load card — ACWR, ATL/CTL sparkline; (5) today's activities; (6) next 3 calendar events; (7) weight trend chip | `date`, `target: NutritionTarget?`, `intake: MacroTotals`, `recovery`, `load`, `activities`, `planned`, `events`, `syncStatus` | quick-add meal, log weight, refresh sync, open any section |
| **Calendar** | Month grid + week strip + agenda list. Each day cell: dots for event / planned / activity / meal-complete, and a kcal-delta colour | `mode: MONTH\|WEEK`, `anchorDay`, `days: Map<Long, CalendarDaySummary>` | switch mode, tap day → DayDetail, `+` → EventEdit |
| **Day detail** | Everything on one day: events, planned sessions, activities, meals, target vs intake, sleep, load | `day`, `CalendarDay` aggregate | link activity ↔ event, mark planned done, add meal/event |
| **Event edit** | Create/edit event; type, title, date/time, duration, location, sport, target distance, key-event flag, recurrence (weekly-by-weekday UI), link to activity | draft + `validation` | save, delete, "delete this occurrence" (creates `event_override`) |
| **Training plan** | Current week/next week of the active plan: per-day planned sessions with status; weekly load bar (planned vs target vs actual); phase badge | `plan`, `weeks: List<PlanWeek>`, `phase`, `weeklyTarget`, `weeklyPlanned`, `weeklyActual` | generate suggestions, add session, lock/unlock, mark done/skip |
| **Suggestion review** | Shows the generated week: each `SuggestedSession` as a card with sport, type, duration, intensity, estimated load and the rationale bullets | `batch`, `sessions`, `perSessionAccepted: Map<Long,Boolean>` | accept all / accept one / edit before accept / regenerate |
| **Planned session edit** | Manual session: sport, type, intensity, duration, distance, pace, description, lock | draft | save, delete |
| **Activities** | Reverse-chronological list, filter by sport + date range; each row: sport icon, title, duration, distance, avg HR, TRIMP, source badge(s) | `filter`, `items: LazyPagingList` (simple `Flow<List<ActivitySummary>>`, no Paging lib) | open detail, manual add |
| **Activity detail** | Header stats, HR chart (Canvas), pace/altitude chart, laps table, source badges, linked event, RPE input, notes; "best efforts" list from `running_best` | `activity`, `streams`, `laps`, `bests`, `linkedEvent` | set RPE, link to event, edit title, delete |
| **Nutrition diary** | One day: target header (kcal + macro bars + remaining), meals grouped by slot, water tracker, "why this target" expander showing `explanation` | `day`, `target`, `meals`, `totals`, `water` | add food to slot, copy yesterday, edit/delete item, log water |
| **Add food** | Tabs: Recents · Favorites · Search · Templates · Scan. Quantity + unit entry with live macro preview | `query`, `results`, `selected`, `quantity`, `unit`, `preview` | add to diary, open Scan, create new ingredient |
| **Ingredients** | Searchable list of ingredients with basis + kcal/100; archived filter | `query`, `items` | new, edit, archive, delete |
| **Ingredient edit** | Full nutrition form: name, brand, barcode, basis, piece/serving grams, kcal + 8 nutrients; validation banners from `LabelValidator` | draft + `warnings` | save, scan barcode → OFF prefill, delete |
| **Scan (camera)** | CameraX preview with two modes (`LABEL` / `BARCODE`) toggled by a segmented button. LABEL: capture → ML Kit text → parse → OcrReview. BARCODE: continuous analysis → first EAN → OFF lookup → IngredientEdit prefilled | `mode`, `permissionState`, `isProcessing`, `lastError` | capture, toggle mode, torch |
| **OCR review** | Side-by-side: captured image (with recognised boxes) + editable form pre-filled from `NutritionFactsDraft`, each field showing its confidence; low-confidence fields highlighted | `draft`, `imageUri`, `warnings` | accept → IngredientEdit, retake, cancel |
| **Meal templates** | List of templates with kcal/macros per template | `query`, `items` | new, edit, "log now" (choose slot + date) |
| **Meal template edit** | Name, default slot, item rows (ingredient + qty + unit) with live totals | draft, `totals` | add/remove item, save |
| **Body & Health** | Weight chart (30/90/365 d) with a 7-day moving average and the goal line; body-fat chart; resting HR chart; sleep duration bars; manual weight entry | `range`, `series` | log weight, log body fat |
| **Load & recovery** | ATL/CTL/ACWR chart, TRIMP bars per day, monotony/strain, recovery score history, active flags with explanations | `range`, `series`, `flags` | change range |
| **Running PRs** | Table of PRs per canonical distance (time, pace, date, link to activity, estimated badge) + Riegel predictions + VDOT | `bests`, `predictions`, `vdot` | tap → activity, add manual PR |
| **Goals** | List of goals with progress (e.g. "5k 20:00 by 15 Nov — current best 21:14, on track/behind") | `goals`, `progress` | new, edit, mark achieved |
| **Goal edit** | Type-dependent form (race time: distance + target time + date + link event; body weight: target + date) | draft | save, delete |
| **Import** | Pick a `.fit`, `.csv`, or `.zip` via `ActivityResultContracts.OpenDocument`; shows parse progress and a result summary (parsed / inserted / duplicates / errors); history of `import_record` | `state: Idle\|Running(progress)\|Done(summary)\|Error` | pick file, retry, view log |
| **Integrations** | Health Connect status (available / needs update / not installed), granted permission list, "Grant permissions", "Sync now", last sync time + error, backfill control (request history permission, choose start date) | `sdkStatus`, `granted: Set<String>`, `lastSync`, `isSyncing` | request permissions, sync, backfill, open HC settings |
| **Settings** | Units (metric fixed), week start (Mon fixed), sleep target, include treadmill in PRs, mobility on rest days, sync interval, theme, destructive-migration debug toggle | settings values | edit each |
| **Backup** | Export the whole DB to a JSON file via `CreateDocument`; import from JSON with a merge/replace choice; show counts | `state` | export, import |
| **More** | Navigation hub list | — | — |
| **Garmin direct (P9)** | Email/password + MFA prompt, connection status, which extra metrics were fetched, disconnect | `state` | connect, disconnect, sync now |

### 4.3 UI building blocks (`ui/common/`)

`SectionCard(title, action, content)` · `StatTile(label, value, unit, trend)` · `MacroBar(label, current, target, color)` · `KcalRing(current, target)` · `NumberField(label, value, onValueChange, suffix, decimals)` · `DatePickerField` · `TimePickerField` · `DurationField` · `SportIcon(sportType)` · `SourceBadge(source)` · `EmptyState(icon, title, message, action)` · `ErrorBanner(message, onRetry)` · `ConfidenceUnderline(confidence)` · `WeekStrip(anchorDay, onSelect)` · `RationaleList(items)`.

Charts (`ui/common/charts/`, hand-drawn with Compose `Canvas` — **no chart library**): `LineChartCard(series, xLabels, yFormatter)`, `BarChartCard(...)`. Introduced in P8; before that, screens show tables/lists.

---

## 5. Phased task list

**Legend.** `Model` = which agent should run it. `Size`: S ≈ 1–5 files, M ≈ 6–14 files, L ≈ 15–25 files.
`Deps` = tasks that must be green first. Every task's acceptance criteria include `ALL` (see §0.2) unless stated.
Total: **83 tasks** across 10 phases.

---

### P0 — Bootstrap (prove the build works)

> Goal: an empty Compose app that builds, tests and lints green with the **verified** toolchain from `docs/TOOLCHAIN.md`. **No feature code.**
> Executed as a single task by one Opus agent (amendment A8). The sub-items P0.1–P0.5 below are the checklist.

#### P0.1 — Project skeleton and toolchain proof
- **Model**: `opus` — foundation of everything; must be exact.
- **Size**: L · **Deps**: —
- **Files**: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/myhealth/MainActivity.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`, `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` (+ adaptive icon drawables), `local.properties`, `.gitignore`
- **Do**:
  1. Start from the verified template directory named in the task prompt (it contains a working wrapper for Gradle 8.14.5, `gradle/libs.versions.toml` with the full verified catalog, and the plugin setup). Copy the wrapper files, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, and the catalog **verbatim**, then rename the catalog aliases/keys as you like — but **do not change any version number** (R4).
  2. `local.properties`: `sdk.dir=/home/robert/android-sdk`.
  3. `gradle.properties`:
     ```
     org.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8
     org.gradle.parallel=true
     org.gradle.caching=true
     android.useAndroidX=true
     android.nonTransitiveRClass=true
     kotlin.daemon.jvmargs=-Xmx2g
     kotlin.code.style=official
     ```
  4. Version catalog = `docs/TOOLCHAIN.md` verbatim (Kotlin 2.3.21, KSP 2.3.12, AGP 8.13.2, Compose BOM 2026.06.01, Room 2.8.5, navigation 2.9.8, lifecycle 2.9.4, core-ktx 1.17.0, activity-compose 1.12.4, work 2.11.2, datastore 1.2.1, HC 1.1.0, CameraX 1.6.2, unbundled ML Kit 19.0.1/18.3.1, okhttp 5.1.0, serialization 1.11.0, coroutines 1.11.0, FIT 21.214.0, security-crypto 1.1.0, junit 4.13.2, truth 1.4.5, mockk 1.14.11, kotlinx-coroutines-test 1.11.0). Plugins: `com.android.application`, `org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.plugin.compose`, `org.jetbrains.kotlin.plugin.serialization`, `com.google.devtools.ksp`, `androidx.room`. Declaring every library in the catalog now is fine; only *apply* the dependencies each phase needs.
  5. `app/build.gradle.kts`: `namespace = "com.myhealth"`, `applicationId = "com.myhealth"`, `compileSdk = 36`, `minSdk = 34`, `targetSdk = 36`, `versionCode = 1`, `versionName = "0.1.0"`, `compileOptions { sourceCompatibility/targetCompatibility = JavaVersion.VERSION_17 }`, `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }` (**no** `jvmToolchain`), `buildFeatures { compose = true; buildConfig = true }`, `packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }`, `testOptions.unitTests.isReturnDefaultValues = true`. **Do not add `coreLibraryDesugaring`** (minSdk 34 has `java.time`).
  6. Dependencies in P0: compose BOM + ui/ui-graphics/ui-tooling-preview/material3/material-icons-extended + activity-compose + core-ktx + lifecycle-runtime-compose + lifecycle-viewmodel-compose + navigation-compose + kotlinx-serialization-json + room (runtime/ktx/ksp compiler); debug: ui-tooling, ui-test-manifest; test: junit, truth, mockk, coroutines-test, kotlin-test.
  7. `MainActivity` = `ComponentActivity` with `setContent { MyHealthTheme { MyHealthNavHost() } }` (P0.3).
  8. Manifest: `<application android:name=".MyHealthApp" android:allowBackup="false" android:icon="@mipmap/ic_launcher" android:label="@string/app_name" android:theme="@style/Theme.MyHealth">`; provide an adaptive launcher icon so lint's `MissingApplicationIcon` is clean.
- **If the verified set fails**: stop and report with the exact error (R9). Do not try other versions.
- **Accept**:
  - `./gradlew --version` prints Gradle 8.14.5, JVM 21.
  - `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` → `BUILD SUCCESSFUL`.
  - `ls -l app/build/outputs/apk/debug/app-debug.apk` exists.
  - `./gradlew clean :app:assembleDebug` succeeds from cold.

#### P0.2 — Test infrastructure
- **Files**: `app/src/test/java/com/myhealth/SanityTest.kt`, `app/src/test/java/com/myhealth/testutil/Fixtures.kt`
- **Do**: `androidTestImplementation` junit-ext 1.3.0, espresso-core, compose ui-test-junit4, room-testing (declared now, not run). `SanityTest` asserts Truth works and `java.time.LocalDate.of(2026,9,12).dayOfWeek == SATURDAY`. `Fixtures.kt` holds `fixedClock(iso: String): Clock`.
- **Accept**: `TEST` passes with ≥ 2 executed tests (`app/build/reports/tests/testDebugUnitTest/index.html` exists).

#### P0.3 — App shell: theme, Application, nav scaffold
- **Files**: `MyHealthApp.kt`, `di/AppGraph.kt`, `di/AppGraphHolder.kt`, `di/ViewModelFactories.kt`, `ui/theme/{Color,Type,Theme}.kt`, `ui/nav/Routes.kt`, `ui/nav/MyHealthNavHost.kt`, `ui/nav/BottomBar.kt`, 5 placeholder screens in `ui/{today,calendar,nutrition,training,more}/`, `MainActivity.kt`
- **Do**: Implement `AppGraph` (empty but constructed), `LocalAppGraph`, `rememberVm` (§1.3). `Scaffold` + `NavigationBar` with 5 destinations, `NavHost` with `composable<TodayRoute> { … }` type-safe routes (all routes from §4.1 declared now; unbuilt ones show a "Coming soon" placeholder). Placeholder screens show their name.
- **Accept**: `ALL` passes. `grep -r "staticCompositionLocalOf" app/src/main` finds `LocalAppGraph`.

#### P0.4 — Room bring-up with KSP and schema export
- **Files**: `data/db/MyHealthDatabase.kt`, `data/db/entity/BootstrapEntity.kt`, `data/db/dao/BootstrapDao.kt`, `app/schemas/…` (generated)
- **Do**: `room { schemaDirectory("$projectDir/schemas") }`; `BootstrapEntity(id: Long, label: String)` with a DAO exposing `Flow<List<BootstrapEntity>>`. `MyHealthDatabase` version 1, `exportSchema = true`, companion `build(context)` using `Room.databaseBuilder(...).build()`.
- **Accept**: `BUILD` passes; `ls app/schemas/com.myhealth.data.db.MyHealthDatabase/1.json` exists and contains `"bootstrap"`.

#### P0.5 — Verification script and lint baseline
- **Files**: `tools/verify.sh`, `app/lint.xml`, `docs/STATUS.md`
- **Do**: `tools/verify.sh` exports `JAVA_HOME`/`ANDROID_HOME`, runs `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`, prints APK size (MB) and the executed unit-test count (parsed from `app/build/test-results/testDebugUnitTest/*.xml`), exits non-zero on failure. `lint.xml`: `HardcodedText`, `MissingTranslation`, `GradleDependency`, `NewerVersionAvailable`, `AndroidGradlePluginVersion` → `warning` (versions are pinned on purpose); `NewApi`, `InlinedApi` → `error`; `abortOnError = true`. `docs/STATUS.md` = a checklist table of every task ID (P0.1 … P9.6) with Model, Done, Tests, APK MB columns; P0 rows marked done.
- **Accept**: `bash tools/verify.sh` exits 0 and prints an APK size.

---

### P1 — Core data, profile, settings

#### P1.1 — Domain enums and core models
- **Model**: `sonnet` — transcription from §2.1/§2.3.
- **Size**: M · **Deps**: P0.5
- **Files**: `domain/model/Enums.kt`, `domain/model/Profile.kt`, `domain/model/Body.kt`, `domain/model/Activity.kt`, `domain/model/Health.kt`, `domain/model/Calendar.kt`, `domain/model/Plan.kt`, `domain/model/Nutrition.kt`, `domain/model/Load.kt`, `domain/util/AppError.kt`, `domain/util/Outcome.kt`, `domain/util/DateUtils.kt`, `domain/util/EngineWarning.kt`
- **Do**: every enum in §2.1 with the stated members; `SportType.group`, `NeatLevel.factor`. Domain data classes for `Profile`, `BodyMeasurement`, `ActivitySummary`, `ActivitySession`, `ActivityStreams`, `Lap`, `DailyHealthSummary`, `SleepRecord`, `CalendarEvent`, `EventOccurrence`, `CalendarDay`, `TrainingPlan`, `PlannedSession`, `SuggestedSession`, `Goal`, `Ingredient`, `MealTemplate`, `MealLog`, `MacroTotals` (with `operator fun plus`), `NutritionTarget`, `NutritionFacts`, `NutritionFactsDraft`, `ParsedValue`, `DailyLoad`, `RecoveryState`, `RunningBest`. `DateUtils`: `LocalDate.toEpochDay()` helpers, `Long.toLocalDate()`, `Instant.toLocalDay(zone)`, `isoWeekOf(date)`.
- **Accept**: `ALL` passes. `MacroTotalsTest` asserts `plus` and zero-identity. `grep -rn "android" app/src/main/java/com/myhealth/domain | grep -v "^Binary"` returns nothing.

#### P1.2 — Room entities A (profile, body, activity, health)
- **Model**: `sonnet` — direct transcription of §2.2.1–§2.2.3.
- **Size**: L · **Deps**: P1.1
- **Files**: `data/db/entity/{ProfileEntity,BodyMeasurementEntity,ActivitySessionEntity,ActivitySourceRecordEntity,ActivityStreamEntity,ActivityLapEntity,DailyHealthSummaryEntity,SleepSessionEntity,SyncStateEntity}.kt`, `data/db/converter/Converters.kt`
- **Do**: exact columns, types, nullability, indices and foreign keys from §2.2. `Converters` handles every enum as `TEXT` via `name()` with a safe fallback, plus `List<String>` ⇄ CSV.
- **Accept**: `BUILD` passes (KSP compiles the entities). No `@Entity` without an explicit `tableName`.

#### P1.3 — Room entities B (calendar, plan, nutrition, derived)
- **Model**: `sonnet` — transcription of §2.2.4–§2.2.6.
- **Size**: L · **Deps**: P1.2
- **Files**: `data/db/entity/{CalendarEventEntity,EventOverrideEntity,TrainingPlanEntity,PlannedSessionEntity,SuggestionBatchEntity,SuggestedSessionEntity,GoalEntity,IngredientEntity,MealTemplateEntity,MealTemplateItemEntity,MealLogEntity,MealLogItemEntity,NutritionTargetSnapshotEntity,WaterLogEntity,DailyLoadEntity,RunningBestEntity,ImportRecordEntity}.kt`
- **Accept**: `BUILD` passes.

#### P1.4 — DAOs
- **Model**: `sonnet` — mechanical, but every query is specified.
- **Size**: L · **Deps**: P1.3
- **Files**: `data/db/dao/{ProfileDao,BodyDao,ActivityDao,HealthDao,SleepDao,SyncStateDao,EventDao,PlanDao,GoalDao,SuggestionDao,IngredientDao,MealDao,NutritionDao,LoadDao,RunningBestDao,ImportDao}.kt`
- **Do**: each DAO gets `upsert`, `getById`, `deleteById`, plus the specific queries below. Use `@Upsert` where available; return `Flow<…>` for observed reads and `suspend` for one-shots.
  - `ActivityDao`: `observeRange(fromDay, toDay): Flow<List<ActivitySessionEntity>>`, `getByBuckets(buckets: List<String>)`, `getByDay(day)`, `observeRecent(limit)`, `observeBySportGroup(group, fromDay)`, `sumTrimpPerDay(fromDay, toDay): Flow<List<DayTrimp>>`.
  - `HealthDao`: `observeRange`, `getByDay`, `upsertAll`.
  - `EventDao`: `observeOverlapping(fromDay, toDay)` — must include recurring events whose `startDay <= toDay` and (`recurrenceUntilDay IS NULL OR recurrenceUntilDay >= fromDay`).
  - `IngredientDao`: `search(q)` using `name LIKE '%'||:q||'%' OR brand LIKE …`, `observeRecent(limit)` ordered by `lastUsedAtMillis DESC`, `getByBarcode`.
  - `MealDao`: `observeDay(day)` returning `@Transaction` relation `MealLogWithItems`.
  - `NutritionDao`: `getTarget(day)`, `upsertTarget`, `observeTargets(from, to)`.
  - `LoadDao`: `observeRange(from, to)`, `getLatest()`.
  - `RunningBestDao`: `observeBestPerDistance(): Flow<List<RunningBestEntity>>` via `MIN(timeSec) GROUP BY distanceMeters`.
- **Accept**: `BUILD` passes (Room validates every query at compile time). `grep -c "@Query" app/src/main/java/com/myhealth/data/db/dao/*.kt` ≥ 40.

#### P1.5 — Database, relations, schema v1
- **Model**: `opus` — database assembly + migration policy is architectural.
- **Size**: M · **Deps**: P1.4
- **Files**: `data/db/MyHealthDatabase.kt`, `data/db/relation/{MealLogWithItems,MealTemplateWithItems,ActivityWithStream}.kt`, `data/db/migration/Migrations.kt`, `app/src/androidTest/java/com/myhealth/data/db/MyHealthDatabaseTest.kt`, `app/schemas/**`
- **Do**: remove `BootstrapEntity`; register all entities; `version = 1`; `Migrations.ALL = arrayOf()` with a documented convention. `MyHealthDatabase.build(context)` adds `.addMigrations(*Migrations.ALL)` and **no** destructive fallback (except the debug-flag path from §2.2). `androidTest` covers insert/read for profile + activity + meal (not run in CI).
- **Accept**: `BUILD`; `app/schemas/com.myhealth.data.db.MyHealthDatabase/1.json` contains all 26 tables (`grep -o '"tableName"' … | wc -l` == 26); `1.json` is committed.

#### P1.6 — Mappers entity ⇄ domain
- **Model**: `sonnet` — pure functions, fully specified.
- **Size**: L · **Deps**: P1.5
- **Files**: `data/mapper/{ProfileMappers,BodyMappers,ActivityMappers,HealthMappers,CalendarMappers,PlanMappers,NutritionMappers,LoadMappers}.kt`, `app/src/test/java/com/myhealth/data/mapper/MapperRoundTripTest.kt`
- **Do**: `toDomain()`/`toEntity()` pairs. `ActivityStreamEntity` JSON ⇄ `ActivityStreams` (`IntArray`/`DoubleArray`) via kotlinx-serialization; nulls preserved.
- **Accept**: `TEST` passes; `MapperRoundTripTest` has ≥ 8 round-trip tests asserting equality after `toEntity().toDomain()`.

#### P1.7 — Repository interfaces + profile/body implementations
- **Model**: `opus` — sets the repository contract that 60 later tasks follow.
- **Size**: M · **Deps**: P1.6
- **Files**: `domain/repository/{ProfileRepository,BodyRepository,ActivityRepository,HealthRepository,CalendarRepository,PlanRepository,GoalRepository,NutritionRepository,IngredientRepository,MealRepository,LoadRepository,RunningBestRepository,SettingsRepository,SyncStateRepository,ImportRepository}.kt`, `data/repository/{RoomProfileRepository,RoomBodyRepository}.kt`, `app/src/test/java/com/myhealth/domain/repository/RepositoryContractTest.kt`
- **Do**: define **all** interfaces now (later tasks only add implementations). Reads → `Flow`, writes → `suspend fun … : Outcome<Unit>`. `ProfileRepository.observeProfile(): Flow<Profile?>`, `upsert(profile)`, `BodyRepository.observeLatest(): Flow<BodyMeasurement?>`, `observeRange(from,to)`, `latestWithBodyFat(within: Long)`.
- **Accept**: `ALL` passes; `grep -L "Flow" domain/repository/*.kt` — every interface uses `Flow` or `suspend` only, no blocking getters.

#### P1.8 — DataStore settings
- **Model**: `sonnet` — well-specified boilerplate.
- **Size**: S · **Deps**: P1.7
- **Files**: `data/prefs/SettingsKeys.kt`, `data/prefs/DataStoreSettingsRepository.kt`, `app/build.gradle.kts`
- **Do**: keys `sleepTargetHours`, `includeTreadmillInPrs` (false), `mobilityOnRestDays` (true), `syncIntervalHours` (6), `themeMode` (`SYSTEM`), `allowDestructiveMigration` (false), `suggestionHorizonDays` (7), `offUserAgentContact`. Expose `Flow<AppSettings>` and per-key setters.
- **Accept**: `ALL` passes.

#### P1.9 — AppGraph wiring + ArchitectureTest
- **Model**: `opus` — enforces the layering contract for the rest of the project.
- **Size**: S · **Deps**: P1.8
- **Files**: `di/AppGraph.kt`, `MyHealthApp.kt`, `app/src/test/java/com/myhealth/ArchitectureTest.kt`
- **Do**: `AppGraph` gains `db`, `settings`, `profileRepo`, `bodyRepo`, `clock`, `appScope`. `ArchitectureTest` walks `app/src/main/java/com/myhealth/domain` (resolve the path from `user.dir`), reads every `.kt` file and asserts no line matches `^import (android|androidx|kotlinx\.coroutines\.android|com\.myhealth\.data)\.`; a second test asserts no file under `ui/` imports `com.myhealth.data.` except `ui/nav` (none allowed — the assertion is absolute).
- **Accept**: `TEST` passes; deliberately adding `import android.util.Log` to a domain file makes `ArchitectureTest` fail (verify, then revert).

#### P1.10 — Onboarding screen
- **Model**: `sonnet` — a form.
- **Size**: M · **Deps**: P1.9
- **Files**: `ui/onboarding/{OnboardingScreen,OnboardingViewModel,OnboardingUiState}.kt`, `ui/common/{NumberField,DatePickerField,SectionCard}.kt`, `ui/nav/MyHealthNavHost.kt`, `res/values/strings.xml`
- **Do**: 3 steps (identity → body → preferences). Validation: height 100–250 cm, weight 30–250 kg, pace −1.0…+0.5, birth date ≥ 10 y ago. Saves `Profile` and an initial `BodyMeasurement` (source `MANUAL`). Start destination switches to `TodayRoute` once a profile exists.
- **Accept**: `ALL`; `OnboardingValidationTest` (unit test on a pure `validate(draft)` function) covers 6 invalid cases.

#### P1.11 — Settings screen + Body & Health v1
- **Model**: `sonnet` — forms and lists.
- **Size**: M · **Deps**: P1.10
- **Files**: `ui/settings/{SettingsScreen,SettingsViewModel}.kt`, `ui/body/{BodyScreen,BodyViewModel,BodyUiState}.kt`, `ui/more/MoreScreen.kt`, `ui/nav/MyHealthNavHost.kt`
- **Do**: Settings edits every key from P1.8 plus profile fields. Body screen: latest weight card, "Log weight" dialog, a plain list (no charts yet) of the last 90 days of measurements, goal-weight delta. `MoreScreen` lists all secondary destinations (dead links allowed for not-yet-built screens — they must show an "Coming soon" placeholder, not crash).
- **Accept**: `ALL` passes; APK builds; `grep -c "TODO" app/src/main/java/com/myhealth/ui` reported in the notes.

---

### P2 — Health Connect sync + activities

#### P2.1 — Health Connect availability, manifest and permission plumbing
- **Model**: `opus` — manifest requirements and the permission contract are subtle and device-specific.
- **Size**: M · **Deps**: P1.11
- **Files**: `app/build.gradle.kts`, `AndroidManifest.xml`, `data/healthconnect/HealthConnectProvider.kt`, `data/healthconnect/HcPermissions.kt`, `ui/settings/PermissionsRationaleActivity.kt`, `res/values/strings.xml`
- **Do**:
  - Dependency `androidx.health.connect:connect-client:1.1.0`.
  - Manifest: `<queries><package android:name="com.google.android.apps.healthdata" /></queries>`; the rationale activity with `<action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" />`; the Android-14+ `<activity-alias android:name="ViewPermissionUsageActivity" android:targetActivity=".ui.settings.PermissionsRationaleActivity" android:exported="true" android:permission="android.permission.START_VIEW_PERMISSION_USAGE">` with `android.intent.action.VIEW_PERMISSION_USAGE` + `android.intent.category.HEALTH_PERMISSIONS`.
  - `<uses-permission>` for: `android.permission.health.READ_EXERCISE`, `READ_STEPS`, `READ_DISTANCE`, `READ_SPEED`, `READ_HEART_RATE`, `READ_RESTING_HEART_RATE`, `READ_HEART_RATE_VARIABILITY`, `READ_SLEEP`, `READ_WEIGHT`, `READ_BODY_FAT`, `READ_TOTAL_CALORIES_BURNED`, `READ_ACTIVE_CALORIES_BURNED`, `READ_FLOORS_CLIMBED`, `READ_ELEVATION_GAINED`, `READ_OXYGEN_SATURATION`, `READ_RESPIRATORY_RATE`, `READ_VO2_MAX`, `android.permission.health.READ_HEALTH_DATA_HISTORY`, `android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND`.
  - `HcPermissions.ALL: Set<String>` built with `HealthPermission.getReadPermission(XRecord::class)` for every record type plus the two literal history/background strings.
  - `HealthConnectProvider`: `fun status(): HcStatus` mapping `HealthConnectClient.getSdkStatus(context)` → `AVAILABLE | UPDATE_REQUIRED | UNAVAILABLE`; `fun client(): HealthConnectClient?`; `suspend fun granted(): Set<String>`; `fun permissionContract(): ActivityResultContract<Set<String>, Set<String>>` from `PermissionController.createRequestPermissionResultContract()`.
- **Accept**: `ALL` passes. `grep -c "android.permission.health" app/src/main/AndroidManifest.xml` ≥ 17. Task report lists the exact record-class names used, so a mismatch with the SDK surfaces here (R9).

#### P2.2 — Health Connect readers
- **Model**: `opus` — paging, aggregation and time-range semantics need care.
- **Size**: M · **Deps**: P2.1
- **Files**: `data/healthconnect/HcReader.kt`, `data/healthconnect/HcAggregates.kt`, `data/healthconnect/HcDto.kt`
- **Do**: `suspend fun readExerciseSessions(from: Instant, to: Instant): List<HcExercise>` with full `pageToken` paging (loop while `pageToken` is non-null **and** non-empty); reads `ExerciseSessionRecord` plus, per session window, `HeartRateRecord`, `DistanceRecord`, `SpeedRecord`, `TotalCaloriesBurnedRecord`, `ActiveCaloriesBurnedRecord`, `StepsCadenceRecord`, `ElevationGainedRecord`, and the session's `laps`/`segments`. `readDailySummaries(fromDay, toDay)` uses `aggregate(AggregateRequest(...))` per local day for `StepsRecord.COUNT_TOTAL`, `TotalCaloriesBurnedRecord.ENERGY_TOTAL`, `ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL`, `DistanceRecord.DISTANCE_TOTAL`, `FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL`, plus raw reads for `RestingHeartRateRecord`, `OxygenSaturationRecord`, `RespiratoryRateRecord`, `HeartRateVariabilityRmssdRecord`, `Vo2MaxRecord`. `readSleep`, `readBody`. All wrapped in `runCatchingApp` mapping `SecurityException → HealthConnectPermissionDenied`, `IllegalStateException/RemoteException → HealthConnectUnavailable`, `IOException → Storage`.
- **Accept**: `BUILD` passes (compile is the real check without a device). No `!!` in this file. All reads bounded by an explicit `TimeRangeFilter`.

#### P2.3 — Health Connect → domain mapping
- **Model**: `sonnet` — a table-driven mapping plus tests over DTOs.
- **Size**: M · **Deps**: P2.2
- **Files**: `data/healthconnect/HcMapper.kt`, `data/healthconnect/ExerciseTypeMap.kt`, `data/healthconnect/SleepStageMap.kt`, `app/src/test/java/com/myhealth/data/healthconnect/HcMapperTest.kt`
- **Do**: map `ExerciseSessionRecord.exerciseType` → `SportType`:
  | HC constant | SportType |
  |---|---|
  | `EXERCISE_TYPE_SOCCER` (and `EXERCISE_TYPE_FOOTBALL_*` if present) | `SOCCER_TRAINING` (title containing `match`/`spiel` upgrades to `SOCCER_MATCH` — also upgraded later by event linking) |
  | `EXERCISE_TYPE_RUNNING` | `RUN_OUTDOOR` · `EXERCISE_TYPE_RUNNING_TREADMILL` → `RUN_TREADMILL` |
  | `EXERCISE_TYPE_STRENGTH_TRAINING`, `_WEIGHTLIFTING` if present | `STRENGTH` |
  | `EXERCISE_TYPE_BIKING` / `_BIKING_STATIONARY` | `CYCLING` / `CYCLING_INDOOR` |
  | `EXERCISE_TYPE_WALKING` / `_HIKING` | `WALK` / `HIKE` |
  | `EXERCISE_TYPE_SWIMMING_*` | `SWIM` · `EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING` → `HIIT` |
  | anything else | `OTHER` |
  Sleep stage ints → `SleepStage`. Build `ActivitySourceRecord(source = HEALTH_CONNECT, externalId = metadata.id, payloadJson)`. Streams: HR samples → offsets + bpm arrays, downsampled to ≥ 1 s.
  Mapping lives behind an interface taking plain data classes so tests need no Android.
- **Accept**: `TEST` passes; `HcMapperTest` has ≥ 10 cases including unknown-type fallback, empty HR samples, and a session crossing midnight (`day` = local day of `startAtMillis`).

#### P2.4 — Activity de-duplication and merge engine
- **Model**: `opus` — the correctness core of multi-source ingestion.
- **Size**: M · **Deps**: P2.3
- **Files**: `domain/engine/activity/ActivityMatcher.kt`, `domain/engine/activity/ActivityMerger.kt`, `domain/engine/activity/DedupeKey.kt`, `app/src/test/java/com/myhealth/domain/engine/activity/{ActivityMatcherTest,ActivityMergerTest}.kt`
- **Do**: implement §2.4 exactly — the 4-part match predicate, the 5-minute bucket key, the field-precedence table, `userEditedFieldsCsv` protection, `mergedSourcesCsv` accumulation.
- **Accept**: `TEST` passes with these named tests:
  `dedup01_same_activity_from_hc_and_fit_matches`, `dedup02_start_offset_over_three_minutes_does_not_match`, `dedup03_duration_tolerance_is_five_percent`, `dedup04_distance_mismatch_rejects`, `dedup05_null_distance_on_one_side_still_matches`, `dedup06_different_sport_group_never_matches`, `dedup07_bucket_spans_neighbour_buckets`, `dedup08_merge_prefers_fit_streams_and_hc_calories`, `dedup09_merge_never_overwrites_user_edited_fields`, `dedup10_merge_is_idempotent`.

#### P2.5 — Activity repository and ingestion pipeline
- **Model**: `opus` — transactional ingestion + merge orchestration.
- **Size**: M · **Deps**: P2.4
- **Files**: `data/repository/RoomActivityRepository.kt`, `data/repository/ActivityIngestor.kt`, `data/repository/RoomHealthRepository.kt`, `data/repository/RoomSyncStateRepository.kt`, `di/AppGraph.kt`
- **Do**: `ActivityIngestor.ingest(records: List<ActivitySourceRecord>): IngestResult` — for each record: (1) insert into `activity_source_record` with `OnConflictStrategy.IGNORE` on `(source, externalId)`; if it already existed, still re-merge (data may have been updated); (2) find candidates via `dedupeBucket` ±1 and `ActivityMatcher`; (3) merge and upsert the canonical row inside one `db.withTransaction {}`. Returns counts `inserted/merged/duplicate`.
- **Accept**: `ALL`; `ActivityIngestorTest` with an in-memory fake DAO (no Room) covering: new activity, exact re-ingest (no duplicate), HC-then-FIT merge, FIT-then-HC merge (order-independence assertion).

#### P2.6 — Incremental sync with changes tokens + backfill
- **Model**: `opus` — token lifecycle, expiry recovery, and the 30-day history rule.
- **Size**: M · **Deps**: P2.5
- **Files**: `data/healthconnect/HcSyncService.kt`, `data/healthconnect/HcBackfill.kt`, `domain/repository/SyncStateRepository.kt`
- **Do**:
  - `suspend fun syncIncremental(): Outcome<SyncSummary>`: for each token key (`hc.exercise`, `hc.daily`, `hc.sleep`, `hc.body`): if no token, do a bounded initial read (last 30 days) then `getChangesToken(ChangesTokenRequest(recordTypes))`; else call `getChanges(token)` → `ChangesResponse`; process `changes` (`UpsertionChange` / `DeletionChange`), persist `nextChangesToken`, and repeat while `hasMore == true`. If `changesTokenExpired == true` (or the call throws for an invalid token) → expiry path below.
  - Token expiry: any failure indicating an invalid/expired token ⇒ clear the token, run a 30-day full read, request a fresh token, and record `lastError` — **never** loop.
  - `HcBackfill.run(fromDay)`: requires `READ_HEALTH_DATA_HISTORY`; reads in **14-day windows**, newest → oldest, with a 250 ms delay between windows (rate-limit courtesy), persisting `backfillCompleteDay` after each window so it is resumable.
  - Deletions: `DeletionChange(recordId)` removes the matching `activity_source_record` and re-merges (or deletes) the canonical row if no sources remain.
- **Accept**: `BUILD`; `HcSyncServiceTest` against a fake `HcReader` interface covering: first-run token acquisition, incremental upsert, deletion handling, token-expiry recovery (exactly one full re-read), and resumable backfill.

#### P2.7 — WorkManager sync scheduling
- **Model**: `sonnet` — standard WorkManager boilerplate.
- **Size**: S · **Deps**: P2.6
- **Files**: `sync/HealthSyncWorker.kt`, `sync/SyncScheduler.kt`, `MyHealthApp.kt`, `AndroidManifest.xml`, `app/build.gradle.kts`
- **Do**: `CoroutineWorker` pulling the graph from `applicationContext as MyHealthApp`. `PeriodicWorkRequest` every `settings.syncIntervalHours` (default 6 h), `ExistingPeriodicWorkPolicy.UPDATE`, constraint `BatteryNotLow`, `BackoffPolicy.EXPONENTIAL` 15 min. A `OneTimeWorkRequest` for "Sync now". Returns `Result.retry()` on `HealthConnectUnavailable`, `Result.failure()` on permission denial (with an output data reason).
- **Accept**: `ALL`; `grep -n "ExistingPeriodicWorkPolicy" app/src/main/java/com/myhealth/sync/SyncScheduler.kt`.

#### P2.8 — Integrations screen
- **Model**: `sonnet` — a status screen over an existing API.
- **Size**: M · **Deps**: P2.7
- **Files**: `ui/settings/{IntegrationsScreen,IntegrationsViewModel,IntegrationsUiState}.kt`, `ui/nav/MyHealthNavHost.kt`, `res/values/strings.xml`
- **Do**: shows SDK status with the three distinct empty states (install / update / available), the granted-permission list with per-permission check marks, "Grant permissions" via `rememberLauncherForActivityResult(provider.permissionContract())`, "Sync now", last-sync timestamp and last error, a backfill section with a start-date picker and progress.
- **Accept**: `ALL`; screen has a `@Preview` for each of the three SDK states.

#### P2.9 — Activities list and detail screens
- **Model**: `sonnet` — list/detail UI over existing repositories.
- **Size**: M · **Deps**: P2.8
- **Files**: `ui/activities/{ActivitiesScreen,ActivitiesViewModel,ActivitiesUiState,ActivityDetailScreen,ActivityDetailViewModel}.kt`, `ui/common/{SportIcon,SourceBadge}.kt`, `ui/nav/MyHealthNavHost.kt`
- **Do**: list with sport filter chips + month grouping; detail with header stats, laps table, source badges, notes, and an HR **table** summary (min/avg/max, time-in-zone by 10 % HRR bands) — charts arrive in P8.
- **Accept**: `ALL`; `HrZoneTest` (pure function `timeInZones(samples, hrRest, hrMax): List<Double>`) with 4 cases.

#### P2.10 — Today screen v1
- **Model**: `sonnet` — composition of existing pieces.
- **Size**: M · **Deps**: P2.9
- **Files**: `ui/today/{TodayScreen,TodayViewModel,TodayUiState}.kt`, `ui/common/{StatTile,EmptyState,ErrorBanner}.kt`
- **Do**: sections for today's activities, latest weight, steps/calories from `daily_health_summary`, sync status banner, and placeholders for nutrition/recovery/plan (filled in P4.12 / P5.8 / P6.6).
- **Accept**: `ALL`; `TodayViewModel` exposes a single `StateFlow<TodayUiState>` built with `combine(...)` + `stateIn`.

---

### P3 — Calendar, events, linking

#### P3.1 — Recurrence expander
- **Model**: `opus` — date arithmetic with overrides is a classic source of off-by-one bugs.
- **Size**: S · **Deps**: P2.10
- **Files**: `domain/engine/calendar/RecurrenceExpander.kt`, `domain/engine/calendar/RecurrenceRule.kt`, `app/src/test/java/com/myhealth/domain/engine/calendar/RecurrenceExpanderTest.kt`
- **Do**: parse the RFC5545 subset `FREQ=WEEKLY|DAILY;BYDAY=MO,TU,…;INTERVAL=n;UNTIL=yyyyMMdd;COUNT=n` into `RecurrenceRule`. `expand(event, overrides, from: LocalDate, to: LocalDate): List<EventOccurrence>`; `INTERVAL` counts weeks from the event's ISO week; apply `SKIP` (drop), `MOVE` (change day/time), `EDIT` (change title/duration). Guard: never emit more than 1000 occurrences per call.
- **Accept**: `TEST` with `rec01_single_event_in_range`, `rec02_weekly_two_weekdays`, `rec03_interval_two_weeks`, `rec04_until_boundary_inclusive`, `rec05_count_limit`, `rec06_skip_override_removes_occurrence`, `rec07_move_override_changes_day`, `rec08_edit_override_changes_title`, `rec09_range_outside_series_returns_empty`, `rec10_dst_does_not_shift_local_day`.

#### P3.2 — Calendar repository and day aggregation
- **Model**: `opus` — the `CalendarDay` aggregate joins six sources and is read by three screens.
- **Size**: M · **Deps**: P3.1
- **Files**: `data/repository/RoomCalendarRepository.kt`, `data/repository/RoomPlanRepository.kt`, `data/repository/CalendarAggregator.kt`
- **Do**: `observeRange(from, to): Flow<Map<Long, CalendarDay>>` combining events (expanded), planned sessions, activities, meal-log summaries, nutrition target snapshots and daily load. Use `combine` over the six DAO flows and aggregate on `Dispatchers.Default`.
- **Accept**: `ALL`; `CalendarAggregatorTest` (pure function over lists, no Room) with 5 cases including an empty range and a day holding all six kinds.

#### P3.3 — Event ↔ activity auto-link engine
- **Model**: `opus` — matching heuristics + confidence.
- **Size**: S · **Deps**: P3.2
- **Files**: `domain/engine/calendar/EventActivityLinker.kt`, `app/src/test/java/com/myhealth/domain/engine/calendar/EventActivityLinkerTest.kt`
- **Do**:
  ```
  candidates = activities on the same local day, not already linked
  overlapRatio = overlapMinutes / min(eventDuration, activityDuration)
  sportScore   = 1.0 same SportType · 0.8 same SportGroup · 0.0 otherwise
  startScore   = 1.0 - clamp(|startDelta| / 90min, 0, 1)
  confidence   = 0.5*overlapRatio + 0.3*sportScore + 0.2*startScore
  propose when confidence >= 0.55; auto-apply when confidence >= 0.80 and it is the unique best
  ```
  Event with no time (all-day): use `overlapRatio = 1.0` if the activity falls on that day, and `startScore = 0.5`.
- **Accept**: `TEST`: `link01_exact_overlap_same_sport_auto_links`, `link02_ambiguous_two_candidates_only_proposes`, `link03_wrong_sport_group_not_linked`, `link04_all_day_event_links_by_day`, `link05_already_linked_activity_skipped`, `link06_confidence_threshold_boundaries`.

#### P3.4 — Calendar screen (month / week / agenda)
- **Model**: `opus` — the most complex layout in the app.
- **Size**: M · **Deps**: P3.3
- **Files**: `ui/calendar/{CalendarScreen,CalendarViewModel,CalendarUiState,MonthGrid,WeekStrip,AgendaList,DayCell}.kt`
- **Do**: month grid (ISO weeks, Monday first, 6 rows), each cell showing up to 4 markers (event/planned/activity/meal) plus a thin kcal-delta bar; week mode = `WeekStrip` + agenda; horizontal paging by month via `HorizontalPager`; today highlighted; `anchorDay` survives process death (`SavedStateHandle`).
- **Accept**: `ALL`; `CalendarGridTest` for the pure helper `monthGridDays(anchor: LocalDate): List<LocalDate>` — 42 days, starts on a Monday, contains the 1st and last of the month (5 cases incl. a February in a leap year).

#### P3.5 — Day detail screen
- **Model**: `sonnet` — composition over `CalendarDay`.
- **Size**: M · **Deps**: P3.4
- **Files**: `ui/calendar/{DayDetailScreen,DayDetailViewModel,DayDetailUiState}.kt`
- **Do**: sections Events · Planned · Activities · Meals · Sleep · Targets; per-item overflow menus (link, edit, delete, mark done).
- **Accept**: `ALL`; `@Preview` with a fully populated fixture day.

#### P3.6 — Event editor with recurrence
- **Model**: `sonnet` — form + the recurrence picker (rule types already exist).
- **Size**: M · **Deps**: P3.5
- **Files**: `ui/calendar/{EventEditScreen,EventEditViewModel,RecurrencePicker}.kt`, `ui/common/{TimePickerField,DurationField}.kt`
- **Do**: type, title, date, time (optional), duration, location, sport, target distance (races), key-event switch, recurrence (`None` / `Weekly on [weekday chips]` / `Every n weeks` + until-date). Deleting a recurring event offers "this occurrence" (→ `event_override` SKIP) or "whole series".
- **Accept**: `ALL`; `EventDraftValidationTest` with 5 cases (empty title, duration ≤ 0, until before start, race without distance, weekly rule with no weekday).

#### P3.7 — Linking UI and link suggestions
- **Model**: `sonnet` — UI over `EventActivityLinker`.
- **Size**: S · **Deps**: P3.6
- **Files**: `ui/calendar/LinkActivitySheet.kt`, `ui/activities/ActivityDetailScreen.kt`, `ui/today/TodayScreen.kt`, `data/repository/RoomCalendarRepository.kt`
- **Do**: bottom sheet listing candidates with confidence %, manual search fallback; a "Suggested links" card on Today when ≥ 1 proposal ≥ 0.55 exists; accepting sets `linkedActivityId` + `linkMethod`. Linking a `SOCCER_MATCH` event upgrades the activity's `sportType` to `SOCCER_MATCH`.
- **Accept**: `ALL`.

---

### P4 — Nutrition

#### P4.1 — Meal math and nutrition domain
- **Model**: `sonnet` — formulas fully specified in §3.7.
- **Size**: S · **Deps**: P3.7
- **Files**: `domain/engine/nutrition/MealMath.kt`, `domain/model/Nutrition.kt`, `app/src/test/java/com/myhealth/domain/engine/nutrition/MealMathTest.kt`
- **Accept**: `TEST` with `meal01_per100g_scaling` … `meal06_missing_piece_grams_warns_and_contributes_zero` (§3.7).

#### P4.2 — Ingredient repository and search
- **Model**: `sonnet` — repository over existing DAOs.
- **Size**: S · **Deps**: P4.1
- **Files**: `data/repository/RoomIngredientRepository.kt`, `domain/repository/IngredientRepository.kt`, `di/AppGraph.kt`
- **Do**: search (debounced in the VM, not here), recents (`lastUsedAtMillis DESC`), favorites, `getByBarcode`, `markUsed(id)` bumping `useCount`/`lastUsedAtMillis`, archive instead of delete when referenced by any `meal_log_item`.
- **Accept**: `ALL`; a fake-DAO test asserting `markUsed` increments and archive-vs-delete branching.

#### P4.3 — Ingredients list and editor
- **Model**: `sonnet` — forms and lists.
- **Size**: M · **Deps**: P4.2
- **Files**: `ui/ingredients/{IngredientsScreen,IngredientsViewModel,IngredientEditScreen,IngredientEditViewModel,IngredientDraft}.kt`
- **Do**: editor covers every field in `ingredient`; basis selector switches the unit suffix (per 100 g / 100 ml / piece); inline validation via `LabelValidator`-style checks (sat ≤ fat, sugar ≤ carbs, Atwater mismatch) shown as warnings, not blockers.
- **Accept**: `ALL`; `IngredientDraftTest` with 6 validation cases.

#### P4.4 — Meal templates
- **Model**: `sonnet`.
- **Size**: M · **Deps**: P4.3
- **Files**: `data/repository/RoomMealRepository.kt` (templates part), `ui/meals/{MealTemplatesScreen,MealTemplatesViewModel,MealTemplateEditScreen,MealTemplateEditViewModel}.kt`
- **Do**: list with computed kcal/macros per template; editor with ingredient picker rows, quantity/unit, live totals; "log now" (date + slot) copies items into a `meal_log` with **snapshotted** nutrient values.
- **Accept**: `ALL`; `TemplateTotalsTest` asserting totals equal `MealMath` over the items.

#### P4.5 — Meal log repository and nutrition diary
- **Model**: `opus` — the snapshot-on-log rule and the day aggregate are correctness-critical.
- **Size**: M · **Deps**: P4.4
- **Files**: `data/repository/RoomMealRepository.kt`, `ui/nutrition/{NutritionScreen,NutritionViewModel,NutritionUiState,MealSlotSection}.kt`
- **Do**: `logMeal(day, slot, items)` computes absolute nutrients per item via `MealMath` and stores them denormalised. Diary groups by `MealSlot`, shows per-slot and per-day totals, "copy yesterday", swipe-to-delete, edit quantity in place (recomputes the snapshot).
- **Accept**: `ALL`; test `meal07_editing_ingredient_does_not_change_past_logs` (fake repo) passes.

#### P4.6 — Add-food screen
- **Model**: `sonnet`.
- **Size**: M · **Deps**: P4.5
- **Files**: `ui/nutrition/{AddFoodScreen,AddFoodViewModel,AddFoodUiState,QuantityEditor}.kt`
- **Do**: tabs Recents/Favorites/Search/Templates; search debounced 250 ms; quantity editor with unit dropdown and a live macro preview; quick-add chips for the ingredient's serving and 100 g.
- **Accept**: `ALL`.

#### P4.7 — Nutrition label parser
- **Model**: `opus` — the most intricate pure algorithm in the app (column detection, lexicon, confidence).
- **Size**: M · **Deps**: P4.6
- **Files**: `domain/engine/label/{NutritionLabelParser,Lexicon,NumberTokenizer,LabelValidator,OcrLine}.kt`, `app/src/test/resources/fixtures/ocr/*.txt` (10 fixtures), `app/src/test/java/com/myhealth/domain/engine/label/{NutritionLabelParserTest,NumberTokenizerTest,LabelValidatorTest}.kt`
- **Do**: implement §3.6 exactly, including the normalisation chain, longest-keyword-first matching, Levenshtein ≤ 1 fuzz for keywords ≥ 6 chars, two-column detection by `centerX`, unit conversion, upper-bound handling, validation, and per-field confidence. Author all 10 fixtures with realistic OCR noise (missing umlauts, `l`/`1`, `O`/`0` confusions, merged lines).
- **Accept**: `TEST` with all 17 named cases `ocr01…ocr17` (§3.6.3) plus `NumberTokenizerTest` (comma decimal, thousands in kJ, `<0,5`, unit suffixes, 5-digit rejection) — ≥ 25 tests total, all asserting concrete values.

#### P4.8 — Camera scan screen (CameraX + ML Kit)
- **Model**: `opus` — camera lifecycle, permissions, `@ExperimentalGetImage`, and analyzer back-pressure.
- **Size**: M · **Deps**: P4.7
- **Files**: `app/build.gradle.kts`, `AndroidManifest.xml`, `data/ocr/MlKitTextSource.kt`, `data/ocr/MlKitBarcodeSource.kt`, `data/ocr/OcrLineMapper.kt`, `ui/camera/{ScanScreen,ScanViewModel,ScanUiState,CameraPreview}.kt`, `ui/camera/DraftStore.kt`
- **Do**: deps `androidx.camera:camera-{core,camera2,lifecycle,view}:1.6.2`, **unbundled** `com.google.android.gms:play-services-mlkit-text-recognition:19.0.1`, `com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1` (amendment A3; same `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)` / `BarcodeScanning.getClient()` API); `<uses-permission android:name="android.permission.CAMERA" />` and `<uses-feature android:name="android.hardware.camera" android:required="false" />`. `PreviewView` + `ImageCapture` (LABEL mode, single shot) + `ImageAnalysis` with `STRATEGY_KEEP_ONLY_LATEST` (BARCODE mode, continuous). `OcrLineMapper` converts `Text.Line` → `OcrLine` using `boundingBox`, skipping lines with a null box. `DraftStore` is a tiny singleton in `AppGraph` holding the last `NutritionFactsDraft` + image `Uri` for handoff to OcrReview (avoids putting a large object in a nav argument). Runtime permission handled with `rememberLauncherForActivityResult`; denied → `EmptyState` with a settings deep link.
- **Accept**: `ALL`; `OcrLineMapperTest` (pure, using plain data stand-ins) with 3 cases. `grep -n "ExperimentalGetImage" app/src/main/java/com/myhealth/data/ocr/*.kt` present. APK size delta reported (unbundled ML Kit adds only a thin client; record the number).

#### P4.9 — OCR review screen
- **Model**: `sonnet` — form over an existing draft.
- **Size**: S · **Deps**: P4.8
- **Files**: `ui/camera/{OcrReviewScreen,OcrReviewViewModel}.kt`, `ui/common/ConfidenceUnderline.kt`
- **Do**: every parsed field editable, coloured by confidence (≥ 0.9 green, ≥ 0.7 amber, else red + focus), warnings banner, "Use per-serving column" toggle when both columns were found, Accept → `IngredientEditRoute` prefilled. **Never auto-saves.**
- **Accept**: `ALL`; `@Preview` with a low-confidence draft.

#### P4.10 — Open Food Facts client and barcode flow
- **Model**: `sonnet` — a small HTTP client with a fixed contract.
- **Size**: S · **Deps**: P4.9
- **Files**: `data/off/OffClient.kt`, `data/off/OffDto.kt`, `data/off/OffMapper.kt`, `app/src/test/java/com/myhealth/data/off/OffMapperTest.kt`, `app/src/test/resources/fixtures/off/*.json`, `app/build.gradle.kts`
- **Do**: OkHttp 5 + kotlinx-serialization. `GET https://world.openfoodfacts.org/api/v2/product/{barcode}.json?fields=product_name,brands,quantity,serving_size,image_url,nutriments`. **Required** header `User-Agent: MyHealth/0.1 (personal app; <contact from settings>)`. Timeouts 10 s; retry once on `IOException`. Client-side throttle: max 15 requests/minute (OFF's documented limit for product reads) via a simple token bucket. Map `nutriments` keys `energy-kcal_100g`, `energy-kj_100g`, `fat_100g`, `saturated-fat_100g`, `carbohydrates_100g`, `sugars_100g`, `fiber_100g`, `proteins_100g`, `salt_100g`, `sodium_100g` → `NutritionFactsDraft` with `basis = PER_100G` (or `PER_100ML` when `quantity`/`serving_size` mentions ml). `status != 1` → `Outcome.Err(Network)` with a "product not found" message. Parsing is offline-testable from 3 saved JSON fixtures (a full product, a sparse product, a not-found response).
- **Accept**: `TEST`; `OffMapperTest` with 6 cases including missing nutriments, kJ-only, and not-found. No network call in any test.

#### P4.11 — Nutrition target engine
- **Model**: `opus` — the most decision-dense engine; safety bounds must be exactly right.
- **Size**: M · **Deps**: P4.10
- **Files**: `domain/engine/nutrition/{NutritionTargetEngine,MacroSplitter,MetTable,DayTypeResolver,NutritionDefaults}.kt`, `app/src/test/java/com/myhealth/domain/engine/nutrition/{NutritionTargetEngineTest,MacroSplitterTest,DayTypeResolverTest,MetTableTest}.kt`
- **Do**: implement §3.1 exactly: BMR ladder, MET table, TDEE ladder, calorie clamps, macro ordering, other targets, explanation template, `DayTypeResolver`.
- **Accept**: `TEST` with all 20 named cases `nut01…nut20` plus `DayTypeResolverTest` covering all 8 rows of the resolution table in order, and `MetTableTest` asserting the ACSM run formula at 8/10/14 km/h.

#### P4.12 — Target snapshots, recompute worker, diary integration
- **Model**: `opus` — cache-invalidation policy.
- **Size**: M · **Deps**: P4.11
- **Files**: `data/repository/RoomNutritionRepository.kt`, `sync/TargetRecomputeWorker.kt`, `sync/SyncScheduler.kt`, `ui/nutrition/NutritionScreen.kt`, `ui/today/TodayScreen.kt`
- **Do**: `inputsHash = sha256(profileVersion|weight|goalWeight|pace|neat|dayType|plannedIds|tdeeSource|tdeeValue)`. `ensureTarget(day)` recomputes only when the hash differs. Worker recomputes `[today − 1, today + 7]` daily at 03:00 local and after any profile/weight/plan/event write (one-time work, 30 s debounce via `ExistingWorkPolicy.REPLACE`). Diary header shows target vs intake with remaining kcal/macros and an expandable "Why this target?" showing `explanation`. Today screen gets the real nutrition card.
- **Accept**: `ALL`; `TargetHashTest` asserting the hash is stable across runs and changes when weight changes.

#### P4.13 — Water logging
- **Model**: `sonnet` — trivial.
- **Size**: S · **Deps**: P4.12
- **Files**: `data/repository/RoomNutritionRepository.kt`, `ui/nutrition/WaterCard.kt`
- **Do**: +250 / +500 ml quick buttons, custom amount, day total vs `waterMl` target.
- **Accept**: `ALL`.

---

### P5 — Training load, recovery, running PRs

#### P5.1 — TRIMP calculator and HR bounds
- **Model**: `opus` — numerically exact, and everything downstream depends on it.
- **Size**: S · **Deps**: P4.13
- **Files**: `domain/engine/load/{TrimpCalculator,HrBounds,TrimpDefaults}.kt`, `app/src/test/java/com/myhealth/domain/engine/load/{TrimpCalculatorTest,HrBoundsTest}.kt`
- **Do**: §3.2.1–§3.2.2 exactly, including the 60 s interval cap, null-sample skipping, sex-specific `y`, the RPE ladder and default RPE table.
- **Accept**: `TEST` with `load01…load08` and `load18` from §3.2.4; assert the `load01` value 108.1 ± 0.5 explicitly.

#### P5.2 — Load series engine
- **Model**: `opus` — EWMA seeding, monotony edge cases.
- **Size**: S · **Deps**: P5.1
- **Files**: `domain/engine/load/LoadSeriesEngine.kt`, `app/src/test/java/com/myhealth/domain/engine/load/LoadSeriesEngineTest.kt`
- **Do**: §3.2.3 exactly — EWMA primary + rolling secondary, ACWR zones, monotony/strain with the sd = 0 rule, all five flags.
- **Accept**: `TEST` with `load09…load17`; `load13` asserts monotony 1.3652 ± 0.001 and strain 559.7 ± 0.5.

#### P5.3 — Recovery engine
- **Model**: `opus` — weight renormalisation and missing-component handling.
- **Size**: S · **Deps**: P5.2
- **Files**: `domain/engine/load/RecoveryEngine.kt`, `app/src/test/java/com/myhealth/domain/engine/load/RecoveryEngineTest.kt`
- **Accept**: `TEST` with `rec01…rec10` (§3.3).

#### P5.4 — Running best-effort engine
- **Model**: `opus` — the two-pointer interpolation is easy to get wrong.
- **Size**: M · **Deps**: P5.3
- **Files**: `domain/engine/running/{RunningBestEngine,RiegelPredictor,VdotCalculator,CanonicalDistances}.kt`, `app/src/test/java/com/myhealth/domain/engine/running/{RunningBestEngineTest,RiegelPredictorTest,VdotCalculatorTest}.kt`
- **Accept**: `TEST` with `pr01…pr12` (§3.4).

#### P5.5 — Load repository and recompute worker
- **Model**: `sonnet` — orchestration with a clear spec.
- **Size**: M · **Deps**: P5.4
- **Files**: `data/repository/RoomLoadRepository.kt`, `data/repository/RoomRunningBestRepository.kt`, `sync/LoadRecomputeWorker.kt`, `sync/SyncScheduler.kt`, `di/AppGraph.kt`
- **Do**: after any activity ingest, recompute `activity_session.trimp` for affected activities, then rebuild `daily_load` for `[minAffectedDay − 28, today]` (EWMA needs the prefix) and refresh `running_best` for changed run activities. Idempotent: rerunning produces identical rows.
- **Accept**: `ALL`; `LoadRecomputeTest` with fakes asserting idempotence and that a 400-day history recomputes in < 500 ms.

#### P5.6 — Load & recovery screen
- **Model**: `sonnet`.
- **Size**: M · **Deps**: P5.5
- **Files**: `ui/load/{LoadScreen,LoadViewModel,LoadUiState}.kt`
- **Do**: range selector (28/90/365 d); ATL/CTL/ACWR values, a daily TRIMP list/bar (plain composables until P8), monotony/strain tiles, recovery score with its component breakdown and confidence, active flags each with a one-line explanation.
- **Accept**: `ALL`.

#### P5.7 — Running PRs screen
- **Model**: `sonnet`.
- **Size**: S · **Deps**: P5.6
- **Files**: `ui/running/{RunningPrsScreen,RunningPrsViewModel}.kt`
- **Do**: PR table per canonical distance (time, pace, date, estimated badge, link to activity), Riegel predictions from the best recent effort, VDOT, and a "add manual PR" dialog.
- **Accept**: `ALL`.

#### P5.8 — Today screen v2 (load + recovery)
- **Model**: `sonnet`.
- **Size**: S · **Deps**: P5.7
- **Files**: `ui/today/{TodayScreen,TodayViewModel,TodayUiState}.kt`
- **Do**: replace the recovery/load placeholders with real cards; recovery card shows score, band, confidence and the top flag; load card shows ACWR with its zone colour.
- **Accept**: `ALL`.

#### P5.9 — RPE entry and load recompute trigger
- **Model**: `sonnet` — small.
- **Size**: S · **Deps**: P5.8
- **Files**: `ui/activities/ActivityDetailScreen.kt`, `data/repository/RoomActivityRepository.kt`
- **Do**: 1–10 RPE selector on activity detail; saving adds `rpe` to `userEditedFieldsCsv` and enqueues `LoadRecomputeWorker`.
- **Accept**: `ALL`.

---

### P6 — Training plans and the suggestion engine

#### P6.1 — Goals repository and screens
- **Model**: `sonnet` — CRUD + a small progress calculation.
- **Size**: M · **Deps**: P5.9
- **Files**: `data/repository/RoomGoalRepository.kt`, `domain/engine/goal/GoalProgress.kt`, `ui/goals/{GoalsScreen,GoalsViewModel,GoalEditScreen,GoalEditViewModel}.kt`, `app/src/test/java/com/myhealth/domain/engine/goal/GoalProgressTest.kt`
- **Do**: `GoalProgress.compute(goal, bests, weights, today): Progress(percent, statusText, onTrack: Boolean)`.
  - `RACE_TIME`: current best for that distance → `percent = clamp(targetTime / currentBest, 0, 1)`; `onTrack` if the Riegel-predicted time from the best effort of the last 60 days ≤ `targetTime * 1.02`.
  - `BODY_WEIGHT`: linear progress from the weight when the goal was created to `targetWeightKg`; `onTrack` if the actual rate ≥ 80 % of the rate required to hit `targetDay`.
  - `CONSISTENCY`: sessions/week over the last 4 weeks vs `targetValue`.
  Only one goal may have `priority = 1` (repository enforces, demoting others).
- **Accept**: `TEST` with `goal01_race_time_percent`, `goal02_race_time_on_track_via_riegel`, `goal03_body_weight_progress`, `goal04_body_weight_behind_schedule`, `goal05_consistency_goal`, `goal06_only_one_primary_goal`.

#### P6.2 — Periodization
- **Model**: `opus` — phase boundaries and the ramp guard drive every suggestion.
- **Size**: S · **Deps**: P6.1
- **Files**: `domain/engine/suggest/Periodization.kt`, `app/src/test/java/com/myhealth/domain/engine/suggest/PeriodizationTest.kt`
- **Do**: §3.5.2 exactly — phase table, factors, the 25 % ramp cap, the ACWR and recovery multipliers, and the recovery-week override.
- **Accept**: `TEST` with `sug09`, `sug10`, `sug11`, `sug12`, `sug17` from §3.5.7 plus `per01_no_goal_no_match_is_base`, `per02_phase_boundaries_exact_days`, `per03_strained_recovery_scales_target_to_60_percent`.

#### P6.3 — Constraints and session catalog
- **Model**: `opus` — 12 interacting hard rules.
- **Size**: M · **Deps**: P6.2
- **Files**: `domain/engine/suggest/{Constraints,SessionCatalog,DayPlan}.kt`, `app/src/test/java/com/myhealth/domain/engine/suggest/ConstraintsTest.kt`
- **Do**: `Constraints.violations(candidate, day, grid, ctx): List<ConstraintId>` implementing `C1..C12` from §3.5.3; `SessionCatalog` as the §3.5.4 table with `estTrimp = 0.30 * rpe * minutes` computed, not hard-coded.
- **Accept**: `TEST` with one named test per constraint: `c01_no_high_within_48h_before_match` … `c12_long_run_only_on_allowed_weekday`, plus `cat01_est_trimp_matches_trimp_formula`.

#### P6.4 — Scorer and suggestion engine
- **Model**: `opus` — the greedy placement loop with re-evaluation.
- **Size**: M · **Deps**: P6.3
- **Files**: `domain/engine/suggest/{Scorer,SuggestionEngine,Rationale}.kt`, `app/src/test/java/com/myhealth/domain/engine/suggest/{ScorerTest,SuggestionEngineTest}.kt`
- **Do**: §3.5.5–§3.5.6 exactly — the five scoring terms, the recovery×intensity matrix, the phase-preference table, the 9-step algorithm, deterministic tie-breaks, the 20-iteration cap, the three post-passes, and rationale assembly.
- **Accept**: `TEST` with all 20 named cases `sug01…sug20` (§3.5.7). `sug14` must assert byte-equal output over two runs.

#### P6.5 — Plan repository and suggestion persistence
- **Model**: `sonnet` — CRUD over specified tables.
- **Size**: M · **Deps**: P6.4
- **Files**: `data/repository/RoomPlanRepository.kt`, `data/repository/RoomSuggestionRepository.kt`, `domain/repository/{PlanRepository,SuggestionRepository}.kt`, `di/AppGraph.kt`
- **Do**: `generate(horizonDays)` gathers inputs, calls `SuggestionEngine`, writes a `suggestion_batch` + `suggested_session` rows, marks any previous `PROPOSED` batch `SUPERSEDED`. `accept(sessionIds)` copies them into `planned_session` (status `PLANNED`, `sourceSuggestionId` set). At most one `ACTIVE` `training_plan`; creating a new active plan archives the old one.
- **Accept**: `ALL`; a fake-repo test asserting supersede + accept semantics.

#### P6.6 — Training plan screen
- **Model**: `opus` — the week board is stateful (drag-free but with many per-item actions).
- **Size**: M · **Deps**: P6.5
- **Files**: `ui/training/{TrainingScreen,TrainingViewModel,TrainingUiState,WeekBoard,PlannedSessionCard}.kt`
- **Do**: phase badge + weekly load bar (planned vs target vs actual TRIMP); 7 day columns/rows with planned sessions, fixed calendar events and completed activities; per-session actions (lock, edit, delete, mark done, skip); "Generate suggestions" button; week paging.
- **Accept**: `ALL`; `WeeklyLoadBarTest` for the pure helper computing the three sums.

#### P6.7 — Suggestion review screen
- **Model**: `sonnet`.
- **Size**: S · **Deps**: P6.6
- **Files**: `ui/training/{SuggestionReviewScreen,SuggestionReviewViewModel}.kt`, `ui/common/RationaleList.kt`
- **Do**: one card per suggested session (day, sport, type, intensity chip, duration, estimated load) with the rationale bullets expanded by default; per-card accept/reject toggles; "Accept selected", "Regenerate"; header shows phase, weekly target and total suggested load.
- **Accept**: `ALL`; `@Preview` with 5 suggestions including a rest day.

#### P6.8 — Planned session editor and completion linking
- **Model**: `sonnet`.
- **Size**: S · **Deps**: P6.7
- **Files**: `ui/training/{PlannedSessionEditScreen,PlannedSessionEditViewModel}.kt`, `domain/engine/calendar/EventActivityLinker.kt`
- **Do**: manual planned-session form; auto-complete a planned session when an activity on the same day matches its sport with ≥ 0.7 confidence (reuse `EventActivityLinker` scoring) — sets `status = COMPLETED` and `linkedActivityId`.
- **Accept**: `ALL`; `PlannedAutoCompleteTest` with 4 cases.

---

### P7 — FIT and CSV import

#### P7.1 — FIT SDK spike and decode wrapper
- **Model**: `opus` — third-party Java SDK on Android; coordinate, desugaring and R8 risk all land here.
- **Size**: M · **Deps**: P6.8
- **Files**: `app/build.gradle.kts`, `gradle/libs.versions.toml`, `data/fit/FitFileDecoder.kt`, `data/fit/FitRecords.kt`, `app/proguard-rules.pro`, `docs/STATUS.md`
- **Do**:
  - Dependency coordinate is **`com.garmin:fit:21.214.0`** — note this differs from the brief's `com.garmin.fit:fit`, which returns HTTP 404 on Maven Central. Verified present: `https://repo.maven.apache.org/maven2/com/garmin/fit/maven-metadata.xml` lists 21.195.0 … 21.214.0.
  - Verify on this machine before writing code: `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep -i garmin`.
  - Inspect the jar for `java.time` usage: `unzip -l ~/.gradle/caches/modules-2/files-2.1/com.garmin/fit/21.214.0/*/fit-21.214.0.jar | head -40` and `javap -classpath <jar> com.garmin.fit.DateTime`. If `java.time` is used, that is still fine at minSdk 34 — record the finding. If any API above Java 8 is required, report and stop (R9).
  - `FitFileDecoder.decode(input: InputStream): FitFileData` using `Decode` + `MesgBroadcaster` with listeners for `FileIdMesg`, `SessionMesg`, `LapMesg`, `RecordMesg`. Convert FIT timestamps with `unixSeconds = fitSeconds + 631_065_600` (FIT epoch 1989-12-31T00:00:00Z). Convert semicircle lat/lon with `deg = semicircles * (180.0 / 2^31)`.
  - `FitFileData` is a plain data class (no Garmin types leak past this file) so the mapper is unit-testable.
  - ProGuard: `-keep class com.garmin.fit.** { *; }` (the SDK uses reflection-ish generated profiles).
- **Accept**: `BUILD` passes with the dependency added; the report states the resolved version, whether `java.time` appears in the jar, and the APK size delta.

#### P7.2 — FIT → domain mapper
- **Model**: `opus` — sport mapping plus stream assembly.
- **Size**: M · **Deps**: P7.1
- **Files**: `data/fit/FitToDomainMapper.kt`, `data/fit/FitSportMap.kt`, `app/src/test/java/com/myhealth/data/fit/FitToDomainMapperTest.kt`, `app/src/test/resources/fixtures/fit/*.json`
- **Do**: map `Sport.SOCCER` → `SOCCER_TRAINING`, `Sport.RUNNING` (+ `SubSport.TREADMILL` → `RUN_TREADMILL`, `SubSport.TRAIL` → `RUN_TRAIL`, `SubSport.TRACK` → `RUN_TRACK`), `Sport.TRAINING`+strength sub-sports → `STRENGTH`, `Sport.CYCLING` → `CYCLING`, `Sport.WALKING`/`HIKING`, `Sport.SWIMMING`; unknown → `OTHER`. Build `ActivitySourceRecord(source = FIT_IMPORT, externalId = sha256(fileIdSerialNumber + fileIdTimeCreated + startTime).take(32))`, streams from `RecordMesg`, laps from `LapMesg`.
  Because binary FIT fixtures cannot be authored by hand, tests run against **JSON fixtures shaped like `FitFileData`** (the plain data class), which is exactly what the mapper consumes. Include one fixture per sport plus one with gaps in the record stream.
- **Accept**: `TEST` with `fit01_running_session_maps_to_run_outdoor`, `fit02_treadmill_subsport`, `fit03_soccer_maps_to_soccer_training`, `fit04_external_id_is_stable`, `fit05_record_stream_gaps_preserved_as_nulls`, `fit06_semicircle_conversion`, `fit07_fit_epoch_conversion`, `fit08_unknown_sport_falls_back_to_other`.

#### P7.3 — Garmin activities CSV parser
- **Model**: `sonnet` — a tolerant CSV parser with a fixed spec.
- **Size**: S · **Deps**: P7.2
- **Files**: `data/fit/GarminCsvParser.kt`, `app/src/test/java/com/myhealth/data/fit/GarminCsvParserTest.kt`, `app/src/test/resources/fixtures/csv/*.csv`
- **Do**: Garmin's export CSV has **no official schema and varies by activity type**, so the parser must be header-driven: read the header row, build a `Map<normalizedHeader, columnIndex>`, and read only the columns it recognises (`activity type`, `date`, `title`, `distance`, `calories`, `time`, `avg hr`, `max hr`, `avg speed`, `max speed`, `elev gain`, `aerobic te`). Unknown columns are ignored; missing known columns yield nulls. Handle: quoted fields with commas, `--` as "no value", German decimal commas and thousands dots when the locale column format implies it, durations `h:mm:ss` / `mm:ss`, distance in km, and both `dd.MM.yyyy HH:mm` and `yyyy-MM-dd HH:mm:ss` date formats. `externalId = sha256(rawRowText)`.
- **Accept**: `TEST` with `csv01_standard_english_export`, `csv02_german_locale_decimals`, `csv03_quoted_title_with_comma`, `csv04_missing_columns_tolerated`, `csv05_dash_means_null`, `csv06_duration_formats`, `csv07_unknown_activity_type_is_other`, `csv08_row_hash_is_stable`. Provide 3 CSV fixtures.

#### P7.4 — Archive walker (zip-in-zip)
- **Model**: `sonnet` — file plumbing.
- **Size**: S · **Deps**: P7.3
- **Files**: `data/fit/GarminArchiveWalker.kt`, `app/src/test/java/com/myhealth/data/fit/GarminArchiveWalkerTest.kt`
- **Do**: Garmin's "Export Your Data" ZIP contains a `DI_CONNECT/` tree whose upload folder name **varies by export vintage** — do not hard-code it. Walk every entry recursively: any entry ending `.zip` is opened as a nested `ZipInputStream`; any entry ending `.fit` is yielded as `(name, bytes)`; any `.csv` is yielded separately. Guards: max nesting depth 3, max total uncompressed 500 MB, reject entries whose normalised path escapes the root (zip-slip).
- **Accept**: `TEST` with a programmatically built nested zip: `zip01_finds_fit_in_nested_zip`, `zip02_depth_limit`, `zip03_zip_slip_rejected`, `zip04_size_limit`.

#### P7.5 — Import pipeline
- **Model**: `opus` — ties decode/parse/dedupe/persistence together with progress and provenance.
- **Size**: M · **Deps**: P7.4
- **Files**: `data/repository/ImportService.kt`, `data/repository/RoomImportRepository.kt`, `sync/ImportWorker.kt`, `di/AppGraph.kt`
- **Do**: `import(uri, kind): Flow<ImportProgress>` — hash the file (SHA-256) and short-circuit if an `import_record` with that hash exists (unless `force`); stream-decode; batch into `ActivityIngestor.ingest` in chunks of 50 inside transactions; write an `import_record` with counts and per-item errors; enqueue `LoadRecomputeWorker` at the end. Runs in a `CoroutineWorker` so a large ZIP survives screen rotation. Memory guard: never hold more than 50 decoded activities plus their streams at once.
- **Accept**: `ALL`; `ImportServiceTest` with fakes: duplicate file short-circuit, partial-failure accounting, chunked ingestion, and merge with an existing HC activity.

#### P7.6 — Import screen
- **Model**: `sonnet`.
- **Size**: S · **Deps**: P7.5
- **Files**: `ui/imports/{ImportScreen,ImportViewModel,ImportUiState}.kt`, `AndroidManifest.xml`
- **Do**: `OpenDocument` picker limited to `application/zip`, `text/csv`, `application/octet-stream`, `*/*` fallback; progress with counts; result summary; import history list. Also register a share-sheet `<intent-filter>` on `MainActivity` for `ACTION_SEND` with those MIME types, routed to `ImportRoute`.
- **Accept**: `ALL`.

---

### P8 — Polish

#### P8.1 — String extraction and lint clean-up
- **Model**: `sonnet` — mechanical.
- **Size**: L · **Deps**: P7.6
- **Files**: `res/values/strings.xml`, every `ui/**` file with a hard-coded string, `app/lint.xml`
- **Do**: move all user-facing strings to `strings.xml` with `screen_element` key naming; then flip `HardcodedText` to `error` in `lint.xml`.
- **Accept**: `LINT` passes with `HardcodedText` as an error; `grep -c "<string " app/src/main/res/values/strings.xml` reported.

#### P8.2 — Chart components (Compose Canvas)
- **Model**: `opus` — a small but reusable drawing layer (axes, scaling, nice ticks, touch-free) used by four screens.
- **Size**: M · **Deps**: P8.1
- **Files**: `ui/common/charts/{LineChartCard,BarChartCard,ChartModels,ChartScale}.kt`, `app/src/test/java/com/myhealth/ui/common/charts/ChartScaleTest.kt`
- **Do**: **no chart library** (amendment A2). `ChartScale` (pure Kotlin): `niceTicks(min, max, maxTicks = 5): List<Double>` (1/2/5 × 10^n steps), `fun Double.toY(range, heightPx)`. `LineChartCard(series: List<ChartSeries>, xLabels: List<String>, yFormatter: (Double) -> String, goalLine: Double? = null, bands: List<ChartBand> = emptyList())` draws with `Canvas` + `drawPath`/`drawLine`, M3 colours (`MaterialTheme.colorScheme`), grid lines, right-aligned y labels via `drawText` (`rememberTextMeasurer`), optional horizontal goal line and shaded y-bands (used for ACWR zones). `BarChartCard(values: List<Double>, xLabels, yFormatter, highlightIndex: Int? = null)`. Both handle empty data with `EmptyState`. Both are `@Preview`ed.
- **Accept**: `ALL`; `ChartScaleTest` with 5 cases (`niceTicks` for ranges 0–1, 0–97, −3–3, 1200–1300, single value).

#### P8.3 — Charts on Body, Load and Activity screens
- **Model**: `sonnet` — call-site work only.
- **Size**: M · **Deps**: P8.2
- **Files**: `ui/body/BodyScreen.kt`, `ui/load/LoadScreen.kt`, `ui/activities/ActivityDetailScreen.kt`, `ui/running/RunningPrsScreen.kt`
- **Do**: weight + 7-day moving average + goal line; ATL/CTL lines with ACWR zone shading; daily TRIMP bars; activity HR and pace/altitude lines; PR progression.
- **Accept**: `ALL`; `MovingAverageTest` (pure) with 4 cases.

#### P8.4 — JSON backup export and import
- **Model**: `opus` — data integrity and schema versioning.
- **Size**: M · **Deps**: P8.3
- **Files**: `data/backup/{BackupSerializer,BackupModel,BackupService}.kt`, `ui/settings/{BackupScreen,BackupViewModel}.kt`, `app/src/test/java/com/myhealth/data/backup/BackupSerializerTest.kt`
- **Do**: a single `@Serializable BackupFile(schemaVersion: Int, exportedAtMillis: Long, appVersion: String, tables…)` covering every table; export via `CreateDocument("application/json")` streaming with `kotlinx.serialization` (`prettyPrint = false`); import with two modes — `REPLACE` (wipe then insert inside one transaction) and `MERGE` (upsert by natural key: `(source, externalId)` for activities, `barcode`/`name+brand` for ingredients, `day` for daily rows). Reject a backup whose `schemaVersion` is newer than the app's.
- **Accept**: `TEST`; `BackupSerializerTest` asserts a full round-trip (export → import → identical domain objects) and rejection of a future schema version.

#### P8.5 — Ingredient full-text search
- **Model**: `sonnet` — an additive migration with a clear recipe.
- **Size**: S · **Deps**: P8.4
- **Files**: `data/db/entity/IngredientFtsEntity.kt`, `data/db/dao/IngredientDao.kt`, `data/db/MyHealthDatabase.kt`, `data/db/migration/Migrations.kt`, `app/schemas/**`
- **Do**: `@Fts4(contentEntity = IngredientEntity::class)` over `name`, `brand`; DB version → 2 with an explicit migration creating the FTS table and rebuilding it; search falls back to `LIKE` when the query is < 3 characters.
- **Accept**: `BUILD`; `app/schemas/.../2.json` exists; both `1.json` and `2.json` are committed.

#### P8.6a — Green theme (owner request, 2026-09-12)
- **Model**: `sonnet` — theme tokens only.
- **Files**: `ui/theme/{Color,Theme}.kt`, `data/prefs/SettingsKeys.kt`, `domain/model/Profile.kt` (`AppSettings.useDynamicColor`, default **false**), `ui/settings/SettingsScreen.kt`
- **Do**: Material 3 light and dark schemes built from a green seed (primary ≈ `#2E7D32`/`#81C784` family, secondary teal-green, tertiary lime/olive, surfaces with a faint green tint; error stays red, warnings amber). Dynamic color is applied only when `useDynamicColor` is true. Charts and progress bars use the primary/tertiary greens for positive states. Add a "Use wallpaper colours" switch in Settings.
- **Accept**: `ALL`; previews of Today/Calendar/Nutrition render green in both light and dark.

#### P8.6 — Visual polish
- **Model**: `sonnet`.
- **Size**: M · **Deps**: P8.5
- **Files**: `res/mipmap-*/**`, `ui/theme/*.kt`, `ui/common/EmptyState.kt`, various screens
- **Do**: adaptive launcher icon, consistent empty states on every list screen, loading skeletons, error banners with retry, pull-to-refresh on Today/Activities, consistent 12 dp card radius and 16 dp screen padding.
- **Accept**: `ALL`.

#### P8.7 — Release build configuration
- **Model**: `opus` — R8 + Room + kotlinx-serialization + Garmin FIT keep rules interact.
- **Size**: S · **Deps**: P8.6
- **Files**: `app/build.gradle.kts`, `app/proguard-rules.pro`, `tools/verify.sh`
- **Do**: `release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }`, debug-signing for sideload convenience (`signingConfig = signingConfigs.getByName("debug")` on release, documented as personal-use only). Keep rules for `kotlinx.serialization` generated serializers, Room, ML Kit and `com.garmin.fit.**`. Add `:app:assembleRelease` to `tools/verify.sh`.
- **Accept**: `./gradlew :app:assembleRelease` succeeds; release APK size reported and compared to debug; `bash tools/verify.sh` exits 0.

#### P8.8 — Home-screen widget (optional)
- **Model**: `sonnet` — skip without penalty if time is short.
- **Size**: M · **Deps**: P8.7
- **Files**: `app/build.gradle.kts`, `ui/widget/{TodayWidget,TodayWidgetReceiver}.kt`, `res/xml/today_widget_info.xml`, `AndroidManifest.xml`
- **Do**: Glance (`androidx.glance:glance-appwidget`) widget showing kcal remaining, protein remaining, today's planned session and the recovery band. Updated by a periodic worker.
- **Accept**: `ALL`. **Optional** — if Glance's version does not pair with the locked Compose/Kotlin versions, stop and report rather than bumping anything (R4/R9).

---

### P9 — Optional direct Garmin client (LAST)

> Everything above must be green and the app fully usable before starting P9. The whole tier is behind
> `settings.garminDirectEnabled` (default false) and must degrade silently: if any call fails, the app
> behaves exactly as it does today.

#### P9.1 — Isolation boundary and feature flag
- **Model**: `opus` — the whole point of this phase is containment.
- **Size**: S · **Deps**: P8.7
- **Files**: `domain/repository/GarminMetricsProvider.kt`, `data/garmin/NoopGarminMetricsProvider.kt`, `data/prefs/SettingsKeys.kt`, `di/AppGraph.kt`
- **Do**: `interface GarminMetricsProvider { suspend fun fetchWellness(from: LocalDate, to: LocalDate): Outcome<List<GarminWellnessDay>> ; val isConnected: Flow<Boolean> }` returning Body Battery, stress avg, HRV status, training readiness, VO2max, training status. Default binding is the no-op returning `Ok(emptyList())`. **No other package may reference anything in `data/garmin` except `AppGraph`.**
- **Accept**: `ALL`; extend `ArchitectureTest` with `arch03_only_appgraph_references_data_garmin`.

#### P9.2 — Garmin SSO client (OAuth1 → OAuth2)
- **Model**: `opus` — reverse-engineered, brittle, security-sensitive.
- **Size**: M · **Deps**: P9.1
- **Files**: `data/garmin/sso/{GarminSsoClient,GarminTokens,OAuth1Signer}.kt`, `app/src/test/java/com/myhealth/data/garmin/OAuth1SignerTest.kt`
- **Do**: reimplement the `garth`/`python-garminconnect` flow with OkHttp: fetch the embedded consumer key/secret, POST the SSO login form, follow the ticket, exchange the OAuth1 token for an OAuth2 bearer token, persist both with expiry. All endpoints and the consumer-key URL live in one `GarminEndpoints` object so a future breakage is a one-file fix. Every network failure maps to `AppError.Network` and is swallowed by the caller.
- **Accept**: `TEST`; `OAuth1SignerTest` verifies HMAC-SHA1 signatures against the RFC 5849 §3.4.1 worked example (offline). **No test performs a network call.** `BUILD` passes.

#### P9.3 — MFA flow and encrypted credential storage
- **Model**: `opus` — Keystore + a suspended interactive step.
- **Size**: S · **Deps**: P9.2
- **Files**: `data/garmin/GarminCredentialStore.kt`, `data/garmin/sso/MfaHandler.kt`, `app/build.gradle.kts`
- **Do**: `androidx.security:security-crypto:1.1.0`, `EncryptedSharedPreferences` with `MasterKey` (AES256-GCM). Store email + password + both tokens. `MfaHandler` exposes `suspend fun requestCode(): String?` fulfilled by the UI via a `CompletableDeferred`. Never log credentials; `toString()` on token/credential types must be overridden to redact.
- **Accept**: `ALL`; `grep -rn "password" app/src/main/java/com/myhealth/data/garmin | grep -i "log\|print"` returns nothing; a unit test asserts redacting `toString()`.

#### P9.4 — Wellness endpoints and merge into daily summaries
- **Model**: `opus`.
- **Size**: M · **Deps**: P9.3
- **Files**: `data/garmin/GarminWellnessClient.kt`, `data/garmin/GarminDto.kt`, `data/garmin/GarminMetricsProviderImpl.kt`, `data/repository/RoomHealthRepository.kt`, `app/src/test/resources/fixtures/garmin/*.json`, `app/src/test/java/com/myhealth/data/garmin/GarminMapperTest.kt`
- **Do**: fetch daily Body Battery, stress, HRV status, training readiness, VO2max and training status; map into `daily_health_summary`'s Tier-3-only columns **without overwriting** any Health-Connect-sourced field. Parsing tested from saved JSON fixtures only.
- **Accept**: `TEST` with 5 mapper cases including missing fields and an empty response; `ALL` passes with the flag off.

#### P9.5 — Garmin direct screen
- **Model**: `sonnet`.
- **Size**: S · **Deps**: P9.4
- **Files**: `ui/settings/{GarminDirectScreen,GarminDirectViewModel}.kt`, `ui/nav/MyHealthNavHost.kt`
- **Do**: enable toggle, email/password form, MFA code dialog, connection status, last-sync time and error, "Disconnect" (wipes the credential store), and a prominent note that this is an unofficial integration that may break at any time.
- **Accept**: `ALL`.

#### P9.6 — Recovery engine uses Body Battery / HRV when available
- **Model**: `sonnet` — an additive, already-specified branch.
- **Size**: S · **Deps**: P9.5
- **Files**: `domain/engine/load/RecoveryEngine.kt`, `app/src/test/java/com/myhealth/domain/engine/load/RecoveryEngineTest.kt`
- **Do**: when `bodyBattery` is present for the morning, add a 5th component (weight 15, renormalised as usual): `bbPts = 15.0 * clamp(bodyBattery / 85.0, 0.0, 1.0)`; the HRV component prefers Garmin HRV status over the HC RMSSD value when both exist. **The existing `rec01…rec10` tests must still pass unchanged.**
- **Accept**: `TEST` with `rec11_body_battery_component_added_when_present`, `rec12_weights_renormalise_with_five_components`, `rec13_existing_cases_unaffected_without_garmin_data`.

---

### P10 — Runtime verification on an emulator (lead-owned; required before delivery)

> Owner requirement (2026-09-12): "test all functionalities of the app, UI and backend yourself before you deliver it". Unit tests and a green build are not sufficient. This phase runs after P8 (and again after P9 if P9 is built), and the smoke list in §6.5 is executed on the emulator with seeded data, then on the Pixel when it is attached.

#### P10.1 — Emulator + Health Connect seeding tool
- **Model**: `opus` — debug-only code that writes into Health Connect and a headless emulator harness.
- **Files**: `app/src/debug/AndroidManifest.xml` (WRITE_* health permissions, a `DebugSeedReceiver` exported broadcast receiver), `app/src/debug/java/com/myhealth/debug/{HcDebugSeeder,DebugSeedReceiver}.kt`, `tools/emu.sh` (create AVD `myhealth_api35` from `system-images;android-35;google_apis;x86_64`, boot headless `-no-window -no-audio -gpu swiftshader_indirect`, wait for `sys.boot_completed`, install, grant runtime permissions via `adb shell pm grant`, seed HC via `adb shell am broadcast -a com.myhealth.debug.SEED`), `tools/ui.sh` (helpers: screenshot to `docs/screenshots/<name>.png`, `uiautomator dump`, tap-by-text).
- **Seeder writes** (last 45 days, deterministic seed): 3 runs/week (with HR samples, distance, speed), 1 soccer session/week titled "Spiel" or "Training", 1 strength session/week, daily steps + total/active kcal, resting HR, sleep sessions with stages, weight every 3 days trending down, one HRV/VO2max record. Everything with `Metadata(dataOrigin = app package)` so the reader can filter by origin in tests.
- **Accept**: `bash tools/emu.sh up && bash tools/emu.sh seed` leaves the emulator running with HC data visible in the Integrations screen after "Sync now"; `./gradlew :app:connectedDebugAndroidTest` passes (Room DAO tests + Compose smoke tests).

#### P10.2 — Instrumented test suite
- **Model**: `sonnet`.
- **Files**: `app/src/androidTest/java/com/myhealth/**` — Room DAO/migration tests (already written in P1.5), plus Compose UI tests using `createAndroidComposeRule<MainActivity>()`: onboarding completes and lands on Today; bottom-nav round trip; calendar month/week toggle; event create + delete; ingredient create by hand; template create + log; diary shows totals; settings toggles persist.
- **Accept**: `./gradlew :app:connectedDebugAndroidTest` → all green on the emulator; report the count.

#### P10.3 — Lead walkthrough of §6.5 on the emulator
- **Model**: lead (Fable) drives adb directly.
- **Do**: execute S1–S25 (S10/S11 OCR: use a label photo pushed to the emulator camera via the virtual scene or a gallery-import path; S12 barcode via OFF with network; S18/S19 with a synthetic FIT file produced by the FIT SDK encoder in a unit test), capture a screenshot per screen into `docs/screenshots/`, and tail `logcat` for `AndroidRuntime`/`MyHealth` errors after every step. Any failure becomes a fix task before delivery.
- **Accept**: `docs/VERIFICATION.md` lists every S-step with PASS/FAIL/N-A and the screenshot; zero uncaught exceptions in logcat during the walkthrough.

---

### P11 — Menstrual cycle tracker + cycle-aware training (owner request, 2026-09-13)

> "For female users, add an ovulation cycle tracker (let the user enter when their ovulation starts, use average cycle duration and average period duration to forecast future cycles) and make the training plan adapt to the female cycle." Anchored on the **first day of the period** (observable); ovulation is forecast from it. Enabled when `profile.sex == FEMALE` or `settings.cycleTrackingEnabled` (default = sex == FEMALE; OTHER/MALE can opt in).

#### P11.1 — Cycle model, engine, storage
- **Model**: `domain/model/Cycle.kt`: `CycleEntry(id, periodStartDay: Long, periodEndDay: Long?, note?, createdAtMillis, updatedAtMillis)`; `CyclePhase { MENSTRUAL, FOLLICULAR, OVULATION, LUTEAL }` with `isLateLuteal: Boolean` flag; `CycleStatus(dayOfCycle, phase, isLateLuteal, isPredicted, cycleLengthDays, periodLengthDays, nextPeriodStart, ovulationDay, fertileWindow: ClosedRange<Long>, confidence: CycleConfidence {LOW, MEDIUM, HIGH})`; `CycleForecast(cycles: List<PredictedCycle(periodStart, periodEnd, ovulationDay, fertileWindow)>)`.
- **Engine** `domain/engine/cycle/CycleEngine.kt` (pure): averages from the last ≤ 6 logged cycles — cycle length = mean of intervals between consecutive period starts clamped 21–45 (default 28 with no interval), period length = mean of (end − start + 1) over logged ends clamped 2–10 (default 5). Ovulation day = predicted next period start − 14 (fixed luteal); fertile window = ovulation − 5 … ovulation + 1. `statusFor(date, entries)`: from the latest start ≤ date, `dayOfCycle = date − start + 1`; if `dayOfCycle > cycleLength` the cycle is `isPredicted` and rolls forward (`((date − start) mod cycleLength) + 1`). Phases: MENSTRUAL = days 1..periodLength; FOLLICULAR = periodLength+1 .. ovulationDayIndex − 2; OVULATION = ovulationDayIndex − 1 .. +1 (where `ovulationDayIndex = cycleLength − 14`); LUTEAL = rest; `isLateLuteal` = last 5 days before the predicted next start. Confidence: HIGH ≥ 3 intervals with population SD ≤ 3 days, MEDIUM ≥ 1 interval, LOW = defaults. `forecast(entries, today, cycles = 6)`. No entries → `null` status.
- **Storage**: table `cycle_entry` (unique `periodStartDay`), DB version 3 + `MIGRATION_2_3`, `CycleDao`, `CycleRepository` (observeAll, upsert, delete, observeStatus(today), observeForecast) wired into `AppGraph`; include in backup; `settings.cycleTrackingEnabled`.
- **Tests** (`CycleEngineTest`, exact names): `cyc01_defaults_without_history_28_5`, `cyc02_average_cycle_from_intervals`, `cyc03_clamps_outliers_21_45`, `cyc04_period_length_from_ends`, `cyc05_phase_boundaries_28_day_cycle` (day 1 MENSTRUAL, 5 MENSTRUAL, 6 FOLLICULAR, 13–15 OVULATION, 16 LUTEAL, 24–28 late luteal), `cyc06_predicted_cycle_rolls_forward`, `cyc07_ovulation_is_14_days_before_next_start`, `cyc08_fertile_window_minus5_plus1`, `cyc09_confidence_levels`, `cyc10_forecast_six_cycles`, `cyc11_no_entries_null_status`.

#### P11.2 — Cycle-aware suggestions and nutrition note
- `SuggestionInput.cycleStatusByDay: Map<Long, CycleStatus>` (empty when tracking is off). Rules (rationale ids in brackets), applied to **suggested** sessions only — fixed calendar matches/races are untouched:
  - `[CYCLE_MENSTRUAL_EARLY]` cycle days 1–2: intensity cap MODERATE (no HIGH/MAX candidates); days 3..period end: HIGH allowed, `recoveryFit ×0.85` for HIGH/MAX.
  - `[CYCLE_FOLLICULAR]` follicular: `+0.10` score for HIGH/MAX runs and any STRENGTH_* (strength/interval work is best tolerated here).
  - `[CYCLE_OVULATION]` ovulation window: MAX → capped to HIGH; rationale asks for a thorough warm-up (ligament laxity).
  - `[CYCLE_LATE_LUTEAL]` last 5 days: at most 1 HIGH session in the window, `+0.10` for RECOVERY/LOW and MOBILITY, weekly target ×0.90 for the days in the window; rationale mentions recovery, sleep and hydration.
  - Confidence LOW → rules still apply but the rationale says "based on a default 28-day cycle — log your period to improve this".
- Tests (`SuggestionEngineTest`): `sug21_menstrual_first_two_days_cap_moderate`, `sug22_follicular_prefers_strength_and_intervals`, `sug23_ovulation_caps_max_to_high`, `sug24_late_luteal_limits_high_and_reduces_target`, `sug25_no_cycle_data_unchanged`. Existing `sug01…sug20` unchanged (empty map).
- Nutrition: add a `NutritionTarget.note` line in the explanation for the luteal phase ("luteal phase: appetite and core temperature are typically higher; the target is unchanged, listen to hunger") — no target change (not requested).

#### P11.3 — Screens
- **Cycle screen** (More → "Cycle", visible when tracking is enabled): current status card (phase badge, day N of ~C, next period in X days, ovulation date, confidence), "Log period start" (date, default today) and "Period ended" actions, history list (start, length, period days, delete), forecast list (next 6 cycles). **Today** card: phase + day of cycle + next period; **Calendar**: period days (logged and predicted, distinct tint), ovulation day marker, fertile window dots; **Day detail**: cycle line; **Settings**: "Track menstrual cycle" switch; **Onboarding** step 3: the switch appears for FEMALE (on by default). Suggestion review/Today show the cycle rationale lines like any other rule.
- Tests: pure `CycleUiStateTest` (labels: "Day 12 of ~28", countdown), instrumented `CycleScreenTest` (log a period start → status card shows MENSTRUAL day 1; Calendar shows the marker).

---

## 6. Verification strategy

### 6.1 After every task (the lead runs this)

```bash
cd /home/robert/my_health
export JAVA_HOME=/home/robert/jdk/current
export ANDROID_HOME=/home/robert/android-sdk
bash tools/verify.sh          # assembleDebug + testDebugUnitTest + lintDebug (+ assembleRelease from P8.7)
```

Checklist:

| # | Check | Pass condition |
|---|---|---|
| V1 | Build | `BUILD SUCCESSFUL`, zero warnings of kind `w: ... is deprecated` introduced by this task (report them if unavoidable) |
| V2 | Unit tests | all green; **test count must not decrease** vs. the previous task; the task's named tests are present (`grep -rn "fun <name>(" app/src/test`) |
| V3 | Lint | no new `error`-severity issues; `app/build/reports/lint-results-debug.html` diffed against the previous run |
| V4 | APK size | `app-debug.apk` size recorded in `docs/STATUS.md`; a jump > 5 MB must be explained (expected jumps: P4.8 CameraX + unbundled ML Kit ≈ +3–6 MB, P7.1 FIT SDK ≈ +1–2 MB). Baseline with the whole verified dependency set is ~81 MB unminified debug |
| V5 | Schema | if any entity changed, a new `app/schemas/**/N.json` exists **and** a `Migration` object was added; `1.json` is never edited |
| V6 | Architecture | `ArchitectureTest` green (domain purity, ui↛data, garmin isolation) |
| V7 | Scope | files touched ⊆ the task's declared file list (± the plan's own `docs/STATUS.md`) |
| V8 | Determinism | for engine tasks, run `TEST` twice; results identical |
| V9 | Cold build | at every phase boundary only: `./gradlew clean && bash tools/verify.sh` |

### 6.2 Test-count expectations per phase (floor, not target)

| Phase | Cumulative unit tests (min) |
|---|---|
| P0 | 2 |
| P1 | 20 |
| P2 | 55 |
| P3 | 85 |
| P4 | 160 |
| P5 | 215 |
| P6 | 275 |
| P7 | 305 |
| P8 | 320 |
| P9 | 335 |

If a phase ends below its floor, the lead rejects the phase and orders the missing named cases from §3.

### 6.3 What cannot be verified on this machine

- **No emulator, no system images** ⇒ `androidTest` (Room DAO/migration tests, Compose UI tests) is written but never run in the normal loop. It is run only via `./gradlew :app:connectedDebugAndroidTest` when the Pixel is plugged in.
- **Health Connect**, **CameraX/ML Kit**, **WorkManager scheduling**, **runtime permissions** and the **Garmin SSO flow** cannot be exercised headlessly. Everything around them is designed so that the *logic* is in pure Kotlin behind an interface (`HcReader`, `FitFileData`, `OcrLine`, `GarminMetricsProvider`), and only the thin Android adapter is unverifiable.
- Compile-time is the real gate for those adapters. Any task touching them must report the exact SDK symbol names it used so a mismatch is caught by review, not at runtime.

### 6.4 Migration ledger (append one row per schema change)

| DB version | Task | Change | Migration object |
|---|---|---|---|
| 1 | P1.5 | initial schema (26 tables) | — |
| 2 | P8.5 | `ingredient_fts` FTS4 table over `ingredient(name, brand)` (external content) + Room's four content-sync triggers + a one-off `INSERT INTO ingredient_fts(ingredient_fts) VALUES('rebuild')` so existing rows are indexed | `MIGRATION_1_2` |

### 6.5 Manual smoke test (phone attached)

Run after P2, P4, P6, P7 and before "done". Install with:
```bash
$ANDROID_HOME/platform-tools/adb devices
./gradlew :app:installDebug
$ANDROID_HOME/platform-tools/adb logcat -c && $ANDROID_HOME/platform-tools/adb logcat | grep -i "MyHealth\|AndroidRuntime"
```

| # | Step | Expected |
|---|---|---|
| S1 | Fresh install, launch | Onboarding appears; complete it; app lands on Today without a crash |
| S2 | Integrations → status | Shows "Available" on the Pixel (HC is a platform component on 14+) |
| S3 | Grant permissions | System HC sheet lists all requested types; after granting, the screen shows them as granted |
| S4 | Sync now | Completes; Activities shows Garmin-sourced sessions; each row shows the `HEALTH_CONNECT` badge |
| S5 | Backfill 90 days | Requires the history permission; progresses in 14-day windows; is resumable after backgrounding the app |
| S6 | Activity detail | Duration/distance/HR match Garmin Connect within 1 %; TRIMP is non-null; HR zones sum to the duration |
| S7 | Sleep + resting HR | Body & Health shows last night's sleep and a resting-HR value |
| S8 | Create a recurring soccer training (Tue + Thu) | Calendar shows every occurrence for the next 8 weeks; deleting one occurrence removes only that day |
| S9 | Link an activity to a match event | Suggested link appears with a confidence %; accepting upgrades the activity to `SOCCER_MATCH` |
| S10 | Scan a real German label (e.g. oat flakes) | Text recognised; ≥ 5 fields pre-filled; per-100g column chosen; confidence colours sensible; nothing saved until Accept |
| S11 | Scan a label with a per-serving column | Both columns detected; toggle switches the values |
| S12 | Scan an EAN barcode | OFF lookup fills the ingredient form; a not-found barcode shows a clean error, not a crash |
| S13 | Log a meal from a template | Diary totals update; target header shows remaining kcal/macros |
| S14 | "Why this target?" | Explanation lists BMR, TDEE source, day type, goal and the macro reasoning |
| S15 | Add a match tomorrow, reopen the diary | Day type becomes `PRE_MATCH`; carbs rise, fat falls, deficit disappears |
| S16 | Generate suggestions for the week | 7-day proposal; no hard session in the 48 h before the match; ≥ 1 rest day; each card has a rationale |
| S17 | Accept suggestions | They appear on Training and Calendar as planned sessions |
| S18 | Import a Garmin `.fit` file via the share sheet | Parses; either merges into an existing HC activity (source badges show both) or creates one; no duplicate row |
| S19 | Import the full Garmin export ZIP | Progresses without OOM; the summary reports parsed/inserted/duplicate counts |
| S20 | Running PRs | 5k/10k PRs match Garmin Connect within 1–2 s; best-split values look sane |
| S21 | Airplane mode | Everything except OFF lookup and the Tier-3 client works; no crashes, no spinners stuck |
| S22 | Rotate on every screen | No state loss, no crash |
| S23 | Kill and relaunch during a scan | No crash; the draft is either preserved or cleanly discarded |
| S24 | Battery/idle | Leave overnight; the periodic sync ran (check `sync_state.lastSuccessAtMillis` in the Integrations screen) |
| S25 | Backup export/import | Export to Downloads; wipe app data; import; all data restored |

### 6.6 Performance budgets

| Operation | Budget |
|---|---|
| Cold app start → Today rendered | < 1.5 s on a Pixel 7A |
| Today screen recomposition on data change | < 16 ms per frame (no jank on scroll) |
| `LoadRecomputeWorker` over 400 days | < 500 ms (unit-tested in P5.5) |
| Suggestion generation (7-day horizon) | < 100 ms (assert in `sug14`) |
| Label parse of 40 OCR lines | < 20 ms |
| Import of a 500-activity ZIP | < 2 min, peak heap < 200 MB |
| Debug APK | ≤ 90 MB unminified; release (minified, P8.7) expected well under 40 MB |

---

## 7. Risks and mitigations

| # | Risk | Likelihood | Impact | Mitigation | Owning task |
|---|---|---|---|---|---|
| R1 | **Toolchain pairing fails** (AGP/Gradle/Kotlin/KSP/Compose BOM) | Low (verified) | Blocks everything | The lead built, unit-tested and linted the exact set in `docs/TOOLCHAIN.md` on this machine on 2026-09-12; R4 forbids later agents from bumping versions. | P0.1 |
| R2 | **Health Connect data from Garmin is thinner than assumed** (Body Battery, stress, HRV status, training load, VO2max, Running Tolerance are confirmed *not* shared; resting HR / sleep-stage granularity / respiration / SpO2 are **unconfirmed**) | High | Recovery engine loses inputs | Every recovery component is optional and the weights renormalise (§3.3); `confidence` is surfaced in the UI; `rec02`/`rec03` test the degraded paths. Tier 2 (FIT import) recovers per-activity detail; Tier 3 (P9) recovers the wellness metrics. The app is fully usable with only exercise sessions + steps + calories. | P5.3, P9.4 |
| R3 | **Health Connect record/permission symbols differ from the plan** (`EXERCISE_TYPE_*` and `STAGE_TYPE_*` integer values were not verifiable offline) | Medium | Compile errors in P2 | Only *names* are relied on, never integer literals; the mapping table is one file (`ExerciseTypeMap.kt`) with an `OTHER` fallback; P2.1's acceptance requires the agent to report the exact symbols used, so a mismatch surfaces immediately (R9: stop and report, don't improvise). | P2.1, P2.3 |
| R4 | **Changes-token semantics / expiry** (`getChanges` API shape unverified offline) | Medium | Silent sync stalls or infinite loops | Token handling is isolated in `HcSyncService` with an explicit "expire → one full 30-day read → new token, never loop" rule and a fake-reader test for exactly that path; `sync_state.lastError` is shown in Integrations. | P2.6 |
| R5 | **30-day read-history limit** hides older data | High | Thin initial history | `READ_HEALTH_DATA_HISTORY` is requested up front; backfill runs in resumable 14-day windows; if the permission is refused, the app still works and the UI says history is limited to 30 days. FIT/CSV import (P7) is the full-history escape hatch. | P2.6, P7.5 |
| R6 | **Health Connect rate limits** (exact numbers undocumented) | Medium | Sync errors | Prefer `aggregate()` over raw reads for daily totals; page with tokens; 250 ms delay between backfill windows; exponential WorkManager backoff; failures are recorded, not retried in a tight loop. | P2.2, P2.6, P2.7 |
| R7 | **Garmin FIT SDK coordinate/licensing** — the brief's `com.garmin.fit:fit` is **wrong** (404); the real coordinate is `com.garmin:fit` | Confirmed | Would block P7 | Corrected in this plan; P7.1 verifies resolution, inspects the jar for post-Java-8 APIs, and adds R8 keep rules before any mapper code is written. If the license turns out to forbid this use, Tier 2 degrades to CSV-only (P7.3), which needs no SDK. | P7.1 |
| R8 | **Garmin export formats vary** (CSV has no official schema; the ZIP's upload-folder name changes between export vintages) | High | Import breaks on the owner's real file | The CSV parser is header-driven and ignores unknown columns; the archive walker never hard-codes folder names and recurses into nested zips; unparsable rows are counted as errors in `import_record`, not fatal. | P7.3, P7.4 |
| R9 | **OCR quality on real labels** (glare, curved packaging, umlauts, merged lines) | High | Wrong nutrition values | OCR **never** auto-saves (brief §2.1): the draft always lands in an editable, confidence-coloured form (P4.9); `LabelValidator`'s Atwater cross-check catches most misreads; barcode + Open Food Facts is the parallel path; manual entry is always available. Fixtures deliberately include noisy text (`ocr14`). | P4.7, P4.9 |
| R10 | **Two-column labels mis-assigned** (per-100 vs per-serving) | Medium | Values off by 1.5–3× | Header-based `centerX` column detection with an explicit `COLUMN_AMBIGUOUS` warning, plus a UI toggle to switch columns (P4.9); `ocr06`/`ocr08` cover both shapes. | P4.7 |
| R11 | **ML Kit model availability** — unbundled models are downloaded by Play Services on first use | Low | First scan needs network once | Unbundled artifacts are used (A3; bundled ones measured +70 MB). The scan screen shows a clear "model downloading" state and a retry; models persist afterwards, so the feature is offline from the second use on. | P4.8 |
| R12 | **Nutrition math produces unsafe targets** | Low | Real-world harm | Hard floors: `max(1.2×BMR, 1500/1200 kcal, BMR + 0.5×trainingKcal, TDEE − 1000)`; surplus capped at +700; goal pace clamped to [−1.0, +0.5] kg/week; no deficit on match/race days; every clamp emits a warning shown in "Why this target?". `nut07`/`nut08`/`nut09` lock this down. | P4.11 |
| R13 | **Suggestion engine proposes something injurious** | Low | Injury / missed match | 12 hard constraints evaluated *before* scoring; ACWR > 1.5 and `STRAINED` recovery suppress intensity entirely; weekly load can never ramp > 25 %; every suggestion is a *proposal* the owner accepts explicitly. One named test per constraint (`c01…c12`). | P6.3, P6.4 |
| R14 | **De-duplication produces doubles or eats real activities** | Medium | Corrupt load history | The 4-part predicate is tolerance-based and unit-tested from both arrival orders (`dedup08`–`dedup10`); `(source, externalId)` uniqueness makes ingestion idempotent; raw source rows are never deleted, so a canonical row can always be rebuilt. | P2.4, P2.5 |
| R15 | **Hand-drawn charts look amateurish or misscale** | Medium | Cosmetic | `ChartScale` (nice ticks, padding) is unit-tested; two wrapper composables only, M3 colours; charts are last so nothing blocks on them. | P8.2 |
| R16 | **Tier-3 Garmin client breaks** (Garmin broke this flow in March 2026) | High | Loss of Body Battery/HRV | Entirely optional, behind a default-off flag, last phase, single package, no-op default binding enforced by `ArchitectureTest`; all endpoints in one `GarminEndpoints` object; every engine works without it. | P9.1 |
| R17 | **Credential leakage** (Tier 3 stores a real Garmin password) | Low | Account compromise | `EncryptedSharedPreferences` + Keystore master key; redacting `toString()`; a grep-based acceptance check for logging; no backup of the credential store (`allowBackup=false`, and the backup export excludes it). | P9.3 |
| R18 | **Room migrations break on the owner's real device** | Medium | Data loss | `exportSchema = true` from day 1; destructive fallback forbidden; every version bump ships a `Migration` + a schema JSON + a ledger row (§6.4); the JSON backup (P8.4) is the recovery path — the smoke test S25 exercises it. | P1.5, P8.4 |
| R19 | **Agents drift from the spec** (inventing formulas, adding libraries, refactoring outside scope) | High | Incoherent codebase | §0.1 rules R1–R10; every engine has exact formulas and named tests; acceptance criteria are commands, not adjectives; V7 checks the touched-file set. | all |
| R20 | **No emulator** ⇒ UI/DB regressions go unnoticed | Certain | Runtime crashes on the phone | Keep logic out of composables (all state in ViewModels, all math in `domain/`); every screen has a `@Preview` on a stateless `…Content` (previews compile in `assembleDebug`); DAO queries are validated by Room at compile time; the manual smoke list (§6.5) is run at four checkpoints. | all |
| R21 | **Scope creep beyond v1** (cloud sync, Wear, LLM features) | Medium | Never ships | Brief §2.5 is binding; P8.8 is the only "optional" task and may be dropped; anything else goes in a `docs/BACKLOG.md`, not into a task. | lead |

---

## Appendix A — Task index

| Phase | Tasks | Opus | Sonnet |
|---|---|---|---|
| P0 Bootstrap | P0.1–P0.5 (5, run as one Opus task) | 5 | 0 |
| P1 Core data / profile / settings | P1.1–P1.11 (11) | 3 | 8 |
| P2 Health Connect + activities | P2.1–P2.10 (10) | 5 | 5 |
| P3 Calendar / events / linking | P3.1–P3.7 (7) | 4 | 3 |
| P4 Nutrition | P4.1–P4.13 (13) | 5 | 8 |
| P5 Load / recovery / PRs | P5.1–P5.9 (9) | 4 | 5 |
| P6 Plans + suggestions | P6.1–P6.8 (8) | 4 | 4 |
| P7 FIT / CSV import | P7.1–P7.6 (6) | 3 | 3 |
| P8 Polish | P8.1–P8.8 (8) | 3 | 5 |
| P9 Garmin direct (optional) | P9.1–P9.6 (6) | 4 | 2 |
| P10 Runtime verification | P10.1–P10.3 (3) | 2 | 1 |
| P11 Cycle tracker + cycle-aware training | P11.1–P11.3 (3) | 2 | 1 |
| **Total** | **86** | **42** | **44** |

Critical path: `P0 → P1.5 → P1.7 → P2.4 → P2.5 → P2.6 → P4.11 → P5.1 → P5.2 → P6.4`.
Phases P4 and P5 are largely independent after P3 and can be interleaved if two agents run in parallel; P7 depends only on P2.4/P2.5 (not on P6) and may be pulled forward if the owner wants historical data early.

## Appendix B — Deviations from the brief, and open items

| # | Item | Brief said | Plan says | Why |
|---|---|---|---|---|
| B1 | Garmin FIT SDK coordinate | `com.garmin.fit:fit` | **`com.garmin:fit:21.214.0`** | `https://repo.maven.apache.org/maven2/com/garmin/fit/fit/maven-metadata.xml` → **404**; `.../com/garmin/fit/maven-metadata.xml` → 200 listing 21.195.0…21.214.0. |
| B2 | Kotlin version | "Kotlin 2.3.x", latest 2.3.21 | **2.3.21** (lead: verified building with KSP 2.3.12 + Room 2.8.5) | Superseded by amendment A1. |
| B3 | Gradle wrapper | "8.14.x" | **8.14.5** (lead: verified) | Superseded by amendment A1; the template already contains the wrapper. |
| B4 | Health Connect rationale intent | `androidx.health.connect.action.HEALTH_CONNECT_PERMISSIONS_RATIONALE` | **`androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`** (pre-U activity) + the `VIEW_PERMISSION_USAGE` activity-alias (U+) | Current Google documentation shows the `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` action. Both are declared, so either resolves. |
| B5 | Garmin → HC data list | listed resting HR, sleep stages, respiration, SpO2 as available | treated as **optional/unconfirmed** | Public coverage of the 2025 integration confirms only: calories, cadence, distance, elevation, HR, speed, steps, swim strokes, body fat, floors, sleep, weight shared — and Body Battery / Training Load / Running Tolerance withheld. Stage-level sleep, resting HR, respiration and SpO2 could not be confirmed. All are optional inputs with renormalised weights. |
| B6 | HR samples storage | not specified | JSON arrays in a single `activity_stream` row, not a samples table | Personal-scale data; avoids 10 k-row tables and keeps Room simple. Decoded into primitive arrays in the domain layer. |
| B7 | Activity dedupe | "stable `externalId`" | `externalId` **per source** + a tolerance-based cross-source matcher + a canonical/source-record split | A single `externalId` cannot dedupe across sources (HC ids and FIT ids are unrelated). §2.4 defines both. |
| B8 | Charts | Vico from the start | **No chart library**; Compose Canvas in P8.2 | Lead: Vico 2.5.x and 3.x both require compileSdk 37 (amendment A2). |
| B9 | `compileSdk` | 35, "don't require 36 unless proven" | **36** | Lead installed android-36; several pinned libraries need it (amendment A1). |
| B10 | Desugaring | not mentioned | **not used** | minSdk 34 ⇒ `java.time` is native. P7.1 re-checks this against the FIT jar. |
| B11 | `compose-compiler` version | listed as a separate concern | **not pinned separately** | Since Kotlin 2.0 the Compose compiler ships with Kotlin and is configured via the `org.jetbrains.kotlin.plugin.compose` plugin at the Kotlin version. |

**Open items for the lead to confirm on first contact with the real device/data** (each is already isolated to one file so a fix is cheap):
1. Exact `ExerciseSessionRecord.EXERCISE_TYPE_*` constant names available in connect-client 1.1.0 → `ExerciseTypeMap.kt` (P2.3).
2. Exact `getChangesToken`/`getChanges` signatures and the token-expiry exception type → `HcSyncService.kt` (P2.6).
3. Which fields Garmin actually populates in Health Connect on the owner's account → decides how much of P7 matters (check after S4).
4. Garmin FIT SDK license terms for personal use → P7.1.
5. Open Food Facts' exact required `User-Agent` format → `OffClient.kt` (P4.10); the contact string is a setting, so it is editable without a rebuild.
6. (resolved) charts are hand-drawn; no library API to confirm.

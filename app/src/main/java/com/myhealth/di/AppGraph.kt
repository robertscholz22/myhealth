package com.myhealth.di

import android.app.Application
import com.myhealth.BuildConfig
import androidx.work.WorkManager
import androidx.room.withTransaction
import com.myhealth.data.backup.AndroidBackupContentSource
import com.myhealth.data.backup.BackupService
import com.myhealth.data.db.MyHealthDatabase
import com.myhealth.data.healthconnect.HcBackfill
import com.myhealth.data.healthconnect.HcMapper
import com.myhealth.data.healthconnect.HcReader
import com.myhealth.data.healthconnect.HcSyncService
import com.myhealth.data.healthconnect.HealthConnectMapper
import com.myhealth.data.healthconnect.HealthConnectProvider
import com.myhealth.data.healthconnect.HealthConnectReader
import com.myhealth.data.fit.GarminCsvParser
import com.myhealth.data.ocr.MlKitBarcodeSource
import com.myhealth.data.ocr.MlKitTextSource
import com.myhealth.data.off.OffClient
import com.myhealth.data.off.OffThrottle
import com.myhealth.data.prefs.DataStoreSettingsRepository
import com.myhealth.data.repository.ActivityIngestor
import com.myhealth.data.repository.AndroidImportContentSource
import com.myhealth.data.repository.ImportService
import com.myhealth.data.repository.RoomActivityRepository
import com.myhealth.data.repository.RoomCalendarRepository
import com.myhealth.data.repository.RoomCycleRepository
import com.myhealth.data.repository.RoomBodyRepository
import com.myhealth.data.repository.RoomGoalRepository
import com.myhealth.data.repository.RoomHealthRepository
import com.myhealth.data.repository.LoadRecomputeService
import com.myhealth.data.repository.RoomImportRepository
import com.myhealth.data.repository.RoomIngredientRepository
import com.myhealth.data.repository.RoomLoadRepository
import com.myhealth.data.repository.RoomMealRepository
import com.myhealth.data.repository.RoomNutritionRepository
import com.myhealth.data.repository.RoomPlanRepository
import com.myhealth.data.repository.RoomProfileRepository
import com.myhealth.data.repository.RoomRideBestRepository
import com.myhealth.data.repository.RoomRunningBestRepository
import com.myhealth.data.repository.RoomStrengthRepository
import com.myhealth.data.repository.RoomSuggestionRepository
import com.myhealth.data.repository.RoomSyncStateRepository
import com.myhealth.data.repository.RoomTransactionRunner
import com.myhealth.domain.engine.calendar.EventActivityLinker
import com.myhealth.domain.engine.nutrition.NutritionTargetEngine
import com.myhealth.domain.engine.suggest.SuggestionEngine
import com.myhealth.domain.repository.ActivityImporter
import com.myhealth.domain.repository.ActivityRepository
import com.myhealth.domain.repository.BackupRepository
import com.myhealth.domain.repository.BodyRepository
import com.myhealth.domain.repository.CalendarRepository
import com.myhealth.domain.repository.CycleRepository
import com.myhealth.domain.repository.GoalRepository
import com.myhealth.domain.repository.HealthRepository
import com.myhealth.domain.repository.ImportRepository
import com.myhealth.domain.repository.IngredientRepository
import com.myhealth.domain.repository.LoadRepository
import com.myhealth.domain.repository.MealRepository
import com.myhealth.domain.repository.NutritionRepository
import com.myhealth.domain.repository.PlanRepository
import com.myhealth.domain.repository.ProfileRepository
import com.myhealth.domain.repository.RideBestRepository
import com.myhealth.domain.repository.RunningBestRepository
import com.myhealth.domain.repository.SettingsRepository
import com.myhealth.domain.repository.StrengthRepository
import com.myhealth.domain.repository.SuggestionRepository
import com.myhealth.domain.repository.SyncStateRepository
import com.myhealth.sync.SyncScheduler
import com.myhealth.ui.camera.DraftStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.time.Clock
import java.time.ZoneId

/**
 * The dependency container (§1.3). A plain class with `by lazy` properties — no annotation
 * processors, no reflection. Constructed once in [com.myhealth.MyHealthApp.onCreate].
 *
 * Rules: holds no Activity/Compose references; everything a test needs must be constructible
 * without an `AppGraph`; engines are stateless and take a [Clock] so tests can pin "today".
 */
class AppGraph(private val app: Application) {

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val clock: Clock by lazy { Clock.systemDefaultZone() }

    /** Every "day" boundary in the app is a local day in this zone (§1.6). */
    val zoneId: ZoneId by lazy { clock.zone }

    val db: MyHealthDatabase by lazy { MyHealthDatabase.build(app) }

    val settings: SettingsRepository by lazy { DataStoreSettingsRepository(app) }

    val profileRepo: ProfileRepository by lazy { RoomProfileRepository(db.profileDao()) }

    val bodyRepo: BodyRepository by lazy { RoomBodyRepository(db.bodyDao(), clock) }

    val syncStateRepo: SyncStateRepository by lazy { RoomSyncStateRepository(db.syncStateDao()) }

    val healthRepo: HealthRepository by lazy { RoomHealthRepository(db.healthDao(), db.sleepDao()) }

    /** `ingredient` search/CRUD, archive-vs-delete on removal (§2.2.5, P4.2). */
    val ingredientRepo: IngredientRepository by lazy {
        RoomIngredientRepository(db.ingredientDao(), db.mealDao(), clock)
    }

    /** `meal_log`/`meal_template` CRUD; the snapshot-on-log rule of §2.2.5 lives in its writer
     * (P4.4/P4.5). */
    val mealRepo: MealRepository by lazy {
        RoomMealRepository(db.mealDao(), db.ingredientDao(), clock)
    }

    /**
     * `cycle_entry` + the P11.1 forecast engine. `isTrackingEnabled` ors the settings flag with
     * `profile.sex == FEMALE`, and it is the only gate the suggestion and nutrition repositories
     * consult before they read any cycle data.
     */
    val cycleRepo: CycleRepository by lazy {
        RoomCycleRepository(
            cycleDao = db.cycleDao(),
            profileRepo = profileRepo,
            settingsRepo = settings,
            clock = clock,
        )
    }

    /** The nutrition target engine (§3.1, P4.11) — stateless; only the clock's zone is used. */
    val nutritionTargetEngine: NutritionTargetEngine by lazy { NutritionTargetEngine(clock) }

    /**
     * `nutrition_target_snapshot` + `water_log`. `ensureTarget` gathers its inputs from the
     * profile/body/health/activity/plan/calendar repositories and recomputes only when the
     * `inputsHash` changed (P4.12).
     */
    val nutritionRepo: NutritionRepository by lazy {
        RoomNutritionRepository(
            nutritionDao = db.nutritionDao(),
            profileRepo = profileRepo,
            bodyRepo = bodyRepo,
            healthRepo = healthRepo,
            activityRepo = activityRepo,
            planRepo = planRepo,
            calendarRepo = calendarRepo,
            engine = nutritionTargetEngine,
            clock = clock,
            cycleRepo = cycleRepo,
        )
    }

    /** De-dup + merge ingestion of activities (§2.4, P2.5) — one Room transaction per arrival. */
    val ingestor: ActivityIngestor by lazy {
        ActivityIngestor(db.activityDao(), RoomTransactionRunner(db), clock)
    }

    val activityRepo: ActivityRepository by lazy {
        RoomActivityRepository(db.activityDao(), ingestor, clock)
    }

    val runningBestRepo: RunningBestRepository by lazy { RoomRunningBestRepository(db.runningBestDao()) }

    /** `ride_best` (P12); read by the Bike screen and the FTP estimate. */
    val rideBestRepo: RideBestRepository by lazy { RoomRideBestRepository(db.rideBestDao()) }

    /** The three strength tables (§2.2.7, P14): workouts, their rows and the per-set log. */
    val strengthRepo: StrengthRepository by lazy { RoomStrengthRepository(db.strengthDao()) }

    /**
     * `daily_load` (§2.2.6, P5.5). [RoomLoadRepository.recomputeFrom] is wired to
     * [loadRecomputeService] through a lambda rather than a direct reference, since that service
     * depends on this repository in turn — see [RoomLoadRepository]'s doc for why that would
     * otherwise be a circular `by lazy` initialization.
     */
    val loadRepo: LoadRepository by lazy {
        RoomLoadRepository(db.loadDao()) { day -> loadRecomputeService.recompute(day) }
    }

    /** TRIMP / ATL-CTL-ACWR / recovery / running-PR recompute pipeline (§3.2–§3.4, P5.5). */
    val loadRecomputeService: LoadRecomputeService by lazy {
        LoadRecomputeService(
            activityDao = db.activityDao(),
            activityRepo = activityRepo,
            loadRepo = loadRepo,
            runningBestRepo = runningBestRepo,
            rideBestRepo = rideBestRepo,
            profileRepo = profileRepo,
            healthRepo = healthRepo,
            settingsRepo = settings,
            clock = clock,
            planRepo = planRepo,
        )
    }

    /** `goal`; enforces the single-primary-goal rule of P6.1. */
    val goalRepo: GoalRepository by lazy { RoomGoalRepository(db.goalDao(), clock) }

    /** `training_plan` + `planned_session`; enforces the single-`ACTIVE`-plan rule (P3.2). */
    val planRepo: PlanRepository by lazy { RoomPlanRepository(db.planDao(), clock) }

    /** The `CalendarDay` aggregate of §2.3 plus event/override CRUD and linking (P3.2). */
    val calendarRepo: CalendarRepository by lazy {
        RoomCalendarRepository(
            eventDao = db.eventDao(),
            planDao = db.planDao(),
            activityDao = db.activityDao(),
            mealDao = db.mealDao(),
            nutritionDao = db.nutritionDao(),
            loadDao = db.loadDao(),
            sleepDao = db.sleepDao(),
            activityRepo = activityRepo,
            clock = clock,
            onPlanChanged = { suggestionRepo.markProposedStale() },
            zone = zoneId,
        )
    }

    /** Event ↔ activity auto-link engine (P3.3) — stateless, the zone comes from the caller. */
    val linker: EventActivityLinker by lazy { EventActivityLinker }

    /** The training-suggestion engine (§3.5, P6.4) — the clock only stamps the batch. */
    val suggestionEngine: SuggestionEngine by lazy { SuggestionEngine(clock) }

    /**
     * `suggestion_batch` + `suggested_session` (P6.5): gathers the §3.5.1 inputs, runs
     * [suggestionEngine], and copies accepted suggestions into `planned_session`. Accepting
     * changes a day's nutrition target, hence the [SyncScheduler.requestTargetRecompute] hook.
     */
    val suggestionRepo: SuggestionRepository by lazy {
        RoomSuggestionRepository(
            suggestionDao = db.suggestionDao(),
            planRepo = planRepo,
            goalRepo = goalRepo,
            profileRepo = profileRepo,
            calendarRepo = calendarRepo,
            loadRepo = loadRepo,
            activityRepo = activityRepo,
            settingsRepo = settings,
            engine = suggestionEngine,
            clock = clock,
            cycleRepo = cycleRepo,
            onPlanChanged = { syncScheduler.requestTargetRecompute() },
        )
    }

    // ---- FIT / CSV / ZIP import (P7.5) --------------------------------------------------

    /** `import_record` (§2.2.6): the file-hash guard against importing the same export twice. */
    val importRepo: ImportRepository by lazy {
        RoomImportRepository(
            importDao = db.importDao(),
            activityDao = db.activityDao(),
            ingestor = ingestor,
            onUndone = { day -> syncScheduler.requestLoadRecompute(day) },
        )
    }

    /**
     * The import pipeline. It feeds [activityRepo]`.ingest` — the same seam Health Connect uses —
     * so a FIT file merges with an already-synced activity under the §2.4 precedence, and it asks
     * for a load recompute from the earliest day it touched.
     */
    val importService: ActivityImporter by lazy {
        ImportService(
            content = AndroidImportContentSource(app),
            activityRepo = activityRepo,
            importRepo = importRepo,
            csvParser = GarminCsvParser(zoneId),
            clock = clock,
            onImported = { day -> syncScheduler.requestLoadRecompute(day) },
        )
    }

    /**
     * A `.fit`/`.csv`/`.zip` handed to the app from the share sheet or a file manager, waiting for
     * the Import screen to pick it up (P7.6). Set by [com.myhealth.MainActivity], cleared by the
     * screen once it has started the import.
     */
    val pendingImportUri: MutableStateFlow<String?> = MutableStateFlow(null)

    // ---- JSON backup (P8.4) --------------------------------------------------------------

    /**
     * Whole-database JSON export/import. It deliberately bypasses the repositories: a backup is
     * about the rows as they are stored, not about the domain view of them, and REPLACE has to be
     * able to write ids the domain models do not even expose.
     */
    val backupRepo: BackupRepository by lazy {
        BackupService(
            dao = db.backupDao(),
            transaction = { block -> db.withTransaction { block() } },
            content = AndroidBackupContentSource(app),
            appVersion = BuildConfig.VERSION_NAME,
            clock = clock,
        )
    }

    /** Health Connect availability + permissions (P2.1). */
    val healthConnect: HealthConnectProvider by lazy { HealthConnectProvider(app) }

    /** The Integrations screen's (P2.8) only window onto Health Connect — `ui/` may not import
     * `com.myhealth.data.*` directly, so this `di`-owned seam stands in for [healthConnect]. */
    val hcIntegration: HcIntegration by lazy { HealthConnectIntegration(healthConnect) }

    val hcMapper: HcMapper by lazy { HealthConnectMapper() }

    /** Null when the Health Connect SDK is unavailable — sync must then stay switched off. */
    val hcReader: HcReader? by lazy { healthConnect.client()?.let { HealthConnectReader(it) } }

    val hcSync: HcSyncService? by lazy {
        hcReader?.let { reader ->
            HcSyncService(
                reader = reader,
                mapper = hcMapper,
                activityRepo = activityRepo,
                healthRepo = healthRepo,
                bodyRepo = bodyRepo,
                syncStateRepo = syncStateRepo,
                clock = clock,
                zone = zoneId,
            )
        }
    }

    val hcBackfill: HcBackfill? by lazy {
        hcSync?.let { sync ->
            HcBackfill(
                sync = sync,
                syncStateRepo = syncStateRepo,
                grantedPermissions = { healthConnect.granted() },
                clock = clock,
            )
        }
    }

    // ---- scanning: camera + OCR + Open Food Facts (P4.8–P4.10) --------------------------

    /** Where a captured label JPEG is kept until the review screen has shown it (P4.8). */
    val cacheDir: File get() = app.cacheDir

    /** The one-slot Scan → OcrReview → IngredientEdit hand-off (P4.8). */
    val draftStore: DraftStore by lazy { DraftStore() }

    /** Both ML Kit detectors are expensive to create, so the process keeps one of each (P4.8). */
    private val textSource: MlKitTextSource by lazy { MlKitTextSource() }

    private val barcodeSource: MlKitBarcodeSource by lazy { MlKitBarcodeSource() }

    /** The camera screen's `di`-owned window onto `data/ocr` (P4.8) — see [ScanSources]. */
    val scanSources: ScanSources by lazy { MlKitScanSources(app, textSource, barcodeSource) }

    /** Open Food Facts product reads, throttled to 15/minute off the app [clock] (P4.10). */
    val offClient: OffClient by lazy { OffClient(settings, OffThrottle(clock)) }

    /** Barcode → prefilled draft, shared by the scan path and the editor's manual lookup (P4.10). */
    val offLookup: OffLookup by lazy { OffProductLookup(offClient) }

    /** WorkManager entry point (P2.7); on-demand initialized from [com.myhealth.MyHealthApp]'s
     * `Configuration.Provider` since the default `androidx.startup` initializer is disabled. */
    val syncScheduler: SyncScheduler by lazy { SyncScheduler(WorkManager.getInstance(app), clock) }

    // One lazy val per remaining repository / engine / worker helper is added from P3 onwards.
}

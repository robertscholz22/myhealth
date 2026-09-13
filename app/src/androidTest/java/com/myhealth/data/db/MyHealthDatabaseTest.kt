package com.myhealth.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.myhealth.data.db.migration.Migrations
import com.myhealth.data.db.entity.ActivitySessionEntity
import com.myhealth.data.db.entity.ActivitySourceRecordEntity
import com.myhealth.data.db.entity.CycleEntryEntity
import com.myhealth.data.db.entity.IngredientEntity
import com.myhealth.data.db.entity.MealLogEntity
import com.myhealth.data.db.entity.MealLogItemEntity
import com.myhealth.data.db.entity.ProfileEntity
import com.myhealth.domain.model.ActivitySource
import com.myhealth.domain.model.MealSlot
import com.myhealth.domain.model.MeasureBasis
import com.myhealth.domain.model.NeatLevel
import com.myhealth.domain.model.QuantityUnit
import com.myhealth.domain.model.Sex
import com.myhealth.domain.model.SportGroup
import com.myhealth.domain.model.SportType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Insert/read coverage for the three representative table families of schema v1 (PLAN P1.5):
 * the single-row profile, an activity, and a meal log with its owned items.
 *
 * Instrumented — there is no emulator on the build machine (§0.3), so this compiles in CI and is
 * only executed when a device is attached.
 */
@RunWith(AndroidJUnit4::class)
class MyHealthDatabaseTest {

    private lateinit var db: MyHealthDatabase

    /** Replays real upgrades from the exported schema JSONs (PLAN §6.4 step 4). */
    @get:Rule
    val migrations = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MyHealthDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyHealthDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun profile_upsert_readsBackSingletonRowWithEnumsIntact() = runTest {
        val profile = ProfileEntity(
            displayName = "Robert",
            sex = Sex.MALE,
            birthDay = 5000L,
            heightCm = 180.0,
            neatLevel = NeatLevel.ACTIVE,
            goalWeightKg = 78.0,
            createdAtMillis = 1_000L,
            updatedAtMillis = 1_000L,
        )
        db.profileDao().upsert(profile)

        val loaded = db.profileDao().observeProfile().first()

        assertThat(loaded).isNotNull()
        assertThat(loaded!!.id).isEqualTo(ProfileEntity.SINGLETON_ID)
        assertThat(loaded.sex).isEqualTo(Sex.MALE)
        assertThat(loaded.neatLevel).isEqualTo(NeatLevel.ACTIVE)
        assertThat(loaded.heightCm).isEqualTo(180.0)
        assertThat(db.profileDao().count()).isEqualTo(1)
    }

    @Test
    fun activity_upsert_isVisibleByDayRangeAndDedupeBucket() = runTest {
        val start = 1_700_000_000_000L
        val id = db.activityDao().upsert(
            ActivitySessionEntity(
                startAtMillis = start,
                endAtMillis = start + 3_600_000L,
                day = 19_662L,
                sportType = SportType.RUN_OUTDOOR,
                sportGroup = SportGroup.RUN,
                title = "Morning run",
                durationSec = 3_540,
                elapsedSec = 3_600,
                distanceMeters = 10_000.0,
                avgHr = 148,
                trimp = 92.5,
                primarySource = ActivitySource.FIT_IMPORT,
                mergedSourcesCsv = "FIT_IMPORT,HEALTH_CONNECT",
                dedupeBucket = "RUN|${start / 300_000}",
                createdAtMillis = start,
                updatedAtMillis = start,
            ),
        )

        val byRange = db.activityDao().observeRange(19_660L, 19_665L).first()
        assertThat(byRange).hasSize(1)
        assertThat(byRange.single().id).isEqualTo(id)
        assertThat(byRange.single().sportType).isEqualTo(SportType.RUN_OUTDOOR)

        val byBucket = db.activityDao().getByBuckets(listOf("RUN|${start / 300_000}"))
        assertThat(byBucket.map { it.id }).containsExactly(id)

        val trimpPerDay = db.activityDao().sumTrimpPerDay(19_660L, 19_665L).first()
        assertThat(trimpPerDay).hasSize(1)
        assertThat(trimpPerDay.single().day).isEqualTo(19_662L)
        assertThat(trimpPerDay.single().trimp).isWithin(1e-9).of(92.5)
    }

    @Test
    fun mealLog_withItems_readsBackThroughRelationAndCascadesOnDelete() = runTest {
        val ingredientId = db.ingredientDao().upsert(
            IngredientEntity(
                name = "Haferflocken",
                basis = MeasureBasis.PER_100G,
                kcal = 372.0,
                proteinG = 13.5,
                carbsG = 58.7,
                fatG = 7.0,
                source = "MANUAL",
                createdAtMillis = 1L,
                updatedAtMillis = 1L,
            ),
        )
        val logId = db.mealDao().upsert(
            MealLogEntity(
                day = 19_662L,
                atMinuteOfDay = 8 * 60,
                slot = MealSlot.BREAKFAST,
                name = "Porridge",
                createdAtMillis = 1L,
                updatedAtMillis = 1L,
            ),
        )
        db.mealDao().upsertItems(
            listOf(
                MealLogItemEntity(
                    mealLogId = logId,
                    ingredientId = ingredientId,
                    nameSnapshot = "Haferflocken",
                    quantity = 80.0,
                    unit = QuantityUnit.G,
                    kcal = 297.6,
                    proteinG = 10.8,
                    carbsG = 47.0,
                    sugarG = 0.8,
                    fatG = 5.6,
                    satFatG = 1.0,
                    fiberG = 8.0,
                    saltG = 0.0,
                ),
            ),
        )

        val day = db.mealDao().observeDay(19_662L).first()
        assertThat(day).hasSize(1)
        assertThat(day.single().log.slot).isEqualTo(MealSlot.BREAKFAST)
        assertThat(day.single().items).hasSize(1)
        assertThat(day.single().items.single().unit).isEqualTo(QuantityUnit.G)
        assertThat(day.single().items.single().kcal).isWithin(1e-9).of(297.6)

        db.mealDao().deleteById(logId)
        assertThat(db.mealDao().observeDay(19_662L).first()).isEmpty()
    }

    /**
     * P8.5: an existing v1 database upgrades in place, the FTS index is rebuilt from the rows it
     * already held, and a row inserted afterwards is indexed by the content-sync triggers.
     */
    @Test
    fun migration_1_to_2_builds_the_ingredient_fts_index_over_existing_rows() {
        migrations.createDatabase(MIGRATION_DB, 1).use { v1 ->
            v1.execSQL(
                "INSERT INTO ingredient (id, name, brand, basis, kcal, isFavorite, source, " +
                    "useCount, archived, createdAtMillis, updatedAtMillis) VALUES " +
                    "(1, 'Hafermilch', 'Oatly', 'PER_100ML', 46.0, 0, 'MANUAL', 0, 0, 1, 1)",
            )
        }

        migrations.runMigrationsAndValidate(MIGRATION_DB, 2, true, *Migrations.ALL)

        val migrated = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyHealthDatabase::class.java,
            MIGRATION_DB,
        ).addMigrations(*Migrations.ALL).build()
        try {
            runTest {
                val existing = migrated.ingredientDao().searchFts("hafer*", limit = 10).first()
                assertThat(existing.map { it.name }).containsExactly("Hafermilch")

                migrated.ingredientDao().upsert(
                    IngredientEntity(
                        name = "Sojamilch",
                        basis = MeasureBasis.PER_100ML,
                        kcal = 39.0,
                        source = "MANUAL",
                        createdAtMillis = 2L,
                        updatedAtMillis = 2L,
                    ),
                )
                val inserted = migrated.ingredientDao().searchFts("soja*", limit = 10).first()
                assertThat(inserted.map { it.name }).containsExactly("Sojamilch")
            }
        } finally {
            migrated.close()
        }
    }

    /**
     * P11.1: an existing v2 database gains the `cycle_entry` table in place — the rows it already
     * held survive, the new table's unique index over `periodStartDay` is really there, and the
     * DAO reads and writes it afterwards.
     */
    @Test
    fun migration_2_to_3_adds_cycle_entry_with_a_unique_period_start() {
        migrations.createDatabase(MIGRATION_DB, 2).use { v2 ->
            v2.execSQL(
                "INSERT INTO ingredient (id, name, brand, basis, kcal, isFavorite, source, " +
                    "useCount, archived, createdAtMillis, updatedAtMillis) VALUES " +
                    "(1, 'Hafermilch', 'Oatly', 'PER_100ML', 46.0, 0, 'MANUAL', 0, 0, 1, 1)",
            )
        }

        migrations.runMigrationsAndValidate(MIGRATION_DB, 3, true, *Migrations.ALL)

        val migrated = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyHealthDatabase::class.java,
            MIGRATION_DB,
        ).addMigrations(*Migrations.ALL).build()
        try {
            runTest {
                // Nothing was lost on the way up.
                assertThat(migrated.ingredientDao().searchFts("hafer*", limit = 10).first())
                    .hasSize(1)

                val dao = migrated.cycleDao()
                val id = dao.upsert(
                    CycleEntryEntity(
                        periodStartDay = 20_800L,
                        periodEndDay = 20_804L,
                        createdAtMillis = 1L,
                        updatedAtMillis = 1L,
                    ),
                )
                assertThat(dao.getAll()).hasSize(1)
                assertThat(dao.getByStartDay(20_800L)?.id).isEqualTo(id)

                // The unique index is what keeps one cycle per start day: Room's @Upsert resolves
                // the UNIQUE conflict by updating by primary key (id 0 matches nothing), so the
                // duplicate is dropped without an exception and the original row survives.
                runCatching {
                    dao.upsert(
                        CycleEntryEntity(
                            periodStartDay = 20_800L,
                            createdAtMillis = 2L,
                            updatedAtMillis = 2L,
                        ),
                    )
                }
                assertThat(dao.getAll()).hasSize(1)
                assertThat(dao.getByStartDay(20_800L)?.id).isEqualTo(id)
                assertThat(dao.getByStartDay(20_800L)?.periodEndDay).isEqualTo(20_804L)
            }
        } finally {
            migrated.close()
        }
    }

    /**
     * "Undo import": an existing v3 database gains `activity_source_record.importRecordId` in
     * place. The rows it already held survive with `NULL` in the new column (they predate undo),
     * and a record written afterwards can be found by its import.
     */
    @Test
    fun migration_3_to_4_adds_import_record_id_to_activity_source_record() {
        migrations.createDatabase(MIGRATION_DB, 3).use { v3 ->
            v3.execSQL(
                "INSERT INTO activity_source_record (id, activityId, source, externalId, " +
                    "payloadJson, receivedAtMillis) VALUES " +
                    "(1, NULL, 'HEALTH_CONNECT', 'hc-1', '{}', 1000)",
            )
        }

        migrations.runMigrationsAndValidate(MIGRATION_DB, 4, true, *Migrations.ALL)

        val migrated = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyHealthDatabase::class.java,
            MIGRATION_DB,
        ).addMigrations(*Migrations.ALL).build()
        try {
            runTest {
                val dao = migrated.activityDao()
                // The pre-v4 arrival survived and is not attributed to any import.
                val existing = dao.getSourceRecord(ActivitySource.HEALTH_CONNECT, "hc-1")
                assertThat(existing?.importRecordId).isNull()
                assertThat(dao.getSourceRecordsOfImport(7L)).isEmpty()

                dao.upsertSourceRecord(
                    ActivitySourceRecordEntity(
                        source = ActivitySource.CSV_IMPORT,
                        externalId = "csv-1",
                        payloadJson = "{}",
                        receivedAtMillis = 2_000L,
                        importRecordId = 7L,
                    ),
                )
                assertThat(dao.getSourceRecordsOfImport(7L).map { it.externalId })
                    .containsExactly("csv-1")
            }
        } finally {
            migrated.close()
        }
    }

    private companion object {
        const val MIGRATION_DB = "migration-test.db"
    }
}

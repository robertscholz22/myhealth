package com.myhealth.data.db.migration

import androidx.room.migration.Migration

/**
 * Schema migrations for [com.myhealth.data.db.MyHealthDatabase] (PLAN §2.2, §6.4).
 *
 * Convention — every schema change follows all five steps, in this order:
 *
 * 1. Change the entity/entities, then bump `@Database(version = N)` by exactly one.
 * 2. Add `private val MIGRATION_(N-1)_N = Migration(N - 1, N) { db -> … }` below, using only
 *    `db.execSQL(...)`. Never reference an entity class or a DAO from a migration: migrations run
 *    against the schema as it was, not as the code now describes it.
 * 3. Append it to [ALL]. The array stays ordered by version, oldest first, and a migration that
 *    has shipped to the device is never reordered or edited in place.
 * 4. Build once so Room writes `app/schemas/com.myhealth.data.db.MyHealthDatabase/N.json`, and
 *    keep that file — `MigrationTestHelper` replays real upgrades from it.
 * 5. Add a row to the migration table in `docs/PLAN.md` §6.4 (from, to, what changed, why).
 *
 * `fallbackToDestructiveMigration` is forbidden outside the debug-only escape hatch in
 * `MyHealthDatabase.build` (§2.2): losing a user's history is never an acceptable upgrade path.
 */
object Migrations {

    /**
     * 1 → 2 (P8.5): the `ingredient_fts` FTS4 index over `ingredient(name, brand)`.
     *
     * The statements are Room's own, copied verbatim from
     * `app/schemas/com.myhealth.data.db.MyHealthDatabase/2.json` (`createSql` with `${'$'}{TABLE_NAME}`
     * resolved, plus the four `contentSyncTriggers`) — an external-content FTS table is only kept
     * in step by those triggers, and Room's schema validation compares them character for
     * character. The final `'rebuild'` command fills the index from the rows that already exist,
     * which the triggers alone would never do.
     */
    private val MIGRATION_1_2 = Migration(1, 2) { db ->
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `ingredient_fts` USING FTS4(" +
                "`name` TEXT NOT NULL, `brand` TEXT, content=`ingredient`)",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_ingredient_fts_BEFORE_UPDATE " +
                "BEFORE UPDATE ON `ingredient` BEGIN " +
                "DELETE FROM `ingredient_fts` WHERE `docid`=OLD.`rowid`; END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_ingredient_fts_BEFORE_DELETE " +
                "BEFORE DELETE ON `ingredient` BEGIN " +
                "DELETE FROM `ingredient_fts` WHERE `docid`=OLD.`rowid`; END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_ingredient_fts_AFTER_UPDATE " +
                "AFTER UPDATE ON `ingredient` BEGIN " +
                "INSERT INTO `ingredient_fts`(`docid`, `name`, `brand`) " +
                "VALUES (NEW.`rowid`, NEW.`name`, NEW.`brand`); END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_ingredient_fts_AFTER_INSERT " +
                "AFTER INSERT ON `ingredient` BEGIN " +
                "INSERT INTO `ingredient_fts`(`docid`, `name`, `brand`) " +
                "VALUES (NEW.`rowid`, NEW.`name`, NEW.`brand`); END",
        )
        db.execSQL("INSERT INTO ingredient_fts(ingredient_fts) VALUES('rebuild')")
    }

    /**
     * 2 → 3 (P11.1): the `cycle_entry` table of the menstrual-cycle tracker, with the unique index
     * over `periodStartDay` that keeps one logged period per day (a duplicate start would corrupt
     * every interval `CycleEngine` averages). The statements are Room's own, copied verbatim from
     * `app/schemas/com.myhealth.data.db.MyHealthDatabase/3.json`, so `runMigrationsAndValidate`
     * compares them character for character.
     */
    private val MIGRATION_2_3 = Migration(2, 3) { db ->
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cycle_entry` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`periodStartDay` INTEGER NOT NULL, `periodEndDay` INTEGER, `note` TEXT, " +
                "`createdAtMillis` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `uq_cycle_entry_start` " +
                "ON `cycle_entry` (`periodStartDay`)",
        )
    }

    /**
     * 3 → 4 (BUG-11 follow-up, "Undo import"): `activity_source_record.importRecordId`, the
     * nullable back-link from a raw arrival to the `import_record` that wrote it, plus the index
     * the undo selects on. Existing rows keep `NULL`: they predate the column, so they were either
     * synced or imported before undo existed and are not undoable. The statements are Room's own,
     * copied from `app/schemas/com.myhealth.data.db.MyHealthDatabase/4.json`, so
     * `runMigrationsAndValidate` compares them character for character.
     */
    private val MIGRATION_3_4 = Migration(3, 4) { db ->
        db.execSQL("ALTER TABLE `activity_source_record` ADD COLUMN `importRecordId` INTEGER")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_asr_import` ON `activity_source_record` (`importRecordId`)",
        )
    }

    /**
     * 4 → 5 (P12 "Bike & power"): cycling power on `activity_session` and `activity_stream`, the
     * FTP override and trainer flag on `profile`, and the new `ride_best` table.
     *
     * The three session columns and the stream column are nullable, so existing rows simply have
     * no power — which is the truth: nothing before 1.1.0 ever read a watt. `profile`'s flag is
     * `NOT NULL DEFAULT 0`, matching the entity default, so the single existing row stays valid
     * without a rewrite. The statements are Room's own, copied verbatim from
     * `app/schemas/com.myhealth.data.db.MyHealthDatabase/5.json`, so `runMigrationsAndValidate`
     * compares them character for character.
     */
    private val MIGRATION_4_5 = Migration(4, 5) { db ->
        db.execSQL("ALTER TABLE `activity_session` ADD COLUMN `avgPowerW` INTEGER")
        db.execSQL("ALTER TABLE `activity_session` ADD COLUMN `maxPowerW` INTEGER")
        db.execSQL("ALTER TABLE `activity_session` ADD COLUMN `normalizedPowerW` INTEGER")
        db.execSQL("ALTER TABLE `activity_stream` ADD COLUMN `powerWJson` TEXT")
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `ftpWattsManual` INTEGER")
        db.execSQL(
            "ALTER TABLE `profile` ADD COLUMN `indoorTrainerAvailable` INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ride_best` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, " +
                "`value` REAL NOT NULL, `activityId` INTEGER, `day` INTEGER NOT NULL, " +
                "`isEstimated` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, " +
                "FOREIGN KEY(`activityId`) REFERENCES `activity_session`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `idx_ride_best_kind_value` ON `ride_best` (`kind`, `value`)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `uq_ride_best_activity_kind` " +
                "ON `ride_best` (`activityId`, `kind`)",
        )
    }

    /**
     * Every migration, oldest first. `.addMigrations(*ALL)` is the only call site, in
     * `MyHealthDatabase.build`, so adding a migration never changes it.
     */
    val ALL: Array<Migration> =
        arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}

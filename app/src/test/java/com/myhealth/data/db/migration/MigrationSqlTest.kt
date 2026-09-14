package com.myhealth.data.db.migration

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File

/**
 * The unit-testable half of the `MIGRATION_5_6` (P14.1) and `MIGRATION_6_7` (P16.1) guarantees.
 *
 * `MyHealthDatabaseTest.migration_5_to_6_…` replays the real upgrade, but it needs a device and
 * there is none on this machine (§0.3). What *can* be checked here, without a device, is the thing
 * that actually breaks a migration: the SQL in `Migrations.kt` drifting away from the schema Room
 * exported. So this test parses the exported schema JSON and compares the statements character
 * for character
 * against the source of the migration — the same comparison `runMigrationsAndValidate` makes at
 * runtime, minus the execution.
 *
 * The source is read rather than executed because a `Migration { db -> … }` lambda has no
 * inspectable statement list; Kotlin's `"…" + "…"` concatenations are flattened first.
 */
class MigrationSqlTest {

    @Test
    fun migration_5_to_6_creates_the_three_strength_tables_with_room_s_own_sql() {
        val entities = exportedEntities(6)
        val sql = flattenedMigrationSource()

        listOf("strength_workout", "strength_workout_exercise", "strength_set_log").forEach { table ->
            assertThat(sql).contains(createSqlOf(entities, table))
            indexSqlOf(entities, table).forEach { assertThat(sql).contains(it) }
        }
    }

    @Test
    fun migration_5_to_6_adds_the_workout_foreign_key_and_its_index_to_planned_session() {
        val entities = exportedEntities(6)
        val plannedSession = createSqlOf(entities, "planned_session")
        val sql = flattenedMigrationSource()

        // Room's exported schema declares the FK …
        assertThat(plannedSession).contains(
            "FOREIGN KEY(`workoutId`) REFERENCES `strength_workout`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL",
        )
        assertThat(plannedSession).contains("`structureJson` TEXT")
        assertThat(plannedSession).contains("`workoutId` INTEGER")

        // … and the migration adds exactly that column, with the same reference and the same index
        // name, through `ALTER TABLE` rather than by recreating the table (§6.4 row 6).
        assertThat(sql).contains(
            "ALTER TABLE `planned_session` ADD COLUMN `workoutId` INTEGER " +
                "REFERENCES `strength_workout`(`id`) ON DELETE SET NULL",
        )
        assertThat(sql).contains("ALTER TABLE `planned_session` ADD COLUMN `structureJson` TEXT")
        assertThat(indexSqlOf(entities, "planned_session"))
            .contains("CREATE INDEX IF NOT EXISTS `idx_planned_workout` ON `planned_session` (`workoutId`)")
        assertThat(sql).contains(
            "CREATE INDEX IF NOT EXISTS `idx_planned_workout` ON `planned_session` (`workoutId`)",
        )
        // The fallback was not needed, so the table is never rebuilt.
        assertThat(sql).doesNotContain("planned_session_new")
    }

    @Test
    fun migration_5_to_6_adds_every_new_column_of_profile_and_suggested_session() {
        val entities = exportedEntities(6)
        val sql = flattenedMigrationSource()

        assertThat(createSqlOf(entities, "profile")).contains("`hrZoneBoundsJson` TEXT")
        assertThat(createSqlOf(entities, "profile")).contains("`lactateThresholdHrManual` INTEGER")
        assertThat(createSqlOf(entities, "suggested_session")).contains("`targetPaceSecPerKm` INTEGER")
        assertThat(createSqlOf(entities, "suggested_session")).contains("`workoutTemplateId` TEXT")

        listOf(
            "ALTER TABLE `profile` ADD COLUMN `hrZoneBoundsJson` TEXT",
            "ALTER TABLE `profile` ADD COLUMN `lactateThresholdHrManual` INTEGER",
            "ALTER TABLE `suggested_session` ADD COLUMN `targetPaceSecPerKm` INTEGER",
            "ALTER TABLE `suggested_session` ADD COLUMN `structureJson` TEXT",
            "ALTER TABLE `suggested_session` ADD COLUMN `workoutTemplateId` TEXT",
        ).forEach { assertThat(sql).contains(it) }
    }

    /**
     * P16.1: `exercise_progress` plus the two nullable columns, all added in place. The table's
     * primary key is the catalog id, a `TEXT` key, so the statement Room exports is compared
     * character for character rather than assumed to look like the other `CREATE TABLE`s.
     */
    @Test
    fun migration_6_to_7_adds_the_exercise_progress_table_and_the_two_columns() {
        val entities = exportedEntities(7)
        val sql = flattenedMigrationSource()

        assertThat(sql).contains(createSqlOf(entities, "exercise_progress"))
        assertThat(createSqlOf(entities, "exercise_progress")).contains("PRIMARY KEY(`exerciseId`)")
        assertThat(indexSqlOf(entities, "exercise_progress")).isEmpty()

        assertThat(createSqlOf(entities, "profile")).contains("`availableEquipmentJson` TEXT")
        assertThat(createSqlOf(entities, "strength_set_log")).contains("`feedback` TEXT")
        listOf(
            "ALTER TABLE `profile` ADD COLUMN `availableEquipmentJson` TEXT",
            "ALTER TABLE `strength_set_log` ADD COLUMN `feedback` TEXT",
        ).forEach { assertThat(sql).contains(it) }
        // Additive only: no table is rebuilt on the way to 7.
        assertThat(sql).doesNotContain("exercise_progress_new")
    }

    @Test
    fun the_exported_schema_is_version_seven_and_the_older_ones_are_untouched() {
        val latest = Json.parseToJsonElement(schemaFile(7).readText()).jsonObject.getValue("database")
        assertThat(latest.jsonObject.getValue("version").jsonPrimitive.content).isEqualTo("7")
        (1..6).forEach { assertThat(schemaFile(it).isFile).isTrue() }
        // `strength_*` exists only from v6 on: v5 must not have grown a table retroactively.
        assertThat(schemaFile(5).readText()).doesNotContain("strength_workout")
        // … and `exercise_progress` only from v7 on.
        assertThat(schemaFile(6).readText()).doesNotContain("exercise_progress")
    }

    // ---- reading the two artefacts ----------------------------------------------------------

    /** One entity of the exported schema: its `CREATE TABLE` and its `CREATE INDEX` statements. */
    private data class ExportedEntity(val createSql: String, val indices: List<String>)

    /** `tableName -> entity` of the exported `<version>.json`. */
    private fun exportedEntities(version: Int): Map<String, ExportedEntity> {
        val database =
            Json.parseToJsonElement(schemaFile(version).readText()).jsonObject.getValue("database")
        return database.jsonObject.getValue("entities").jsonArray.associate { entity ->
            val obj = entity.jsonObject
            val table = obj.getValue("tableName").jsonPrimitive.content
            table to ExportedEntity(
                createSql = obj.getValue("createSql").jsonPrimitive.content.replace(TABLE_NAME, table),
                indices = obj["indices"]?.jsonArray.orEmpty().map {
                    it.jsonObject.getValue("createSql").jsonPrimitive.content.replace(TABLE_NAME, table)
                },
            )
        }
    }

    private fun createSqlOf(entities: Map<String, ExportedEntity>, table: String): String =
        entities.getValue(table).createSql

    private fun indexSqlOf(entities: Map<String, ExportedEntity>, table: String): List<String> =
        entities.getValue(table).indices

    /** `Migrations.kt` with Kotlin's `"a" + "b"` string concatenations joined into one literal. */
    private fun flattenedMigrationSource(): String =
        MIGRATION_SOURCE.readText().replace(CONCATENATION, "")

    private fun schemaFile(version: Int): File = File(schemaDir, "$version.json")

    private companion object {
        const val TABLE_NAME = "\${TABLE_NAME}"

        /** `"…" +` followed by whitespace and the next `"` — what Kotlin joins at compile time. */
        val CONCATENATION = Regex("""["]\s*\+\s*["]""")

        /** Resolves a path under `app/` from either the module directory or the project root. */
        fun resolve(suffix: String): File {
            val userDir = File(System.getProperty("user.dir") ?: ".").absoluteFile
            return listOf(File(userDir, suffix), File(userDir, "app/$suffix"), File(userDir.parentFile, "app/$suffix"))
                .firstOrNull { it.exists() }
                ?: error("Could not locate $suffix from user.dir=$userDir")
        }

        val schemaDir: File = resolve("schemas/com.myhealth.data.db.MyHealthDatabase")
        val MIGRATION_SOURCE: File =
            resolve("src/main/java/com/myhealth/data/db/migration/Migrations.kt")
    }
}

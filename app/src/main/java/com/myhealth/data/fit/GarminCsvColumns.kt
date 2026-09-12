package com.myhealth.data.fit

import com.myhealth.domain.model.SportType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * The vocabulary and cell grammar [GarminCsvParser] reads Garmin's activity CSV with (PLAN P7.3).
 *
 * Kept apart from the parser itself because it is the part that grows: every new export vintage or
 * UI language adds header spellings and activity-type names, none of which change the parsing
 * algorithm.
 */
internal object Columns {
    const val ACTIVITY_TYPE = "activity type"
    const val DATE = "date"
    const val TITLE = "title"
    const val DISTANCE = "distance"
    const val CALORIES = "calories"
    const val TIME = "time"
    const val AVG_HR = "avg hr"
    const val MAX_HR = "max hr"
    const val AVG_SPEED = "avg speed"
    const val MAX_SPEED = "max speed"
    const val ELEV_GAIN = "elev gain"
    const val AEROBIC_TE = "aerobic te"
}

/**
 * Every spelling of a recognised column seen in Garmin exports, normalised. Pace columns share the
 * speed slots (the parser converts), and the German UI's headers are accepted as aliases so a
 * localised export does not silently lose every column.
 */
internal val HEADER_ALIASES: Map<String, String> = mapOf(
    "activity type" to Columns.ACTIVITY_TYPE, "aktivitatstyp" to Columns.ACTIVITY_TYPE,
    "aktivitatstyp" to Columns.ACTIVITY_TYPE, "typ" to Columns.ACTIVITY_TYPE,
    "date" to Columns.DATE, "datum" to Columns.DATE, "start time" to Columns.DATE,
    "title" to Columns.TITLE, "titel" to Columns.TITLE, "activity name" to Columns.TITLE,
    "distance" to Columns.DISTANCE, "distanz" to Columns.DISTANCE, "strecke" to Columns.DISTANCE,
    "calories" to Columns.CALORIES, "kalorien" to Columns.CALORIES,
    "time" to Columns.TIME, "zeit" to Columns.TIME, "moving time" to Columns.TIME,
    "duration" to Columns.TIME, "dauer" to Columns.TIME,
    "avg hr" to Columns.AVG_HR, "average hr" to Columns.AVG_HR, "durchschnittliche hf" to Columns.AVG_HR,
    "hf" to Columns.AVG_HR,
    "max hr" to Columns.MAX_HR, "maximale hf" to Columns.MAX_HR,
    "avg speed" to Columns.AVG_SPEED, "avg pace" to Columns.AVG_SPEED,
    "durchschnittliche geschwindigkeit" to Columns.AVG_SPEED, "durchschnittspace" to Columns.AVG_SPEED,
    "max speed" to Columns.MAX_SPEED, "best pace" to Columns.MAX_SPEED,
    "maximale geschwindigkeit" to Columns.MAX_SPEED, "beste pace" to Columns.MAX_SPEED,
    "elev gain" to Columns.ELEV_GAIN, "total ascent" to Columns.ELEV_GAIN,
    "anstieg gesamt" to Columns.ELEV_GAIN, "gesamtanstieg" to Columns.ELEV_GAIN,
    "aerobic te" to Columns.AEROBIC_TE, "aerober te" to Columns.AEROBIC_TE,
)

/**
 * Lowercases, strips the unit suffix in parentheses, folds the German umlauts and the `Ø` prefix
 * Garmin uses for averages, and collapses everything that is not a letter or digit to one space.
 */
internal fun canonicalHeader(raw: String): String? {
    val folded = raw.lowercase()
        .replace("ø", "durchschnittliche ")
        .replace("ä", "a").replace("ö", "o").replace("ü", "u").replace("ß", "ss")
        .substringBefore('(')
    val normalized = folded.map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .split(' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ")
    return HEADER_ALIASES[normalized]
}

// ---- cell parsing ---------------------------------------------------------------------------

/** Decimal separator convention of one file, inferred from the numbers the file actually holds. */
internal enum class NumberStyle {
    ENGLISH,
    GERMAN,
    ;

    fun parse(raw: String): Double? {
        val cleaned = raw.trim().removeSuffix("%").filter { it.isDigit() || it == '.' || it == ',' || it == '-' }
        if (cleaned.isEmpty()) return null
        val normalized = when (this) {
            ENGLISH -> cleaned.replace(",", "")
            GERMAN -> cleaned.replace(".", "").replace(',', '.')
        }
        return normalized.toDoubleOrNull()
    }

    companion object {
        private val GERMAN_DECIMAL = Regex("""^-?\d{1,3}(\.\d{3})*,\d{1,3}$|^-?\d+,\d{1,3}$""")

        /** German as soon as any cell is unambiguously `1.234,5` / `10,52`; English otherwise. */
        fun detect(lines: List<String>): NumberStyle {
            val german = lines.asSequence()
                .flatMap { splitCsvLine(it).asSequence() }
                .any { GERMAN_DECIMAL.matches(it.trim()) }
            return if (german) GERMAN else ENGLISH
        }
    }
}

/** `h:mm:ss`, `mm:ss` or plain seconds; fractional seconds are truncated. */
internal fun parseDuration(raw: String, style: NumberStyle = NumberStyle.ENGLISH): Int? {
    val value = raw.trim()
    if (':' !in value) return style.parse(value)?.toInt()
    val parts = value.split(':')
    if (parts.size !in 2..3) return null
    val numbers = parts.map { part ->
        part.replace(',', '.').toDoubleOrNull() ?: return null
    }
    val seconds = when (numbers.size) {
        2 -> numbers[0] * 60 + numbers[1]
        else -> numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
    }
    return seconds.toInt()
}

internal val DATE_FORMATS: List<DateTimeFormatter> = listOf(
    "yyyy-MM-dd HH:mm:ss",
    "yyyy-MM-dd HH:mm",
    "dd.MM.yyyy HH:mm:ss",
    "dd.MM.yyyy HH:mm",
    "dd/MM/yyyy HH:mm:ss",
    "MM/dd/yyyy HH:mm:ss",
    "yyyy-MM-dd'T'HH:mm:ss",
).map { DateTimeFormatter.ofPattern(it) }

/** Garmin writes wall-clock local time with no offset, so the app's zone supplies it. */
internal fun parseDateTime(raw: String, zone: ZoneId): Long? {
    val value = raw.trim().removeSuffix("Z")
    for (format in DATE_FORMATS) {
        try {
            return LocalDateTime.parse(value, format).atZone(zone).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            // Try the next known layout.
        }
    }
    return try {
        LocalDate.parse(value).atStartOfDay(zone).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}

/** RFC-4180 splitting: quoted fields may contain commas, and `""` is a literal quote. */
internal fun splitCsvLine(line: String): List<String> {
    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var index = 0
    while (index < line.length) {
        val char = line[index]
        when {
            inQuotes && char == '"' && line.getOrNull(index + 1) == '"' -> {
                current.append('"')
                index++
            }
            char == '"' -> inQuotes = !inQuotes
            char == ',' && !inQuotes -> {
                cells += current.toString()
                current.setLength(0)
            }
            else -> current.append(char)
        }
        index++
    }
    cells += current.toString()
    return cells
}

/** Garmin's activity-type strings (English and German UI) → [SportType]; unknown → `OTHER`. */
internal object GarminActivityTypeMap {

    fun toSportType(raw: String?): SportType {
        val key = raw?.lowercase()?.trim().orEmpty()
        if (key.isEmpty()) return SportType.OTHER
        EXACT[key]?.let { return it }
        return CONTAINS.firstOrNull { (needle, _) -> needle in key }?.second ?: SportType.OTHER
    }

    private val EXACT: Map<String, SportType> = mapOf(
        "running" to SportType.RUN_OUTDOOR, "laufen" to SportType.RUN_OUTDOOR,
        "street running" to SportType.RUN_OUTDOOR, "laufen im freien" to SportType.RUN_OUTDOOR,
        "treadmill running" to SportType.RUN_TREADMILL, "laufband" to SportType.RUN_TREADMILL,
        "indoor running" to SportType.RUN_TREADMILL,
        "trail running" to SportType.RUN_TRAIL, "traillauf" to SportType.RUN_TRAIL,
        "track running" to SportType.RUN_TRACK, "bahnlauf" to SportType.RUN_TRACK,
        "cycling" to SportType.CYCLING, "radfahren" to SportType.CYCLING,
        "road cycling" to SportType.CYCLING, "gravel/unpaved cycling" to SportType.CYCLING,
        "indoor cycling" to SportType.CYCLING_INDOOR, "indoor-radfahren" to SportType.CYCLING_INDOOR,
        "walking" to SportType.WALK, "gehen" to SportType.WALK,
        "hiking" to SportType.HIKE, "wandern" to SportType.HIKE,
        "soccer" to SportType.SOCCER_TRAINING, "football" to SportType.SOCCER_TRAINING,
        "fussball" to SportType.SOCCER_TRAINING, "fußball" to SportType.SOCCER_TRAINING,
        "strength training" to SportType.STRENGTH, "krafttraining" to SportType.STRENGTH,
        "cardio" to SportType.HIIT, "hiit" to SportType.HIIT,
        "yoga" to SportType.MOBILITY, "pilates" to SportType.MOBILITY,
        "breathwork" to SportType.MOBILITY, "stretching" to SportType.MOBILITY,
        "pool swim" to SportType.SWIM, "open water swimming" to SportType.SWIM,
        "schwimmbadschwimmen" to SportType.SWIM, "schwimmen" to SportType.SWIM,
        "rowing" to SportType.ROWING, "indoor rowing" to SportType.ROWING, "rudern" to SportType.ROWING,
    )

    /** Fallbacks for the long tail ("Virtual Running", "E-Bike Ride", …). */
    private val CONTAINS: List<Pair<String, SportType>> = listOf(
        "treadmill" to SportType.RUN_TREADMILL,
        "trail" to SportType.RUN_TRAIL,
        "running" to SportType.RUN_OUTDOOR,
        "lauf" to SportType.RUN_OUTDOOR,
        "cycling" to SportType.CYCLING,
        "bike" to SportType.CYCLING,
        "rad" to SportType.CYCLING,
        "walk" to SportType.WALK,
        "swim" to SportType.SWIM,
        "strength" to SportType.STRENGTH,
        "rowing" to SportType.ROWING,
    )
}

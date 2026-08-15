package com.iar.tardypass.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One period's bell times as local wall-clock strings ("HH:mm"). Wall clock, not
 * epochs — a bell schedule is a wall-clock fact and doesn't shift with the date.
 */
@Serializable
data class BellPeriod(
    val name: String,
    val start: String,
    val end: String
) {
    val startMinutes: Int? get() = parseHhMm(start)
    val endMinutes: Int? get() = parseHhMm(end)
}

/**
 * The school's bell schedule. Entirely optional: an empty schedule makes every
 * lookup return null, which is what keeps period preselection and the
 * minutes-late line behaving exactly as they did before this existed.
 *
 * Deliberately no A/B days, early-release or per-weekday variants yet — those
 * triple the editor's complexity, and this shape extends to `days: [...]` per
 * period later without a rewrite.
 */
@Serializable
data class BellSchedule(
    val periods: List<BellPeriod> = emptyList()
) {
    val isConfigured: Boolean get() = periods.isNotEmpty()

    /** The period containing [minutesOfDay]; start inclusive, end exclusive. */
    fun periodAt(minutesOfDay: Int): BellPeriod? = periods.firstOrNull { p ->
        val s = p.startMinutes
        val e = p.endMinutes
        s != null && e != null && minutesOfDay >= s && minutesOfDay < e
    }

    /**
     * The next period to start after [minutesOfDay]. This is what makes passing
     * time work: a student in the hallway at 8:52 is late to the 8:55 period,
     * not the one that just ended.
     */
    fun nextPeriodAfter(minutesOfDay: Int): BellPeriod? = periods
        .filter { (it.startMinutes ?: return@filter false) > minutesOfDay }
        .minByOrNull { it.startMinutes ?: Int.MAX_VALUE }

    /** Whichever period a pass at [minutesOfDay] most likely belongs to. */
    fun suggestedPeriodAt(minutesOfDay: Int): BellPeriod? =
        periodAt(minutesOfDay) ?: nextPeriodAfter(minutesOfDay)

    /**
     * How late [atMinutesOfDay] is for [periodName]. Null when the period isn't
     * in the schedule; negative when the student is early, which the caller
     * treats as "don't print a number".
     */
    fun minutesLate(periodName: String, atMinutesOfDay: Int): Int? {
        val start = periods.firstOrNull { it.name.equals(periodName, ignoreCase = true) }
            ?.startMinutes ?: return null
        return atMinutesOfDay - start
    }

    companion object {
        val EMPTY = BellSchedule()

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Never throws: a malformed schedule degrades to "not configured". */
        fun fromJson(raw: String): BellSchedule =
            if (raw.isBlank()) EMPTY
            else runCatching { json.decodeFromString<BellSchedule>(raw) }.getOrDefault(EMPTY)

        fun toJson(schedule: BellSchedule): String =
            json.encodeToString(serializer(), schedule)
    }
}

/** "HH:mm" to minutes past midnight, or null if it isn't a valid time. */
fun parseHhMm(value: String): Int? {
    val parts = value.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

/**
 * Upper bound on a printable "minutes late". Anything beyond this is a device
 * clock that has drifted or been changed, not a fact about a student.
 */
const val MAX_SENSIBLE_MINUTES_LATE = 480

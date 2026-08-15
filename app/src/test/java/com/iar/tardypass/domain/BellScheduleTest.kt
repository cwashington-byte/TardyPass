package com.iar.tardypass.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BellScheduleTest {

    /** P1 08:00-08:50, P2 08:55-09:45, P3 09:50-10:40 — note the 5-min gaps. */
    private val schedule = BellSchedule(
        listOf(
            BellPeriod("P1", "08:00", "08:50"),
            BellPeriod("P2", "08:55", "09:45"),
            BellPeriod("P3", "09:50", "10:40")
        )
    )

    private fun at(h: Int, m: Int) = h * 60 + m

    @Test
    fun `parses times and rejects nonsense`() {
        assertEquals(0, parseHhMm("00:00"))
        assertEquals(8 * 60 + 5, parseHhMm("08:05"))
        assertEquals(23 * 60 + 59, parseHhMm("23:59"))
        assertNull(parseHhMm("24:00"))
        assertNull(parseHhMm("08:60"))
        assertNull(parseHhMm("0800"))
        assertNull(parseHhMm(""))
    }

    @Test
    fun `start is inclusive and end exclusive`() {
        assertEquals("P1", schedule.periodAt(at(8, 0))?.name)
        assertEquals("P1", schedule.periodAt(at(8, 49))?.name)
        // 08:50 is the end of P1, so it belongs to no period.
        assertNull(schedule.periodAt(at(8, 50)))
    }

    @Test
    fun `passing time suggests the period about to start, not the one that ended`() {
        // 08:52 sits in the gap between P1 and P2.
        assertNull(schedule.periodAt(at(8, 52)))
        assertEquals("P2", schedule.nextPeriodAfter(at(8, 52))?.name)
        assertEquals("P2", schedule.suggestedPeriodAt(at(8, 52))?.name)
    }

    @Test
    fun `before school suggests the first period`() {
        assertEquals("P1", schedule.suggestedPeriodAt(at(7, 15))?.name)
    }

    @Test
    fun `after the last period suggests nothing`() {
        assertNull(schedule.suggestedPeriodAt(at(15, 30)))
    }

    @Test
    fun `minutes late is measured from the period start`() {
        assertEquals(12, schedule.minutesLate("P1", at(8, 12)))
        assertEquals(0, schedule.minutesLate("P1", at(8, 0)))
    }

    @Test
    fun `an early arrival is negative so the caller can omit the line`() {
        assertTrue(schedule.minutesLate("P2", at(8, 52))!! < 0)
    }

    @Test
    fun `unknown period yields null rather than a wrong number`() {
        assertNull(schedule.minutesLate("P9", at(9, 0)))
        assertNull(BellSchedule.EMPTY.minutesLate("P1", at(9, 0)))
    }

    @Test
    fun `period names match case-insensitively`() {
        assertEquals(12, schedule.minutesLate("p1", at(8, 12)))
    }

    @Test
    fun `out-of-order definitions still resolve correctly`() {
        val jumbled = BellSchedule(
            listOf(
                BellPeriod("P3", "09:50", "10:40"),
                BellPeriod("P1", "08:00", "08:50"),
                BellPeriod("P2", "08:55", "09:45")
            )
        )
        assertEquals("P1", jumbled.periodAt(at(8, 10))?.name)
        // nextPeriodAfter must pick the earliest upcoming, not the first listed.
        assertEquals("P2", jumbled.nextPeriodAfter(at(8, 52))?.name)
    }

    @Test
    fun `an unconfigured schedule is inert`() {
        assertFalse(BellSchedule.EMPTY.isConfigured)
        assertNull(BellSchedule.EMPTY.periodAt(at(9, 0)))
        assertNull(BellSchedule.EMPTY.suggestedPeriodAt(at(9, 0)))
    }

    @Test
    fun `malformed json degrades to not-configured instead of throwing`() {
        assertFalse(BellSchedule.fromJson("not json").isConfigured)
        assertFalse(BellSchedule.fromJson("").isConfigured)
        assertFalse(BellSchedule.fromJson("{}").isConfigured)
    }

    @Test
    fun `json round trips`() {
        val restored = BellSchedule.fromJson(BellSchedule.toJson(schedule))
        assertEquals(schedule, restored)
        assertEquals("P2", restored.suggestedPeriodAt(at(8, 52))?.name)
    }

    @Test
    fun `a period with an unparsable time is skipped, not crashed on`() {
        val broken = BellSchedule(listOf(BellPeriod("P1", "oops", "08:50")))
        assertNull(broken.periodAt(at(8, 10)))
        assertNull(broken.nextPeriodAfter(at(7, 0)))
    }
}

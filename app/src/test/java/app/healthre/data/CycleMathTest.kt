package app.healthre.data

import org.junit.Assert.*
import org.junit.Test

class CycleMathTest {
    private fun cycles(vararg starts: Long) = starts.map { PeriodCycle(startDay = it, endDay = it + 4) }

    @Test fun noData() {
        assertNull(CycleMath.info(emptyList(), 100))
        assertTrue(CycleMath.warnings(emptyList(), emptyList(), 100).isEmpty())
    }

    @Test fun regularPredicts() {
        val c = cycles(0, 28, 56, 84)
        val i = CycleMath.info(c, 90)!!
        assertEquals(28, i.avgCycle)
        assertEquals(112, i.nextStart)
        assertEquals(98, i.ovulation)
        assertEquals(7, i.cycleDay)
        assertEquals(1, i.confidence)
        assertTrue(CycleMath.warnings(c, emptyList(), 90).isEmpty())
    }

    @Test fun shortAndLongFlagged() {
        assertEquals("Short cycles", CycleMath.warnings(cycles(0, 18, 38, 58), emptyList(), 60).first().title)
        assertTrue(CycleMath.warnings(cycles(0, 40, 80, 120), emptyList(), 122).any { it.title == "Long cycles" })
    }

    @Test fun variationFlagged() {
        val w = CycleMath.warnings(cycles(0, 24, 58, 82), emptyList(), 84)
        assertTrue(w.any { it.title == "Cycle length varies a lot" })
    }

    @Test fun veryLateFlagged() {
        assertTrue(CycleMath.warnings(cycles(0, 28, 56), emptyList(), 56 + 50).any { it.title == "Period is very late" })
    }

    @Test fun dailyEvents() {
        val c = listOf(PeriodCycle(startDay = 0, endDay = 4), PeriodCycle(startDay = 28, endDay = 32), PeriodCycle(startDay = 56))
        fun ev(today: Long) = CycleMath.dailyEvent(CycleMath.info(c, today)!!, CycleMath.active(c, today), today)
        assertEquals(CycleEvent.DAY1_2, ev(56))
        assertEquals(CycleEvent.DAY1_2, ev(57))
        assertEquals(CycleEvent.DAY3_PLUS, ev(58))
        assertNull(ev(70))
        assertEquals(CycleEvent.PMS, ev(81))
        assertEquals(CycleEvent.PRE, ev(82))
        assertNull(ev(84))
    }

    @Test fun betweenPeriodBleeding() {
        val w = CycleMath.warnings(cycles(0, 28, 56), listOf(PeriodDay(epochDay = 45, flow = 1)), 60)
        assertTrue(w.any { it.title == "Bleeding between periods" })
    }

    @Test fun slotsStayInDaytimeAndVary() {
        val counts = mutableSetOf<Int>()
        for (d in 20000L..20100L) {
            val s = CycleMath.slots(d, false)
            counts += s.size
            assertTrue(s.size in 1..3)
            assertTrue(s.all { it.minute in 540..1170 })
            assertEquals(s.map { it.minute }.sorted(), s.map { it.minute })
            assertEquals(s, CycleMath.slots(d, false))
            val e = CycleMath.slots(d, true).last()
            assertEquals(CycleMath.EVENING, e.id)
            assertTrue(e.minute in 1200..1244)
        }
        assertEquals(setOf(1, 2, 3), counts)
    }
}

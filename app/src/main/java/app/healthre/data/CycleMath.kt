package app.healthre.data

import kotlin.math.roundToInt

enum class Phase(val label: String) { MENSTRUAL("Period"), FOLLICULAR("Follicular"), OVULATORY("Fertile window"), LUTEAL("Luteal") }

data class CycleInfo(
    val lengths: List<Int>,
    val avgCycle: Int,
    val avgPeriod: Int,
    val nextStart: Long,
    val ovulation: Long,
    val cycleDay: Int,
    val phase: Phase,
    /** 0 = low (under 3 cycles or very variable), 1 = medium, 2 = high. */
    val confidence: Int,
) {
    val fertile get() = (ovulation - 5)..(ovulation + 1)
    val predictedPeriod get() = nextStart until nextStart + avgPeriod
}

data class CycleWarning(val severe: Boolean, val title: String, val detail: String)

enum class CycleEvent { PMS, PRE, DAY1_2, DAY3_PLUS }

object CycleMath {
    /** True while the latest period has started but no end is logged (assumed over after 10 days). */
    fun ongoing(cycles: List<PeriodCycle>, today: Long): Boolean {
        val last = cycles.maxByOrNull { it.startDay } ?: return false
        return last.endDay == null && today >= last.startDay && today - last.startDay <= 9
    }

    /** True while the latest period is under way (open, or ended today or later). */
    fun active(cycles: List<PeriodCycle>, today: Long): Boolean {
        val last = cycles.maxByOrNull { it.startDay } ?: return false
        return ongoing(cycles, today) || (last.endDay != null && today in last.startDay..last.endDay)
    }

    /** What today's notification is about, or null for a quiet day. */
    fun dailyEvent(info: CycleInfo, periodActive: Boolean, today: Long): CycleEvent? {
        val untilNext = info.nextStart - today
        return when {
            periodActive && info.cycleDay <= 2 -> CycleEvent.DAY1_2
            periodActive -> CycleEvent.DAY3_PLUS
            untilNext == 2L -> CycleEvent.PRE
            untilNext in 3L..5L -> CycleEvent.PMS
            else -> null
        }
    }

    const val DEFAULT_CYCLE = 28
    const val DEFAULT_PERIOD = 5
    private const val LUTEAL = 14

    /** Period start-to-start lengths, oldest first. */
    fun lengths(cycles: List<PeriodCycle>): List<Int> =
        cycles.sortedBy { it.startDay }.zipWithNext { a, b -> (b.startDay - a.startDay).toInt() }

    /** Null until at least one period is logged. */
    fun info(cycles: List<PeriodCycle>, today: Long): CycleInfo? {
        val sorted = cycles.sortedBy { it.startDay }
        val last = sorted.lastOrNull() ?: return null
        val lens = lengths(sorted)
        val recent = lens.takeLast(6)
        val avg = if (recent.isEmpty()) DEFAULT_CYCLE else recent.average().roundToInt()
        val periods = sorted.mapNotNull { c -> c.endDay?.let { (it - c.startDay + 1).toInt() } }.takeLast(6)
        val avgP = if (periods.isEmpty()) DEFAULT_PERIOD else periods.average().roundToInt()
        val next = last.startDay + avg
        val ov = next - LUTEAL
        val day = (today - last.startDay + 1).toInt().coerceAtLeast(1)
        val ovDay = (ov - last.startDay + 1).toInt()
        val phase = when {
            day <= avgP -> Phase.MENSTRUAL
            day < ovDay - 4 -> Phase.FOLLICULAR
            day <= ovDay + 1 -> Phase.OVULATORY
            else -> Phase.LUTEAL
        }
        val spread = if (recent.size >= 2) recent.max() - recent.min() else 0
        val conf = when { recent.size < 3 || spread > 7 -> 0; recent.size < 6 -> 1; else -> 2 }
        return CycleInfo(lens, avg, avgP, next, ov, day, phase, conf)
    }

    /** Guidance-based irregularity checks (NICHD: normal cycle 21-35 days, period up to 7). Not a diagnosis. */
    fun warnings(cycles: List<PeriodCycle>, days: List<PeriodDay>, today: Long): List<CycleWarning> {
        val sorted = cycles.sortedBy { it.startDay }
        val last = sorted.lastOrNull() ?: return emptyList()
        val out = mutableListOf<CycleWarning>()
        val recent = lengths(sorted).takeLast(6)

        recent.takeLast(3).let { r ->
            val short = r.count { it < 21 }
            val long = r.count { it > 35 }
            if (short > 0) out += CycleWarning(short >= 2, "Short cycles", "$short of your last ${r.size} cycles were under 21 days (shortest ${r.min()}).")
            if (long > 0) out += CycleWarning(long >= 2, "Long cycles", "$long of your last ${r.size} cycles were over 35 days (longest ${r.max()}).")
        }
        if (recent.size >= 3) {
            val spread = recent.max() - recent.min()
            if (spread > 7) out += CycleWarning(spread > 9, "Cycle length varies a lot", "Your cycles ranged from ${recent.min()} to ${recent.max()} days, a $spread-day difference. Over 7 is considered irregular.")
        }

        val longPeriods = sorted.takeLast(3).count { (it.endDay ?: it.startDay) - it.startDay + 1 > 7 }
        if (longPeriods > 0) out += CycleWarning(longPeriods >= 2, "Long periods", "Bleeding lasted more than 7 days in $longPeriods of your last 3 periods.")

        val since = today - last.startDay
        val ongoing = last.endDay == null && since <= 10
        if (!ongoing && recent.isNotEmpty() && since >= 45)
            out += CycleWarning(since >= 90, if (since >= 90) "No period for ${since / 30} months" else "Period is very late", "It has been $since days since your last period started.")

        val lastRange = last.startDay..(last.endDay ?: minOf(today, last.startDay + 8))
        if (days.count { it.epochDay in lastRange && it.flow == 4 } >= 4)
            out += CycleWarning(false, "Consistently heavy flow", "Heavy flow on 4 or more days of your last period.")

        val ranges = sorted.map { it.startDay..(it.endDay ?: minOf(today, it.startDay + DEFAULT_PERIOD + 2)) }
        val between = days.filter { it.flow >= 1 && it.epochDay >= today - 90 && ranges.none { r -> it.epochDay in r } }
        if (between.isNotEmpty()) out += CycleWarning(false, "Bleeding between periods", "Spotting or bleeding was logged on ${between.size} day(s) outside your periods in the last 3 months.")
        return out
    }
}

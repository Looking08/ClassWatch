package com.classwatch.shared

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

/**
 * 课表计算引擎 —— 整个 App 的「大脑」。
 *
 * 它只做一件事：给一个时间点，算出此刻该显示什么。
 * 纯函数、无副作用、不依赖 Android，所以手表端、手机端、Tile 小组件都能复用。
 */

// ---------------------------------------------------------------- 状态定义

/** 单节课相对此刻的状态。 */
enum class ItemState { PAST, ONGOING, SOON }

/**
 * 一天当中整体所处的阶段：
 * - NOW    正在上课
 * - BREAK  课间（上一节已下课，下一节还没开始）
 * - BEFORE 今天还没上第一节
 * - OVER   今天的课全部结束
 * - FREE   今天没排课 / 假期
 */
enum class Phase { NOW, BREAK, BEFORE, OVER, FREE }

/** 一条已经落到具体时间上的课程。 */
data class Slot(
    val course: Course,
    val startMinute: Int,  // 距 00:00 的分钟数
    val endMinute: Int
) {
    val durationMinute: Int get() = (endMinute - startMinute).coerceAtLeast(1)
}

data class DayItem(
    val slot: Slot,
    val state: ItemState,
    /** 当前节次已过去的进度 0f..1f，仅 [ItemState.ONGOING] 有意义 */
    val progress: Float,
    /** 到下课/上课还剩多少分钟 */
    val minutesLeft: Int
)

data class Snapshot(
    val phase: Phase,
    val date: LocalDate,
    val dayOfWeek: Int,          // 1..7
    val week: Int,               // 教学周，可能超出 1..totalWeeks
    val inSemester: Boolean,     // false = 寒暑假，一律按 FREE 处理
    val nowMinute: Float,
    val current: DayItem?,
    val next: DayItem?,
    val items: List<DayItem>,    // 当天全部课程，按时间升序
    /** Hero 区要用到的「还剩几分钟」与「进度」，课间时为距下一节上课 */
    val heroMinutesLeft: Int,
    val heroProgress: Float,
    /** 本次计算是否启用了单双周过滤（来自 Timetable.oddEvenEnabled） */
    val oddEvenEnabled: Boolean = true
) {
    /** 单/双周标签。关掉单双周总开关后返回空串，界面上就不显示这一项。 */
    val oddEvenLabel: String get() = when {
        !inSemester -> "假期"
        !oddEvenEnabled -> ""
        week % 2 == 1 -> "单周"
        else -> "双周"
    }
    val dayLabel: String get() = "周" + "一二三四五六日"[dayOfWeek - 1]
}

// ---------------------------------------------------------------- 引擎

object ScheduleEngine {

    // ---- 时间工具 ----

    /** "08:45" -> 525 */
    fun parseTime(t: String): Int {
        val parts = t.split(":")
        if (parts.size != 2) return 0
        val h = parts[0].trim().toIntOrNull() ?: 0
        val m = parts[1].trim().toIntOrNull() ?: 0
        return h * 60 + m
    }

    /** 525 -> "08:45" */
    fun formatTime(minute: Int): String {
        val clamped = minute.coerceIn(0, 24 * 60)
        return "%02d:%02d".format(clamped / 60, clamped % 60)
    }

    private fun startOfWeek1(t: Timetable): LocalDate = try {
        LocalDate.parse(t.semester.startDate)
    } catch (e: DateTimeParseException) {
        LocalDate.now()
    }

    /**
     * 给定日期属于第几教学周；开学前返回 0 或负数。
     *
     * 必须用 floorDiv 而不是 `/`：Java/Kotlin 的整数除法是向零截断的，
     * 开学前 1~6 天用 `/` 会被算成「第 1 周」，直接把假期算成上课周。
     */
    fun weekOf(t: Timetable, date: LocalDate): Int {
        val days = java.time.temporal.ChronoUnit.DAYS.between(startOfWeek1(t), date)
        return (Math.floorDiv(days, 7) + 1).toInt()
    }

    fun isInSemester(t: Timetable, date: LocalDate): Boolean {
        val w = weekOf(t, date)
        return w in 1..t.semester.totalWeeks
    }

    /**
     * 周次过滤：这门课在这一周要不要上。
     *
     * 单双周总开关 [Timetable.oddEvenEnabled] 关掉后，只要课程配了周次就一律算「要上」，
     * 但仍要排除 weeks 为空的课程 —— 那是用户明确标了「从不排课」的。
     */
    fun weekMatches(t: Timetable, weeks: List<Int>, week: Int): Boolean {
        if (weeks.isEmpty()) return false
        return !t.oddEvenEnabled || weeks.contains(week)
    }

    // ---- 核心查询 ----

    /** 某一天实际要上的课，已按开始时间升序排列。 */
    fun slotsOfDay(t: Timetable, date: LocalDate): List<Slot> {
        val dow = if (date.dayOfWeek.value == 7) 7 else date.dayOfWeek.value
        val week = weekOf(t, date)
        val periodSize = t.periods.size
        val periodByIndex = t.periods.associateBy { it.index }

        return t.courses
            .asSequence()
            .filter { it.day == dow }
            .filter { weekMatches(t, it.weeks, week) }
            .mapNotNull { c ->
                val pStart = periodByIndex[c.startNode]
                val pEnd = periodByIndex[c.endNode]
                if (pStart == null || pEnd == null || periodSize == 0) return@mapNotNull null
                Slot(c, parseTime(pStart.start), parseTime(pEnd.end))
            }
            .sortedBy { it.startMinute }
            .toList()
    }

    /**
     * 计算某一时刻的完整状态快照。手表表盘、主界面、Tile 都调用它。
     *
     * @param now 当前时刻；传 dateOverride / weekOverride 可以做「模拟时间」，方便调试
     */
    fun snapshot(
        t: Timetable,
        now: LocalDateTime = LocalDateTime.now(),
        dateOverride: LocalDate? = null,
        weekOverride: Int? = null
    ): Snapshot {
        val date = dateOverride ?: now.toLocalDate()
        val week = weekOverride ?: weekOf(t, date)
        val inSemester = week in 1..t.semester.totalWeeks
        val dow = if (date.dayOfWeek.value == 7) 7 else date.dayOfWeek.value
        val nowMin = now.hour * 60 + now.minute + now.second / 60f

        val raw = if (inSemester || weekOverride != null) slotsOfDayAt(t, dow, week) else emptyList()

        val items = raw.map { slot ->
            val (state, progress, left) = when {
                nowMin < slot.startMinute ->
                    Triple(ItemState.SOON, 0f, (slot.startMinute - nowMin).roundToInt())
                nowMin < slot.endMinute ->
                    Triple(
                        ItemState.ONGOING,
                        ((nowMin - slot.startMinute) / slot.durationMinute).coerceIn(0f, 1f),
                        (slot.endMinute - nowMin).roundToInt()
                    )
                else ->
                    Triple(ItemState.PAST, 1f, 0)
            }
            DayItem(slot, state, progress, left)
        }

        val current = items.firstOrNull { it.state == ItemState.ONGOING }
        val next = items.firstOrNull { it.state == ItemState.SOON }

        val oddEven = t.oddEvenEnabled

        return when {
            current != null -> Snapshot(
                Phase.NOW, date, dow, week, inSemester, nowMin, current, null, items,
                current.minutesLeft, current.progress, oddEven
            )
            next != null -> {
                val prev = items.lastOrNull { it.state == ItemState.PAST }
                if (prev != null) {
                    val gap = (next.slot.startMinute - prev.slot.endMinute).coerceAtLeast(1)
                    Snapshot(
                        Phase.BREAK, date, dow, week, inSemester, nowMin, null, next, items,
                        next.minutesLeft, ((nowMin - prev.slot.endMinute) / gap).coerceIn(0f, 1f),
                        oddEven
                    )
                } else {
                    Snapshot(
                        Phase.BEFORE, date, dow, week, inSemester, nowMin, null, next, items,
                        next.minutesLeft, 0f, oddEven
                    )
                }
            }
            items.isNotEmpty() -> Snapshot(
                Phase.OVER, date, dow, week, inSemester, nowMin, null, null, items, 0, 1f, oddEven
            )
            else -> Snapshot(
                Phase.FREE, date, dow, week, inSemester, nowMin, null, null, items, 0, 1f, oddEven
            )
        }
    }

    /** slotsOfDay 的变体：允许直接给星期和周次，便于模拟。 */
    fun slotsOfDayAt(t: Timetable, dow: Int, week: Int): List<Slot> {
        val periodByIndex = t.periods.associateBy { it.index }
        return t.courses
            .asSequence()
            .filter { it.day == dow }
            .filter { weekMatches(t, it.weeks, week) }
            .mapNotNull { c ->
                val pStart = periodByIndex[c.startNode] ?: return@mapNotNull null
                val pEnd = periodByIndex[c.endNode] ?: return@mapNotNull null
                Slot(c, parseTime(pStart.start), parseTime(pEnd.end))
            }
            .sortedBy { it.startMinute }
            .toList()
    }

    /**
     * 找「下一次要上的课」，可能是今天也可能是之后某天。
     * 用于「今日课程已结束」之后显示「明天 08:00 操作系统」。
     */
    fun findUpcoming(
        t: Timetable,
        now: LocalDateTime = LocalDateTime.now(),
        maxDays: Int = 14
    ): Triple<LocalDate, Slot, Int>? {
        val nowMin = now.hour * 60 + now.minute
        repeat(maxDays) { i ->
            val date = now.toLocalDate().plusDays(i.toLong())
            val week = weekOf(t, date)
            val dow = if (date.dayOfWeek.value == 7) 7 else date.dayOfWeek.value
            val today = slotsOfDayAt(t, dow, week)
            val hit = if (i == 0) today.firstOrNull { it.startMinute > nowMin } else today.firstOrNull()
            if (hit != null) return Triple(date, hit, week)
        }
        return null
    }

    // ---- 周次文本解析 ----

    /**
     * 把用户输入的周次文本解析成周次列表。
     * 支持：
     *  - ""         -> 全学期
     *  - "1-8,10-16"
     *  - "1-16单"   -> 只取单周
     *  - "2,4,6,8"  -> 枚举
     */
    fun parseWeeks(input: String, totalWeeks: Int): List<Int> {
        val raw = input.trim().replace("，", ",")
        if (raw.isEmpty()) return (1..totalWeeks).toList()

        val oddEven: Int? = when {
            raw.endsWith("单") -> 1
            raw.endsWith("双") -> 0
            else -> null
        }
        val body = if (oddEven != null) raw.dropLast(1) else raw

        val set = LinkedHashSet<Int>()
        body.split(",").forEach { seg ->
            val s = seg.trim()
            if (s.isEmpty()) return@forEach
            if (s.contains("-")) {
                val (a, b) = s.split("-", limit = 2)
                val from = a.trim().toIntOrNull() ?: return@forEach
                val to = b.trim().toIntOrNull() ?: return@forEach
                for (i in from..to) set.add(i)
            } else {
                s.toIntOrNull()?.let { set.add(it) }
            }
        }
        var result = set.filter { it in 1..totalWeeks }.sorted()
        if (oddEven != null) result = result.filter { it % 2 == oddEven }
        return result
    }

    /** 反向：把周次列表压缩成 "1-8,10-16" 这样的文本。 */
    fun formatWeeks(weeks: List<Int>): String {
        if (weeks.isEmpty()) return ""
        val sorted = weeks.distinct().sorted()
        val out = mutableListOf<String>()
        var i = 0
        while (i < sorted.size) {
            var j = i
            while (j + 1 < sorted.size && sorted[j + 1] == sorted[j] + 1) j++
            out += if (i == j) "${sorted[i]}" else "${sorted[i]}-${sorted[j]}"
            i = j + 1
        }
        return out.joinToString(",")
    }
}

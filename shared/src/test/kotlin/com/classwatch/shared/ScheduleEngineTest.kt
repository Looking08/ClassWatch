package com.classwatch.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 课表引擎的行为测试。
 *
 * 这里覆盖的是最容易出错、也最影响体感的几件事：
 * 周次推算、单双周过滤、以及「此刻到底是什么状态」的判定。
 */
class ScheduleEngineTest {

    // 学期第 1 周的周一固定为 2026-09-07（确实是周一）
    private val semester = Semester("test", "2026-09-07", 16)

    private fun table(courses: List<Course>) =
        Timetable(semester = semester, periods = defaultPeriods(), courses = courses)

    private fun course(
        day: Int, sn: Int, en: Int, weeks: List<Int>, name: String = "课"
    ) = Course("id", name, "", "地点", day, sn, en, weeks, 0)

    // ------------------------------------------------------------ 周次

    @Test
    fun `学期第一周的周一是第 1 周`() {
        assertEquals(1, ScheduleEngine.weekOf(table(emptyList()), LocalDate.parse("2026-09-07")))
        assertEquals(1, ScheduleEngine.weekOf(table(emptyList()), LocalDate.parse("2026-09-13")))
        assertEquals(2, ScheduleEngine.weekOf(table(emptyList()), LocalDate.parse("2026-09-14")))
    }

    @Test
    fun `开学前是第 0 周或负数，不算在学期内`() {
        val t = table(emptyList())
        assertEquals(0, ScheduleEngine.weekOf(t, LocalDate.parse("2026-09-06")))
        assertFalse(ScheduleEngine.isInSemester(t, LocalDate.parse("2026-09-06")))
    }

    @Test
    fun `超过总周数后不再算学期内`() {
        val t = table(emptyList())
        assertTrue(ScheduleEngine.isInSemester(t, LocalDate.parse("2026-12-27"))) // 第 16 周
        assertFalse(ScheduleEngine.isInSemester(t, LocalDate.parse("2027-01-04")))
    }

    // ------------------------------------------------------------ 周次文本解析

    @Test
    fun `周次文本解析`() {
        assertEquals((1..16).toList(), ScheduleEngine.parseWeeks("", 16))
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16),
            ScheduleEngine.parseWeeks("1-8,10-16", 16))
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15), ScheduleEngine.parseWeeks("1-16单", 16))
        assertEquals(listOf(2, 4, 6, 8), ScheduleEngine.parseWeeks("2,4,6,8", 16))
        assertEquals(listOf(2, 4, 6, 8), ScheduleEngine.parseWeeks("1-8双", 8))

        // 越界的周次要被裁掉
        assertEquals(listOf(1, 2, 3), ScheduleEngine.parseWeeks("1-30", 3))
    }

    @Test
    fun `周次文本反向压缩`() {
        assertEquals("1-8,10-16", ScheduleEngine.formatWeeks(
            listOf(1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16)))
        assertEquals("1,3,5", ScheduleEngine.formatWeeks(listOf(1, 3, 5)))
    }

    // ------------------------------------------------------------ 时间工具

    @Test
    fun `时间解析与格式化`() {
        assertEquals(480, ScheduleEngine.parseTime("08:00"))
        assertEquals(575, ScheduleEngine.parseTime("09:35"))
        assertEquals("08:00", ScheduleEngine.formatTime(480))
        assertEquals("09:35", ScheduleEngine.formatTime(575))
    }

    // ------------------------------------------------------------ 状态机

    private fun at(date: String, time: String): LocalDateTime =
        LocalDateTime.parse("${date}T$time:00")

    @Test
    fun `上课中：有当前课，进度与剩余分钟正确`() {
        // 2026-09-07 是周一，第 1 周
        val t = table(listOf(course(day = 1, sn = 1, en = 2, weeks = (1..16).toList())))
        val snap = ScheduleEngine.snapshot(t, at("2026-09-07", "08:30"))
        assertEquals(Phase.NOW, snap.phase)
        assertNotNull(snap.current)
        assertEquals(480, snap.current!!.slot.startMinute)   // 第1节 08:00 开始
        assertEquals(575, snap.current!!.slot.endMinute)     // 第2节 09:35 结束
        // 08:30 时：已过 30 分钟 / 总长 95 分钟，剩 65 分钟
        assertEquals(30 / 95f, snap.heroProgress, 0.001f)
        assertEquals(65, snap.heroMinutesLeft)
        assertEquals(1, snap.items.size)
        assertEquals(ItemState.ONGOING, snap.items[0].state)
    }

    @Test
    fun `课间：第一节已结束、第二节未开始`() {
        val t = table(
            listOf(
                course(day = 1, sn = 1, en = 2, weeks = (1..16).toList(), name = "第一节"),
                course(day = 1, sn = 6, en = 7, weeks = (1..16).toList(), name = "第二节")
            )
        )
        // 09:35 下课，14:00 才上课 —— 10:00 处于课间
        val snap = ScheduleEngine.snapshot(t, at("2026-09-07", "10:00"))
        assertEquals(Phase.BREAK, snap.phase)
        assertNull(snap.current)
        assertNotNull(snap.next)
        assertEquals("第二节", snap.next!!.slot.course.name)
        assertEquals(240, snap.next!!.minutesLeft) // 14:00 - 10:00
        assertEquals(ItemState.PAST, snap.items[0].state)
        assertEquals(ItemState.SOON, snap.items[1].state)
    }

    @Test
    fun `课前：今天还没上第一节`() {
        val t = table(listOf(course(day = 1, sn = 1, en = 2, weeks = (1..16).toList())))
        val snap = ScheduleEngine.snapshot(t, at("2026-09-07", "07:00"))
        assertEquals(Phase.BEFORE, snap.phase)
        assertEquals(60, snap.next!!.minutesLeft)
    }

    @Test
    fun `放学：今天的课都上完了`() {
        val t = table(listOf(course(day = 1, sn = 1, en = 2, weeks = (1..16).toList())))
        val snap = ScheduleEngine.snapshot(t, at("2026-09-07", "10:30"))
        assertEquals(Phase.OVER, snap.phase)
        assertTrue(snap.items.all { it.state == ItemState.PAST })
    }

    @Test
    fun `没课：不在有效周次内`() {
        // 只在双周排课，第 1 周（单周）不该出现
        val t = table(listOf(course(day = 1, sn = 1, en = 2, weeks = (2..16 step 2).toList())))
        val week1 = ScheduleEngine.snapshot(t, at("2026-09-07", "08:30"))
        assertEquals(1, week1.week)
        assertEquals(Phase.FREE, week1.phase)
        assertTrue(week1.items.isEmpty())

        // 第 2 周（双周）就该出现
        val week2 = ScheduleEngine.snapshot(t, at("2026-09-14", "08:30"))
        assertEquals(2, week2.week)
        assertEquals(Phase.NOW, week2.phase)
    }

    @Test
    fun `星期过滤：周一的课不会出现在周二`() {
        val t = table(listOf(course(day = 1, sn = 1, en = 2, weeks = (1..16).toList())))
        val snap = ScheduleEngine.snapshot(t, at("2026-09-08", "08:30")) // 周二
        assertEquals(2, snap.dayOfWeek)
        assertTrue(snap.items.isEmpty())
    }

    @Test
    fun `连堂课的时间跨度取首节开始到末节结束`() {
        val t = table(listOf(course(day = 5, sn = 10, en = 12, weeks = (1..16).toList())))
        val slots = ScheduleEngine.slotsOfDay(t, LocalDate.parse("2026-09-11")) // 周五
        assertEquals(1, slots.size)
        assertEquals(ScheduleEngine.parseTime("18:30"), slots[0].startMinute)
        assertEquals(ScheduleEngine.parseTime("20:55"), slots[0].endMinute)
    }

    @Test
    fun `今日结束后能找到下一次课`() {
        val t = table(
            listOf(
                course(day = 1, sn = 1, en = 2, weeks = (1..16).toList(), name = "周一课"),
                course(day = 2, sn = 6, en = 7, weeks = (1..16).toList(), name = "周二课")
            )
        )
        val found = ScheduleEngine.findUpcoming(t, at("2026-09-07", "18:00"))
        assertNotNull(found)
        assertEquals("周二课", found!!.second.course.name)
        assertEquals(LocalDate.parse("2026-09-08"), found.first)
    }

    // ------------------------------------------------------------ 序列化往返

    @Test
    fun `JSON 序列化往返不丢字段`() {
        val t = table(listOf(course(day = 3, sn = 6, en = 7, weeks = listOf(1, 3, 5), name = "概率论")))
        val json = TimetableCodec.encode(t)
        val back = TimetableCodec.decode(json)
        assertNotNull(back)
        assertEquals("概率论", back!!.courses[0].name)
        assertEquals(3, back.courses[0].day)
        assertEquals(listOf(1, 3, 5), back.courses[0].weeks)
        assertEquals(t.semester.startDate, back.semester.startDate)
        assertEquals(12, back.periods.size)
    }

    @Test
    fun `坏 JSON 不会崩，返回 null`() {
        assertNull(TimetableCodec.decode("这不是 JSON"))
    }

    // ------------------------------------------------------------ 单双周总开关

    @Test
    fun `开关打开时按周次过滤`() {
        // 只在单周（1、3、5…）上的课
        val t = table(listOf(course(day = 1, sn = 1, en = 2, weeks = listOf(1, 3, 5, 7), name = "单周课")))
        assertTrue(t.oddEvenEnabled)
        assertEquals(1, ScheduleEngine.slotsOfDay(t, LocalDate.parse("2026-09-07")).size)  // 第1周
        assertEquals(0, ScheduleEngine.slotsOfDay(t, LocalDate.parse("2026-09-14")).size)  // 第2周
    }

    @Test
    fun `关掉开关后忽略周次，每周都上`() {
        val t = table(listOf(course(day = 1, sn = 1, en = 2, weeks = listOf(1, 3, 5, 7), name = "单周课")))
            .copy(oddEvenEnabled = false)
        assertEquals(1, ScheduleEngine.slotsOfDay(t, LocalDate.parse("2026-09-07")).size)  // 第1周
        assertEquals(1, ScheduleEngine.slotsOfDay(t, LocalDate.parse("2026-09-14")).size)  // 第2周也上
        assertEquals(1, ScheduleEngine.slotsOfDay(t, LocalDate.parse("2026-09-21")).size)  // 第3周
    }

    @Test
    fun `关掉开关也不会让 weeks 为空的课程出现`() {
        val t = table(listOf(course(day = 1, sn = 1, en = 2, weeks = emptyList(), name = "从不排课")))
            .copy(oddEvenEnabled = false)
        assertEquals(0, ScheduleEngine.slotsOfDay(t, LocalDate.parse("2026-09-07")).size)
    }

    @Test
    fun `关掉开关后单双周标签为空`() {
        val t = table(listOf(course(day = 1, sn = 1, en = 2, weeks = listOf(1, 3), name = "课")))
        val on = ScheduleEngine.snapshot(t, at("2026-09-07", "10:00"))   // 第 1 周
        val off = ScheduleEngine.snapshot(t.copy(oddEvenEnabled = false), at("2026-09-07", "10:00"))
        assertEquals("单周", on.oddEvenLabel)
        assertEquals("", off.oddEvenLabel)
        assertFalse(off.oddEvenEnabled)
    }
}

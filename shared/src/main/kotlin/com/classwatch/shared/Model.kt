package com.classwatch.shared

import com.google.gson.annotations.SerializedName

/**
 * 课表数据模型。
 *
 * 这一套 model 是手表端和手机端**共用**的：手机负责生成，手表负责展示，
 * 两边通过同一个 JSON 结构互相理解。所以改这里要同时想清楚两端。
 */

/** 学期。startDate 必须是「第 1 周周一」的日期，格式 yyyy-MM-dd。 */
data class Semester(
    @SerializedName("name") val name: String = "2026-2027 秋季学期",
    @SerializedName("startDate") val startDate: String = "2026-09-07",
    @SerializedName("totalWeeks") val totalWeeks: Int = 18
)

/** 节次时间表。index 从 1 开始：第 1 节、第 2 节…… */
data class Period(
    @SerializedName("i") val index: Int,
    @SerializedName("s") val start: String, // "08:00"
    @SerializedName("e") val end: String    // "08:45"
)

/**
 * 一门课。
 *
 * @param day      星期，1=周一 … 7=周日
 * @param startNode 起始节次
 * @param endNode   结束节次（连堂就填更大的值）
 * @param weeks    有效周次列表，例如 listOf(1,2,3,4) 或 listOf(1,3,5,7)。空=从不排课
 * @param color    配色档位，0..7，对应 [CoursePalette.colors]
 */
data class Course(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("teacher") val teacher: String = "",
    @SerializedName("location") val location: String = "",
    @SerializedName("day") val day: Int,
    @SerializedName("sn") val startNode: Int,
    @SerializedName("en") val endNode: Int,
    @SerializedName("weeks") val weeks: List<Int>,
    @SerializedName("color") val color: Int = 0
)

/**
 * @param oddEvenEnabled 单双周总开关。
 *   true（默认）→ 按每门课自己的 weeks 过滤，1、3、5 周上的课就只在单周出现；
 *   false        → 忽略周次，只要这天有课就显示（相当于「每周都上」）。
 *   有些学校前半学期单双周、后半学期全周上，或者干脆不分单双周，
 *   这时候关掉总开关就不用把每门课的周次重写一遍。
 *   注意：weeks 为空的课程表示「从不排课」，关掉开关也一样不会出现。
 */
data class Timetable(
    @SerializedName("version") val version: Int = 1,
    @SerializedName("semester") val semester: Semester = Semester(),
    @SerializedName("periods") val periods: List<Period> = defaultPeriods(),
    @SerializedName("courses") val courses: List<Course> = emptyList(),
    @SerializedName("oddEvenEnabled") val oddEvenEnabled: Boolean = true
)

/** 默认节次时间表，采用国内高校常见的 12 节制。可以在手机端修改。 */
fun defaultPeriods(): List<Period> = listOf(
    Period(1, "08:00", "08:45"),
    Period(2, "08:50", "09:35"),
    Period(3, "09:55", "10:40"),
    Period(4, "10:45", "11:30"),
    Period(5, "11:35", "12:20"),
    Period(6, "14:00", "14:45"),
    Period(7, "14:50", "15:35"),
    Period(8, "15:55", "16:40"),
    Period(9, "16:45", "17:30"),
    Period(10, "18:30", "19:15"),
    Period(11, "19:20", "20:05"),
    Period(12, "20:10", "20:55")
)

/** 8 档课程配色，索引与 [Course.color] 对应。值为 0xAARRGGBB。 */
object CoursePalette {
    val colors = longArrayOf(
        0xFF5B8CFF, // 0 蓝
        0xFF4CD7A0, // 1 青绿
        0xFFFFB64C, // 2 琥珀
        0xFFFF6B6B, // 3 珊瑚红
        0xFFA475FF, // 4 紫
        0xFF38C6E8, // 5 天蓝
        0xFFFF8FAB, // 6 粉
        0xFF9CCC65  // 7 嫩绿
    )
    fun of(index: Int): Long = colors.getOrElse(index % colors.size) { colors[0] }
}

/** 样例课表，方便第一次启动就有东西可看，用户随时可以在手机端全删。 */
fun sampleTimetable(): Timetable = Timetable(
    courses = listOf(
        Course("c1", "高等数学A2", "张伟", "教三-301", day = 1, 1, 2, (1..16).toList(), 0),
        Course("c2", "数据结构", "李娜", "实验楼B-112", day = 1, 6, 7, (1..16).toList(), 4),
        Course("c3", "大学英语", "Sarah", "外语楼 402", day = 2, 3, 4, (1..16).toList(), 1),
        Course("c4", "线性代数", "王强", "教二-208", day = 2, 8, 9, (1..16).toList(), 2),
        Course("c5", "计算机组成原理", "陈晨", "信息楼 5F 报告厅", day = 2, 10, 11, (1..16).toList(), 5),
        Course("c6", "操作系统", "刘洋", "信息楼 305", day = 3, 1, 2, (1..16).toList(), 0),
        Course("c7", "概率论", "赵敏", "教三-405", day = 3, 6, 7, (1..16).toList(), 3),
        Course("c8", "编译原理", "周涛", "信息楼 402", day = 4, 3, 4, (1..16).toList(), 4),
        Course("c9", "软件工程", "郑雪", "信息楼 201", day = 5, 1, 2, (1..16).toList(), 1),
        Course("c10", "数据库实验", "李娜", "实验楼A-305", day = 5, 10, 12, (2..16 step 2).toList(), 2)
    )
)

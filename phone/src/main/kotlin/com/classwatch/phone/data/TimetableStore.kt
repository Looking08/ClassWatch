package com.classwatch.phone.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.classwatch.shared.Course
import com.classwatch.shared.Period
import com.classwatch.shared.ScheduleEngine
import com.classwatch.shared.Semester
import com.classwatch.shared.Timetable
import com.classwatch.shared.TimetableCodec
import com.classwatch.shared.sampleTimetable
import java.io.File

/**
 * 手机端课表数据源。
 *
 * 做成一个单例 object 是为了省掉 ViewModel/DI 那一整套依赖，
 * 对「就想快速把课表同步到手表上」这件事来说，够用了。
 *
 * timetable 是 Compose 的 MutableState，改它界面会自动刷新。
 */
object TimetableStore {

    private const val TAG = "TimetableStore"
    private const val FILE_NAME = "timetable.json"

    private lateinit var file: File

    var timetable: Timetable by mutableStateOf(sampleTimetable())
        private set

    fun init(context: Context) {
        if (::file.isInitialized) return
        val dir = context.applicationContext.filesDir
        file = File(dir, FILE_NAME)
        val saved = runCatching { TimetableCodec.decode(file.readText()) }.getOrNull()
        timetable = saved ?: sampleTimetable()
    }

    // ------------------------------------------------------------ 写操作

    fun update(t: Timetable) {
        timetable = t
        persist()
    }

    fun upsertCourse(course: Course) {
        val list = timetable.courses.toMutableList()
        val idx = list.indexOfFirst { it.id == course.id }
        if (idx >= 0) list[idx] = course else list.add(course)
        update(timetable.copy(courses = list.sortedWith(compareBy({ it.day }, { it.startNode }))))
    }

    fun removeCourse(id: String) {
        update(timetable.copy(courses = timetable.courses.filter { it.id != id }))
    }

    fun clearAll() {
        update(timetable.copy(courses = emptyList()))
    }

    fun setSemester(semester: Semester) {
        update(timetable.copy(semester = semester))
    }

    /** 单双周总开关：关掉后忽略周次，这天有课就显示。 */
    fun setOddEvenEnabled(enabled: Boolean) {
        update(timetable.copy(oddEvenEnabled = enabled))
    }

    fun setPeriod(index: Int, start: String, end: String) {
        val list = timetable.periods.toMutableList()
        val idx = list.indexOfFirst { it.index == index }
        val p = Period(index, start, end)
        if (idx >= 0) list[idx] = p else list.add(p)
        // 改完时间立刻按时间重新排序并连带修正课程引用，避免「第 3 节比第 5 节还晚」
        sortPeriodsByTime(list)
    }

    /** 追加一节：默认接着最后一节往后排 45 分钟 + 10 分钟课间。 */
    fun addPeriod() {
        val list = timetable.periods.toMutableList()
        val nextIndex = (list.maxOfOrNull { it.index } ?: 0) + 1
        val last = list.maxByOrNull { ScheduleEngine.parseTime(it.end) }
        val start = last?.let { ScheduleEngine.parseTime(it.end) + 10 } ?: (8 * 60)
        val end = start + 45
        list.add(
            Period(
                nextIndex,
                ScheduleEngine.formatTime(start),
                ScheduleEngine.formatTime(end.coerceAtMost(23 * 60 + 55))
            )
        )
        sortPeriodsByTime(list)
    }

    /**
     * 删除一节。调用方必须先确认 [canRemovePeriod]，否则课程会引用到不存在的节次。
     */
    fun removePeriod(index: Int) {
        val list = timetable.periods.filter { it.index != index }.toMutableList()
        sortPeriodsByTime(list)
    }

    /** 这一节有没有被课程用到；被引用的节次不允许删除。 */
    fun canRemovePeriod(index: Int): Boolean =
        timetable.courses.none { it.startNode == index || it.endNode == index }

    /**
     * 按开始时间排序，并重新编号 1..n。
     *
     * 重新编号后，课程里存的 sn/en 是「旧编号」，必须一起映射过去，
     * 否则排完序课程就串节次了。
     */
    /** 手动触发一次「按时间排序」（编辑页的「按时间排序」按钮用）。 */
    fun sortPeriodsByTime() {
        sortPeriodsByTime(timetable.periods.toMutableList())
    }

    fun sortPeriodsByTime(list: MutableList<Period>) {
        val sorted = list.sortedWith(compareBy({ ScheduleEngine.parseTime(it.start) }, { it.index }))
        val oldToNew = sorted.mapIndexed { i, p -> p.index to (i + 1) }.toMap()
        val newPeriods = sorted.mapIndexed { i, p -> p.copy(index = i + 1) }
        val newCourses = timetable.courses.map { c ->
            c.copy(
                startNode = oldToNew[c.startNode] ?: c.startNode,
                endNode = oldToNew[c.endNode] ?: c.endNode
            )
        }
        update(timetable.copy(periods = newPeriods, courses = newCourses))
    }

    /** 时间（分钟）→ 最接近的节次编号，用于把用户填的时间落回节次体系。 */
    fun nodeOfTime(minute: Int, useEnd: Boolean = false): Int {
        val p = timetable.periods.minByOrNull {
            kotlin.math.abs(ScheduleEngine.parseTime(if (useEnd) it.end else it.start) - minute)
        }
        return p?.index ?: 1
    }

    /** 节次编号 → 该节的开始/结束时间文本，用于回显到输入框。 */
    fun timeOfNode(index: Int, useEnd: Boolean = false): String =
        (timetable.periods.firstOrNull { it.index == index } ?: timetable.periods.firstOrNull())
            ?.let { if (useEnd) it.end else it.start } ?: "08:00"

    fun importJson(json: String): Boolean {
        val parsed = TimetableCodec.decode(json) ?: return false
        update(parsed)
        return true
    }

    fun exportJson(): String = TimetableCodec.encode(timetable)

    private fun persist() {
        runCatching {
            if (!file.exists()) file.createNewFile()
            file.writeText(TimetableCodec.encode(timetable))
        }.onFailure { Log.e(TAG, "保存课表失败", it) }
    }

    // ------------------------------------------------------------ 便捷读取

    /** 生成下一个课程 id，避免手搓。 */
    fun nextId(): String = "c" + System.currentTimeMillis()

    /** 把 [weeks] 压缩成 "1-8,10-16" 这样的文本，用于回显到输入框。 */
    fun weeksToText(weeks: List<Int>): String = ScheduleEngine.formatWeeks(weeks)
}

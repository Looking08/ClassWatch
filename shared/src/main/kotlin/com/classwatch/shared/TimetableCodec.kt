package com.classwatch.shared

import com.google.gson.GsonBuilder

/**
 * Timetable <-> JSON 的读写。
 *
 * 手机端编辑完课表后序列化成 JSON，通过局域网（或 adb）送到手表，
 * 手表反序列化回来。两端共用这一个编解码器，避免格式漂移。
 */
object TimetableCodec {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun encode(t: Timetable): String = gson.toJson(t)

    fun decode(json: String): Timetable? = try {
        val parsed = gson.fromJson(json, Timetable::class.java)
        if (parsed == null) null else normalized(parsed)
    } catch (t: Throwable) {
        null
    }

    /** 兜底修正：防止手改 JSON 时缺字段导致崩溃。 */
    private fun normalized(t: Timetable): Timetable = t.copy(
        periods = t.periods.ifEmpty { defaultPeriods() },
        courses = t.courses.filter { it.startNode >= 1 && it.endNode >= it.startNode }
    )
}

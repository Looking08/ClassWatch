package com.classwatch.watch.data

import android.content.Context
import android.util.Log
import com.classwatch.shared.ScheduleEngine
import com.classwatch.shared.Timetable
import com.classwatch.shared.TimetableCodec
import com.classwatch.shared.sampleTimetable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 手表端的课表仓库。
 *
 * 职责：
 *  1. 启动时从本地文件读取课表（没有就用样例课表兜底）
 *  2. 同步通道收到新 JSON 后写盘并更新 StateFlow
 *  3. UI 只观察 StateFlow，自动重组
 */
class ScheduleRepository(context: Context) {

    private val appContext = context.applicationContext
    private val file: File get() = File(appContext.filesDir, FILE_NAME)

    private val _timetable = MutableStateFlow(readFromDisk() ?: sampleTimetable())
    val timetable: StateFlow<Timetable> = _timetable.asStateFlow()

    /** 同步状态，UI 用来显示「已连接 / 已更新」 */
    private val _lastSync = MutableStateFlow<Long?>(null)
    val lastSync: StateFlow<Long?> = _lastSync.asStateFlow()

    /**
     * 收到手机推来的 JSON。
     * @return 是否解析成功
     */
    suspend fun accept(json: String): Boolean = withContext(Dispatchers.IO) {
        val parsed = TimetableCodec.decode(json)
        if (parsed == null) {
            Log.w(TAG, "收到无法解析的课表 JSON")
            false
        } else {
            runCatching { file.writeText(json) }
                .onFailure { Log.e(TAG, "写盘失败", it) }
            _timetable.value = parsed
            _lastSync.value = System.currentTimeMillis()
            Log.i(TAG, "课表已更新：${parsed.courses.size} 门课")
            true
        }
    }

    /** 手动导入（比如用系统文件选择器选一个 timetable.json）。 */
    suspend fun importFrom(uriJson: String): Boolean = accept(uriJson)

    /** 重置为样例课表。 */
    suspend fun reset() = accept(TimetableCodec.encode(sampleTimetable()))

    private fun readFromDisk(): Timetable? {
        if (!file.exists()) return null
        return runCatching {
            TimetableCodec.decode(file.readText())
        }.onFailure { Log.w(TAG, "本地课表损坏，改用样例", it) }.getOrNull()
    }

    /** 当前 App 进程生命周期，用于兜底：确保分线程里更新也是安全的。 */
    fun observe(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            // 每次读取时顺带修正一次（跨天时没有任何副作用，保留钩子）
            val t = _timetable.value
            if (t.periods.isEmpty()) _timetable.value = sampleTimetable()
        }
    }

    companion object {
        private const val TAG = "ScheduleRepository"
        const val FILE_NAME = "timetable.json"
    }
}

/** 常用的一次性辅助：算今天是第几周。 */
fun currentWeekLabel(t: Timetable): String {
    val w = ScheduleEngine.weekOf(t, java.time.LocalDate.now())
    return "第${w}周"
}

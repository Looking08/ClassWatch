package com.classwatch.phone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classwatch.phone.data.TimetableStore
import com.classwatch.phone.ui.theme.courseColor
import com.classwatch.shared.ScheduleEngine.formatTime
import com.classwatch.shared.ScheduleEngine.weekOf
import com.classwatch.shared.ScheduleEngine.slotsOfDay
import java.time.LocalDate

/**
 * 「本周」页：周一到周日七天纵向排列，按当前周次过滤（单双周自动生效）。
 *
 * 之所以不做成 7×12 的网格：手机上格子太窄，课程名会被截断成「高等数…」，
 * 反而是纵向列表更好读。真要做网格的话，改这里就行，数据层不用动。
 */
@Composable
fun WeekScreen() {
    val timetable = TimetableStore.timetable
    val today = LocalDate.now()
    val week = weekOf(timetable, today)
    val todayDow = if (today.dayOfWeek.value == 7) 7 else today.dayOfWeek.value

    var editing by remember { mutableStateOf<com.classwatch.shared.Course?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("本周", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                Text(
                    "第 ${week} 周 · ${if (week % 2 == 1) "单周" else "双周"}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (week < 1 || week > timetable.semester.totalWeeks) {
                Text(
                    "当前日期不在学期范围内，下面的课可能不全",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            (1..7).forEach { dow ->
                DaySection(
                    dow = dow,
                    isToday = dow == todayDow,
                    week = week
                ) { course -> editing = course }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(80.dp))
        }

        FloatingActionButton(
            onClick = { editing = newDraft() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "添加课程")
        }
    }

    editing?.let { target ->
        val isNew = timetable.courses.none { it.id == target.id }
        CourseEditorDialog(
            course = if (isNew) null else target,
            totalWeeks = timetable.semester.totalWeeks,
            onDismiss = { editing = null },
            onSave = { TimetableStore.upsertCourse(it); editing = null },
            onDelete = if (isNew) null else {
                { TimetableStore.removeCourse(target.id); editing = null }
            }
        )
    }
}

private fun newDraft() = com.classwatch.shared.Course(
    id = "draft_${System.currentTimeMillis()}",
    name = "",
    day = (LocalDate.now().dayOfWeek.value).let { if (it == 7) 7 else it },
    startNode = 1,
    endNode = 2,
    weeks = (1..TimetableStore.timetable.semester.totalWeeks).toList(),
    color = 0
)

@Composable
private fun DaySection(
    dow: Int,
    isToday: Boolean,
    week: Int,
    onEdit: (com.classwatch.shared.Course) -> Unit
) {
    val timetable = TimetableStore.timetable
    val date = runCatching {
        LocalDate.parse(timetable.semester.startDate).plusWeeks((week - 1).toLong())
            .plusDays((dow - 1).toLong())
    }.getOrDefault(LocalDate.now())
    val slots = remember(timetable, week, dow) { slotsOfDay(timetable, date) }

    val dayName = "周" + "一二三四五六日"[dow - 1]

    Row(verticalAlignment = Alignment.Top) {
        // 左侧星期标
        Box(
            Modifier
                .width(34.dp)
                .padding(top = 4.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    dayName,
                    fontSize = 13.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${date.monthValue}/${date.dayOfMonth}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(Modifier.weight(1f)) {
            if (slots.isEmpty()) {
                Text(
                    "没课",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
                )
            } else {
                slots.forEach { slot ->
                    val accent = courseColor(slot.course.color)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { onEdit(slot.course) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isToday) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .background(accent, RoundedCornerShape(4.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                slot.course.name,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Text(
                                formatTime(slot.startMinute),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                slot.course.location.takeIf { it.isNotBlank() } ?: "—",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(72.dp),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.classwatch.phone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.classwatch.shared.DayItem
import com.classwatch.shared.ItemState
import com.classwatch.shared.ScheduleEngine
import com.classwatch.shared.ScheduleEngine.formatTime
import com.classwatch.shared.ScheduleEngine.weekOf
import com.classwatch.phone.ui.theme.courseColor
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/**
 * 「今日」页：显示今天一整天的课，点卡片可以直接编辑。
 *
 * 和手表端共用同一个 [ScheduleEngine]，所以手机看到的「正在进行」
 * 跟手表上显示的完全一致 — 不会出现两边对不上的情况。
 */
@Composable
fun TodayScreen() {
    val timetable = TimetableStore.timetable

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }

    val snapshot = remember(timetable, now) { ScheduleEngine.snapshot(timetable, now) }
    var editing by remember { mutableStateOf<com.classwatch.shared.Course?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(snapshot.dayLabel, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "第 ${snapshot.week} 周 · ${snapshot.oddEvenLabel}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "${snapshot.items.size} 节课",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(Modifier.height(14.dp))

            if (snapshot.items.isEmpty()) {
                EmptyHint(if (snapshot.inSemester) "今天没排课" else "当前不在学期中，检查一下学期起始日")
            } else {
                snapshot.items.forEach { item ->
                    CourseCard(item = item, onClick = { editing = item.slot.course })
                }
            }

            Spacer(Modifier.height(24.dp))
        }
        // 「添加课程」按钮原来在这里，已按需求移除 —— 加课统一从「本周」页操作
    }

    editing?.let { target ->
        val isNew = timetable.courses.none { it.id == target.id }
        CourseEditorDialog(
            course = if (isNew) null else target,
            totalWeeks = timetable.semester.totalWeeks,
            onDismiss = { editing = null },
            onSave = { saved ->
                TimetableStore.upsertCourse(saved)
                editing = null
            },
            onDelete = if (isNew) null else {
                { TimetableStore.removeCourse(target.id); editing = null }
            }
        )
    }
}

@Composable
private fun CourseCard(item: DayItem, onClick: () -> Unit) {
    val c = item.slot.course
    val accent = courseColor(c.color)
    val past = item.state == ItemState.PAST

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(38.dp)
                    .background(accent, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    when (item.state) {
                        ItemState.ONGOING -> {
                            Spacer(Modifier.width(8.dp))
                            Tag("进行中", accent, MaterialTheme.colorScheme.onPrimary)
                        }
                        ItemState.PAST -> {
                            Spacer(Modifier.width(8.dp))
                            Tag("已结束", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ItemState.SOON -> Unit
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    listOf(c.location, c.teacher).filter { it.isNotBlank() }.joinToString(" · ")
                        .ifBlank { "未填写地点" },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatTime(item.slot.startMinute), fontSize = 13.sp)
                Text(formatTime(item.slot.endMinute), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "第${c.startNode}-${c.endNode}节",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    if (past) Unit // 保留钩子：以后可做「已结束的折叠」
}

@Composable
private fun Tag(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .background(bg, RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = fg, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

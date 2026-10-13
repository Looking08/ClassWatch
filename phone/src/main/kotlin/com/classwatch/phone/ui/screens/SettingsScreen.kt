package com.classwatch.phone.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classwatch.phone.data.TimetableStore

/**
 * 「编辑」页 —— 作息时间、单双周这类「设一次、全表通用」的东西都收在这里。
 *
 * 设计原则：节次时间只在这里定义一次，添加课程时直接挑时间段，
 * 不用每门课重复敲 08:00 / 09:40。改完记得回「同步」页推一次给手表。
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val timetable = TimetableStore.timetable

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Spacer(Modifier.width(4.dp))
            Text("编辑", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "这里定好的作息时间会直接出现在「添加课程」的时间选择里，" +
                "改完记得去「同步」页推送一次给手表。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp
        )

        Spacer(Modifier.height(16.dp))

        // ---------------- 节次时间表（可增删，自动按时间排序）----------------
        SectionCard("节次时间表") {
            Text(
                "改完自动按时间排序并重新编号，已有课程会跟着映射到新的节次。" +
                    "被课程占用的节次不能删除（先把那门课改到别的节次）。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(8.dp))
            timetable.periods.forEach { p ->
                val removable = TimetableStore.canRemovePeriod(p.index)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("第${p.index}节", fontSize = 12.sp, modifier = Modifier.width(44.dp))
                    CompactTimeField(p.start) { v ->
                        TimetableStore.setPeriod(p.index, v, p.end)
                    }
                    Text("—", modifier = Modifier.padding(horizontal = 6.dp))
                    CompactTimeField(p.end) { v ->
                        TimetableStore.setPeriod(p.index, p.start, v)
                    }
                    IconButton(
                        onClick = { TimetableStore.removePeriod(p.index) },
                        enabled = removable
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除第${p.index}节",
                            tint = if (removable) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { TimetableStore.addPeriod() },
                    modifier = Modifier.weight(1f)
                ) { Text("添加一节") }
                OutlinedButton(
                    onClick = { TimetableStore.sortPeriodsByTime() },
                    modifier = Modifier.weight(1f)
                ) { Text("按时间排序") }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- 单双周 + 周次基准 ----------------
        SectionCard("周次与单双周") {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("启用单双周", fontSize = 14.sp)
                    Text(
                        if (timetable.oddEvenEnabled)
                            "开：按每门课自己设的周次过滤（单周课只在单周出现）"
                        else
                            "关：忽略周次，排了课的日子就显示（相当于每周都上）",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = timetable.oddEvenEnabled,
                    onCheckedChange = { TimetableStore.setOddEvenEnabled(it) }
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "关掉它不用改写任何一门课的周次。注意：把某门课的周次清空表示「从不排课」，"
                    + "关掉开关也不会显示出来。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )

            // 这两项是周次的地基：没有正确的开学日期，单双周和有效周次全都会算错，
            // 所以虽然「学期设置」卡片删了，这两个字段必须留着（压缩成两行）
            Spacer(Modifier.height(12.dp))
            var start by remember(timetable.semester.startDate) {
                mutableStateOf(timetable.semester.startDate)
            }
            OutlinedTextField(
                value = start,
                onValueChange = {
                    start = it
                    runCatching { java.time.LocalDate.parse(it) }.onSuccess { _ ->
                        TimetableStore.setSemester(timetable.semester.copy(startDate = it))
                    }
                },
                label = { Text("第 1 周的周一（yyyy-MM-dd）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                runCatching { java.time.LocalDate.parse(start) }
                    .map { if (it.dayOfWeek.value != 1) "注意：这一天不是周一" else "格式正确" }
                    .getOrElse { "日期格式不对，应为 2026-09-07" },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))
            var total by remember(timetable.semester.totalWeeks) {
                mutableStateOf(timetable.semester.totalWeeks.toString())
            }
            OutlinedTextField(
                value = total,
                onValueChange = { raw ->
                    total = raw.filter { it.isDigit() }.take(2)
                    total.toIntOrNull()?.let { n ->
                        if (n in 1..30) {
                            TimetableStore.setSemester(timetable.semester.copy(totalWeeks = n))
                        }
                    }
                },
                label = { Text("学期总周数") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** 紧凑的时间输入（HH:mm），去掉外框省地方。 */
@Composable
internal fun CompactTimeField(value: String, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it.filter { ch -> ch.isDigit() || ch == ':' }.take(5)
            // 形如 08:00 才生效
            if (text.matches(Regex("^\\d{2}:\\d{2}$"))) onCommit(text)
        },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent
        ),
        modifier = Modifier.width(76.dp).height(52.dp)
    )
}

/** 圆角卡片小节，「同步」页和「编辑」页共用。 */
@Composable
internal fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

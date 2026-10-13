package com.classwatch.phone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.classwatch.phone.ui.theme.courseColor
import com.classwatch.shared.Course

/**
 * 新增 / 编辑一门课的弹窗。
 *
 * 之所以不用下拉框：1.5 英寸的课控件在小屏幕上反而不如横向滚动的「选择条」好点。
 * 这里所有输入都即时反映到 UI，点保存才写库。
 */
@Composable
fun CourseEditorDialog(
    course: Course?,
    totalWeeks: Int,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(course?.name ?: "") }
    var teacher by remember { mutableStateOf(course?.teacher ?: "") }
    var location by remember { mutableStateOf(course?.location ?: "") }
    var day by remember { mutableIntStateOf(course?.day ?: 1) }

    // 教师 / 地点是可选项：开关关掉就整行不出现，手表上也不会显示空白
    var showTeacher by remember { mutableStateOf(course?.teacher?.isNotBlank() ?: false) }
    var showLocation by remember { mutableStateOf(course?.location?.isNotBlank() ?: false) }

    // 时间段直接从「设置」里定义好的作息表里选，不用每次手输时间（单选一节）
    var startNode by remember { mutableIntStateOf(course?.startNode ?: 1) }
    var endNode by remember { mutableIntStateOf(course?.endNode ?: 1) }

    // 周次不再手填，改成三选一：每周 / 仅单周 / 仅双周
    var weekMode by remember { mutableIntStateOf(weekModeOf(course?.weeks, totalWeeks)) }

    var color by remember { mutableIntStateOf(course?.color ?: 0) }

    // 作息表（节次时间）来自设置页，这里只负责挑
    val timetable = TimetableStore.timetable

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = when (weekMode) {
                        1 -> (1..totalWeeks).filter { it % 2 == 1 }   // 仅单周
                        2 -> (1..totalWeeks).filter { it % 2 == 0 }   // 仅双周
                        else -> (1..totalWeeks).toList()              // 每周
                    }
                    onSave(
                        Course(
                            id = course?.id ?: ("c" + System.currentTimeMillis()),
                            name = name.trim().ifBlank { "未命名课程" },
                            teacher = if (showTeacher) teacher.trim() else "",
                            location = if (showLocation) location.trim() else "",
                            day = day,
                            startNode = minOf(startNode, endNode),
                            endNode = maxOf(startNode, endNode),
                            weeks = parsed,
                            color = color
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text(if (course == null) "添加课程" else "编辑课程") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("课程名称") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                // 教师 / 地点都做成「开关 + 输入框」：不填就直接关掉，界面更干净
                OptionalField(
                    label = "教师",
                    value = teacher,
                    checked = showTeacher,
                    onCheckedChange = { checked ->
                        showTeacher = checked
                        if (!checked) teacher = ""
                    },
                    onValueChange = { teacher = it }
                )
                Spacer(Modifier.height(6.dp))
                OptionalField(
                    label = "地点",
                    value = location,
                    checked = showLocation,
                    onCheckedChange = { checked ->
                        showLocation = checked
                        if (!checked) location = ""
                    },
                    onValueChange = { location = it }
                )

                Spacer(Modifier.height(12.dp))
                Text("星期", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                // 可横向滑动：手机上 7 个 chip 一排会把「日」挤出屏幕
                val dayScroll = rememberScrollState()
                val density = androidx.compose.ui.platform.LocalDensity.current
                LaunchedEffect(day) {
                    with(density) { dayScroll.animateScrollTo((44.dp * (day - 1)).roundToPx()) }
                }
                Row(
                    Modifier.horizontalScroll(dayScroll),
                    Arrangement.spacedBy(6.dp)
                ) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { i, label ->
                        ChoiceChip(label, selected = day == i + 1) { day = i + 1 }
                    }
                }

                Spacer(Modifier.height(12.dp))
                // 单选：点哪节就是哪节（多选区间的写法边界情况太多，已废弃）
                Text("上课时间（点一下选一节）", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                PeriodSingleRow(timetable.periods, startNode) { node ->
                    startNode = node
                    endNode = node
                }
                val picked = timetable.periods.firstOrNull { it.index == startNode }
                Text(
                    buildString {
                        append(
                            if (picked != null) "第 ${picked.index} 节 · ${picked.start}–${picked.end}"
                            else "请先在「编辑」页添加作息节次"
                        )
                        // 老数据可能是跨节的，单选保存后会砍成一节，提前说一声
                        if (course != null && course.startNode != course.endNode) {
                            append("（原第${course.startNode}–${course.endNode}节，保存后改为一节）")
                        }
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )

                Spacer(Modifier.height(12.dp))
                Text("上课周次", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("每周", "仅单周", "仅双周").forEachIndexed { i, label ->
                        ChoiceChip(label, selected = weekMode == i) { weekMode = i }
                    }
                }
                Text(
                    "「仅单周 / 仅双周」要配合「编辑」页的「启用单双周」开关使用；"
                        + "关掉那个开关，所有课都会按每周显示。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )

                Spacer(Modifier.height(12.dp))
                Text("颜色", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..7).forEach { i ->
                        val c = courseColor(i)
                        Box(
                            Modifier
                                .size(26.dp)
                                .background(c, RoundedCornerShape(8.dp))
                                .then(
                                    if (color == i)
                                        Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                                .clickable { color = i }
                        )
                    }
                }

                if (onDelete != null) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("删除这门课", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    )
}

/**
 * 可选项字段：前面一个开关，打开才出现输入框。
 * 教师、地点这类「有的人想记、有的人根本不在乎」的信息用它最合适。
 */
@Composable
private fun OptionalField(
    label: String,
    value: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.height(28.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 13.sp)
        }
        if (checked) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label, fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 从已保存的 weeks 反推「每周 / 单周 / 双周」，用于编辑已有课程时回显。
 * 手填出来的不规则周次（比如 1-8,10-16）一律按「每周」处理。
 */
private fun weekModeOf(weeks: List<Int>?, totalWeeks: Int): Int {
    if (weeks == null) return 0
    val odd = (1..totalWeeks).filter { it % 2 == 1 }
    val even = (1..totalWeeks).filter { it % 2 == 0 }
    return when {
        weeks.containsAll(odd) && weeks.size == odd.size -> 1
        weeks.containsAll(even) && weeks.size == even.size -> 2
        else -> 0
    }
}

/**
 * 一排横向滚动的节次选择条：单选，点哪节就是哪节（整节的起止时间直接显示在下方）。
 * 打开时自动滚到当前选中的节次，节次多的时候不用左右找。
 */
@Composable
private fun PeriodSingleRow(
    periods: List<com.classwatch.shared.Period>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(selected) {
        with(density) { scrollState.animateScrollTo((72.dp * (selected - 1)).roundToPx()) }
    }
    Row(Modifier.horizontalScroll(scrollState), Arrangement.spacedBy(6.dp)) {
        periods.forEach { p ->
            ChoiceChip(
                text = "${p.index}·${p.start}",
                selected = p.index == selected,
                onClick = { onSelect(p.index) }
            )
        }
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

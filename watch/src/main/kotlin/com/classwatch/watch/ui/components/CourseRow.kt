package com.classwatch.watch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.classwatch.shared.DayItem
import com.classwatch.shared.ItemState
import com.classwatch.shared.ScheduleEngine.formatTime
import com.classwatch.watch.ui.theme.courseColor

/**
 * 列表里的一行课程。
 *
 * 布局刻意做成「左时间轴 + 右内容」：
 *   08:00   高等数学A2   [进行中]
 *   09:35   教三-301 · 第1-2节
 *
 * 已经上过的课整体降到 34% 透明度：手表只有 1.5 英寸，
 * 在这么小的屏幕上「信息分级」远比「信息量」重要。
 */
@Composable
fun CourseRow(
    item: DayItem,
    isNext: Boolean,
    modifier: Modifier = Modifier
) {
    val past = item.state == ItemState.PAST
    val accent = courseColor(item.slot.course.color)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp)
                .alpha(if (past) 0.34f else 1f),
            verticalAlignment = Alignment.Top
        ) {
            // 左：时间轴
            Column(modifier = Modifier.width(44.dp)) {
                Text(
                    text = formatTime(item.slot.startMinute),
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface
                )
                Text(
                    text = formatTime(item.slot.endMinute),
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }

            // 右：课程主体
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.padding(top = 6.dp, end = 6.dp)) {
                        Dot(accent)
                    }
                    Text(
                        text = item.slot.course.name,
                        style = MaterialTheme.typography.title3,
                        color = MaterialTheme.colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    when {
                        item.state == ItemState.ONGOING -> {
                            Spacer(Modifier.width(6.dp))
                            StatusChip("进行中", accent, Color(0xFF0B0C10))
                        }
                        isNext -> {
                            Spacer(Modifier.width(6.dp))
                            StatusChip("下一节", Color.White.copy(alpha = 0.10f), accent)
                        }
                    }
                }
                Text(
                    text = buildString {
                        append(item.slot.course.location.ifBlank { "待定地点" })
                        append(" · 第")
                        append(item.slot.course.startNode)
                        append("-")
                        append(item.slot.course.endNode)
                        append("节")
                    },
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 分隔线
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(1.dp)
                .alpha(if (past) 0.20f else 0.45f)
                .background(Color.White.copy(alpha = 0.06f))
        )
    }
}

@Composable
private fun StatusChip(text: String, background: Color, foreground: Color) {
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.caption3,
            color = foreground
        )
    }
}

package com.classwatch.watch.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.classwatch.shared.DayItem
import com.classwatch.shared.Phase
import com.classwatch.shared.ScheduleEngine
import com.classwatch.shared.ScheduleEngine.formatTime
import com.classwatch.shared.Snapshot
import com.classwatch.watch.ui.theme.courseColor

/**
 * Hero 大卡 —— 手表屏幕上信息密度最高的一块地方。
 *
 * 它自己不做任何计算，只负责把 [Snapshot.phase] 翻译成合适的画面：
 *   NOW    → 正在上课：课程名 + 倒计时圆环 + 进度条
 *   BREAK  → 课间：下一节是什么 + 还有几分钟上课
 *   BEFORE → 还没上第一节
 *   OVER   → 今天放学了，顺带告诉你下一次课在哪天
 *   FREE   → 今天没课
 *
 * @param upcomingLabel 由外层算好的「下一次课」文案，放学会用到
 */
@Composable
fun HeroCard(
    snapshot: Snapshot,
    upcomingLabel: String?,
    modifier: Modifier = Modifier
) {
    when (snapshot.phase) {
        Phase.NOW -> NowHero(snapshot, modifier)
        Phase.BREAK -> BreakHero(snapshot, modifier)
        Phase.BEFORE -> BeforeHero(snapshot, modifier)
        Phase.OVER -> RestHero(
            title = "今日课程已结束",
            body = if (snapshot.items.isNotEmpty()) "${snapshot.items.size} 节课，辛苦了" else "今天很轻松",
            accent = Color(0xFF4CD7A0),
            upcomingLabel = upcomingLabel,
            modifier = modifier
        )
        Phase.FREE -> RestHero(
            title = if (snapshot.inSemester) "今天没有课" else "假期中",
            body = "好好休息",
            accent = Color(0xFF9CCC65),
            upcomingLabel = upcomingLabel,
            modifier = modifier
        )
    }
}

// ---------------------------------------------------------------- 卡片外壳

@Composable
private fun HeroShell(
    accent: Color,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF15171D), RoundedCornerShape(20.dp))
                .padding(14.dp)
        ) {
            content()
        }
    }
}

// ---------------------------------------------------------------- NOW

@Composable
private fun NowHero(snapshot: Snapshot, modifier: Modifier) {
    val item = snapshot.current ?: return
    val c = item.slot.course
    val accent = courseColor(c.color)

    HeroShell(accent, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingDot(accent)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "正在上课 · 第${c.startNode}-${c.endNode}节",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = c.name,
                    style = MaterialTheme.typography.title2,
                    color = accent,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = listOf(c.location, c.teacher).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.caption3,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${formatTime(item.slot.startMinute)} – ${formatTime(item.slot.endMinute)}",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(10.dp))
            ProgressRing(
                progress = snapshot.heroProgress,
                minutes = snapshot.heroMinutesLeft,
                color = accent
            )
        }

        Spacer(Modifier.height(12.dp))
        MiniProgressBar(snapshot.heroProgress, accent)
        Spacer(Modifier.height(5.dp))
        Text(
            text = "剩余 ${snapshot.heroMinutesLeft} 分钟下课",
            style = MaterialTheme.typography.caption3,
            color = MaterialTheme.colors.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------- BREAK

@Composable
private fun BreakHero(snapshot: Snapshot, modifier: Modifier) {
    val item = snapshot.next ?: return
    val c = item.slot.course
    val accent = courseColor(c.color)
    val neutral = Color(0xFF8B93A5)

    HeroShell(accent, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(neutral)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "课间休息",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = c.name,
                    style = MaterialTheme.typography.title2,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${c.location.ifBlank { "待定地点" }} · ${formatTime(item.slot.startMinute)} 上课",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            ProgressRing(snapshot.heroProgress, item.minutesLeft, neutral)
        }
        Spacer(Modifier.height(12.dp))
        MiniProgressBar(snapshot.heroProgress, neutral)
        Spacer(Modifier.height(5.dp))
        Text(
            text = "${item.minutesLeft} 分钟后上课",
            style = MaterialTheme.typography.caption3,
            color = MaterialTheme.colors.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------- BEFORE

@Composable
private fun BeforeHero(snapshot: Snapshot, modifier: Modifier) {
    val item = snapshot.next ?: return
    val c = item.slot.course
    val accent = courseColor(c.color)

    HeroShell(accent, modifier) {
        Text(
            text = "今天第一节",
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = c.name,
                    style = MaterialTheme.typography.title2,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${c.location.ifBlank { "待定地点" }} · ${formatTime(item.slot.startMinute)} – ${formatTime(item.slot.endMinute)}",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            ProgressRing(0f, item.minutesLeft, accent)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "还有 ${item.minutesLeft} 分钟开始",
            style = MaterialTheme.typography.caption3,
            color = MaterialTheme.colors.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------- OVER / FREE

@Composable
private fun RestHero(
    title: String,
    body: String,
    accent: Color,
    upcomingLabel: String?,
    modifier: Modifier
) {
    HeroShell(accent, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(accent)
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.title2,
            color = MaterialTheme.colors.onSurface
        )
        if (!upcomingLabel.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "下一次：$upcomingLabel",
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------- 原子件

/**
 * 「正在进行中」的小圆点。
 *
 * 之前这里是个 rememberInfiniteTransition 的呼吸动画 —— 效果是好看，但意味着
 * 界面无论静止多久都在逐帧重绘 Canvas。OPPO Watch X3 国行是 32 位 + 软渲染，
 * 这一个动画就能把帧率拖到肉眼可见的卡顿，所以改成静态点（配合旁边
 * 「正在上课」的文案，语义已经足够清楚）。
 */
@Composable
fun PulsingDot(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(8.dp)) {
        drawCircle(color = color, radius = size.minDimension / 2f)
    }
}

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(7.dp)) {
        drawCircle(color = color, radius = size.minDimension / 2f)
    }
}

/** 倒计时圆环：外圈底 + 进度弧 + 中间剩余分钟。 */
@Composable
fun ProgressRing(
    progress: Float,
    minutes: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(54.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = color.copy(alpha = 0.16f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "$minutes",
            style = MaterialTheme.typography.title3,
            color = MaterialTheme.colors.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun MiniProgressBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(2.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption3,
        color = MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.75f),
        modifier = modifier.padding(start = 14.dp, top = 14.dp, bottom = 6.dp)
    )
}

/**
 * 生成「下一次课」的文案，例如：明天 08:00 操作系统。
 * 入参直接是 [ScheduleEngine.findUpcoming] 的返回值。
 */
fun buildUpcomingLabel(found: Triple<java.time.LocalDate, com.classwatch.shared.Slot, Int>?): String? {
    if (found == null) return null
    val (date, slot, _) = found
    val today = java.time.LocalDate.now()
    val day = when (date) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        else -> "周" + "一二三四五六日"[date.dayOfWeek.value - 1]
    }
    return "$day ${ScheduleEngine.formatTime(slot.startMinute)} ${slot.course.name}"
}

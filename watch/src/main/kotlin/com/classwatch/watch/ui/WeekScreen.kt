package com.classwatch.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.classwatch.shared.ScheduleEngine
import com.classwatch.watch.data.ScheduleRepository
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 手表主界面：横向滑动看一整周。
 *
 *   ┌ 顶部 TimeText：第几周 + 单/双周
 *   ├ 星期条：一 二 三 四 五 六 日，点一下跳到那天，当前天高亮，今天带小圆点
 *   └ HorizontalPager：7 页，每页是一整天的课表，左右滑切换
 *
 * 表冠只控制纵向滚动：[RotaryBus] 只把事件派给「当前页」注册的列表，
 * 所以横滑切页和旋钮滚动互不干扰。
 */
@Composable
fun WeekScreen(
    repository: ScheduleRepository,
    modifier: Modifier = Modifier
) {
    val timetable by repository.timetable.collectAsState()

    val today = LocalDate.now()
    // with(DayOfWeek.MONDAY) 返回本周周一（周一为每周第一天）
    val monday = today.with(DayOfWeek.MONDAY)
    val weekDates = (0..6).map { monday.plusDays(it.toLong()) }
    val todayIndex = today.dayOfWeek.value - 1      // 周一=0 … 周日=6

    val pagerState = rememberPagerState(initialPage = todayIndex) { 7 }
    val scope = rememberCoroutineScope()

    val week = ScheduleEngine.weekOf(timetable, weekDates[pagerState.currentPage])

    Scaffold(
        modifier = modifier,
        timeText = {
            TimeText(
                startLinearContent = {
                    Text(
                        text = if (timetable.oddEvenEnabled) {
                            "第${week}周 · " + if (week % 2 == 1) "单周" else "双周"
                        } else {
                            "第${week}周"
                        },
                        color = MaterialTheme.colors.onSurfaceVariant,
                        style = MaterialTheme.typography.caption3
                    )
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
        ) {
            WeekdayStrip(
                selected = pagerState.currentPage,
                todayIndex = todayIndex,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } }
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                DaySchedule(
                    repository = repository,
                    date = weekDates[page],
                    isToday = page == todayIndex,
                    isActive = page == pagerState.currentPage
                )
            }
        }
    }
}

/**
 * 顶部星期条 —— 可横向滚动。
 *
 * 为什么要能滚：圆形屏有效宽度只有 233dp，7 个 32dp 的圆点一排已经是 224dp，
 * 两端的「一」和「日」正好落在圆弧被裁掉的区域。所以改成横向滚动，
 * 并且**切页时自动把当前星期滚到屏幕中央**（否则翻到周日时高亮还在屏幕外）。
 *
 * 顶上留 28dp 是为了避开 TimeText —— 圆形屏最上面那一条是系统时间的位置。
 */
@Composable
private fun WeekdayStrip(
    selected: Int,
    todayIndex: Int,
    onSelect: (Int) -> Unit
) {
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenWidthPx = with(density) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }.toInt()
    val itemWidthPx = with(density) { 32.dp.toPx() }.toInt()

    // 当前页变化时，把选中项滚到屏幕正中
    LaunchedEffect(selected) {
        val contentWidth = labels.size * itemWidthPx
        val maxScroll = (contentWidth - screenWidthPx).coerceAtLeast(0)
        val target = (selected * itemWidthPx - (screenWidthPx / 2 - itemWidthPx / 2))
            .coerceIn(0, maxScroll)
        scrollState.animateScrollTo(target)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState, reverseScrolling = false)
            .padding(top = 28.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 两端各留一点余量，避免第一个/最后一个贴着圆弧边被切
        Spacer(Modifier.width(6.dp))
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = if (isSelected) Color(0xFF2A3140) else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.caption3,
                        color = if (isSelected) Color(0xFFE8EAF0) else Color(0xFF6E7688)
                    )
                    // 今天额外点一个小蓝点，方便一眼分清「今天」和「选中」
                    if (index == todayIndex) {
                        Box(
                            Modifier
                                .padding(top = 1.dp)
                                .size(3.dp)
                                .background(Color(0xFF5B8CFF), CircleShape)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(6.dp))
    }
}

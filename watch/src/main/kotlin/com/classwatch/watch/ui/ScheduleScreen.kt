package com.classwatch.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.classwatch.shared.Phase
import com.classwatch.shared.ScheduleEngine
import com.classwatch.watch.data.ScheduleRepository
import com.classwatch.watch.sync.SyncServer
import com.classwatch.watch.sync.localIpAddress
import com.classwatch.watch.ui.components.CourseRow
import com.classwatch.watch.ui.components.HeroCard
import com.classwatch.watch.ui.components.SectionLabel
import com.classwatch.watch.ui.components.buildUpcomingLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 手表主界面。
 *
 * 结构很简单：
 *  ┌ 顶部 TimeText：系统会在圆形屏顶部显示时间，左边补一行「周二 · 第5周 单周」
 *  ├ Hero 大卡：根据时间自动切换 上课中 / 课间 / 课前 / 已放学 / 没课
 *  └ 列表：当天全部课程，过去的变灰，下一节打标签
 *
 * 旋钮：
 *  用 Wear Compose 官方的 Modifier.rotaryScrollable，配上 focusRequester 让它
 *  拿到焦点。behavior 用 RotaryScrollableDefaults.behavior()，自带阻尼和惯性，
 *  转起来是连续的而不是一格一格的。
 */
@Composable
fun DaySchedule(
    repository: ScheduleRepository,
    date: LocalDate,
    isToday: Boolean,
    /** 是否当前这一页：只有当前页会接管表冠（否则 7 页会一起滚） */
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val timetable by repository.timetable.collectAsState()

    // 刷新策略：只有今天这一页需要每分钟刷新（「还剩 XX 分钟」是分钟粒度）。
    // 其他日期是静态的，跟着每秒/每分钟空转纯属白烧 CPU —— 这块表是 32 位的。
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    if (isToday) {
        LaunchedEffect(Unit) {
            while (true) {
                now = LocalDateTime.now()
                val passed = System.currentTimeMillis() % 60_000
                delay(60_000 - passed + 30)
            }
        }
    }

    // 非今天：把时间锚定到当天 00:00，于是所有课都是「还没开始」，
    // 显示的就是这一天的静态课表，不会出现「正在上课」这种假状态
    val anchor = if (isToday) now else date.atStartOfDay()
    val snapshot = remember(timetable, anchor, date) {
        ScheduleEngine.snapshot(timetable, anchor)
    }

    // 放学 / 没课时才需要往后找「下一次课」；其他阶段算它纯属浪费
    val upcomingLabel = remember(timetable, snapshot) {
        if (isToday && (snapshot.phase == Phase.OVER || snapshot.phase == Phase.FREE)) {
            buildUpcomingLabel(ScheduleEngine.findUpcoming(timetable, now))
        } else null
    }

    // 本机 IP：滚到列表底部就能看到，手机端「同步」页直接填这个地址推送。
    // 每分钟跟着刷新一次，Wi-Fi 换网段时也会跟着更新。
    val context = LocalContext.current
    var ip by remember { mutableStateOf(localIpAddress(context)) }
    LaunchedEffect(now) {
        ip = withContext(Dispatchers.IO) { localIpAddress(context) }
    }

    // 初始锚点必须是第 0 项（Hero 卡）：默认锚第 1 项会把 Hero 顶出屏幕。
    // itemOffset 正数把首项往下推 —— 让它整体落在星期条下方而不是贴着屏幕顶，
    // 之前用户反馈「当前课程卡片太靠上」就是靠这个值调的。
    val listState = rememberScalingLazyListState(
        initialCenterItemIndex = 0,
        initialCenterItemScrollOffset = 0
    )
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isActive) {
        if (isActive) runCatching { focusRequester.requestFocus() }
    }

    // OPPO 表冠：MainActivity 拦到 ACTION_SCROLL 后经 RotaryBus 派发。
    // 只有当前这一页注册，否则横滑切页后 7 个列表会一起动。
    val scope = rememberCoroutineScope()
    DisposableEffect(isActive) {
        if (!isActive) return@DisposableEffect onDispose { }
        val handler: (Float) -> Unit = { deltaPx ->
            scope.launch { listState.scrollBy(deltaPx) }
        }
        RotaryBus.register(handler)
        onDispose { RotaryBus.unregister(handler) }
    }

    ScalingLazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .focusRequester(focusRequester)
            .focusable()
            .rotaryScrollable(
                behavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
                focusRequester = focusRequester
            ),
        state = listState,
        autoCentering = AutoCenteringParams(
            itemIndex = 0,
            itemOffset = with(LocalDensity.current) { 34.dp.roundToPx() }
        )
    ) {
        if (isToday) {
            item { HeroCard(snapshot = snapshot, upcomingLabel = upcomingLabel) }
        } else {
            item {
                DaySummaryCard(
                    label = snapshot.dayLabel,
                    count = snapshot.items.size,
                    firstStart = snapshot.items.firstOrNull()?.slot?.startMinute
                )
            }
        }

        if (snapshot.items.isNotEmpty()) {
            item {
                SectionLabel(
                    if (isToday) "今日 · ${snapshot.items.size} 节课"
                    else "${snapshot.items.size} 节课"
                )
            }

            items(snapshot.items.size) { index ->
                val item = snapshot.items[index]
                CourseRow(item = item, isNext = isToday && snapshot.next == item)
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (ip != null) "手表 IP  $ip:${SyncServer.DEFAULT_PORT}\n手机同步页填这个地址"
                       else "手表未连 Wi-Fi，手机端无法扫描到",
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            )
        }

        item { Spacer(Modifier.height(28.dp)) }
    }
}

/** 非「今天」的页面用的简洁头卡：周几 + 几节课 + 第一节几点。 */
@Composable
private fun DaySummaryCard(label: String, count: Int, firstStart: Int?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .background(Color(0xFF15171D), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (count == 0) "这天没课" else "$count 节课",
            style = MaterialTheme.typography.title2,
            color = MaterialTheme.colors.onSurface
        )
        if (firstStart != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "第一节 ${ScheduleEngine.formatTime(firstStart)}",
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        }
    }
}

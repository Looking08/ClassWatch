package com.classwatch.watch.tile

import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.ColorBuilders.argb
import androidx.wear.tiles.DimensionBuilders
import androidx.wear.tiles.LayoutElementBuilders.Box
import androidx.wear.tiles.LayoutElementBuilders.Column
import androidx.wear.tiles.LayoutElementBuilders.FontStyle
import androidx.wear.tiles.LayoutElementBuilders.Layout
import androidx.wear.tiles.LayoutElementBuilders.Text
import androidx.wear.tiles.ModifiersBuilders.Background
import androidx.wear.tiles.ModifiersBuilders.Clickable
import androidx.wear.tiles.ModifiersBuilders.Modifiers
import androidx.wear.tiles.ModifiersBuilders.Padding
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders
import androidx.wear.tiles.TimelineBuilders.TimeInterval
import com.classwatch.shared.Phase
import com.classwatch.shared.ScheduleEngine
import com.classwatch.shared.ScheduleEngine.formatTime
import com.classwatch.watch.App
import com.classwatch.watch.data.ScheduleRepository
import com.google.common.util.concurrent.ListenableFuture
import java.time.LocalDateTime
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

private const val RESOURCES_VERSION = "1"

/**
 * Tile 小组件：在表盘上左右滑一下就能看到「现在 / 下一节」，不用打开 App。
 *
 * Tile 不跑在 App 的主流程里，而是由系统定期向本 Service 请求渲染数据，
 * 所以这里设了 60 秒刷新一次 —— 小尺寸信息够用了，也不怎么耗电。
 *
 * 用的是 androidx.wear.tiles 的 ListenableFuture 版 API（tiles 1.4.0 只有这一套），
 * 手写一个极简的 [ImmediateFuture] 就不用额外引 guava 了。
 */
class ScheduleTileService : TileService() {

    private val repository: ScheduleRepository by lazy { (application as App).repository }

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> = ImmediateFuture(buildTile())

    override fun onResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> =
        ImmediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(requestParams.version)
                .build()
        )

    private fun buildTile(): TileBuilders.Tile {
        val now = LocalDateTime.now()
        val snap = ScheduleEngine.snapshot(repository.timetable.value, now)
        val accent = when (snap.phase) {
            Phase.NOW -> 0xFF5B8CFF.toInt()
            Phase.OVER, Phase.FREE -> 0xFF4CD7A0.toInt()
            else -> 0xFF9AA1B1.toInt()
        }

        // 第一行永远是「状态 + 还有多久」，下面两行是当前 / 下一节的详情
        val lines = ArrayList<String>(3)
        lines += when (snap.phase) {
            Phase.NOW -> "正在上课 · 剩 ${snap.heroMinutesLeft} 分钟"
            Phase.BREAK -> "课间 · ${snap.next?.minutesLeft} 分钟后上课"
            Phase.BEFORE -> "${snap.next?.minutesLeft} 分钟后开始"
            Phase.OVER -> "今日课程已结束"
            Phase.FREE -> if (snap.inSemester) "今天没有课" else "假期中"
        }
        snap.current?.let { c ->
            lines += c.slot.course.name
            lines += "${c.slot.course.location} · " +
                "${formatTime(c.slot.startMinute)}-${formatTime(c.slot.endMinute)}"
        } ?: snap.next?.let { n ->
            lines += "下一节 ${n.slot.course.name}"
            lines += "${n.slot.course.location} · ${formatTime(n.slot.startMinute)}"
        }

        val column = Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setModifiers(
                Modifiers.Builder()
                    .setPadding(
                        Padding.Builder()
                            .setStart(DimensionBuilders.dp(16f))
                            .setEnd(DimensionBuilders.dp(16f))
                            .setTop(DimensionBuilders.dp(26f))
                            .setBottom(DimensionBuilders.dp(26f))
                            .build()
                    )
                    .build()
            )
        lines.forEachIndexed { i, s ->
            column.addContent(textLine(s, accent, isHeader = i == 0))
        }

        val root = Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(
                        Background.Builder().setColor(argb(0xFF000000.toInt())).build()
                    )
                    // 点一下小组件直接打开 App
                    .setClickable(
                        Clickable.Builder()
                            .setId("tile_root")
                            .setOnClick(
                                ActionBuilders.LaunchAction.Builder()
                                    .setAndroidActivity(
                                        ActionBuilders.AndroidActivity.Builder()
                                            .setPackageName(packageName)
                                            .setClassName(MAIN_ACTIVITY)
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(column.build())
            .build()

        val nowMillis = System.currentTimeMillis()
        val entry = TimelineBuilders.TimelineEntry.Builder()
            .setLayout(Layout.Builder().setRoot(root).build())
            .setValidity(
                TimeInterval.Builder()
                    .setStartMillis(nowMillis)
                    .setEndMillis(nowMillis + 10 * 60 * 1000)
                    .build()
            )
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(60_000)
            .setTimeline(
                TimelineBuilders.Timeline.Builder().addTimelineEntry(entry).build()
            )
            .build()
    }

    private fun textLine(value: String, color: Int, isHeader: Boolean): Text =
        Text.Builder()
            .setText(value)
            .setMaxLines(1)
            .setModifiers(
                Modifiers.Builder()
                    .setPadding(
                        Padding.Builder()
                            .setBottom(DimensionBuilders.dp(if (isHeader) 6f else 2f))
                            .build()
                    )
                    .build()
            )
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(if (isHeader) 14f else 12f))
                    .setColor(argb(if (isHeader) color else 0xFFFFFFFF.toInt()))
                    .build()
            )
            .build()

    companion object {
        private const val MAIN_ACTIVITY = "com.classwatch.watch.ui.MainActivity"
    }
}

/**
 * 一个「已经完成」的 ListenableFuture —— 用来替代 guava 的 Futures.immediateFuture，
 * 省掉一个额外依赖。
 */
private class ImmediateFuture<T>(private val value: T) : ListenableFuture<T> {
    override fun addListener(listener: Runnable, executor: Executor) {
        executor.execute(listener)
    }
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
    override fun isCancelled(): Boolean = false
    override fun isDone(): Boolean = true
    override fun get(): T = value
    override fun get(timeout: Long, unit: TimeUnit): T = value
}

package com.classwatch.watch.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Typography
import androidx.compose.runtime.Composable
import com.classwatch.shared.CoursePalette

/**
 * 手表端配色。
 *
 * 手表是 AMOLED 屏，所以用纯黑背景最省电、对比也最好。
 * DayText / Surface 跟 prototype/index.html 里那套保持完全一致，
 * 这样你在网页上调出来的效果，真机上基本一致。
 */
private val WatchColors = Colors(
    primary = Color(0xFF5B8CFF),
    primaryVariant = Color(0xFF3D6FE0),
    secondary = Color(0xFFA475FF),
    secondaryVariant = Color(0xFF8A5BE0),
    background = Color(0xFF000000),
    surface = Color(0xFF15171D),
    error = Color(0xFFFF6B6B),
    onPrimary = Color(0xFF0B0C10),
    onSecondary = Color(0xFF0B0C10),
    onBackground = Color(0xFFE8EAF0),
    onSurface = Color(0xFFE8EAF0),
    onSurfaceVariant = Color(0xFF9AA1B1),
    onError = Color(0xFF0B0C10)
)

@Composable
fun ClassWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = WatchColors,
        typography = Typography(),
        content = content
    )
}

/**
 * 把 Course.color 的档位换算成 Compose 的 Color（颜色板与手机端共用）。
 *
 * 注意：CoursePalette 里存的是 0xAARRGGBB 的 Long，必须走 Color(Int) 这个
 * 「ARGB 打包」的构造。千万不要用 Color(ULong) —— 那是「色彩空间打包格式」，
 * 把 ARGB 塞进去会解析出非法的 colorSpace id，一 drawCircle 就
 * ArrayIndexOutOfBoundsException（实测踩过：length=18; index=63）。
 */
fun courseColor(index: Int): Color = Color(CoursePalette.of(index).toInt())

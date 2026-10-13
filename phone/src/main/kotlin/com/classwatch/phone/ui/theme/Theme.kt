package com.classwatch.phone.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme

/** 手机端沿用和手表一样的两套色：深底 + 蓝紫强调。 */
private val Scheme = darkColorScheme(
    primary = Color(0xFF5B8CFF),
    onPrimary = Color(0xFF0B0C10),
    primaryContainer = Color(0xFF1C2A4D),
    secondary = Color(0xFFA475FF),
    background = Color(0xFF101218),
    surface = Color(0xFF15171D),
    surfaceVariant = Color(0xFF1B1E26),
    onBackground = Color(0xFFE8EAF0),
    onSurface = Color(0xFFE8EAF0),
    onSurfaceVariant = Color(0xFF9AA1B1),
    outline = Color(0xFF2A2E39),
    error = Color(0xFFFF6B6B)
)

@Composable
fun PhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        content = content
    )
}

/**
 * Course.color 档位 → Compose Color。和手表端共用同一张色板。
 *
 * CoursePalette 存的是 0xAARRGGBB 的 Long，必须走 Color(Int)（ARGB 打包）。
 * 用 Color(ULong) 会被当成色彩空间打包格式，解析出非法 colorSpace id 导致绘制崩溃。
 */
fun courseColor(index: Int): Color = Color(com.classwatch.shared.CoursePalette.of(index).toInt())

package com.classwatch.watch.ui

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * 【临时诊断页】搞清楚 OPPO Watch X3 的表冠到底发的是哪种输入事件。
 *
 * 为什么需要它：OPPO 国行手表跑的是 ColorOS Watch（Android 11），不是 Wear OS，
 * 所以没有 Wear OS 那套 rotary 事件。表冠到底是以 KeyEvent / GenericMotionEvent /
 * 特定 Vendor Motion axis 传进来，必须真机抓一次才知道。
 *
 * 用法：
 *   adb shell am start -n com.classwatch.watch/.ui.InputProbeActivity
 *   然后转动表冠（正转、反转各几次）、按侧边实体键、上下滑屏
 *   adb logcat -s ClassWatchProbe
 *
 * 抓到协议后就可以把这个 Activity 删掉，把处理逻辑接进 [ScheduleScreen]。
 */
class InputProbeActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ClassWatchProbe"
    }

    /** 给 Compose 展示的最近事件日志（同时也写 logcat）。 */
    val logLines = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log("=== InputProbe 启动，请转动表冠 ===")
        setContent { ProbeScreen(logLines) }
    }

    private fun log(msg: String) {
        Log.i(TAG, msg)
        val trimmed = if (msg.length > 46) msg.take(46) + "…" else msg
        runOnUiThread {
            logLines.add(0, trimmed)
            if (logLines.size > 14) logLines.removeAt(logLines.size - 1)
        }
    }

    // ---------- 拦截所有可能的输入通道 ----------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        log("[KEY] code=${event.keyCode} act=${event.action} dev=${event.device?.name} src=${event.source}")
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        log("[KEYDOWN] $keyCode repeat=${event.repeatCount}")
        return super.onKeyDown(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        dumpMotion("[GMOTION]", event)
        return super.onGenericMotionEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            dumpMotion("[DISPATCH-SCROLL]", event)
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_MOVE) {
            dumpMotion("[TOUCH]", event)
        }
        return super.dispatchTouchEvent(event)
    }

    private fun dumpMotion(prefix: String, event: MotionEvent) {
        val axes = StringBuilder()
        // 逐个探测所有可能承载「旋转量」的 axis，非 0 才输出
        listOf(
            "SCROLL" to MotionEvent.AXIS_SCROLL,
            "VSCROLL" to MotionEvent.AXIS_VSCROLL,
            "HSCROLL" to MotionEvent.AXIS_HSCROLL,
            "GENERIC_1" to MotionEvent.AXIS_GENERIC_1,
            "GENERIC_2" to MotionEvent.AXIS_GENERIC_2,
            "GENERIC_3" to MotionEvent.AXIS_GENERIC_3,
            "RSERVED_X" to MotionEvent.AXIS_X,
            "RESERVED_Y" to MotionEvent.AXIS_Y,
            "ORIENTATION" to MotionEvent.AXIS_ORIENTATION,
            "SIZE" to MotionEvent.AXIS_SIZE,
            "PRESSURE" to MotionEvent.AXIS_PRESSURE,
            "WHEEL" to MotionEvent.AXIS_WHEEL
        ).forEach { (name, axis) ->
            val v = event.getAxisValue(axis)
            if (v != 0f) axes.append(" $name=$v")
        }
        log(
            "$prefix act=${actionName(event)} src=${event.source} dev=${event.device?.name} " +
                "ptrCount=${event.pointerCount} axes=[$axes]"
        )
    }

    private fun actionName(e: MotionEvent): String = when (e.actionMasked) {
        MotionEvent.ACTION_DOWN -> "DOWN"
        MotionEvent.ACTION_UP -> "UP"
        MotionEvent.ACTION_MOVE -> "MOVE"
        MotionEvent.ACTION_SCROLL -> "SCROLL"
        MotionEvent.ACTION_CANCEL -> "CANCEL"
        else -> e.actionMasked.toString()
    }
}

@Composable
private fun ProbeScreen(lines: List<String>) {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 30.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                "转动表冠 / 按侧键\n事件会写进 logcat -s ClassWatchProbe",
                style = MaterialTheme.typography.caption3,
                color = Color(0xFF5B8CFF),
                textAlign = TextAlign.Center
            )
            lines.forEach {
                Box(contentAlignment = Alignment.CenterStart) {
                    Text(
                        it,
                        style = MaterialTheme.typography.caption3.copy(fontSize = 9.sp),
                        color = Color(0xFFD7DAE2),
                        maxLines = 2
                    )
                }
            }
        }
    }
}

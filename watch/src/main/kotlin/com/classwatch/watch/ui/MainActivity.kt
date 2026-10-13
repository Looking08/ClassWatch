package com.classwatch.watch.ui

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.classwatch.watch.App
import com.classwatch.watch.data.ScheduleRepository
import com.classwatch.watch.ui.theme.ClassWatchTheme

/**
 * 手表端唯一一个主 Activity。
 *
 * 整个界面用 Compose 写成，没有 XML 布局。
 * 旋转表冠的滚动处理在 [ScheduleScreen] 里。
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ClassWatchRotary"

        /**
         * 表冠滚动灵敏度：AXIS_VSCROLL 的原始值 × 这个系数 = 实际滚动的像素。
         *
         * 实测 OPPO 表冠每转一格会发一串 ±2 ~ ±80 的事件（转得越快值越大）。
         * 320dpi 下 density = 2.0，所以这里写 2.5 就相当于 5px/单位。
         *
         * 嫌快就把 2.5 调小（1.5 很慢、2.5 适中、5 很快），嫌慢就调大。
         */
        private const val ROTARY_DP_PER_UNIT = 2.5f
        private val ROTARY_PX_PER_UNIT get() = ROTARY_DP_PER_UNIT * 2f // × density(2.0)
    }

    private val repository: ScheduleRepository by lazy { (application as App).repository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ClassWatchTheme {
                WeekScreen(repository = repository)
            }
        }
    }

    /**
     * OPPO 表冠适配：ColorOS Watch 的表冠以 SOURCE_MOUSE + AXIS_VSCROLL 的
     * ACTION_SCROLL 事件发出，Wear Compose 的 rotaryScrollable 认不出来，
     * 所以在 Activity 层拦下，转发给 RotaryBus → ScheduleScreen 的列表。
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (RotaryBus.isRotary(event)) {
            val value = RotaryBus.rotaryValue(event)
            if (value != 0f) {
                val deltaPx = -value * ROTARY_PX_PER_UNIT
                Log.d(TAG, "crown value=$value -> scrollBy=$deltaPx")
                RotaryBus.dispatch(deltaPx)
                return true
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }
}

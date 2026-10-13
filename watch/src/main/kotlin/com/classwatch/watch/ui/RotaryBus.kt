package com.classwatch.watch.ui

import android.view.InputDevice
import android.view.MotionEvent

/**
 * 表冠事件总线。
 *
 * 为什么需要它：OPPO Watch X3 国行（ColorOS Watch，Android 11）的表冠走的是
 *   MotionEvent.ACTION_SCROLL + source = SOURCE_MOUSE(8194) + AXIS_VSCROLL，
 * 设备名 oplus_crown —— 不是 Wear OS 的 SOURCE_ROTARY_ENCODER，所以
 * androidx.wear 的 Modifier.rotaryScrollable 完全收不到（已用 InputProbeActivity
 * 在真机抓包证实，见 README 10.5）。
 *
 * Compose 内部拦不到 generic motion 事件，只能在 Activity 层重写
 * dispatchGenericMotionEvent 拦下来，再通过这个总线转发给当前在界面上的
 * 滚动容器（ScheduleScreen 注册自己的 ScalingLazyListState）。
 */
object RotaryBus {

    private val handlers = mutableListOf<(Float) -> Unit>()

    fun register(handler: (Float) -> Unit) {
        handlers.add(handler)
    }

    fun unregister(handler: (Float) -> Unit) {
        handlers.remove(handler)
    }

    /** 派发一次滚动，像素为正表示列表向下滚（看更后面的内容）。 */
    fun dispatch(deltaPx: Float) {
        handlers.toList().forEach { it(deltaPx) }
    }

    /**
     * 判断一个 generic motion 事件是不是表冠/旋转编码器：
     * · 标准 Wear OS：source 含 SOURCE_ROTARY_ENCODER
     * · OPPO ColorOS Watch：source == SOURCE_MOUSE(8194)，设备名 oplus_crown
     */
    fun isRotary(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_SCROLL) return false
        val src = event.source
        if (src and InputDevice.SOURCE_ROTARY_ENCODER == InputDevice.SOURCE_ROTARY_ENCODER) return true
        if (src and 0x2002 == 0x2002 && event.device?.name?.contains("crown", ignoreCase = true) == true) return true
        return false
    }

    /** 从事件里取出旋转增量（ Wear OS 用 ROTARY_AXIS，OPPO 用 AXIS_VSCROLL）。 */
    fun rotaryValue(event: MotionEvent): Float {
        // 标准 rotary：androidx.wear.ambient? RotaryEncoder 在 androidx.wear 里拿，
        // 这里为避免重新引 Wear OS 依赖，直接按约定 axis 读
        val v = event.getAxisValue(MotionEvent.AXIS_SCROLL)
        if (v != 0f) return v
        return event.getAxisValue(MotionEvent.AXIS_VSCROLL)
    }
}

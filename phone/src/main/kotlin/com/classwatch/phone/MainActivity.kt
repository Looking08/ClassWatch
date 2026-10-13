package com.classwatch.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.classwatch.phone.data.TimetableStore
import com.classwatch.phone.ui.screens.SettingsScreen
import com.classwatch.phone.ui.screens.SyncScreen
import com.classwatch.phone.ui.screens.TodayScreen
import com.classwatch.phone.ui.screens.WeekScreen
import com.classwatch.phone.ui.theme.PhoneTheme

/**
 * 手机端唯一 Activity。三个底部 Tab：今日 / 本周 / 同步。
 *
 * 课表数据放在 [TimetableStore] 单例里，几个页面共读同一份数据，
 * 所以在一处改、别处立刻跟着变。
 *
 * 「编辑」页（作息时间、单双周）不是 Tab，而是从同步页右上角的「编辑」按钮进入的子页，
 * 返回就回到同步页。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        TimetableStore.init(this)

        setContent {
            PhoneTheme {
                MainApp()
            }
        }
    }
}

@Composable
private fun MainApp() {
    var tab by remember { mutableIntStateOf(0) }
    // 编辑页不是 Tab，是同步页右上角「编辑」按钮打开的子页
    var showSettings by remember { mutableStateOf(false) }

    val inSettings = showSettings && tab == 2
    BackHandler(enabled = inSettings) { showSettings = false }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                listOf(
                    Triple("今日", Icons.Filled.Schedule, 0),
                    Triple("本周", Icons.Filled.CalendarMonth, 1),
                    Triple("同步", Icons.Filled.Sync, 2)
                ).forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when {
                inSettings -> SettingsScreen(onBack = { showSettings = false })
                tab == 0 -> TodayScreen()
                tab == 1 -> WeekScreen()
                else -> SyncScreen(onOpenSettings = { showSettings = true })
            }
        }
    }
}

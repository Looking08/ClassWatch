package com.classwatch.phone.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classwatch.phone.data.TimetableStore
import com.classwatch.phone.sync.SyncClient
import kotlinx.coroutines.launch

/**
 * 「同步」页 —— 把课表推到手表上去。
 *
 * 国行 OPPO Watch 没有 GMS，官方 Data Layer 用不了，
 * 所以这里走自建的局域网 TCP 通道（协议在 SyncClient 里有说明）。
 *
 * 流程：手表端 App 只要开着，就会在 8899 端口监听。
 *       手机这边点「扫描」自动找到手表 IP，点「推送」即可。
 */
@Composable
fun SyncScreen(onOpenSettings: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val timetable = TimetableStore.timetable

    // 记住上次成功连上的手表 IP —— 路由器一般会给同一个 MAC 分配同一个地址，
    // 所以下次打开自动填好，多数时候根本不用扫
    val prefs = remember { context.getSharedPreferences("sync", android.content.Context.MODE_PRIVATE) }

    var host by remember { mutableStateOf(prefs.getString(KEY_HOST, "") ?: "") }
    var port by remember { mutableStateOf(SyncClient.DEFAULT_PORT.toString()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(TimetableStore.exportJson().toByteArray(Charsets.UTF_8))
            }
        }.onSuccess {
            Toast.makeText(context, "已导出 timetable.json", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
        }.getOrNull()
        if (text != null && TimetableStore.importJson(text)) {
            Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "导入失败：不是有效的课表 JSON", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("同步到手表", fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            // 作息表、单双周这类设置统一收进「编辑」页，同步页只管同步
            TextButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("编辑")
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---------------- 连接与推送 ----------------
        SectionCard("连接") {
            Text(
                "手表 IP（留空点「扫描」自动查找）",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    placeholder = { Text("192.168.1.87") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { ch -> ch.isDigit() }.take(5) },
                    singleLine = true,
                    modifier = Modifier.width(90.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            // 自动找手表：上次 IP → 广播发现 → 网段扫描，IP 变了也能找到
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        status = "正在自动查找手表…"
                        val result = runCatching {
                            SyncClient.autoConnect(
                                context,
                                host.ifBlank { prefs.getString(KEY_HOST, null) },
                                port.toIntOrNull() ?: SyncClient.DEFAULT_PORT
                            )
                        }.getOrNull()
                        status = if (result == null) {
                            "没找到手表：确认手表端 App 打开过一次、两端在同一 Wi-Fi、手机没开 VPN"
                        } else {
                            host = result.first
                            prefs.edit().putString(KEY_HOST, host).apply()
                            "已连接 ${result.first}（${result.second}），点「推送课表」同步"
                        }
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("自动找手表") }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            val prefix = SyncClient.localPrefix(context)
                            if (prefix == null) {
                                status = "请先连上 Wi-Fi（手机和手表要在同一个局域网）"
                            } else {
                                status = "正在扫描 $prefix.1~254 …（约 10 秒）"
                                val found = SyncClient.scan(prefix, port.toIntOrNull() ?: SyncClient.DEFAULT_PORT)
                                status = if (found.isEmpty()) {
                                    "没扫到手表。三点自查：\n" +
                                        "① 手表端 App 打开过一次（监听在启动时才开）\n" +
                                        "② 手表和手机在同一个 Wi-Fi（不是访客网络/AP 隔离）\n" +
                                        "③ 关掉手机上的 VPN 再试"
                                } else {
                                    host = found.first()
                                    prefs.edit().putString(KEY_HOST, host).apply()
                                    "找到 ${found.size} 台设备：${found.joinToString()}"
                                }
                            }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("扫描局域网") }

                Button(
                    onClick = {
                        scope.launch {
                            if (host.isBlank()) {
                                status = "请先填写手表 IP"
                                return@launch
                            }
                            busy = true
                            status = "正在推送…"
                            val ok = runCatching {
                                SyncClient.push(
                                    host,
                                    port.toIntOrNull() ?: SyncClient.DEFAULT_PORT,
                                    TimetableStore.exportJson()
                                )
                            }.getOrElse { false }
                            if (ok) {
                                prefs.edit().putString(KEY_HOST, host).apply()
                                status = "推送成功！手表已刷新（${timetable.courses.size} 门课）"
                            } else {
                                status = "推送失败。注意 ping 不通是正常的（手表拦 ICMP），\n" +
                                    "只要 TCP 能连上就行，确认 IP 与手表端 App 是否在运行"
                            }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("推送课表") }
            }
            Spacer(Modifier.height(10.dp))
            StatusLine(status)
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- 导入导出 ----------------
        SectionCard("备份 / 导入") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { exportLauncher.launch("timetable.json") },
                    modifier = Modifier.weight(1f)
                ) { Text("导出 JSON") }
                OutlinedButton(
                    onClick = { importLauncher.launch("application/json") },
                    modifier = Modifier.weight(1f)
                ) { Text("从 JSON 导入") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "导出的文件也可以用 adb 推到手表上：\n" +
                    "adb push timetable.json /data/local/tmp/ && adb shell run-as com.classwatch.watch cp /data/local/tmp/timetable.json /data/data/com.classwatch.watch/files/",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_TEXT, TimetableStore.exportJson())
                }
                context.startActivity(Intent.createChooser(share, "发送课表"))
            }) { Text("以文本形式分享课表") }
        }

        Spacer(Modifier.height(24.dp))
    }
}

private const val KEY_HOST = "last_host"

@Composable
private fun StatusLine(text: String) {
    if (text.isNotBlank()) {
        Text(
            text,
            fontSize = 12.sp,
            color = if (text.startsWith("推送成功")) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

package com.classwatch.watch.sync

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import com.classwatch.watch.data.ScheduleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket

/**
 * 局域网同步通道 —— 手表端作为「服务端」。
 *
 * 为什么不用官方的 Wear OS Data Layer API？
 *   因为国行 OPPO Watch 没有 GMS（Google 移动服务），Data Layer 依赖 Google Play services，
 *   在国行设备上完全不可用。所以这里自己实现一条极简 TCP 通道：
 *
 *   协议：[4 字节 body 长度(BigEndian)] + [UTF-8 的 JSON]
 *   回包：[1 字节]  1=成功 0=失败
 *
 * 手机端 [com.classwatch.phone.sync.SyncClient] 是配对的客户端实现。
 */
class SyncServer(
    private val repository: ScheduleRepository,
    private val port: Int = DEFAULT_PORT
) {

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    /** 最近一次连上来的手机 IP，UI 用来显示连接状态 */
    @Volatile
    var lastClientIp: String? = null
        private set

    fun start(scope: CoroutineScope) {
        if (isRunning) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val ss = ServerSocket(port)
                serverSocket = ss
                isRunning = true
                Log.i(TAG, "同步服务已启动 :$port")
                while (serverSocket != null) {
                    val client = ss.accept()
                    handle(client)
                }
            }.onFailure {
                Log.e(TAG, "同步服务启动失败", it)
                isRunning = false
            }
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        isRunning = false
    }

    private suspend fun handle(client: Socket) = withContext(Dispatchers.IO) {
        runCatching {
            lastClientIp = client.inetAddress?.hostAddress
            val dis = DataInputStream(client.getInputStream())

            // 可选握手：读 4 字节长度
            val len = try {
                dis.readInt()
            } catch (t: Throwable) {
                -1
            }
            if (len <= 0 || len > MAX_BYTES) {
                DataOutputStream(client.getOutputStream()).writeByte(0)
                return@runCatching
            }
            val body = ByteArray(len)
            dis.readFully(body)
            val json = String(body, Charsets.UTF_8)

            val ok = repository.accept(json)
            DataOutputStream(client.getOutputStream()).apply {
                writeByte(if (ok) 1 else 0)
                flush()
            }
            Log.i(TAG, "收到来自 $lastClientIp 的课表，结果=$ok")
        }.onFailure {
            Log.w(TAG, "处理客户端失败", it)
        }
        runCatching { client.close() }
    }

    companion object {
        private const val TAG = "SyncServer"
        const val DEFAULT_PORT = 8899
        private const val MAX_BYTES = 4 * 1024 * 1024 // 4MB，普通课表远远用不到
    }
}

/**
 * 获取当前 Wi-Fi 下本机的 IPv4 地址，用于在手表界面上显示给用户输入。
 *
 * 这里用 ConnectivityManager 而不是已废弃的 WifiManager.connectionInfo，
 * 前者在高版本 Wear OS 上依然有效。
 */
fun localIpAddress(context: Context): String? = runCatching {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val link = cm.activeNetwork?.let { cm.getLinkProperties(it) }
    link?.linkAddresses
        ?.map { it.address }
        ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
        ?.hostAddress
}.getOrNull()

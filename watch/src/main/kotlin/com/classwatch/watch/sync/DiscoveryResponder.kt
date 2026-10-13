package com.classwatch.watch.sync

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket

/**
 * 广播发现服务 —— 回答「手表你的 IP 是多少」。
 *
 * 为什么需要它：
 *   手表 IP 是路由器 DHCP 发的，换个网络、路由器重启、甚至手表开了随机 MAC，
 *   IP 都可能变。手机端靠「扫整个网段」来找手表又慢又容易被超时坑到。
 *   所以这里让手表自己监听一个 UDP 端口，手机广播一句暗号，手表把当前 IP 报回去，
 *   一次来回不到 1 秒，跟网段在哪、IP 是多少都无关。
 *
 * 协议（极简，UDP）：
 *   手机 → 广播  CLASSWATCH_WHO
 *   手表 → 单播  CLASSWATCH_HERE|<手表IP>|<同步端口>
 *
 * 与 TCP 同步通道（8899）分开用 8898，互不干扰。
 */
class DiscoveryResponder(
    private val context: Context,
    private val port: Int = DEFAULT_DISCOVERY_PORT
) {

    @Volatile
    private var socket: DatagramSocket? = null

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val s = DatagramSocket(port).apply { broadcast = true }
                socket = s
                while (isActive) {
                    val buf = ByteArray(256)
                    val packet = DatagramPacket(buf, buf.size)
                    s.receive(packet)
                    val msg = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    if (msg == MAGIC_QUERY) {
                        // 回包时实时取一次 IP，保证拿到的是当前生效的地址
                        val ip = localIpAddress(context)
                        val reply = if (ip == null) {
                            "$MAGIC_REPLY||$SYNC_PORT"      // 没连 Wi-Fi
                        } else {
                            "$MAGIC_REPLY|$ip|$SYNC_PORT"
                        }
                        val payload = reply.toByteArray(Charsets.UTF_8)
                        s.send(
                            DatagramPacket(payload, payload.size, packet.address, packet.port)
                        )
                    }
                }
            }.onFailure {
                android.util.Log.w(TAG, "广播发现服务退出", it)
            }
        }
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
    }

    companion object {
        private const val TAG = "DiscoveryResponder"
        const val DEFAULT_DISCOVERY_PORT = 8898
        const val SYNC_PORT = SyncServer.DEFAULT_PORT
        const val MAGIC_QUERY = "CLASSWATCH_WHO"
        const val MAGIC_REPLY = "CLASSWATCH_HERE"
    }
}

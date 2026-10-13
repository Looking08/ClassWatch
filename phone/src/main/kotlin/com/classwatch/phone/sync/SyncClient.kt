package com.classwatch.phone.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 手机端 → 手表端 的推送客户端。
 *
 * 协议（与手表端 SyncServer 对应）：
 *   发送：[4 字节 body 长度 BigEndian] + [UTF-8 JSON]
 *   接收：[1 字节]  1=手表接收成功 0=失败
 */
object SyncClient {

    /**
     * 把课表 JSON 推到手表。
     * @param host 手表的 IP
     * @return 成功 true；失败会抛出 IOException
     */
    suspend fun push(host: String, port: Int, json: String): Boolean = withContext(Dispatchers.IO) {
        val body = json.toByteArray(Charsets.UTF_8)
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            DataOutputStream(socket.getOutputStream()).apply {
                writeInt(body.size)
                write(body)
                flush()
            }
            withTimeoutOrNull(READ_TIMEOUT_MS.toLong()) {
                DataInputStream(socket.getInputStream()).read()
            } == 1
        }
    }

    /**
     * 扫描局域网里所有开着同步端口的设备。
     *
     * 两个坑（都是真机踩出来的）：
     *
     * 1. **不能用 ping / isReachable 探测**。OPPO 手表和不少路由器都会拦 ICMP，
     *    实测 ping 100% 丢包但 TCP 连 8899 完全正常 —— 所以这里一律走 TCP connect。
     *
     * 2. **超时不能太短**。手表息屏时 Wi-Fi 处于省电状态，TCP 握手要先把射频唤醒，
     *    实测 180ms 会全部超时，改成 800ms 才稳。
     *
     * 另外 254 个并发会瞬间把路由器连接表打满（家用路由会被限流），
     * 所以分批 32 个一组扫。
     */
    suspend fun scan(prefix: String, port: Int): List<String> = supervisorScope {
        val found = java.util.Collections.synchronizedList(mutableListOf<String>())
        (1..254).chunked(SCAN_BATCH).forEach { batch ->
            batch.map { last ->
                async(Dispatchers.IO) {
                    val host = "$prefix.$last"
                    val reachable = runCatching {
                        Socket().use { s -> s.connect(InetSocketAddress(host, port), SCAN_TIMEOUT_MS) }
                    }.isSuccess
                    if (reachable) found.add(host)
                }
            }.awaitAll()
        }
        found.sortedBy { it.substringAfterLast('.').toIntOrNull() ?: 0 }
    }

    /** 探一个 IP：连得上返回 IP，否则 null。push 成功后建议把这个 IP 记下来。 */
    suspend fun probe(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { s -> s.connect(InetSocketAddress(host, port), SCAN_TIMEOUT_MS) }
        }.isSuccess
    }

    /**
     * 广播发现：往局域网喊一句暗号，手表自己把 IP 报回来。
     *
     * 比扫整个网段快得多，而且**不受网段变化影响** —— 手表换到 192.168.0.x
     * 或者 IP 被 DHCP 重新分配了都能找到。
     *
     * @return 手表 IP；没收到回包返回 null
     */
    suspend fun discoverByBroadcast(context: Context): String? = withContext(Dispatchers.IO) {
        // 不少手机默认会丢弃多播/广播包，先拿锁
        val lock = acquireMulticastLock(context)
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(null).apply {
                broadcast = true
                reuseAddress = true
                soTimeout = DISCOVERY_TIMEOUT_MS
                bind(InetSocketAddress(0))
            }
            val query = MAGIC_QUERY.toByteArray(Charsets.UTF_8)
            // 先发受限广播，再补一发本网段的定向广播（有些路由器拦 255.255.255.255）
            val targets = mutableListOf(InetAddress.getByName("255.255.255.255"))
            localPrefix(context)?.let { prefix ->
                runCatching { targets += InetAddress.getByName("$prefix.255") }
            }
            targets.forEach { addr ->
                socket.send(DatagramPacket(query, query.size, addr, DEFAULT_DISCOVERY_PORT))
            }

            val deadline = System.currentTimeMillis() + DISCOVERY_TIMEOUT_MS
            val buf = ByteArray(256)
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buf, buf.size)
                // 超时（SocketTimeoutException）就收工
                val received = runCatching { socket.receive(packet) }.getOrNull()
                if (received == null) break
                val msg = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                // 只看手表回的 CLASSWATCH_HERE|IP|PORT
                if (msg.startsWith(MAGIC_REPLY)) {
                    val ip = msg.split("|").getOrNull(1).orEmpty()
                    if (ip.isNotBlank()) return@withContext ip
                }
            }
            null
        } catch (e: Exception) {
            null
        } finally {
            runCatching { socket?.close() }
            runCatching { lock?.release() }
        }
    }

    private fun acquireMulticastLock(context: Context): WifiManager.MulticastLock? = runCatching {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifi.createMulticastLock("classwatch-discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
    }.getOrNull()

    /**
     * 一键找手表：按「快→慢」的顺序试，找到第一个就返回。
     *
     * ① 上次成功过的 IP（最快，命中率也最高，路由器一般会给同一 MAC 留同一个地址）
     * ② UDP 广播发现（1 秒左右，IP 变了也能找到）
     * ③ 扫整个网段（兜底，约 10 秒）
     *
     * @return (找到的 IP, 用了哪种方式)
     */
    suspend fun autoConnect(
        context: Context,
        lastHost: String?,
        port: Int
    ): Pair<String, String>? {
        if (!lastHost.isNullOrBlank() && probe(lastHost, port)) {
            return lastHost to "上次记住的地址"
        }
        discoverByBroadcast(context)?.let { return it to "广播发现" }
        val prefix = localPrefix(context) ?: return null
        return scan(prefix, port).firstOrNull()?.let { it to "网段扫描" }
    }

    /** 当前 Wi-Fi 的网段前缀，例如 "192.168.1"。 */
    fun localPrefix(context: Context): String? =
        localIp(context)?.substringBeforeLast(".")

    fun localIp(context: Context): String? = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.activeNetwork?.let { cm.getLinkProperties(it) }
            ?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()

    const val DEFAULT_PORT = 8899
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000

    /** 扫描用的单次连接超时：见 scan() 注释，太短会因为手表 Wi-Fi 休眠而漏掉。 */
    const val SCAN_TIMEOUT_MS = 800
    private const val SCAN_BATCH = 32

    // ---- 广播发现（与手表端 DiscoveryResponder 对应）----
    const val DEFAULT_DISCOVERY_PORT = 8898
    const val MAGIC_QUERY = "CLASSWATCH_WHO"
    const val MAGIC_REPLY = "CLASSWATCH_HERE"
    private const val DISCOVERY_TIMEOUT_MS = 1_500
}

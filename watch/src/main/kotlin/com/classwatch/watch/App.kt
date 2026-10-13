package com.classwatch.watch

import android.app.Application
import com.classwatch.watch.data.ScheduleRepository
import com.classwatch.watch.sync.DiscoveryResponder
import com.classwatch.watch.sync.SyncServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * 手表端 Application。
 *
 * 在这里启动同步服务的原因：只要进程活着（哪怕主界面退到后台），
 * 手机端推送课表也能收到。真要 7×24 常驻的话，下一步把它升级成
 * ForegroundService + 常驻通知即可（README 里有说明）。
 */
class App : Application() {

    val appScope = CoroutineScope(SupervisorJob())

    /** 全局唯一的课表仓库，UI 和 Tile 都从这里取数据。 */
    val repository: ScheduleRepository by lazy { ScheduleRepository(this) }

    val syncServer: SyncServer by lazy { SyncServer(repository) }

    /** 广播发现：手机一句暗号，手表就把当前 IP 报回去，IP 变了也能找到。 */
    val discovery: DiscoveryResponder by lazy { DiscoveryResponder(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        syncServer.start(appScope)
        discovery.start(appScope)
        repository.observe(appScope)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}

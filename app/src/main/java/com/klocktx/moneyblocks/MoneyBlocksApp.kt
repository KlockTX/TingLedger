package com.klocktx.moneyblocks

import android.app.Application
import android.util.Log
import com.klocktx.moneyblocks.core.AppContainer
import kotlinx.coroutines.launch

class MoneyBlocksApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 周期账单与回收站清理都在进程启动时补一次，不养后台任务：这类 App 被系统杀掉是常态，
        // 起一次做一次就够，省下一条常驻通知和一套 WorkManager 依赖。
        container.applicationScope.launch {
            runCatching { container.recurring.runToday() }
                .onFailure { Log.w(TAG, "周期性账单生成失败，本次跳过", it) }
            runCatching { container.manageBills.purgeTrash() }
                .onFailure { Log.w(TAG, "回收站清理失败，本次跳过", it) }
        }
    }

    private companion object {
        const val TAG = "TingLedger"
    }
}

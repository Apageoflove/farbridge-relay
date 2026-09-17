// 开机或应用升级后恢复周期任务、全量收敛和心跳。
package com.zj.phonemirror.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zj.phonemirror.worker.WorkerScheduler

/** 只恢复 WorkManager 调度，不在开机广播中访问 Provider 或网络。 */
class BootCompletedReceiver : BroadcastReceiver() {
    /** 对 BOOT_COMPLETED 和 MY_PACKAGE_REPLACED 执行幂等恢复。 */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            WorkerScheduler.schedulePeriodic(context)
            WorkerScheduler.enqueueBootstrap(context)
        }
    }
}

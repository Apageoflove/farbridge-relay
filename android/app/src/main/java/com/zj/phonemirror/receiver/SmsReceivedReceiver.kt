// 普通 Mirror Mode 的 SMS 广播接收器，仅入队 Reconcile。
package com.zj.phonemirror.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zj.phonemirror.worker.WorkerScheduler

/** 广播生命周期内不查询 Provider、不写数据库、不发送 HTTP。 */
class SmsReceivedReceiver : BroadcastReceiver() {
    /** 收到 SMS_RECEIVED 后仅触发唯一任务。 */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            WorkerScheduler.enqueueSms(context)
            // 部分系统可能在广播之后才提交 Provider；独立尾随任务保证最终读到已落盘短信。
            WorkerScheduler.enqueueSmsAfterProviderCommit(context)
        }
    }
}

// SMS Provider 变化观察器只快速触发唯一 WorkManager 任务。
package com.zj.phonemirror.observer

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import com.zj.phonemirror.worker.WorkerScheduler

/** 不在 Binder/主线程回调中查询 Provider 或访问网络。 */
class SmsContentObserver(handler: Handler, private val context: Context) : ContentObserver(handler) {
    /** 合并频繁变更为唯一 Reconcile。 */
    override fun onChange(selfChange: Boolean) { WorkerScheduler.enqueueSms(context) }
}

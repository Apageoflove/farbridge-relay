// CallLog Provider 变化观察器只快速触发唯一 WorkManager 任务。
package com.zj.phonemirror.observer

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import com.zj.phonemirror.worker.WorkerScheduler

/** 不把 ContentObserver 当作删除可靠性来源，周期 Reconcile 仍是兜底。 */
class CallLogContentObserver(handler: Handler, private val context: Context) : ContentObserver(handler) {
    /** 合并频繁变更为唯一 Reconcile。 */
    override fun onChange(selfChange: Boolean) { WorkerScheduler.enqueueCalls(context) }
}

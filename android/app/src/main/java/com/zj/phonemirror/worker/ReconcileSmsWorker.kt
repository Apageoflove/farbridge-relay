// WorkManager 中执行完整 SMS Provider 读取和事务收敛。
package com.zj.phonemirror.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zj.phonemirror.data.db.AppDatabase
import com.zj.phonemirror.model.ProviderSnapshot
import com.zj.phonemirror.provider.SmsProviderReader
import com.zj.phonemirror.repository.MirrorRepository

/** Provider 失败返回 retry，且 Repository 保证零删除、零写入。 */
class ReconcileSmsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    /** 完整快照���功���触发 Outbox 网络同步。 */
    override suspend fun doWork(): Result {
        val snapshot = SmsProviderReader(applicationContext.contentResolver).readAll()
        if (snapshot is ProviderSnapshot.Failure) return Result.retry()
        MirrorRepository(AppDatabase.get(applicationContext)).reconcileSms(snapshot)
        WorkerScheduler.enqueueSync(applicationContext)
        return Result.success()
    }
}

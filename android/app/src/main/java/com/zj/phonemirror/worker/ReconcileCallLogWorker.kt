// WorkManager 中执行完整 CallLog Provider 读取和事务收敛。
package com.zj.phonemirror.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zj.phonemirror.data.db.AppDatabase
import com.zj.phonemirror.model.ProviderSnapshot
import com.zj.phonemirror.provider.CallLogProviderReader
import com.zj.phonemirror.repository.MirrorRepository

/** Provider 失败返回 retry，且 Repository 保证零删除、零写入。 */
class ReconcileCallLogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    /** 完整快照成功后触发 Outbox 网络同步。 */
    override suspend fun doWork(): Result {
        val snapshot = CallLogProviderReader(applicationContext.contentResolver).readAll()
        if (snapshot is ProviderSnapshot.Failure) return Result.retry()
        MirrorRepository(AppDatabase.get(applicationContext)).reconcileCalls(snapshot)
        WorkerScheduler.enqueueSync(applicationContext)
        return Result.success()
    }
}

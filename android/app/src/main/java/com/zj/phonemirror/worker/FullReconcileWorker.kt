// 串行完成 SMS 与 CallLog 全量读取，避免部分成功被误当完整 Snapshot。
package com.zj.phonemirror.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.zj.phonemirror.data.db.AppDatabase
import com.zj.phonemirror.model.ProviderSnapshot
import com.zj.phonemirror.provider.CallLogProviderReader
import com.zj.phonemirror.provider.SmsProviderReader
import com.zj.phonemirror.repository.MirrorRepository

/** 任一 Provider 失败则整轮重试，不启动后续 Snapshot。 */
class FullReconcileWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    /** 两份完整快照均成功后分别执行原子收敛。 */
    override suspend fun doWork(): Result {
        val sms = SmsProviderReader(applicationContext.contentResolver).readAll()
        val calls = CallLogProviderReader(applicationContext.contentResolver).readAll()
        if (sms is ProviderSnapshot.Failure || calls is ProviderSnapshot.Failure) {
            return failureOrRetry("provider_read_failed")
        }
        val repository = MirrorRepository(AppDatabase.get(applicationContext))
        repository.reconcileSms(sms)
        repository.reconcileCalls(calls)
        if (!inputData.getBoolean(ManualSyncPolicy.INPUT_IS_MANUAL, false)) {
            WorkerScheduler.enqueueSync(applicationContext)
        }
        return Result.success()
    }

    /** 手动任务结束并返回受控原因，周期或启动任务仍自动重试。 */
    private fun failureOrRetry(code: String): Result =
        if (ManualSyncPolicy.failureDisposition(inputData.getBoolean(ManualSyncPolicy.INPUT_IS_MANUAL, false)) ==
            ManualSyncPolicy.FailureDisposition.FINISH
        ) {
            Result.failure(workDataOf(ManualSyncPolicy.OUTPUT_ERROR_CODE to code))
        } else {
            Result.retry()
        }
}

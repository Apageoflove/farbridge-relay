// 可靠上传 Outbox 批次，partial ACK 和模糊失败均不会丢事件。
package com.zj.phonemirror.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

/** 先事务领取、再发送、最后只删除明确 ACK 的行。 */
class OutboxSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    /** 对失败使用 event_id 幂等重放，只保存错误码，不保存原始报文。 */
    override suspend fun doWork(): Result {
        val isManual = inputData.getBoolean(ManualSyncPolicy.INPUT_IS_MANUAL, false)
        return when (val outcome = OutboxSyncRunner(applicationContext).run(isManual = isManual)) {
            OutboxSyncResult.Completed -> Result.success()
            is OutboxSyncResult.Retry -> Result.retry()
            is OutboxSyncResult.Failed -> Result.failure(
                workDataOf(ManualSyncPolicy.OUTPUT_ERROR_CODE to outcome.code),
            )
        }
    }
}

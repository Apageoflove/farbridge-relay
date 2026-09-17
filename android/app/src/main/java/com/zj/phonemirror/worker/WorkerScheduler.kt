// 集中配置唯一即时任务、周期收敛、心跳和开机恢复调度。
package com.zj.phonemirror.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import java.util.UUID

/** 所有触发器使用唯一任务名，避免广播/Observer 风暴。 */
object WorkerScheduler {
    const val MANUAL_SYNC_WORK_NAME = "manual-full-sync"

    /** 快速触发完整 SMS Reconcile，Receiver 本身不访问网络。 */
    fun enqueueSms(context: Context) = WorkManager.getInstance(context).enqueueUniqueWork(
        "reconcile-sms", RealtimeTriggerPolicy.existingWorkPolicy(RealtimeTriggerPolicy.Provider.SMS),
        OneTimeWorkRequestBuilder<ReconcileSmsWorker>().build(),
    )

    /** 广播触发后等待系统 SMS Provider 提交，再执行一次去重的尾随收敛。 */
    fun enqueueSmsAfterProviderCommit(context: Context) = WorkManager.getInstance(context).enqueueUniqueWork(
        "reconcile-sms-after-provider-commit",
        SmsProviderCommitPolicy.existingWorkPolicy,
        OneTimeWorkRequestBuilder<ReconcileSmsWorker>()
            .setInitialDelay(SmsProviderCommitPolicy.delayMillis, TimeUnit.MILLISECONDS)
            .build(),
    )

    /** 快速触发完整 CallLog Reconcile。 */
    fun enqueueCalls(context: Context) = WorkManager.getInstance(context).enqueueUniqueWork(
        "reconcile-calls", RealtimeTriggerPolicy.existingWorkPolicy(RealtimeTriggerPolicy.Provider.CALL_LOG),
        OneTimeWorkRequestBuilder<ReconcileCallLogWorker>().build(),
    )

    /** 仅在网络连接时上传到期 Outbox。 */
    fun enqueueSync(context: Context) = WorkManager.getInstance(context).enqueueUniqueWork(
        "outbox-sync", ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<OutboxSyncWorker>()
            .setConstraints(networkConstraints())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build(),
    )

    /** 前台中继恢复时立即上报一次心跳，周期心跳继续负责长期兜底。 */
    fun enqueueHeartbeat(context: Context) = WorkManager.getInstance(context).enqueueUniqueWork(
        "heartbeat-now", ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<HeartbeatWorker>().setConstraints(networkConstraints()).build(),
    )

    /** 首次/升级时先完整读取，再用可恢复 Snapshot 收敛服务器。 */
    fun enqueueBootstrap(context: Context) {
        WorkManager.getInstance(context).beginUniqueWork(
            "bootstrap", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<FullReconcileWorker>().build(),
        ).then(OneTimeWorkRequestBuilder<SnapshotWorker>().setConstraints(networkConstraints()).build()).enqueue()
    }

    /** 以 REPLACE 强制重启完整手动链，并让心跳与 Provider 读取立即并行开始。 */
    fun enqueueManualSync(context: Context): String {
        val work = WorkManager.getInstance(context)
        val runTag = "manual-sync-${UUID.randomUUID()}"
        val input = workDataOf(ManualSyncPolicy.INPUT_IS_MANUAL to true)
        val full = OneTimeWorkRequestBuilder<FullReconcileWorker>().setInputData(input).addTag(runTag).build()
        val heartbeat = OneTimeWorkRequestBuilder<HeartbeatWorker>()
            .setInputData(input).setConstraints(networkConstraints()).addTag(runTag).build()
        val snapshot = OneTimeWorkRequestBuilder<SnapshotWorker>()
            .setInputData(input).setConstraints(networkConstraints()).addTag(runTag).build()
        val upload = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
            .setInputData(input).setConstraints(networkConstraints()).addTag(runTag).build()
        work.beginUniqueWork(MANUAL_SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, listOf(full, heartbeat))
            .then(snapshot)
            .then(upload)
            .enqueue()
        return runTag
    }

    /** 注册 15 分钟增量兜底、24 小时完整校验和 15 分钟心跳。 */
    fun schedulePeriodic(context: Context) {
        val work = WorkManager.getInstance(context)
        work.enqueueUniquePeriodicWork("sms-periodic", ExistingPeriodicWorkPolicy.UPDATE, PeriodicWorkRequestBuilder<ReconcileSmsWorker>(15, TimeUnit.MINUTES).build())
        work.enqueueUniquePeriodicWork("call-periodic", ExistingPeriodicWorkPolicy.UPDATE, PeriodicWorkRequestBuilder<ReconcileCallLogWorker>(15, TimeUnit.MINUTES).build())
        work.enqueueUniquePeriodicWork("full-periodic", ExistingPeriodicWorkPolicy.UPDATE, PeriodicWorkRequestBuilder<FullReconcileWorker>(24, TimeUnit.HOURS).build())
        work.enqueueUniquePeriodicWork("heartbeat", ExistingPeriodicWorkPolicy.UPDATE, PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES).setConstraints(networkConstraints()).build())
    }

    /** 统一要求可用网络，具体 TLS/HTTP 失败继续由 Worker 重试。 */
    private fun networkConstraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
}

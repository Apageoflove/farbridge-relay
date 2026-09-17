// 从 Room 当前镜像生成可恢复全量 Snapshot，失败后复用同一 snapshotId。
package com.zj.phonemirror.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.zj.phonemirror.BuildConfig
import com.zj.phonemirror.data.db.AppDatabase
import com.zj.phonemirror.network.ApiClient
import com.zj.phonemirror.network.dto.SnapshotRequest
import com.zj.phonemirror.security.SecretStore
import com.zj.phonemirror.settings.SettingsStore

/** Snapshot 只在前置 FullReconcile 成功后调度，且 complete 固定为 true。 */
class SnapshotWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    /** 服务端成功前保留 snapshotId 以支持进程重启后的幂等重试。 */
    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext)
        val url = settings.serverUrl ?: return failureOrRetry("config_missing")
        if (!SecretStore(applicationContext).hasSecret()) return failureOrRetry("config_missing")
        return try {
            val db = AppDatabase.get(applicationContext)
            val messages = db.smsDao().all().map { item -> mapOf<String, Any?>(
                "source_id" to item.sourceId, "entity_version" to item.entityVersion,
                "occurred_at" to item.smsDate / 1000L,
                "payload" to mapOf<String, Any?>(
                    "sender" to item.address, "body" to item.body, "received_at" to item.smsDate / 1000L,
                    "date_sent" to item.dateSent / 1000L, "sms_type" to item.smsType, "thread_id" to item.threadId,
                    "subscription_id" to item.subscriptionId, "otp" to item.verificationCode,
                    "fingerprint" to item.fingerprint,
                ),
            ) }
            val calls = db.callDao().all().map { item -> mapOf<String, Any?>(
                "source_id" to item.sourceId, "entity_version" to item.entityVersion,
                "occurred_at" to item.callDate / 1000L,
                "payload" to mapOf<String, Any?>(
                    "number" to item.number, "cached_name" to item.cachedName, "call_type" to item.callType,
                    "call_date" to item.callDate / 1000L, "duration" to item.duration,
                    "phone_account_id" to item.phoneAccountId,
                    "phone_account_component_name" to item.phoneAccountComponentName,
                    "fingerprint" to item.fingerprint,
                ),
            ) }
            val request = SnapshotRequest(settings.deviceId, settings.pendingSnapshotId(), System.currentTimeMillis() / 1000L, true, messages, calls)
            val response = ApiClient.create(url, settings.deviceId, SecretStore(applicationContext), BuildConfig.ALLOW_CLEARTEXT_SERVER).syncSnapshot(request)
            if (response.isSuccessful) {
                settings.completeSnapshot()
                Result.success()
            } else failureOrRetry("http_${response.code()}")
        } catch (_: Exception) {
            failureOrRetry("network_or_tls_failure")
        }
    }

    /** 手动任务立即结束并回传白名单错误码，自动任务继续退避重试。 */
    private fun failureOrRetry(code: String): Result =
        if (ManualSyncPolicy.failureDisposition(inputData.getBoolean(ManualSyncPolicy.INPUT_IS_MANUAL, false)) ==
            ManualSyncPolicy.FailureDisposition.FINISH
        ) {
            Result.failure(workDataOf(ManualSyncPolicy.OUTPUT_ERROR_CODE to code))
        } else {
            Result.retry()
        }
}

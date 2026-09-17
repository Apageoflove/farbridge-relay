// 为前台服务和 WorkManager 复用同一套可靠 Outbox 上传与 ACK 状态机。
package com.zj.phonemirror.worker

import android.content.Context
import androidx.room.withTransaction
import com.zj.phonemirror.BuildConfig
import com.zj.phonemirror.data.db.AppDatabase
import com.zj.phonemirror.data.db.OutboxEventEntity
import com.zj.phonemirror.network.ApiClient
import com.zj.phonemirror.network.SyncStateMachine
import com.zj.phonemirror.network.dto.SyncBatchRequest
import com.zj.phonemirror.network.dto.SyncEventDto
import com.zj.phonemirror.security.SecretStore
import com.zj.phonemirror.settings.SettingsStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** 同一进程内串行领取和上传，避免前台直传与 Worker 重复发送同一批事件。 */
class OutboxSyncRunner(private val context: Context) {
    /** 执行一批上传；仅服务器明确 ACK 的事件会被删除。 */
    suspend fun run(isManual: Boolean = false, immediateFallback: Boolean = false): OutboxSyncResult = mutex.withLock {
        val db = AppDatabase.get(context)
        val now = System.currentTimeMillis()
        db.withTransaction {
            db.outboxDao().recoverAbandoned(SyncStateMachine.abandonedLeaseBefore(now))
            if (isManual) db.outboxDao().expeditePending()
        }
        val batch = db.outboxDao().due(now, 100)
        if (batch.isEmpty()) return@withLock OutboxSyncResult.Completed
        val ids = batch.map { it.eventId }
        db.withTransaction { db.outboxDao().markSending(ids, now) }
        val settings = SettingsStore(context)
        val url = settings.serverUrl ?: return@withLock retry(db, batch, "config_missing", isManual, immediateFallback)
        if (!SecretStore(context).hasSecret()) {
            return@withLock retry(db, batch, "config_missing", isManual, immediateFallback)
        }
        try {
            val api = ApiClient.create(url, settings.deviceId, SecretStore(context), BuildConfig.ALLOW_CLEARTEXT_SERVER)
            val response = api.syncEvents(SyncBatchRequest(batch.map { it.toDto(settings.deviceId) }))
            if (!response.isSuccessful || response.body() == null) {
                return@withLock retry(db, batch, "http_${response.code()}", isManual, immediateFallback)
            }
            val transition = SyncStateMachine.afterResponse(ids, response.body()!!.ackedEventIds.toSet())
            db.withTransaction {
                if (transition.acked.isNotEmpty()) db.outboxDao().deleteAcked(transition.acked.toList())
                if (transition.retry.isNotEmpty()) {
                    markRetry(db, batch.filter { it.eventId in transition.retry }, "partial_ack", immediateFallback)
                }
            }
            return@withLock if (transition.retry.isEmpty()) {
                OutboxSyncResult.Completed
            } else {
                OutboxSyncResult.Retry("partial_ack")
            }
        } catch (_: Exception) {
            return@withLock retry(db, batch, "network_or_tls_failure", isManual, immediateFallback)
        }
    }

    /** 模糊失败恢复为 PENDING；前台失败立即交给 Worker，Worker 失败再使用持久退避。 */
    private suspend fun retry(
        db: AppDatabase,
        batch: List<OutboxEventEntity>,
        code: String,
        isManual: Boolean,
        immediateFallback: Boolean,
    ): OutboxSyncResult {
        db.withTransaction { markRetry(db, batch, code, immediateFallback) }
        return if (isManual && ManualSyncPolicy.failureDisposition(true) == ManualSyncPolicy.FailureDisposition.FINISH) {
            OutboxSyncResult.Failed(code)
        } else {
            OutboxSyncResult.Retry(code)
        }
    }

    /** 按运行入口选择立即兜底或数据库退避，不会删除未 ACK 事件。 */
    private suspend fun markRetry(
        db: AppDatabase,
        batch: List<OutboxEventEntity>,
        code: String,
        immediateFallback: Boolean,
    ) {
        val now = System.currentTimeMillis()
        batch.forEach { event ->
            val next = if (immediateFallback) 0L else now + SyncStateMachine.backoffMillis(event.retryCount)
            db.outboxDao().markRetry(listOf(event.eventId), next, code)
        }
    }

    /** 从受控 JSON payload 转为 Retrofit DTO。 */
    private fun OutboxEventEntity.toDto(deviceId: String) = SyncEventDto(
        deviceId, eventId, entityType, action, sourceId, entityVersion, occurredAt,
        payloadJson?.let { jsonObjectToMap(JSONObject(it)) },
    )

    /** 递归转换 JSONObject，避免把平台对象交给 Moshi。 */
    private fun jsonObjectToMap(value: JSONObject): Map<String, Any?> = value.keys().asSequence().associateWith { key ->
        when (val item = value.get(key)) {
            JSONObject.NULL -> null
            is JSONObject -> jsonObjectToMap(item)
            is JSONArray -> (0 until item.length()).map { item.get(it) }
            else -> item
        }
    }

    companion object {
        private val mutex = Mutex()
    }
}

/** 上传结果只暴露固定错误码，供 Worker 决策和本地诊断使用。 */
sealed interface OutboxSyncResult {
    data object Completed : OutboxSyncResult
    data class Retry(val code: String) : OutboxSyncResult
    data class Failed(val code: String) : OutboxSyncResult
}

// 在单一 Room 事务内完成镜像变化和 Outbox 事件写入。
package com.zj.phonemirror.repository

import androidx.room.withTransaction
import com.zj.phonemirror.data.db.AppDatabase
import com.zj.phonemirror.data.db.CallMirrorEntity
import com.zj.phonemirror.data.db.OutboxEventEntity
import com.zj.phonemirror.data.db.SmsMirrorEntity
import com.zj.phonemirror.model.CallRecord
import com.zj.phonemirror.model.ProviderSnapshot
import com.zj.phonemirror.model.SmsRecord
import org.json.JSONObject
import java.util.UUID

/** 强制完整快照门禁，并为每条变化生成单调版本事件。 */
class MirrorRepository(private val db: AppDatabase) {
    /** 完整收敛 SMS；失败/重复快照零写入。 */
    suspend fun reconcileSms(snapshot: ProviderSnapshot<SmsRecord>): ReconcileOutcome {
        val records = (snapshot as? ProviderSnapshot.Complete)?.records
            ?: return ReconcileOutcome.Skipped((snapshot as ProviderSnapshot.Failure).reason)
        if (records.map { it.sourceId }.toSet().size != records.size) return ReconcileOutcome.Skipped("duplicate_source_id")
        return db.withTransaction {
            val now = System.currentTimeMillis()
            val old = db.smsDao().all().associateBy { it.sourceId }
            var changed = 0
            records.forEach { record ->
                val previous = old[record.sourceId]
                if (previous == null || previous.fingerprint != record.fingerprint) {
                    val version = (previous?.entityVersion ?: 0L) + 1L
                    db.smsDao().upsert(record.toEntity(version, previous?.createdAt ?: now, now))
                    db.outboxDao().upsert(event("SMS", "UPSERT", record.sourceId, version, smsPayload(record), now))
                    changed++
                }
            }
            // OEM Provider 快照不是删除日志；任何一次缺失都可能是落盘延迟、权限视图或系统竞态。
            // 普通镜像模式因此只 UPSERT，仅明确的默认短信删除流程可调用 recordSmsProviderDelete。
            ReconcileOutcome.Applied(changed)
        }
    }

    /** 完整收敛 CallLog；失败/重复快照零写入。 */
    suspend fun reconcileCalls(snapshot: ProviderSnapshot<CallRecord>): ReconcileOutcome {
        val records = (snapshot as? ProviderSnapshot.Complete)?.records
            ?: return ReconcileOutcome.Skipped((snapshot as ProviderSnapshot.Failure).reason)
        if (records.map { it.sourceId }.toSet().size != records.size) return ReconcileOutcome.Skipped("duplicate_source_id")
        return db.withTransaction {
            val now = System.currentTimeMillis()
            val old = db.callDao().all().associateBy { it.sourceId }
            val currentIds = records.mapTo(mutableSetOf()) { it.sourceId }
            var changed = 0
            records.forEach { record ->
                val previous = old[record.sourceId]
                if (previous == null || previous.fingerprint != record.fingerprint) {
                    val version = (previous?.entityVersion ?: 0L) + 1L
                    db.callDao().upsert(record.toEntity(version, previous?.createdAt ?: now, now))
                    db.outboxDao().upsert(event("CALL", "UPSERT", record.sourceId, version, callPayload(record), now))
                    changed++
                }
            }
            old.values.filterNot { it.sourceId in currentIds }.forEach { previous ->
                val version = previous.entityVersion + 1L
                db.callDao().delete(previous.sourceId)
                db.outboxDao().upsert(event("CALL", "DELETE", previous.sourceId, version, null, now))
                changed++
            }
            ReconcileOutcome.Applied(changed)
        }
    }

    /** 默认短信删除在 Provider 成功后用此事务收敛本地与 Outbox。 */
    suspend fun recordSmsProviderDelete(sourceId: String): ReconcileOutcome = db.withTransaction {
        val previous = db.smsDao().all().firstOrNull { it.sourceId == sourceId }
            ?: return@withTransaction ReconcileOutcome.Applied(0)
        val now = System.currentTimeMillis()
        db.smsDao().delete(sourceId)
        db.outboxDao().upsert(event("SMS", "DELETE", sourceId, previous.entityVersion + 1L, null, now))
        ReconcileOutcome.Applied(1)
    }

    /** 构造具有 UUID、秒级协议时间和 PENDING 状态的事件。 */
    private fun event(type: String, action: String, sourceId: String, version: Long, payload: String?, now: Long) =
        OutboxEventEntity(UUID.randomUUID().toString(), type, action, sourceId, version, payload, now / 1000L, now)

    /** 将 SMS 记录编码为服务端协议字段。 */
    private fun smsPayload(record: SmsRecord): String = JSONObject().apply {
        put("sender", record.address)
        put("body", record.body)
        put("received_at", record.date / 1000L)
        put("date_sent", record.dateSent / 1000L)
        put("sms_type", record.type)
        put("thread_id", record.threadId)
        put("subscription_id", record.subscriptionId)
        put("otp", record.verificationCode)
        put("fingerprint", record.fingerprint)
    }.toString()

    /** 将 CallLog 记录编码为服务端协议字段。 */
    private fun callPayload(record: CallRecord): String = JSONObject().apply {
        put("number", record.number)
        put("cached_name", record.cachedName)
        put("call_type", record.callType)
        put("call_date", record.date / 1000L)
        put("duration", record.duration)
        put("phone_account_id", record.phoneAccountId)
        put("phone_account_component_name", record.phoneAccountComponentName)
        put("fingerprint", record.fingerprint)
    }.toString()

    /** 转换为 Room SMS 实体并保留初次创建时间。 */
    private fun SmsRecord.toEntity(version: Long, created: Long, now: Long) = SmsMirrorEntity(
        sourceId, threadId, subscriptionId, address, body, type, date, dateSent,
        verificationCode, fingerprint, version, now, created, now,
    )

    /** 转换为 Room Call 实体并保留初次创建时间。 */
    private fun CallRecord.toEntity(version: Long, created: Long, now: Long) = CallMirrorEntity(
        sourceId, number, cachedName, callType, date, duration, phoneAccountId,
        phoneAccountComponentName, fingerprint, version, now, created, now,
    )
}

/** Reconcile 结果明确区分已应用与因安全门禁跳过。 */
sealed interface ReconcileOutcome {
    /** 事务成功及变化数量。 */
    data class Applied(val changed: Int) : ReconcileOutcome
    /** 无任何状态变化及可公开的错误码。 */
    data class Skipped(val reason: String) : ReconcileOutcome
}

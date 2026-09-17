// 纯函数计算完整快照与本地镜像间的 UPSERT/DELETE 差异。
package com.zj.phonemirror.reconcile

import com.zj.phonemirror.model.ProviderSnapshot
import com.zj.phonemirror.model.SourceRecord

/** 描述一条需写入镜像和 Outbox 的 UPSERT。 */
data class VersionedUpsert(val sourceId: String, val fingerprint: String, val nextVersion: Long)

/** 差分结果；skipped 为 true 时禁止任何数据库写入。 */
data class DiffResult(
    val upserts: List<VersionedUpsert> = emptyList(),
    val deletes: List<String> = emptyList(),
    val skipped: Boolean = false,
    val reason: String? = null,
)

/** 仅对完整、无重复主键的快照计算变化。 */
object DiffEngine {
    /** 对比指纹并为每个变化生成单调递增实体版本。 */
    fun calculate(previous: List<SourceRecord>, snapshot: ProviderSnapshot<SourceRecord>): DiffResult {
        if (snapshot is ProviderSnapshot.Failure) return DiffResult(skipped = true, reason = snapshot.reason)
        val current = (snapshot as ProviderSnapshot.Complete).records
        if (current.map { it.sourceId }.toSet().size != current.size) {
            return DiffResult(skipped = true, reason = "duplicate_source_id")
        }
        val oldById = previous.associateBy { it.sourceId }
        val currentById = current.associateBy { it.sourceId }
        val upserts = current.mapNotNull { record ->
            val old = oldById[record.sourceId]
            if (old == null || old.fingerprint != record.fingerprint) {
                VersionedUpsert(record.sourceId, record.fingerprint, (old?.version ?: 0) + 1)
            } else null
        }
        val deletes = previous.asSequence().map { it.sourceId }.filterNot(currentById::containsKey).sorted().toList()
        return DiffResult(upserts = upserts, deletes = deletes)
    }
}

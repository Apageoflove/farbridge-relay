// 定义首次 bootstrap 和人工修复使用的完整镜像快照。
package com.zj.phonemirror.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Snapshot 只有 complete=true 才允许服务端收敛删除。 */
@JsonClass(generateAdapter = false)
data class SnapshotRequest(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "snapshot_id") val snapshotId: String,
    @Json(name = "generated_at") val generatedAt: Long,
    val complete: Boolean = true,
    val messages: List<Map<String, Any?>>,
    val calls: List<Map<String, Any?>>,
)

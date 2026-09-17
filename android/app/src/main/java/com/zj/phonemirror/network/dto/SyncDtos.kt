// 定义 Android 与服务端 /sync/events 的精确 JSON DTO。
package com.zj.phonemirror.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** 单个幂等 UPSERT/DELETE 协议事件。 */
@JsonClass(generateAdapter = false)
data class SyncEventDto(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "event_id") val eventId: String,
    @Json(name = "entity_type") val entityType: String,
    val action: String,
    @Json(name = "source_id") val sourceId: String,
    @Json(name = "entity_version") val entityVersion: Long,
    @Json(name = "occurred_at") val occurredAt: Long,
    val payload: Map<String, Any?>?,
)

/** 事件批次请求。 */
@JsonClass(generateAdapter = false)
data class SyncBatchRequest(val events: List<SyncEventDto>)

/** 服务端只明确 ACK 成功提交的 eventId。 */
@JsonClass(generateAdapter = false)
data class SyncBatchResponse(@Json(name = "acked_event_ids") val ackedEventIds: List<String>)

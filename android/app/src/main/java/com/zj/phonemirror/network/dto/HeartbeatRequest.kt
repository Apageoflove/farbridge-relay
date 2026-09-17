// 定义不含短信、号码或 OTP 的设备健康上报。
package com.zj.phonemirror.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** 设备心跳只包含运行与权限状态。 */
@JsonClass(generateAdapter = false)
data class HeartbeatRequest(
    @Json(name = "device_id") val deviceId: String,
    val timestamp: Long,
    @Json(name = "battery_percent") val batteryPercent: Int,
    val charging: Boolean,
    @Json(name = "network_type") val networkType: String,
    @Json(name = "pending_event_count") val pendingEventCount: Int,
    @Json(name = "sms_permission_ok") val smsPermissionOk: Boolean,
    @Json(name = "call_log_permission_ok") val callLogPermissionOk: Boolean,
    @Json(name = "app_version") val appVersion: String,
    val status: String,
)

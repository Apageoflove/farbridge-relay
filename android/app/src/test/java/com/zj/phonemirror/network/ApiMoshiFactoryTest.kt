// 验证 Retrofit 使用的 Moshi 能按服务端协议序列化真实 Kotlin DTO。
package com.zj.phonemirror.network

import com.zj.phonemirror.network.dto.HeartbeatRequest
import org.junit.Assert.assertTrue
import org.junit.Test

/** 防止 Kotlin data class 因缺少反射适配器而在发送请求前序列化失败。 */
class ApiMoshiFactoryTest {
    /** 心跳 DTO 必须输出服务端约定的 snake_case 字段。 */
    @Test
    fun serializesHeartbeatRequestWithProtocolFieldNames() {
        val request = HeartbeatRequest(
            deviceId = "oneplus-test",
            timestamp = 1_789_000_000L,
            batteryPercent = 73,
            charging = true,
            networkType = "WIFI",
            pendingEventCount = 2,
            smsPermissionOk = true,
            callLogPermissionOk = true,
            appVersion = "1.0.4",
            status = "ONLINE",
        )

        val json = ApiMoshiFactory.create()
            .adapter(HeartbeatRequest::class.java)
            .toJson(request)

        assertTrue(json.contains("\"device_id\":\"oneplus-test\""))
        assertTrue(json.contains("\"battery_percent\":73"))
        assertTrue(json.contains("\"network_type\":\"WIFI\""))
    }
}

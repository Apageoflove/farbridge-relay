// 声明 Android 设备侧同步、快照和心跳接口。
package com.zj.phonemirror.network

import com.zj.phonemirror.network.dto.HeartbeatRequest
import com.zj.phonemirror.network.dto.SnapshotRequest
import com.zj.phonemirror.network.dto.SyncBatchRequest
import com.zj.phonemirror.network.dto.SyncBatchResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** 所有请求由签名拦截器附加 HMAC 头。 */
interface ApiService {
    /** 上传一批至少一次投递事件。 */
    @POST("api/v1/sync/events") suspend fun syncEvents(@Body request: SyncBatchRequest): Response<SyncBatchResponse>
    /** 上传完整可恢复快照。 */
    @POST("api/v1/sync/snapshot") suspend fun syncSnapshot(@Body request: SnapshotRequest): Response<Unit>
    /** 上报设备运行健康状态。 */
    @POST("api/v1/heartbeat") suspend fun heartbeat(@Body request: HeartbeatRequest): Response<Unit>
}

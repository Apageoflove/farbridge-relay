// 定期上报电量、网络、权限、角色与 Outbox 积压，不包含敏感正文。
package com.zj.phonemirror.worker

import android.Manifest
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.zj.phonemirror.BuildConfig
import com.zj.phonemirror.data.db.AppDatabase
import com.zj.phonemirror.default_sms.DefaultSmsRoleManager
import com.zj.phonemirror.default_sms.SmsMode
import com.zj.phonemirror.network.ApiClient
import com.zj.phonemirror.network.dto.HeartbeatRequest
import com.zj.phonemirror.permission.PermissionChecker
import com.zj.phonemirror.security.SecretStore
import com.zj.phonemirror.settings.SettingsStore
import com.zj.phonemirror.util.DeviceStatusUtils

/** 权限或用户请求后的角色丢失统一上报 DEGRADED。 */
class HeartbeatWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    /** TLS/网络失败交由 WorkManager 重试，不输出异常正文。 */
    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext)
        val url = settings.serverUrl ?: return failureOrRetry("config_missing")
        if (!SecretStore(applicationContext).hasSecret()) return failureOrRetry("config_missing")
        val permissions = PermissionChecker(applicationContext).checkAll()
        fun granted(name: String) = permissions.first { it.permission == name }.granted
        val smsOk = granted(Manifest.permission.RECEIVE_SMS) && granted(Manifest.permission.READ_SMS)
        val callOk = granted(Manifest.permission.READ_CALL_LOG)
        val mode = DefaultSmsRoleManager(applicationContext).mode()
        val status = if (smsOk && callOk && mode != SmsMode.DEGRADED) "ONLINE" else "DEGRADED"
        val request = HeartbeatRequest(
            settings.deviceId, System.currentTimeMillis() / 1000L,
            DeviceStatusUtils.batteryPercent(applicationContext), DeviceStatusUtils.charging(applicationContext),
            DeviceStatusUtils.networkType(applicationContext), AppDatabase.get(applicationContext).outboxDao().countPending(),
            smsOk, callOk, BuildConfig.VERSION_NAME, status,
        )
        return try {
            val response = ApiClient.create(url, settings.deviceId, SecretStore(applicationContext), BuildConfig.ALLOW_CLEARTEXT_SERVER).heartbeat(request)
            if (response.isSuccessful) Result.success() else failureOrRetry("http_${response.code()}")
        } catch (_: Exception) { failureOrRetry("network_or_tls_failure") }
    }

    /** 手动连接检测不无限等待，自动心跳继续使用 WorkManager 重试。 */
    private fun failureOrRetry(code: String): Result =
        if (ManualSyncPolicy.failureDisposition(inputData.getBoolean(ManualSyncPolicy.INPUT_IS_MANUAL, false)) ==
            ManualSyncPolicy.FailureDisposition.FINISH
        ) {
            Result.failure(workDataOf(ManualSyncPolicy.OUTPUT_ERROR_CODE to code))
        } else {
            Result.retry()
        }
}

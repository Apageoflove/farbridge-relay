// 将前台中继最近的探测、上传和错误状态保存为本地可读的诊断记录。
package com.zj.phonemirror.service

import android.content.Context

/** 仅保存时间戳和固定错误码，不保存短信、号码、密钥或服务器响应正文。 */
class RelayDiagnosticsStore(context: Context) {
    private val preferences = context.getSharedPreferences("phone_mirror_relay_diagnostics", Context.MODE_PRIVATE)

    /** 记录 Provider 探测成功完成的时间。 */
    fun recordProbe(now: Long = System.currentTimeMillis()) {
        preferences.edit().putLong("last_probe_at", now).remove("last_probe_error").apply()
    }

    /** 记录服务器明确完成本轮 Outbox 处理的时间。 */
    fun recordUpload(now: Long = System.currentTimeMillis()) {
        preferences.edit().putLong("last_upload_at", now).remove("last_upload_error").apply()
    }

    /** 记录受控错误码，供界面或现场排障读取。 */
    fun recordError(code: String) {
        preferences.edit().putString("last_probe_error", code.take(80)).apply()
    }

    /** 记录上传失败错误码且不包含异常正文。 */
    fun recordUploadError(code: String) {
        preferences.edit().putString("last_upload_error", code.take(80)).apply()
    }
}

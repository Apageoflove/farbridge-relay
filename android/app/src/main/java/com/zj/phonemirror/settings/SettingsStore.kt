// 保存非敏感设备配置和可恢复 Snapshot 标识，secret 由 Keystore 单独管理。
package com.zj.phonemirror.settings

import android.content.Context
import java.util.UUID

/** 为 Worker 提供进程重启后仍可恢复的最小配置。 */
class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("phone_mirror_settings", Context.MODE_PRIVATE)

    /** 当前服务器 HTTPS 根地址。 */
    var serverUrl: String?
        get() = preferences.getString("server_url", null)
        set(value) { preferences.edit().putString("server_url", value).apply() }

    /** 与服务端 secret 映射一致的设备标识。 */
    var deviceId: String
        get() = preferences.getString("device_id", null) ?: "android-device-01"
        set(value) { preferences.edit().putString("device_id", value).apply() }

    /** 用户是否明确请求 Default SMS 模式。 */
    var defaultSmsEnabled: Boolean
        get() = preferences.getBoolean("default_sms_enabled", false)
        set(value) { preferences.edit().putBoolean("default_sms_enabled", value).apply() }

    /** 获取本轮可恢复的 snapshotId；成功前不会改变。 */
    fun pendingSnapshotId(): String {
        preferences.getString("snapshot_id", null)?.let { return it }
        val value = UUID.randomUUID().toString()
        check(preferences.edit().putString("snapshot_id", value).commit()) { "snapshot_state_write_failed" }
        return value
    }

    /** 服务端明确成功后清除 snapshotId，下轮才生成新值。 */
    fun completeSnapshot() { preferences.edit().remove("snapshot_id").apply() }
}

// 将 Android Provider 数值类型映射为跨端稳定枚举。
package com.zj.phonemirror.provider

import android.provider.CallLog

/** 隔离 Android 常量，未知新类型安全回退而不是崩溃。 */
object ProviderMappings {
    /** 映射 CallLog 类型为协议枚举。 */
    fun callType(type: Int): String = when (type) {
        CallLog.Calls.INCOMING_TYPE -> "INCOMING"
        CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
        CallLog.Calls.MISSED_TYPE -> "MISSED"
        CallLog.Calls.REJECTED_TYPE -> "REJECTED"
        CallLog.Calls.BLOCKED_TYPE -> "BLOCKED"
        CallLog.Calls.VOICEMAIL_TYPE -> "VOICEMAIL"
        else -> "UNKNOWN"
    }
}

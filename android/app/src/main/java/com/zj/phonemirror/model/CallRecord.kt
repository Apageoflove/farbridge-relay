// 定义从 Android CallLog Provider 完整读取的领域记录。
package com.zj.phonemirror.model

/** CallLog Provider 记录；号码仅允许在受控存储和加密传输中使用。 */
data class CallRecord(
    val sourceId: String,
    val number: String,
    val cachedName: String?,
    val callType: String,
    val date: Long,
    val duration: Long,
    val phoneAccountId: String?,
    val phoneAccountComponentName: String?,
    val fingerprint: String,
)

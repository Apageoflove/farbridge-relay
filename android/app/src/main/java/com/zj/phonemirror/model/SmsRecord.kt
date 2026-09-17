// 定义从 Android SMS Provider 完整读取的领域记录。
package com.zj.phonemirror.model

/** SMS Provider 记录；正文仅进入加密传输/受控数据库，不进入日志。 */
data class SmsRecord(
    val sourceId: String,
    val address: String,
    val body: String,
    val type: Int,
    val date: Long,
    val dateSent: Long,
    val subscriptionId: Int?,
    val verificationCode: String?,
    val fingerprint: String,
    val threadId: String? = null,
)

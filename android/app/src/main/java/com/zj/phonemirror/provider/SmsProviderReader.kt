// 完整读取 Telephony SMS Provider，任何中途错误都丢弃部分结果。
package com.zj.phonemirror.provider

import android.content.ContentResolver
import android.provider.Telephony
import com.zj.phonemirror.model.ProviderSnapshot
import com.zj.phonemirror.model.SmsRecord
import com.zj.phonemirror.parser.VerificationCodeParser
import com.zj.phonemirror.util.FingerprintUtils

/** 将 Cursor 生命周期与异常收敛封装为完整快照门禁。 */
class SmsProviderReader(private val resolver: ContentResolver) {
    /** 查询全部 SMS；null Cursor、缺列、权限异常或遍历异常均返回 Failure。 */
    fun readAll(): ProviderSnapshot<SmsRecord> = try {
        val projection = arrayOf(
            Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.SUBSCRIPTION_ID,
            Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.TYPE,
            Telephony.Sms.DATE, Telephony.Sms.DATE_SENT,
        )
        val cursor = resolver.query(Telephony.Sms.CONTENT_URI, projection, null, null, "${Telephony.Sms.DATE} ASC")
            ?: return ProviderSnapshot.Failure("sms_null_cursor")
        cursor.use {
            val id = it.getColumnIndexOrThrow(Telephony.Sms._ID)
            val thread = it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val subscription = it.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)
            val address = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val type = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val date = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val sent = it.getColumnIndexOrThrow(Telephony.Sms.DATE_SENT)
            val records = ArrayList<SmsRecord>(it.count.coerceAtLeast(0))
            while (it.moveToNext()) {
                val sourceId = it.getLong(id).toString()
                val valueAddress = it.getString(address).orEmpty()
                val valueBody = it.getString(body).orEmpty()
                val valueType = it.getInt(type)
                val valueDate = it.getLong(date)
                val valueSent = it.getLong(sent)
                val subId = if (subscription >= 0 && !it.isNull(subscription)) it.getInt(subscription) else null
                val threadId = if (!it.isNull(thread)) it.getLong(thread).toString() else null
                records += SmsRecord(
                    sourceId, valueAddress, valueBody, valueType, valueDate, valueSent, subId,
                    VerificationCodeParser.parse(valueBody),
                    FingerprintUtils.of(sourceId, valueAddress, valueBody, valueType, valueDate, subId),
                    threadId,
                )
            }
            ProviderSnapshot.Complete(records)
        }
    } catch (_: SecurityException) {
        ProviderSnapshot.Failure("sms_permission_denied")
    } catch (_: RuntimeException) {
        ProviderSnapshot.Failure("sms_query_failed")
    }
}

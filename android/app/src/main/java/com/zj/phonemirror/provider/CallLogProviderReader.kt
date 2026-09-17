// 完整读取 CallLog Provider，任何中途错误都丢弃部分结果。
package com.zj.phonemirror.provider

import android.content.ContentResolver
import android.provider.CallLog.Calls
import com.zj.phonemirror.model.CallRecord
import com.zj.phonemirror.model.ProviderSnapshot
import com.zj.phonemirror.util.FingerprintUtils

/** 将 Cursor 生命周期与异常收敛封装为完整快照门禁。 */
class CallLogProviderReader(private val resolver: ContentResolver) {
    /** 查询全部 CallLog；失败只返回错误码，不返回已读取的前缀。 */
    fun readAll(): ProviderSnapshot<CallRecord> = try {
        val projection = arrayOf(Calls._ID, Calls.NUMBER, Calls.CACHED_NAME, Calls.TYPE, Calls.DATE, Calls.DURATION, Calls.PHONE_ACCOUNT_ID, Calls.PHONE_ACCOUNT_COMPONENT_NAME)
        val cursor = resolver.query(Calls.CONTENT_URI, projection, null, null, "${Calls.DATE} ASC")
            ?: return ProviderSnapshot.Failure("call_null_cursor")
        cursor.use {
            val id = it.getColumnIndexOrThrow(Calls._ID)
            val number = it.getColumnIndexOrThrow(Calls.NUMBER)
            val name = it.getColumnIndexOrThrow(Calls.CACHED_NAME)
            val type = it.getColumnIndexOrThrow(Calls.TYPE)
            val date = it.getColumnIndexOrThrow(Calls.DATE)
            val duration = it.getColumnIndexOrThrow(Calls.DURATION)
            val account = it.getColumnIndexOrThrow(Calls.PHONE_ACCOUNT_ID)
            val component = it.getColumnIndexOrThrow(Calls.PHONE_ACCOUNT_COMPONENT_NAME)
            val records = ArrayList<CallRecord>(it.count.coerceAtLeast(0))
            while (it.moveToNext()) {
                val sourceId = it.getLong(id).toString()
                val valueNumber = it.getString(number).orEmpty()
                val valueType = ProviderMappings.callType(it.getInt(type))
                val valueDate = it.getLong(date)
                val valueDuration = it.getLong(duration)
                val accountId = it.getString(account)
                val componentName = it.getString(component)
                records += CallRecord(
                    sourceId, valueNumber, it.getString(name), valueType, valueDate, valueDuration,
                    accountId, componentName,
                    FingerprintUtils.of(sourceId, valueNumber, valueType, valueDate, valueDuration, accountId),
                )
            }
            ProviderSnapshot.Complete(records)
        }
    } catch (_: SecurityException) {
        ProviderSnapshot.Failure("call_permission_denied")
    } catch (_: RuntimeException) {
        ProviderSnapshot.Failure("call_query_failed")
    }
}

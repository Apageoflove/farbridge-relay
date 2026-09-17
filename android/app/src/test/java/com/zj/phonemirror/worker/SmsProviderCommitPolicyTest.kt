// 验证短信广播后必须安排一次去重的 Provider 落盘后补读。
package com.zj.phonemirror.worker

import androidx.work.ExistingWorkPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 防止短信广播早于系统 Provider 写入时永久漏掉该短信。 */
class SmsProviderCommitPolicyTest {
    /** 尾随读取必须延迟且用 REPLACE 合并广播风暴。 */
    @Test
    fun trailingReadWaitsForProviderCommitAndDeduplicates() {
        assertTrue(SmsProviderCommitPolicy.delayMillis > 0L)
        assertEquals(ExistingWorkPolicy.REPLACE, SmsProviderCommitPolicy.existingWorkPolicy)
    }
}

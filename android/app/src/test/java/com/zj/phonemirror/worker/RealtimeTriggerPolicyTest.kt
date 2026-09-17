// 验证短信与通话 Provider 的实时触发不会在已有任务运行时被静默丢弃。
package com.zj.phonemirror.worker

import androidx.work.ExistingWorkPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 锁定两类实时 Provider 都必须保留一次尾随收敛任务。 */
class RealtimeTriggerPolicyTest {
    /** SMS 与 CallLog 都必须使用 APPEND_OR_REPLACE，禁止回退到 KEEP。 */
    @Test
    fun providerChangesAlwaysAppendTrailingReconcile() {
        RealtimeTriggerPolicy.Provider.values().forEach { provider ->
            val policy = RealtimeTriggerPolicy.existingWorkPolicy(provider)
            assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, policy)
            assertNotEquals(ExistingWorkPolicy.KEEP, policy)
        }
    }
}

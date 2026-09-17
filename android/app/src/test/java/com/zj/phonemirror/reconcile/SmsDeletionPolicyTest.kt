// 验证 SMS Provider 瞬态缺失不会立刻生成远端删除事件。
package com.zj.phonemirror.reconcile

import org.junit.Assert.assertFalse
import org.junit.Test

/** 锁定验证码优先的数据保留门禁。 */
class SmsDeletionPolicyTest {
    /** Provider 快照缺失不具备删除证明；删除只允许走用户明确操作路径。 */
    @Test
    fun providerAbsenceNeverInfersSmsDeletion() {
        val now = 2_000_000L
        assertFalse(SmsDeletionPolicy.shouldDelete(lastSeenAt = now - 1_000L, now = now))
        assertFalse(SmsDeletionPolicy.shouldDelete(lastSeenAt = 0L, now = now))
    }
}

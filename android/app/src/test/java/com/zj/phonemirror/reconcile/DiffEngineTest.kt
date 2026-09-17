// 验证完整快照差分、实体版本与不完整查询的删除安全边界。
package com.zj.phonemirror.reconcile

import com.zj.phonemirror.model.ProviderSnapshot
import com.zj.phonemirror.model.SourceRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffEngineTest {
    private val old = listOf(SourceRecord("1", "aaa", 3), SourceRecord("2", "bbb", 1))

    @Test fun `完整快照生成新增修改和删除`() {
        val snapshot = ProviderSnapshot.Complete(listOf(SourceRecord("1", "changed", 0), SourceRecord("3", "ccc", 0)))
        val diff = DiffEngine.calculate(old, snapshot)
        assertEquals(listOf("3"), diff.upserts.filter { it.sourceId == "3" }.map { it.sourceId })
        assertEquals(listOf("1"), diff.upserts.filter { it.sourceId == "1" }.map { it.sourceId })
        assertEquals(listOf("2"), diff.deletes)
        assertEquals(4, diff.upserts.first { it.sourceId == "1" }.nextVersion)
    }

    @Test fun `查询失败绝不产生任何变更`() {
        val diff = DiffEngine.calculate(old, ProviderSnapshot.Failure("permission_denied"))
        assertTrue(diff.skipped)
        assertTrue(diff.upserts.isEmpty())
        assertTrue(diff.deletes.isEmpty())
    }

    @Test fun `重复 source id 使快照整体失败`() {
        val snapshot = ProviderSnapshot.Complete(listOf(SourceRecord("1", "a", 0), SourceRecord("1", "b", 0)))
        val diff = DiffEngine.calculate(old, snapshot)
        assertTrue(diff.skipped)
        assertEquals("duplicate_source_id", diff.reason)
    }
}

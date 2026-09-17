// 验证前台 Provider 发现变化后直接上传且失败时保留兜底重试。
package com.zj.phonemirror.service

import com.zj.phonemirror.repository.ReconcileOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** 锁定一轮双 Provider 变化只能触发一次直传的可靠性契约。 */
class ForegroundDirectSyncTest {
    @Test fun `短信和通话同时变化也只直传一次`() = runTest {
        var directUploads = 0
        var fallbacks = 0
        val probe = ForegroundRelayProbe(
            reconcileSms = { ReconcileOutcome.Applied(1) },
            reconcileCalls = { ReconcileOutcome.Applied(1) },
            directSync = { directUploads++; DirectSyncOutcome.Completed },
            enqueueFallback = { fallbacks++ },
        )

        probe.probe()

        assertEquals(1, directUploads)
        assertEquals(0, fallbacks)
    }

    @Test fun `直传失败只安排兜底且不执行删除回调`() = runTest {
        var directUploads = 0
        var fallbacks = 0
        val probe = ForegroundRelayProbe(
            reconcileSms = { ReconcileOutcome.Applied(1) },
            reconcileCalls = { ReconcileOutcome.Applied(0) },
            directSync = { directUploads++; DirectSyncOutcome.Retry },
            enqueueFallback = { fallbacks++ },
        )

        probe.probe()

        assertEquals(1, directUploads)
        assertEquals(1, fallbacks)
    }
}

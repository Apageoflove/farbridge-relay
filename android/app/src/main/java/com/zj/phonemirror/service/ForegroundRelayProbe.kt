// 将短信和通话的前台直读收敛为单个串行探测单元。
package com.zj.phonemirror.service

import com.zj.phonemirror.repository.ReconcileOutcome
import kotlinx.coroutines.CancellationException

/**
 * 每轮完整读取两类 Provider，并在任一来源实际变化时仅安排一次上传。
 *
 * 部分 OEM 可能延迟或抑制 ContentObserver；此单元由前台服务主动调用，
 * 因而不把实时性建立在系统广播是否送达的前提上。
 */
class ForegroundRelayProbe(
    private val reconcileSms: suspend () -> ReconcileOutcome,
    private val reconcileCalls: suspend () -> ReconcileOutcome,
    private val directSync: suspend () -> DirectSyncOutcome,
    private val enqueueFallback: () -> Unit,
    private val onReconcileError: (String) -> Unit = {},
) {
    /** 串行完成两种 Provider 收敛，并在同一轮内只进行一次网络直传。 */
    suspend fun probe(): Boolean {
        // 单个 Provider 的瞬时异常不能阻断另一类事件；取消仍需向上传播以便服务正常退出。
        val smsOutcome = safeReconcile("sms", reconcileSms)
        val callOutcome = safeReconcile("call", reconcileCalls)
        if (!smsOutcome.hasChanges() && !callOutcome.hasChanges()) return false
        if (directSync() == DirectSyncOutcome.Retry) enqueueFallback()
        return true
    }

    /** 仅已完整应用且有变更的快照才允许触发网络上传。 */
    private fun ReconcileOutcome.hasChanges(): Boolean = this is ReconcileOutcome.Applied && changed > 0

    /** 将 Provider 异常降级为可诊断的跳过结果，并继续完成另一类 Provider 读取。 */
    private suspend fun safeReconcile(
        source: String,
        reconcile: suspend () -> ReconcileOutcome,
    ): ReconcileOutcome = try {
        reconcile()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        onReconcileError("${source}_${error.javaClass.simpleName.ifBlank { "provider_failure" }}")
        ReconcileOutcome.Skipped("${source}_provider_failure")
    }
}

/** 前台直传只暴露是否需要 WorkManager 兜底，避免泄露短信正文或网络异常细节。 */
enum class DirectSyncOutcome { Completed, Retry }

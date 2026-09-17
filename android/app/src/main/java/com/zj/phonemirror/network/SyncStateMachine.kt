// 定义 Outbox 批次在 partial ACK、超时和重试时的确定性状态转换。
package com.zj.phonemirror.network

/** 把 ACK 与仍需重试的事件明确分开。 */
data class BatchTransition(val acked: Set<String>, val retry: Set<String>)

/** 纯函数状态机防止网络模糊结果误删事件。 */
object SyncStateMachine {
    private const val BASE_MS = 15_000L
    private const val MAX_MS = 3_600_000L
    private const val SENDING_LEASE_MS = 600_000L

    /** 仅确认请求批次内且被服务器明确列出的事件。 */
    fun afterResponse(sent: List<String>, acknowledged: Set<String>): BatchTransition {
        val sentSet = sent.toSet()
        val acked = acknowledged.intersect(sentSet)
        return BatchTransition(acked, sentSet - acked)
    }

    /** 超时等模糊失败必须保留整批，由服务器幂等处理重放。 */
    fun afterAmbiguousFailure(sent: List<String>): BatchTransition = BatchTransition(emptySet(), sent.toSet())

    /** 指数退避最高一小时，避免位移溢出并限制耗电。 */
    fun backoffMillis(retryCount: Int): Long {
        val safePower = retryCount.coerceIn(0, 20)
        val raw = BASE_MS * (1L shl safePower)
        return raw.coerceAtMost(MAX_MS)
    }

    /** 将进程崩溃遗留的 SENDING 事件在十分钟租约后重新开放。 */
    fun abandonedLeaseBefore(now: Long): Long = now - SENDING_LEASE_MS
}

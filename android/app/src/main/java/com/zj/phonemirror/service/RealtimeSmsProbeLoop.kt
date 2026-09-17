// 串行运行前台短信探测，避免 WorkManager 配额耗尽和探测任务重叠。
package com.zj.phonemirror.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 在调用方作用域内立即探测，并在每轮完成后等待固定间隔。 */
class RealtimeSmsProbeLoop(
    private val scope: CoroutineScope,
    private val intervalMillis: Long,
    private val onError: (String) -> Unit = {},
    private val probe: suspend () -> Unit,
) {
    private var job: Job? = null

    /** 幂等启动单一循环；慢查询会自然推迟下一轮而不会并发堆积。 */
    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                try {
                    probe()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // 只记录稳定异常类型，不写正文，下一轮继续自愈。
                    onError(error.javaClass.simpleName.ifBlank { "probe_failure" })
                }
                delay(intervalMillis)
            }
        }
    }

    /** 取消当前循环；Service 销毁后不得继续持有其 Context。 */
    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }
}

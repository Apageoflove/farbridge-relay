// 管理可靠常驻模式的 CPU 唤醒锁生命周期，避免锁屏后探测循环被冻结。
package com.zj.phonemirror.service

/** 用可注入动作保证唤醒锁只获取和释放一次，便于纯 JVM 验证。 */
class RelayWakeLockController(
    private val acquireAction: () -> Unit,
    private val releaseAction: () -> Unit,
) {
    private var acquired = false

    /** 仅当配置完整时持有唤醒锁，重复启动不会重复 acquire。 */
    @Synchronized
    fun start(configured: Boolean) {
        if (!configured || acquired) return
        acquireAction()
        acquired = true
    }

    /** 服务销毁时幂等释放，防止泄漏系统资源。 */
    @Synchronized
    fun stop() {
        if (!acquired) return
        releaseAction()
        acquired = false
    }
}

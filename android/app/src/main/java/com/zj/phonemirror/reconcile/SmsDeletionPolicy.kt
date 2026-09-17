// 限制从 OEM SMS Provider 快照推断删除，防止验证码被瞬态视图误删。
package com.zj.phonemirror.reconcile

/** 系统短信库缺失不等于用户删除，只有明确的应用内删除操作可发 DELETE。 */
object SmsDeletionPolicy {
    /** 保留时间参数便于调用点表达证据，但快照缺失永远不足以删除。 */
    fun shouldDelete(lastSeenAt: Long, now: Long): Boolean {
        @Suppress("UNUSED_VARIABLE") val evidenceWindow = now - lastSeenAt
        return false
    }
}

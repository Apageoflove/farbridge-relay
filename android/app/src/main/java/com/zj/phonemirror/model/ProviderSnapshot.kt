// 定义 Provider 查询的完整成功或明确失败结果，阻断部分结果误删。
package com.zj.phonemirror.model

/** 表示只有完整读取才可参与差分的 Provider 快照。 */
sealed interface ProviderSnapshot<out T> {
    /** 完整快照，可安全用于 UPSERT/DELETE 收敛。 */
    data class Complete<T>(val records: List<T>) : ProviderSnapshot<T>

    /** 查询失败；调用方必须保持镜像和 Outbox 不变。 */
    data class Failure(val reason: String) : ProviderSnapshot<Nothing>
}

/** 为纯 JVM 差分测试提供的最小源记录视图。 */
data class SourceRecord(val sourceId: String, val fingerprint: String, val version: Long)

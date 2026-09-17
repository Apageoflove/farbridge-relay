// 对 multipart SMS 做完整性校验后按序合并。
package com.zj.phonemirror.default_sms

/** 表示短信分段序号、总数和正文。 */
data class Part(val index: Int, val total: Int, val body: String)

/** 缺段、重复段或总数不一致时拒绝产生部分短信。 */
object MultipartAssembler {
    /** 只有 0..total-1 每段恰好存在一次时才返回完整正文。 */
    fun assemble(parts: List<Part>): String? {
        val total = parts.firstOrNull()?.total ?: return null
        if (total <= 0 || parts.any { it.total != total } || parts.size != total) return null
        val ordered = parts.sortedBy { it.index }
        if (ordered.map { it.index } != (0 until total).toList()) return null
        return ordered.joinToString("") { it.body }
    }
}

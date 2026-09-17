// 定义手动同步的结束策略和脱敏展示文案，避免界面假成功或暴露底层异常。
package com.zj.phonemirror.worker

/** 将后台 Worker 状态收敛为用户能理解的单一同步结果。 */
object ManualSyncPolicy {
    const val INPUT_IS_MANUAL = "is_manual_sync"
    const val OUTPUT_ERROR_CODE = "manual_error_code"
    const val EXPECTED_STAGE_COUNT = 4

    /** 后台失败可重试，用户主动任务必须结束并显示结果。 */
    enum class FailureDisposition { RETRY, FINISH }

    /** 与 WorkManager 状态一一对应的纯 Kotlin 状态。 */
    enum class StageState { ENQUEUED, RUNNING, BLOCKED, SUCCEEDED, FAILED, CANCELLED }

    /** 单个阶段只携带受控错误码，不接收异常正文或请求地址。 */
    data class Stage(val state: StageState, val errorCode: String? = null)

    /** 按触发来源决定失败后是否继续自动重试。 */
    fun failureDisposition(isManual: Boolean): FailureDisposition =
        if (isManual) FailureDisposition.FINISH else FailureDisposition.RETRY

    /** 汇总本轮全部阶段；只有四阶段全部成功才报告完成。 */
    fun statusMessage(stages: List<Stage>): String {
        val failed = stages.firstOrNull { it.state == StageState.FAILED }
        if (failed != null) return failureMessage(failed.errorCode)
        if (stages.any { it.state == StageState.CANCELLED }) return "同步已取消，请重新尝试"
        if (stages.size >= EXPECTED_STAGE_COUNT && stages.all { it.state == StageState.SUCCEEDED }) {
            return "服务器连接成功，同步已完成"
        }
        return "正在同步，请保持网络畅通…"
    }

    /** 将内部白名单错误码映射为不含隐私的中文排查提示。 */
    private fun failureMessage(code: String?): String = when {
        code == "http_401" || code == "http_403" -> "服务器拒绝认证，请检查设备 ID 和设备密钥"
        code?.startsWith("http_4") == true -> "服务器拒绝请求，请检查手机时间和服务器配置"
        code?.startsWith("http_5") == true -> "服务器暂时不可用，请稍后重试"
        code == "network_or_tls_failure" -> "网络或证书连接失败，请检查网络和服务器地址"
        code == "provider_read_failed" -> "无法读取短信或通话记录，请检查系统权限"
        code == "config_missing" -> "服务器配置不完整，请重新填写并保存"
        else -> "同步失败，请检查权限、网络和服务器状态"
    }
}

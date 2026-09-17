// 校验服务器根地址，生产环境禁止明文和内嵌凭据。
package com.zj.phonemirror.settings

import java.net.URI

/** 将 URL 安全策略集中在网络客户端创建之前执行。 */
object ServerUrlValidator {
    /** 生产仅 HTTPS；调试仅额外允许本机 loopback HTTP。 */
    fun isAllowed(value: String, debug: Boolean): Boolean = runCatching {
        val uri = URI(value)
        val noCredentials = uri.userInfo == null
        val rootOnly = uri.path.isNullOrEmpty() || uri.path == "/"
        val noQuery = uri.query == null && uri.fragment == null
        val host = uri.host ?: return false
        val secure = uri.scheme.equals("https", ignoreCase = true)
        val debugLoopback = debug && uri.scheme.equals("http", ignoreCase = true) && (host == "127.0.0.1" || host == "localhost" || host == "::1")
        noCredentials && rootOnly && noQuery && (secure || debugLoopback)
    }.getOrDefault(false)
}

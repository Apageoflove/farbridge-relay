// 定义短信中继前台服务的合规启动边界。
package com.zj.phonemirror.service

/** Android 后台启动限制要求首次启动必须来自用户可见界面。 */
object RelayServiceStartPolicy {
    /** 只有用户正在使用应用且服务器配置完整时才允许启动。 */
    fun shouldStart(userVisible: Boolean, configured: Boolean): Boolean = userVisible && configured
}

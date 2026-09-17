// 验证主界面的生产默认地址与系统栏安全区计算，防止首个输入框再次被顶部遮挡。
package com.zj.phonemirror.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 主界面布局策略的纯 Kotlin 回归测试。 */
class UiLayoutPolicyTest {
    /** 新安装后应直接给出已部署的 HTTPS 服务地址，避免用户手输长网址。 */
    @Test
    fun defaultServerUrlPointsToProductionHttpsEndpoint() {
        assertEquals(
            "https://your-phone-mirror.example:8443",
            UiLayoutPolicy.DEFAULT_SERVER_URL,
        )
        assertTrue(UiLayoutPolicy.DEFAULT_SERVER_URL.startsWith("https://"))
    }

    /** 内容顶部间距必须叠加状态栏高度，不能只保留固定设计间距。 */
    @Test
    fun contentTopPaddingAddsStatusBarInset() {
        assertEquals(40, UiLayoutPolicy.contentTopPadding(baseTopPx = 16, statusBarTopPx = 24))
        assertEquals(16, UiLayoutPolicy.contentTopPadding(baseTopPx = 16, statusBarTopPx = 0))
    }

    /** 底部间距同样必须叠加导航栏高度，滚动到底后按钮仍可完整点击。 */
    @Test
    fun contentBottomPaddingAddsNavigationBarInset() {
        assertEquals(44, UiLayoutPolicy.contentBottomPadding(baseBottomPx = 20, navigationBarBottomPx = 24))
        assertEquals(20, UiLayoutPolicy.contentBottomPadding(baseBottomPx = 20, navigationBarBottomPx = 0))
    }
}

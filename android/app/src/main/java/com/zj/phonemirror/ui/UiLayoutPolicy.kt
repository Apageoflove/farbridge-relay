// 集中管理主界面生产默认值和系统栏安全区计算，避免不同机型再次出现遮挡。
package com.zj.phonemirror.ui

/** 与 Android View 无关的布局策略，便于用纯单元测试锁定关键行为。 */
object UiLayoutPolicy {
    const val DEFAULT_SERVER_URL = "https://your-phone-mirror.example:8443"

    /** 在设计间距上叠加非负状态栏高度，确保首个控件始终位于系统栏下方。 */
    fun contentTopPadding(baseTopPx: Int, statusBarTopPx: Int): Int =
        baseTopPx + statusBarTopPx.coerceAtLeast(0)

    /** 在设计间距上叠加非负导航栏高度，确保末尾操作始终可滚动到完整可见。 */
    fun contentBottomPadding(baseBottomPx: Int, navigationBarBottomPx: Int): Int =
        baseBottomPx + navigationBarBottomPx.coerceAtLeast(0)
}

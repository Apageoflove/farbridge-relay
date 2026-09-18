// 提供一步式服务器配置、权限引导和同步状态；网络动作仍统一交给 WorkManager。
package com.zj.phonemirror.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.zj.phonemirror.BuildConfig
import com.zj.phonemirror.PhoneMirrorApplication
import com.zj.phonemirror.R
import com.zj.phonemirror.data.db.AppDatabase
import com.zj.phonemirror.default_sms.DefaultSmsRoleManager
import com.zj.phonemirror.default_sms.SmsMode
import com.zj.phonemirror.permission.PermissionChecker
import com.zj.phonemirror.security.SecretStore
import com.zj.phonemirror.service.RelayForegroundService
import com.zj.phonemirror.settings.ServerUrlValidator
import com.zj.phonemirror.settings.SettingsStore
import com.zj.phonemirror.worker.WorkerScheduler
import com.zj.phonemirror.worker.ManualSyncPolicy
import kotlinx.coroutines.launch

/** 用三个步骤完成首次配置，并持续展示可操作的运行状态。 */
class MainActivity : AppCompatActivity() {
    private val settings by lazy { SettingsStore(this) }
    private val secrets by lazy { SecretStore(this) }
    private val roleManager by lazy { DefaultSmsRoleManager(this) }

    private lateinit var urlInput: AppCompatEditText
    private lateinit var idInput: AppCompatEditText
    private lateinit var secretInput: AppCompatEditText
    private lateinit var secretToggleButton: AppCompatImageButton
    private lateinit var defaultSmsSwitch: SwitchCompat
    private lateinit var permissionStatusText: TextView
    private lateinit var statusText: TextView
    private lateinit var syncFeedbackText: TextView
    private var manualSyncLiveData: LiveData<List<WorkInfo>>? = null
    private val manualSyncObserver = Observer<List<WorkInfo>> { renderManualSyncStatus(it.orEmpty()) }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            refreshStatus()
            toast(if (grants.isNotEmpty() && grants.values.all { it }) "权限已全部授予" else "仍有权限未授予，请按页面提示检查")
        }

    /** 构建无系统 ActionBar 的边到边滚动界面，并由 Insets 显式避让系统栏。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSystemBars()

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setBackgroundColor(color(R.color.pm_background))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }

        root.addView(buildHeader())
        root.addView(buildServerCard(), cardLayoutParams())
        root.addView(buildPermissionCard(), cardLayoutParams())
        root.addView(buildSyncCard(), cardLayoutParams(bottom = 0))
        scroll.addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        applySafeArea(scroll)
        setContentView(scroll)

        // 首次进入不主动弹出键盘，保证用户先看见完整的第一步。
        root.requestFocus()
        refreshStatus()
    }

    /** 从系统设置返回应用时立即刷新权限和默认短信角色。 */
    override fun onResume() {
        super.onResume()
        (application as? PhoneMirrorApplication)?.ensureProviderObservers()
        RelayForegroundService.startFromVisibleApp(this)
        if (::statusText.isInitialized) refreshStatus()
    }

    /** 使用透明系统栏并根据深浅色模式保持状态栏图标可读。 */
    private fun configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val darkMode =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkMode
            isAppearanceLightNavigationBars = !darkMode
        }
    }

    /** 将状态栏和导航栏尺寸叠加到设计间距，适配各厂商定制系统。 */
    private fun applySafeArea(scroll: ScrollView) {
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                0,
                UiLayoutPolicy.contentTopPadding(dp(12), bars.top),
                0,
                UiLayoutPolicy.contentBottomPadding(dp(24), bars.bottom),
            )
            insets
        }
        ViewCompat.requestApplyInsets(scroll)
    }

    /** 构建应用自己的标题区，彻底移除会遮挡内容的系统 ActionBar。 */
    private fun buildHeader() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(12), dp(4), dp(18))
        addView(text(getString(R.string.app_name), 28f, R.color.pm_text_primary, bold = true))
        addView(text("让家里的手机，稳定把短信与通话同步到你手上", 14f, R.color.pm_text_secondary).apply {
            setPadding(0, dp(4), 0, 0)
        })
    }

    /** 第一步只保留三个必要字段，并预填可直接使用的生产服务器地址。 */
    private fun buildServerCard() = card().apply {
        addView(sectionHeader("1", "连接服务器", "服务器地址和设备 ID 已替你填好"))
        urlInput = addField(
            this,
            label = "服务器地址",
            value = settings.serverUrl ?: UiLayoutPolicy.DEFAULT_SERVER_URL,
            hint = UiLayoutPolicy.DEFAULT_SERVER_URL,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
        ).apply {
            imeOptions = EditorInfo.IME_ACTION_NEXT
            setSelectAllOnFocus(true)
        }
        idInput = addField(
            this,
            label = "设备 ID",
            value = settings.deviceId.ifBlank { "android-device-01" },
            hint = "android-device-01",
            inputType = InputType.TYPE_CLASS_TEXT,
        ).apply { imeOptions = EditorInfo.IME_ACTION_NEXT }
        secretInput = addSecretField(
            this,
            label = "设备密钥",
            value = SecretPresentationPolicy.decodeAndClear(secrets.get()),
            hint = "粘贴 32 字节以上的设备密钥",
        ).apply { imeOptions = EditorInfo.IME_ACTION_DONE }
        addView(text("密钥只保存在这台手机；留空保存表示沿用原密钥。", 12f, R.color.pm_text_secondary).apply {
            setPadding(0, dp(6), 0, 0)
        })
        addView(primaryButton("保存配置并启动") { saveConfig() }, topMarginParams(dp(18)))
    }

    /** 第二步把受限设置入口直接写在页面内，减少来回猜测。 */
    private fun buildPermissionCard() = card().apply {
        addView(sectionHeader("2", "允许系统权限", "读取短信和通话记录后才能完成同步"))
        permissionStatusText = statusPanel()
        addView(permissionStatusText, topMarginParams(dp(16)))
        addView(primaryButton("授予短信与通话权限") { requestPermissions() }, topMarginParams(dp(14)))
        addView(text("若弹窗提示“已限制”：打开应用信息 → 右上角 ⋮ → 允许受限设置，再回到这里重试。", 12f, R.color.pm_warning).apply {
            setPadding(0, dp(10), 0, 0)
        })
    }

    /** 第三步集中展示同步状态和手动动作，高级短信角色不阻塞主流程。 */
    private fun buildSyncCard() = card().apply {
        addView(sectionHeader("3", "同步与状态", "配置完成后后台会自动运行"))
        statusText = statusPanel()
        addView(statusText, topMarginParams(dp(16)))
        addView(primaryButton("立即同步一次") { syncNow() }, topMarginParams(dp(14)))
        addView(text("本次手动同步", 13f, R.color.pm_text_primary, bold = true), topMarginParams(dp(16)))
        syncFeedbackText = statusPanel().apply { text = "尚未执行手动同步" }
        addView(syncFeedbackText, topMarginParams(dp(6)))
        addView(text("高级模式（通常不需要）", 13f, R.color.pm_text_primary, bold = true).apply {
            setPadding(0, dp(20), 0, 0)
        })
        defaultSmsSwitch = SwitchCompat(this@MainActivity).apply {
            text = "默认短信模式（仅在验证码延迟时开启）"
            setTextColor(color(R.color.pm_text_primary))
            textSize = 14f
            minHeight = dp(48)
            isChecked = settings.defaultSmsEnabled
            setOnCheckedChangeListener { _, checked ->
                settings.defaultSmsEnabled = checked
                if (checked && !roleManager.requestRole(this@MainActivity)) {
                    roleManager.openDefaultAppSettings(this@MainActivity)
                }
                refreshStatus()
            }
        }
        addView(defaultSmsSwitch, topMarginParams(dp(4)))
    }

    /** 校验配置、加密保存可选密钥，并启动既有后台同步链路。 */
    private fun saveConfig() {
        val url = urlInput.text?.toString()?.trim().orEmpty()
        val deviceId = idInput.text?.toString()?.trim().orEmpty()
        val secret = secretInput.text?.toString()?.trim().orEmpty()
        if (url.isEmpty() || deviceId.isEmpty()) {
            toast("请填写服务器地址和设备 ID")
            return
        }
        if (!ServerUrlValidator.isAllowed(url, BuildConfig.ALLOW_CLEARTEXT_SERVER)) {
            toast("服务器地址无效：请使用 HTTPS 根地址")
            return
        }
        if (secret.isNotEmpty()) {
            if (SecretPresentationPolicy.encodedSize(secret) < 32) {
                toast("设备密钥至少 32 字节")
                return
            }
            SecretPresentationPolicy.useEncoded(secret) { secrets.put(it) }
        }
        if (secret.isEmpty() && !secrets.hasSecret()) {
            toast("首次配置必须粘贴设备密钥")
            return
        }
        settings.serverUrl = url
        settings.deviceId = deviceId
        val displayedSecret = if (secret.isNotEmpty()) secret else SecretPresentationPolicy.decodeAndClear(secrets.get())
        secretInput.setText(displayedSecret)
        hideSecretInput()
        WorkerScheduler.schedulePeriodic(this)
        WorkerScheduler.enqueueBootstrap(this)
        RelayForegroundService.startFromVisibleApp(this)
        toast("配置已保存，后台同步已启动")
        refreshStatus()
    }

    /** 请求同步所需的最小短信和通话权限集合。 */
    private fun requestPermissions() {
        toast("请在系统弹窗中选择允许")
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_PHONE_STATE,
            ),
        )
    }

    /** 在配置完整时强制重启独立手动链，并观察真实 WorkInfo 结果。 */
    private fun syncNow() {
        if (settings.serverUrl != null && secrets.hasSecret()) {
            WorkerScheduler.schedulePeriodic(this)
            syncFeedbackText.text = "正在同步，请保持网络畅通…"
            observeManualSync(WorkerScheduler.enqueueManualSync(this))
            toast("已开始读取并连接服务器")
        } else {
            toast("请先完成第 1 步服务器配置")
        }
    }

    /** 仅观察当前按钮触发的唯一标签，重复点击时移除旧观察器。 */
    private fun observeManualSync(runTag: String) {
        manualSyncLiveData?.removeObserver(manualSyncObserver)
        manualSyncLiveData = WorkManager.getInstance(this).getWorkInfosByTagLiveData(runTag).also { liveData ->
            liveData.observe(this, manualSyncObserver)
        }
    }

    /** 把 WorkManager 状态转换为纯策略输入，并在全部阶段成功后刷新积压量。 */
    private fun renderManualSyncStatus(workInfos: List<WorkInfo>) {
        if (!::syncFeedbackText.isInitialized) return
        val stages = workInfos.map { info ->
            ManualSyncPolicy.Stage(
                state = when (info.state) {
                    WorkInfo.State.ENQUEUED -> ManualSyncPolicy.StageState.ENQUEUED
                    WorkInfo.State.RUNNING -> ManualSyncPolicy.StageState.RUNNING
                    WorkInfo.State.BLOCKED -> ManualSyncPolicy.StageState.BLOCKED
                    WorkInfo.State.SUCCEEDED -> ManualSyncPolicy.StageState.SUCCEEDED
                    WorkInfo.State.FAILED -> ManualSyncPolicy.StageState.FAILED
                    WorkInfo.State.CANCELLED -> ManualSyncPolicy.StageState.CANCELLED
                },
                errorCode = info.outputData.getString(ManualSyncPolicy.OUTPUT_ERROR_CODE),
            )
        }
        val message = ManualSyncPolicy.statusMessage(stages)
        syncFeedbackText.text = message
        syncFeedbackText.setTextColor(
            color(
                when {
                    message == "服务器连接成功，同步已完成" -> R.color.pm_success
                    message.startsWith("正在同步") -> R.color.pm_text_primary
                    else -> R.color.pm_warning
                },
            ),
        )
        if (message == "服务器连接成功，同步已完成") refreshStatus()
    }

    /** 查询真实权限、队列和短信角色，避免只显示笼统的成功或失败。 */
    @SuppressLint("SetTextI18n")
    private fun refreshStatus() {
        val permissions = PermissionChecker(this).checkAll()
        val missing = permissions.count { it.required && !it.granted }
        if (::permissionStatusText.isInitialized) {
            permissionStatusText.text =
                if (missing == 0) "✓ 必要权限已全部授予" else "还缺 $missing 项必要权限"
            permissionStatusText.setTextColor(color(if (missing == 0) R.color.pm_success else R.color.pm_warning))
        }
        lifecycleScope.launch {
            val pending = AppDatabase.get(applicationContext).outboxDao().countPending()
            val configured = settings.serverUrl != null && secrets.hasSecret()
            val modeText = when (roleManager.mode()) {
                SmsMode.MIRROR -> "普通镜像"
                SmsMode.DEFAULT_SMS -> "默认短信（验证码最快）"
                SmsMode.DEGRADED -> "已降级（请重新授权短信角色）"
            }
            if (::statusText.isInitialized) {
                statusText.text = buildString {
                    append(if (configured) "✓ 服务器已配置" else "服务器尚未配置").append('\n')
                    append(if (missing == 0) "✓ 必要权限完整" else "权限还缺 $missing 项").append('\n')
                    append("工作模式：").append(modeText).append('\n')
                    append("等待上传：").append(pending).append(" 条")
                }
                statusText.setTextColor(color(if (configured && missing == 0) R.color.pm_success else R.color.pm_text_primary))
            }
        }
    }

    /** 创建统一卡片容器，依靠原生 drawable 实现圆角和边界。 */
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_card)
        elevation = dp(2).toFloat()
    }

    /** 创建带步骤编号、标题和说明的卡片头部。 */
    private fun sectionHeader(step: String, title: String, subtitle: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(text(step, 16f, R.color.pm_primary, bold = true).apply {
            gravity = Gravity.CENTER
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_step_badge)
        }, LinearLayout.LayoutParams(dp(36), dp(36)))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(text(title, 19f, R.color.pm_text_primary, bold = true))
            addView(text(subtitle, 12f, R.color.pm_text_secondary).apply { setPadding(0, dp(2), 0, 0) })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    /** 添加始终可见的字段标签和至少 52dp 高的输入框。 */
    private fun addField(
        parent: LinearLayout,
        label: String,
        value: String,
        hint: String,
        inputType: Int,
    ): AppCompatEditText {
        parent.addView(text(label, 13f, R.color.pm_text_primary, bold = true), topMarginParams(dp(16)))
        return AppCompatEditText(this).also { input ->
            input.hint = hint
            input.setText(value)
            input.setTextColor(color(R.color.pm_text_primary))
            input.setHintTextColor(color(R.color.pm_text_secondary))
            input.background = ContextCompat.getDrawable(this, R.drawable.bg_field)
            input.minHeight = dp(52)
            input.setSingleLine(true)
            input.ellipsize = TextUtils.TruncateAt.END
            input.setPadding(dp(14), 0, dp(14), 0)
            input.textSize = 15f
            input.setInputType(inputType)
            parent.addView(input, topMarginParams(dp(6)))
        }
    }

    /** 创建设备密钥输入与独立 48dp 眼睛按钮，默认隐藏且切换不丢值或光标。 */
    private fun addSecretField(
        parent: LinearLayout,
        label: String,
        value: String,
        hint: String,
    ): AppCompatEditText {
        parent.addView(text(label, 13f, R.color.pm_text_primary, bold = true), topMarginParams(dp(16)))
        val container = FrameLayout(this)
        val input = AppCompatEditText(this).apply {
            this.hint = hint
            setText(value)
            setTextColor(color(R.color.pm_text_primary))
            setHintTextColor(color(R.color.pm_text_secondary))
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_field)
            minHeight = dp(52)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(14), 0, dp(54), 0)
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
        }
        val toggle = AppCompatImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_view)
            setColorFilter(color(R.color.pm_text_secondary))
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "显示设备密钥"
            setOnClickListener { toggleSecretVisibility(input, this) }
        }
        secretToggleButton = toggle
        container.addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        container.addView(
            toggle,
            FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END or Gravity.CENTER_VERTICAL),
        )
        parent.addView(container, topMarginParams(dp(6)))
        return input
    }

    /** 切换密文显示时恢复原选择区间，避免重新输入或光标跳回开头。 */
    private fun toggleSecretVisibility(input: AppCompatEditText, toggle: AppCompatImageButton) {
        val start = input.selectionStart
        val end = input.selectionEnd
        val show = input.transformationMethod != null
        input.transformationMethod = if (show) null else PasswordTransformationMethod.getInstance()
        toggle.contentDescription = if (show) "隐藏设备密钥" else "显示设备密钥"
        val length = input.text?.length ?: 0
        if (start in 0..length && end in 0..length) input.setSelection(start, end) else input.setSelection(length)
        input.requestFocus()
    }

    /** 保存后恢复密码圆点和眼睛说明，但保留当前密钥文本供再次核对。 */
    private fun hideSecretInput() {
        secretInput.transformationMethod = PasswordTransformationMethod.getInstance()
        secretToggleButton.contentDescription = "显示设备密钥"
        secretInput.setSelection(secretInput.text?.length ?: 0)
    }

    /** 创建高对比度主操作按钮，并保证至少 52dp 触控高度。 */
    private fun primaryButton(label: String, onClick: () -> Unit) = AppCompatButton(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        minHeight = dp(52)
        setTextColor(color(R.color.pm_on_primary))
        backgroundTintList = ColorStateList.valueOf(color(R.color.pm_primary))
        setOnClickListener { onClick() }
    }

    /** 创建可复用的状态面板，内容由 refreshStatus 写入。 */
    private fun statusPanel() = TextView(this).apply {
        textSize = 14f
        setTextColor(color(R.color.pm_text_primary))
        setLineSpacing(dp(4).toFloat(), 1f)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_field)
    }

    /** 创建统一文本样式，避免各区块出现随机字号和颜色。 */
    private fun text(value: String, sizeSp: Float, colorRes: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = sizeSp
        setTextColor(color(colorRes))
        if (bold) paint.isFakeBoldText = true
    }

    /** 创建卡片外边距，保持单列信息节奏。 */
    private fun cardLayoutParams(bottom: Int = dp(14)) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = bottom
        }

    /** 创建带顶部间距的子控件参数。 */
    private fun topMarginParams(top: Int) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = top
        }

    /** 将 dp 转为像素，保证各密度设备上的触控尺寸一致。 */
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    /** 从当前日夜主题中解析颜色。 */
    private fun color(resourceId: Int) = ContextCompat.getColor(this, resourceId)

    /** 显示短反馈，避免按钮点击后用户无法判断是否生效。 */
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

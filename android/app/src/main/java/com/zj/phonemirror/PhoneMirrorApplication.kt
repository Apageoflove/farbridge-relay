// 进程启动时恢复周期任务；未配置完成前保持静默。
package com.zj.phonemirror
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.Telephony
import com.zj.phonemirror.observer.CallLogContentObserver
import com.zj.phonemirror.observer.SmsContentObserver
import com.zj.phonemirror.security.SecretStore
import com.zj.phonemirror.settings.SettingsStore
import com.zj.phonemirror.worker.WorkerScheduler
/** 应用入口注册轻量 Provider 观察器，并恢复幂等后台调度。 */
class PhoneMirrorApplication : Application() {
    private var smsObserver: SmsContentObserver? = null
    private var callObserver: CallLogContentObserver? = null

    /** 进程存活时监听真实短信和通话变化；读取与网络仍由 WorkManager 执行。 */
    override fun onCreate() {
        super.onCreate()
        ensureProviderObservers()
        val settings = SettingsStore(this)
        val configured = settings.serverUrl != null && SecretStore(this).hasSecret()
        if (configured) {
            WorkerScheduler.schedulePeriodic(this)
            WorkerScheduler.enqueueBootstrap(this)
        }
    }

    /** 观察系统 Provider 根 URI，后续子路径变化也会触发完整收敛。 */
    fun ensureProviderObservers() {
        val handler = Handler(Looper.getMainLooper())
        if (smsObserver == null) {
            val observer = SmsContentObserver(handler, applicationContext)
            try {
                contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
                smsObserver = observer
            } catch (_: SecurityException) {
                // 首次启动尚未授权时保持界面可用，授权后 MainActivity.onResume 会重试注册。
            }
        }
        if (callObserver == null) {
            val observer = CallLogContentObserver(handler, applicationContext)
            try {
                contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
                callObserver = observer
            } catch (_: SecurityException) {
                // 某些定制系统在授权前拒绝注册；返回应用后会再次尝试。
            }
        }
    }
}

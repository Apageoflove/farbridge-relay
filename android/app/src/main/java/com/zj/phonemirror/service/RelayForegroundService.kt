// 用 remoteMessaging 前台服务维持一加设备上的 Provider 观察器和同步调度。
package com.zj.phonemirror.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.zj.phonemirror.PhoneMirrorApplication
import com.zj.phonemirror.R
import com.zj.phonemirror.data.db.AppDatabase
import com.zj.phonemirror.provider.CallLogProviderReader
import com.zj.phonemirror.provider.SmsProviderReader
import com.zj.phonemirror.repository.MirrorRepository
import com.zj.phonemirror.security.SecretStore
import com.zj.phonemirror.settings.SettingsStore
import com.zj.phonemirror.ui.MainActivity
import com.zj.phonemirror.worker.WorkerScheduler
import com.zj.phonemirror.worker.OutboxSyncResult
import com.zj.phonemirror.worker.OutboxSyncRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** 显示低打扰常驻通知，降低 ColorOS/一加冻结进程导致漏实时事件的概率。 */
class RelayForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val diagnostics by lazy { RelayDiagnosticsStore(applicationContext) }
    private val wakeLock by lazy {
        (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PhoneMirror:RelayForegroundService",
        ).apply { setReferenceCounted(false) }
    }
    private val wakeLockController by lazy {
        RelayWakeLockController(
            acquireAction = { if (!wakeLock.isHeld) wakeLock.acquire() },
            releaseAction = { if (wakeLock.isHeld) wakeLock.release() },
        )
    }
    private val providerProbeLoop by lazy {
        RealtimeSmsProbeLoop(
            scope = serviceScope,
            intervalMillis = RealtimePollingPolicy.smsProbeIntervalMillis,
            probe = { probeProviders() },
            onError = { diagnostics.recordError(it) },
        )
    }

    /** 服务创建后立即进入前台，满足 Android 的五秒启动约束。 */
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification())
    }

    /** 系统重建服务时恢复观察器、周期任务和一次即时收敛。 */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val configured = SettingsStore(this).serverUrl != null && SecretStore(this).hasSecret()
        if (!configured) {
            stopSelf()
            return START_NOT_STICKY
        }
        wakeLockController.start(configured = true)
        (application as? PhoneMirrorApplication)?.ensureProviderObservers()
        WorkerScheduler.schedulePeriodic(this)
        WorkerScheduler.enqueueSms(this)
        WorkerScheduler.enqueueCalls(this)
        WorkerScheduler.enqueueHeartbeat(this)
        providerProbeLoop.start()
        return START_STICKY
    }

    /** 不依赖 OEM Observer，直接读取两类 Provider 并在任一变化时上传一次。 */
    private suspend fun probeProviders() {
        val repository = MirrorRepository(AppDatabase.get(applicationContext))
        ForegroundRelayProbe(
            reconcileSms = { repository.reconcileSms(SmsProviderReader(contentResolver).readAll()) },
            reconcileCalls = { repository.reconcileCalls(CallLogProviderReader(contentResolver).readAll()) },
            directSync = {
                when (val outcome = OutboxSyncRunner(applicationContext).run(immediateFallback = true)) {
                    OutboxSyncResult.Completed -> {
                        diagnostics.recordUpload()
                        DirectSyncOutcome.Completed
                    }
                    is OutboxSyncResult.Retry -> {
                        diagnostics.recordUploadError(outcome.code)
                        DirectSyncOutcome.Retry
                    }
                    is OutboxSyncResult.Failed -> {
                        diagnostics.recordUploadError(outcome.code)
                        DirectSyncOutcome.Retry
                    }
                }
            },
            enqueueFallback = { WorkerScheduler.enqueueSync(this) },
            onReconcileError = { diagnostics.recordError(it) },
        ).probe()
        diagnostics.recordProbe()
    }

    /** 服务退出时移除回调，避免持有 Service 或残留重复探测。 */
    override fun onDestroy() {
        providerProbeLoop.stop()
        wakeLockController.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    /** 本服务不提供跨进程绑定接口。 */
    override fun onBind(intent: Intent?): IBinder? = null

    /** 建立无声音低打扰通知渠道，避免影响验证码提醒渠道。 */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            channel.description = "保持家中手机的短信与通话同步在线"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** 点击常驻通知返回配置与运行状态页面。 */
    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.notification_running_title))
        .setContentText("短信与通话会自动同步")
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    companion object {
        private const val CHANNEL_ID = "phone_mirror_relay"
        private const val NOTIFICATION_ID = 1042

        /** 由可见 Activity 调用；开机广播继续只恢复 WorkManager，避免非法后台启动。 */
        fun startFromVisibleApp(context: Context) {
            val configured = SettingsStore(context).serverUrl != null && SecretStore(context).hasSecret()
            if (!RelayServiceStartPolicy.shouldStart(userVisible = true, configured = configured)) return
            ContextCompat.startForegroundService(context, Intent(context, RelayForegroundService::class.java))
        }
    }
}

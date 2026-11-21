package ru.theairblow.turnable

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import ru.theairblow.turnable.lib.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Keeps the app process alive while local Turnable services run, and shows how many are running. */
class LocalServiceForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val host get() = AndroidAppGraph.get(this).localHost

    override fun onCreate() {
        super.onCreate()
        val graph = AndroidAppGraph.get(this)
        scope.launch {
            combine(host.running, graph.notifier.clientStatus) { ids, status -> ids to status }.collect { (ids, status) ->
                if (ids.isEmpty()) stopSelf()
                else getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(status))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALL) {
            host.stopAll()
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(AndroidAppGraph.get(this).notifier.clientStatus.value),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        return START_NOT_STICKY
    }

    private fun buildNotification(status: ClientStatus) = NotificationCompat.Builder(this, AndroidNotifier.STATUS_CHANNEL)
        .setSmallIcon(R.drawable.ic_turnable_notification)
        .setContentTitle(getString(R.string.notification_status_app_name))
        .setContentText(
            when {
                status.total == 0 -> getString(R.string.notification_status_running)
                else -> resources.getQuantityString(
                    R.plurals.notification_status_count, status.total, status.connected, status.total,
                )
            },
        )
        .setContentIntent(
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
            }
        )
        .addAction(
            0, getString(R.string.notification_action_disconnect_all),
            PendingIntent.getService(
                this, 1,
                Intent(this, LocalServiceForegroundService::class.java).setAction(ACTION_STOP_ALL),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP_ALL = "ru.theairblow.turnable.STOP_ALL_LOCAL_SERVICES"
    }
}

package ru.theairblow.turnable

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import ru.theairblow.turnable.lib.R
import ru.theairblow.turnable.platform.CaptchaRequest
import ru.theairblow.turnable.platform.CoreUpdate
import ru.theairblow.turnable.platform.PlatformNotifier

/** Intent extra carrying the instance whose captcha the user tapped a notification for. */
const val EXTRA_CAPTCHA_INSTANCE = "ru.theairblow.turnable.CAPTCHA_INSTANCE"

private const val CHANNEL_STATUS = "turnable_status"
private const val CHANNEL_CAPTCHA = "turnable_captcha"
private const val CHANNEL_UPDATE = "turnable_update"
private const val UPDATE_NOTIFICATION_ID = 1500
private const val CAPTCHA_NOTIFICATION_BASE = 2000
private const val CAPTCHA_TIMEOUT_MS = 10 * 60 * 1000L

/** Connected and total clients, shown in the persistent notification. */
data class ClientStatus(val connected: Int = 0, val total: Int = 0)

/** Posts the captcha prompts and feeds the persistent status notification. */
class AndroidNotifier(private val context: Context) : PlatformNotifier {
    val clientStatus = MutableStateFlow(ClientStatus())

    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS, context.getString(R.string.notification_channel_status_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notification_channel_status_desc) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CAPTCHA, context.getString(R.string.notification_channel_captcha_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.notification_channel_captcha_desc) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_UPDATE, context.getString(R.string.notification_channel_update_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notification_channel_update_desc) },
        )
    }

    override fun status(connected: Int, total: Int) {
        clientStatus.value = ClientStatus(connected, total)
    }

    override fun captchaRequired(request: CaptchaRequest) {
        if (!canNotify()) return
        val open = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_CAPTCHA_INSTANCE, request.instanceId)
        val tap = PendingIntent.getActivity(
            context, notificationId(request.instanceId), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_CAPTCHA)
            .setSmallIcon(R.drawable.ic_turnable_notification)
            .setContentTitle(context.getString(R.string.notification_captcha_title))
            .setContentText(context.getString(R.string.notification_captcha_text, request.name))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notification_captcha_big, request.name)),
            )
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(CAPTCHA_TIMEOUT_MS)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(request.instanceId), notification)
    }

    override fun captchaCleared(instanceId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(instanceId))
    }

    override fun updateAvailable(update: CoreUpdate, appOutdated: Boolean) {
        if (!canNotify()) return
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.let { PendingIntent.getActivity(context, UPDATE_NOTIFICATION_ID, it, PendingIntent.FLAG_IMMUTABLE) }
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATE)
            .setSmallIcon(R.drawable.ic_turnable_notification)
            .setContentTitle(context.getString(R.string.notification_update_title, update.version))
            .setContentText(
                if (appOutdated) context.getString(R.string.notification_update_app_outdated)
                else context.getString(R.string.notification_update_text, update.serviceName),
            )
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.notification_action_update), answer(UpdateActionReceiver.ACTION_UPDATE))
            .addAction(0, context.getString(R.string.notification_action_later), answer(UpdateActionReceiver.ACTION_LATER))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(context).notify(UPDATE_NOTIFICATION_ID, notification)
    }

    override fun updateCleared() {
        NotificationManagerCompat.from(context).cancel(UPDATE_NOTIFICATION_ID)
    }

    private fun answer(action: String): PendingIntent = PendingIntent.getBroadcast(
        context, action.hashCode(), Intent(context, UpdateActionReceiver::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notificationId(instanceId: String) = CAPTCHA_NOTIFICATION_BASE + (instanceId.hashCode() and 0xFFFF)

    private fun canNotify(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    companion object {
        const val STATUS_CHANNEL = CHANNEL_STATUS
    }
}

/** Handles the Update and Later buttons of the update notification. */
class UpdateActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val vm = AndroidAppGraph.get(context).viewModel
        when (intent.action) {
            ACTION_UPDATE -> vm.applyUpdate()
            ACTION_LATER -> vm.dismissUpdate()
        }
    }

    companion object {
        const val ACTION_UPDATE = "ru.theairblow.turnable.UPDATE_CORE"
        const val ACTION_LATER = "ru.theairblow.turnable.UPDATE_LATER"
    }
}

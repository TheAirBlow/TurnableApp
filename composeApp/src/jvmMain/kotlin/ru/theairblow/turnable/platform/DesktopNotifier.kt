package ru.theairblow.turnable.platform

import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import turnable.composeapp.generated.resources.*

private const val TITLE = "Turnable"

private fun text(resource: StringResource, vararg args: Any): String =
    runBlocking { getString(resource, *args) }

/**
 * Tray icon with balloon notifications. Where the desktop has no system tray (some Linux setups),
 * captcha prompts go through `notify-send` instead; the in-app banner is always there either way.
 */
class DesktopNotifier : PlatformNotifier {
    /** Called when the user clicks a captcha notification. */
    @Volatile var onCaptchaClick: (instanceId: String) -> Unit = {}

    @Volatile private var lastCaptcha: String? = null
    /** notify-send notification IDs by instance, so each prompt can be closed when it ends. */
    private val sentNotifications = ConcurrentHashMap<String, Int>()
    private val tray: TrayIcon? = createTray()

    private fun createTray(): TrayIcon? {
        if (!SystemTray.isSupported()) return null
        return runCatching {
            TrayIcon(icon(), TITLE).also { icon ->
                icon.isImageAutoSize = true
                icon.addActionListener { lastCaptcha?.let(onCaptchaClick) }
                SystemTray.getSystemTray().add(icon)
            }
        }.getOrNull()
    }

    override fun status(connected: Int, total: Int) {
        tray?.toolTip = if (total == 0) TITLE
        else "$TITLE: " + text(Res.string.clients_connected_of, connected, total)
    }

    override fun captchaRequired(request: CaptchaRequest) {
        lastCaptcha = request.instanceId
        val title = text(Res.string.desktop_captcha_title)
        val message = text(Res.string.desktop_captcha_message, request.name)
        if (tray != null) {
            tray.displayMessage(title, text(Res.string.desktop_captcha_balloon, message), TrayIcon.MessageType.WARNING)
        } else {
            closeSentNotification(request.instanceId)
            sendLinuxNotification(title, message)?.let { sentNotifications[request.instanceId] = it }
        }
    }

    override fun updateAvailable(update: CoreUpdate, appOutdated: Boolean) {
        val title = text(Res.string.desktop_update_title)
        val message = if (appOutdated) text(Res.string.desktop_update_app_outdated, update.version)
        else text(Res.string.desktop_update_message, update.version)
        if (tray != null) tray.displayMessage(title, message, TrayIcon.MessageType.INFO)
        else runCatching { ProcessBuilder("notify-send", "-a", TITLE, title, message).start() }
    }

    override fun captchaCleared(instanceId: String) {
        if (lastCaptcha == instanceId) lastCaptcha = null
        closeSentNotification(instanceId)
    }

    fun dispose() {
        sentNotifications.keys.toList().forEach(::closeSentNotification)
        tray?.let { SystemTray.getSystemTray().remove(it) }
    }

    /** Posts a notification through notify-send, returning its ID so it can be closed when the request ends. */
    private fun sendLinuxNotification(title: String, message: String): Int? = runCatching {
        val process = ProcessBuilder("notify-send", "-u", "critical", "-p", "-a", TITLE, title, message).start()
        process.outputStream.close()
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return@runCatching null
        }
        process.inputStream.bufferedReader().use { it.readLine() }?.trim()?.toIntOrNull()
    }.getOrNull()

    private fun closeSentNotification(instanceId: String) {
        val id = sentNotifications.remove(instanceId) ?: return
        runCatching {
            ProcessBuilder(
                "gdbus", "call", "--session",
                "--dest", "org.freedesktop.Notifications",
                "--object-path", "/org/freedesktop/Notifications",
                "--method", "org.freedesktop.Notifications.CloseNotification",
                id.toString(),
            ).start()
        }
    }

    private fun icon(): BufferedImage = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB).also { image ->
        image.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val unit = 64f / 108f
            color = Color(0x3F, 0x4F, 0xC4)
            fill(RoundRectangle2D.Float(0f, 0f, 64f, 64f, 14f, 14f))
            color = Color.WHITE
            stroke = BasicStroke(7f * unit, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            draw(Arc2D.Float(34f * unit, 34f * unit, 40f * unit, 40f * unit, 90f, -270f, Arc2D.OPEN))
            fill(Path2D.Float().apply { moveTo(27f * unit, 54f * unit); lineTo(41f * unit, 54f * unit); lineTo(34f * unit, 42f * unit); closePath() })
            fill(Ellipse2D.Float(48f * unit, 48f * unit, 12f * unit, 12f * unit))
            dispose()
        }
    }
}

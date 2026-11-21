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

private const val TITLE = "Turnable"

/**
 * Tray icon with balloon notifications. Where the desktop has no system tray (some Linux setups),
 * captcha prompts go through `notify-send` instead; the in-app banner is always there either way.
 */
class DesktopNotifier : PlatformNotifier {
    /** Called when the user clicks a captcha notification. */
    @Volatile var onCaptchaClick: (instanceId: String) -> Unit = {}

    @Volatile private var lastCaptcha: String? = null
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
        tray?.toolTip = if (total == 0) TITLE else "$TITLE: $connected of $total connected"
    }

    override fun captchaRequired(request: CaptchaRequest) {
        lastCaptcha = request.instanceId
        val message = "${request.name} can't connect until you solve a captcha"
        if (tray != null) tray.displayMessage("Captcha required", "$message. Click to solve it.", TrayIcon.MessageType.WARNING)
        else runCatching { ProcessBuilder("notify-send", "-u", "critical", "-a", TITLE, "Captcha required", message).start() }
    }

    override fun updateAvailable(update: CoreUpdate) {
        val message = "Turnable ${update.version} is available. Open the app to update."
        if (tray != null) tray.displayMessage("Update available", message, TrayIcon.MessageType.INFO)
        else runCatching { ProcessBuilder("notify-send", "-a", TITLE, "Update available", message).start() }
    }

    override fun captchaCleared(instanceId: String) {
        if (lastCaptcha == instanceId) lastCaptcha = null
    }

    fun dispose() {
        tray?.let { SystemTray.getSystemTray().remove(it) }
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

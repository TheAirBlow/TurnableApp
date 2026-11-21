package ru.theairblow.turnable

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ru.theairblow.turnable.data.AppPreferences
import ru.theairblow.turnable.platform.DesktopNotifier
import ru.theairblow.turnable.service.AppInstaller
import ru.theairblow.turnable.service.DesktopLocalServiceHost
import ru.theairblow.turnable.ui.App
import ru.theairblow.turnable.ui.AppIcon
import ru.theairblow.turnable.viewmodel.AppViewModel
import java.awt.Frame

fun main() {
    val localHost = DesktopLocalServiceHost()
    val notifier = DesktopNotifier()
    val vm = AppViewModel(AppPreferences(), localHost, AppInstaller.Unsupported, notifier)
    notifier.onCaptchaClick = { id ->
        vm.openCaptcha(id)
        // Bring the window forward so the solve screen is what the user lands on
        Frame.getFrames().firstOrNull { it.isDisplayable }?.let { frame ->
            frame.state = Frame.NORMAL
            frame.toFront()
        }
    }
    application {
        Window(
            onCloseRequest = {
                vm.disconnect()
                localHost.stopAll()
                notifier.dispose()
                exitApplication()
            },
            title = "Turnable",
            icon = AppIcon,
            state = rememberWindowState(width = 1100.dp, height = 760.dp),
        ) {
            App(vm)
        }
    }
}

package ru.theairblow.turnable.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.theairblow.turnable.platform.CaptchaRequest
import ru.theairblow.turnable.platform.captchaInApp
import ru.theairblow.turnable.ui.components.PaneTopBar
import turnable.composeapp.generated.resources.*

/**
 * Hosts the call page, fills in everything except the captcha and hands the tokens to the service.
 * Calls [onSolved] once the service has them.
 */
@Composable
expect fun CaptchaWebView(request: CaptchaRequest, onSolved: () -> Unit, modifier: Modifier)

/** Full-screen manual captcha flow for one client. */
@Composable
fun CaptchaScreen(request: CaptchaRequest, onSolved: () -> Unit, onClose: () -> Unit) {
    Scaffold(topBar = {
        PaneTopBar(stringResource(Res.string.captcha_solve_title), request.name, onBack = onClose)
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (captchaInApp) Text(
                stringResource(Res.string.captcha_wait_hint),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            CaptchaWebView(request, onSolved, Modifier.fillMaxSize())
        }
    }
}

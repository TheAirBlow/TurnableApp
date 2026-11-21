package ru.theairblow.turnable.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import ru.theairblow.turnable.platform.CaptchaRequest
import ru.theairblow.turnable.ui.components.rememberCopier

/** Desktop has no embeddable browser; the user runs the service's userscript, which finds the port in the call link. */
@Composable
actual fun CaptchaWebView(request: CaptchaRequest, onSolved: () -> Unit, modifier: Modifier) {
    val uriHandler = LocalUriHandler.current
    val copy = rememberCopier()
    val userscript = "http://localhost:${request.port}/captcha_manual.user.js"
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Solve it in your browser", style = MaterialTheme.typography.titleMedium)
        Text(
            "1. Install a userscript manager such as Tampermonkey, then install the Turnable userscript.\n" +
                "2. Open the call page in a private window and solve the captcha. The userscript joins the call and sends the tokens back, " +
                "and the link tells it which port to use.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { runCatching { uriHandler.openUri(userscript) } }) { Text("Open userscript") }
            Button(onClick = { runCatching { uriHandler.openUri(request.url) } }) { Text("Open call page") }
        }
        FilledTonalButton(onClick = { copy(request.url, "Call page link") }) { Text("Copy call page link") }
    }
}

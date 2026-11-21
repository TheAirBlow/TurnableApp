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
import org.jetbrains.compose.resources.stringResource
import ru.theairblow.turnable.platform.CaptchaRequest
import ru.theairblow.turnable.ui.components.rememberCopier
import turnable.composeapp.generated.resources.*

/** Desktop has no embeddable browser; the user runs the service's userscript, which finds the port in the call link. */
@Composable
actual fun CaptchaWebView(request: CaptchaRequest, onSolved: () -> Unit, modifier: Modifier) {
    val uriHandler = LocalUriHandler.current
    val copy = rememberCopier()
    val userscript = "http://localhost:${request.port}/captcha_manual.user.js"
    val callLinkLabel = stringResource(Res.string.captcha_call_link_label)
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.captcha_solve_in_browser), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.captcha_userscript_step1), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(Res.string.captcha_userscript_step2), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { runCatching { uriHandler.openUri(userscript) } }) {
                Text(stringResource(Res.string.captcha_open_userscript))
            }
            Button(onClick = { runCatching { uriHandler.openUri(request.url) } }) {
                Text(stringResource(Res.string.captcha_open_call_page))
            }
        }
        FilledTonalButton(onClick = { copy(request.url, callLinkLabel) }) {
            Text(stringResource(Res.string.captcha_copy_call_link))
        }
    }
}

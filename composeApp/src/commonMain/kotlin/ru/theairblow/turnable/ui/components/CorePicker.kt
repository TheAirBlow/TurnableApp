package ru.theairblow.turnable.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import turnable.composeapp.generated.resources.*

/** Returns a function that lets the user pick a Turnable binary; [onPicked] receives a path the app can execute. */
@Composable
expect fun rememberCorePicker(serviceId: String, onPicked: (path: String) -> Unit): () -> Unit

/** Buttons to choose or clear the custom core; place them inside a row of other buttons. */
@Composable
fun CustomCoreButtons(serviceId: String, path: String, onChange: (String) -> Unit) {
    val pick = rememberCorePicker(serviceId, onChange)
    OutlinedButton(onClick = pick) {
        Text(stringResource(if (path.isBlank()) Res.string.core_use_custom else Res.string.core_change_core))
    }
    if (path.isNotBlank()) TextButton(onClick = { onChange("") }) {
        Text(stringResource(Res.string.core_use_releases))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomCoreRow(serviceId: String, path: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CustomCoreButtons(serviceId, path, onChange)
    }
}

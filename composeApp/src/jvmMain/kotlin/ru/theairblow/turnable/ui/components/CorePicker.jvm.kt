package ru.theairblow.turnable.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame

@Composable
actual fun rememberCorePicker(serviceId: String, onPicked: (path: String) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    return {
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                val dialog = FileDialog(null as Frame?, "Choose a Turnable binary", FileDialog.LOAD).apply { isVisible = true }
                dialog.file?.let { dialog.directory + it }
            }
            path?.let(picked)
        }
    }
}

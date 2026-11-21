package ru.theairblow.turnable.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The picked file is copied into app storage, the only place Android lets the app execute from. */
@Composable
actual fun rememberCorePicker(serviceId: String, onPicked: (path: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val target = withContext(Dispatchers.IO) {
                File(context.filesDir, "cores/$serviceId/turnable").also { file ->
                    file.parentFile?.mkdirs()
                    context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                    file.setExecutable(true)
                }
            }
            picked(target.absolutePath)
        }
    }
    return { launcher.launch(arrayOf("*/*")) }
}

package ru.theairblow.turnable

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import ru.theairblow.turnable.data.AppPreferences
import ru.theairblow.turnable.service.JvmLocalServiceHost
import ru.theairblow.turnable.viewmodel.AppViewModel

/** Process-wide singletons, so activity recreation never orphans running service processes. */
object AndroidAppGraph {
    @Volatile private var initialized: Graph? = null

    /**
     * The view model lives as long as the process, not the activity: the foreground service keeps the app
     * alive in the background, and captcha prompts must keep arriving while no screen is open.
     */
    class Graph(val prefs: AppPreferences, val localHost: AndroidLocalServiceHost, val notifier: AndroidNotifier) {
        val viewModel: AppViewModel by lazy { AppViewModel(prefs, localHost, notifier) }
    }

    fun get(context: Context): Graph = initialized ?: synchronized(this) {
        initialized ?: context.applicationContext.let { app ->
            Graph(AppPreferences(app), AndroidLocalServiceHost(app), AndroidNotifier(app))
        }.also { initialized = it }
    }
}

class AndroidLocalServiceHost(private val context: Context) :
    JvmLocalServiceHost(context.filesDir, androidAssetName()) {

    override fun onRunningChanged(anyRunning: Boolean) {
        val intent = Intent(context, LocalServiceForegroundService::class.java)
        if (anyRunning) ContextCompat.startForegroundService(context, intent)
        else context.stopService(intent)
    }
}

private fun androidAssetName(): String? = when (Build.SUPPORTED_ABIS.firstOrNull()) {
    "arm64-v8a" -> "turnable-android-arm64"
    "armeabi-v7a" -> "turnable-android-arm"
    // No Android builds for x86; the static Linux builds run on x86 emulators
    "x86_64" -> "turnable-linux-amd64"
    "x86" -> "turnable-linux-386"
    else -> null
}

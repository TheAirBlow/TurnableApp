package ru.theairblow.turnable

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import ru.theairblow.turnable.ui.App

class MainActivity : ComponentActivity() {
    private val vm get() = AndroidAppGraph.get(this).viewModel

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        openCaptchaFrom(intent)
        setContent { App(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openCaptchaFrom(intent)
    }

    /** A tapped captcha notification opens that client's solve screen. */
    private fun openCaptchaFrom(intent: Intent?) {
        intent?.getStringExtra(EXTRA_CAPTCHA_INSTANCE)?.let(vm::openCaptcha)
    }
}

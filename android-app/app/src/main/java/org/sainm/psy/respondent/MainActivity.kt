package org.sainm.psy.respondent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import org.sainm.psy.respondent.ui.PsyRespondentApp
import org.sainm.psy.respondent.ui.AppText
import org.sainm.psy.respondent.data.local.SessionStorage

class MainActivity : ComponentActivity() {
    private var notificationTargetPath by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionStorage = SessionStorage(this)
        AppText.initialize(this, sessionStorage.readLocaleTag())
        notificationTargetPath = intent.getStringExtra(EXTRA_TARGET_PATH)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        setContent {
            PsyRespondentApp(notificationTargetPath) { notificationTargetPath = null }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationTargetPath = intent.getStringExtra(EXTRA_TARGET_PATH)
    }

    companion object {
        const val EXTRA_TARGET_PATH = "notification_target_path"
    }
}

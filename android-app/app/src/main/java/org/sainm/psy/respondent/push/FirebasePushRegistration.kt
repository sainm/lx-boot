package org.sainm.psy.respondent.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.sainm.psy.respondent.BuildConfig
import org.sainm.psy.respondent.MainActivity
import org.sainm.psy.respondent.R
import org.sainm.psy.respondent.data.RespondentRepository
import org.sainm.psy.respondent.data.local.SessionStorage
import org.sainm.psy.respondent.data.remote.ApiFactory

object FirebasePushRegistration {
    fun initialize(context: Context): Boolean {
        if (FirebaseApp.getApps(context).isNotEmpty()) return true
        if (listOf(
                BuildConfig.FIREBASE_APPLICATION_ID,
                BuildConfig.FIREBASE_API_KEY,
                BuildConfig.FIREBASE_PROJECT_ID,
                BuildConfig.FIREBASE_SENDER_ID
            ).any { it.isBlank() }
        ) return false
        val options = FirebaseOptions.Builder()
            .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
            .build()
        return FirebaseApp.initializeApp(context, options) != null
    }

    suspend fun registerCurrentToken(context: Context, repository: RespondentRepository) {
        if (!initialize(context)) return
        runCatching { FirebaseMessaging.getInstance().token.await() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { repository.registerPushToken(it) } }
    }
}

class PsyFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val storage = SessionStorage(applicationContext)
        if (storage.readTokens() == null) return
        val repository = RespondentRepository(ApiFactory(storage), storage)
        scope.launch { runCatching { repository.registerPushToken(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val storage = SessionStorage(applicationContext)
        val repository = RespondentRepository(ApiFactory(storage), storage)
        message.data["deliveryId"]?.toLongOrNull()?.let { deliveryId ->
            scope.launch { runCatching { repository.reportDeliveryReceived(deliveryId) } }
        }
        createNotificationChannel()
        val targetPath = message.data["targetPath"] ?: "/notifications"
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TARGET_PATH, targetPath)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this,
            targetPath.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = message.notification?.title ?: message.data["title"] ?: getString(R.string.notification_default_title)
        val body = message.notification?.body ?: message.data["body"] ?: return
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(message.messageId?.hashCode() ?: body.hashCode(), notification) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "psy_updates"
    }
}

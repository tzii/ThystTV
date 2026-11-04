package com.github.andreyasadchy.xtra.ui.main

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.liveNotificationStartedAt
import com.github.andreyasadchy.xtra.repository.NotificationUsersRepository
import com.github.andreyasadchy.xtra.repository.ShownNotificationsRepository
import com.github.andreyasadchy.xtra.ui.common.SpoilerShield
import com.github.andreyasadchy.xtra.ui.common.spoilerSubject
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@HiltWorker
class LiveNotificationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted parameters: WorkerParameters,
    private val shownNotifications: ShownNotificationsRepository,
    private val notificationUsersRepository: NotificationUsersRepository,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
) : CoroutineWorker(context, parameters) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = context.getString(R.string.notification_live_channel_id)

    override suspend fun doWork(): Result {
        currentCoroutineContext().ensureActive()
        if (!canPostNotifications()) return Result.success()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager.getNotificationChannel(channelId) == null) {
                notificationManager.createNotificationChannel(
                    NotificationChannel(channelId, context.getString(R.string.notification_live_channel_title), NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            var posted = false
            val retry = shownNotifications.notifyNewStreams(
                notificationUsersRepository = notificationUsersRepository,
                networkLibrary = context.prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                gqlHeaders = TwitchApiHelper.getGQLHeaders(context, true),
                graphQLRepository = graphQLRepository,
                helixHeaders = TwitchApiHelper.getHelixHeaders(context),
                helixRepository = helixRepository,
            ) { stream ->
                if (canPostNotifications()) {
                    post(stream)
                    posted = true
                    true
                } else false
            }
            if (posted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && canPostNotifications()) {
                val summary = NotificationCompat.Builder(context, channelId)
                    .setGroup(GROUP_KEY)
                    .setSmallIcon(R.drawable.notification_icon)
                    .setGroupSummary(true)
                    .setOnlyAlertOnce(true)
                    .build()
                notificationManager.notify(GROUP_KEY, 0, summary)
            }
            if (retry && canRetry()) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (canRetry()) Result.retry() else Result.success()
        }
    }

    // Periodic work backs off exponentially on every retry, up to five hours. A few quick
    // retries cover transient failures; a persistent one returns to the 15-minute period.
    private fun canRetry() = runAttemptCount < MAX_QUICK_RETRIES && canPostNotifications()

    private fun canPostNotifications(): Boolean {
        if (!context.prefs().getBoolean(C.LIVE_NOTIFICATIONS_ENABLED, false)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            notificationManager.getNotificationChannel(channelId)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun post(stream: Stream) {
        val notificationTag = "$GROUP_KEY:${stream.channelId}"
        val startedAt = liveNotificationStartedAt(stream.createdAt) ?: return
        val alreadyPosted = notificationManager.activeNotifications.any {
            it.tag == notificationTag && if (!stream.id.isNullOrBlank()) {
                it.notification.extras.getString(STREAM_ID_KEY) == stream.id
            } else it.notification.extras.getLong(STARTED_AT_KEY) == startedAt
        }
        val displayName = if (stream.channelLogin != null && !stream.channelLogin.equals(stream.channelName, true)) {
            when (context.prefs().getString(C.UI_NAME_DISPLAY, "0")) {
                "0" -> "${stream.channelName ?: stream.channelLogin}(${stream.channelLogin})"
                "1" -> stream.channelName ?: stream.channelLogin
                else -> stream.channelLogin
            }
        } else stream.channelName ?: stream.channelLogin ?: stream.channelId
        val notification = NotificationCompat.Builder(context, channelId)
            .setGroup(GROUP_KEY)
            .setContentTitle(context.getString(R.string.live_notification, displayName))
            .setContentText(stream.title.takeUnless { SpoilerShield.hidesTitle(context.prefs(), stream.spoilerSubject()) })
            .setSmallIcon(R.drawable.notification_icon)
            .setAutoCancel(true)
            .setOnlyAlertOnce(alreadyPosted)
            .addExtras(Bundle().apply { putLong(STARTED_AT_KEY, startedAt); putString(STREAM_ID_KEY, stream.id) })
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                        action = MainActivity.INTENT_LIVE_NOTIFICATION
                        // PendingIntent compares data, not extras. Channel identity must survive
                        // String.hashCode collisions as well as repeated broadcasts.
                        data = "thysttv://live-notification/${stream.channelId}".toUri()
                        putExtra(MainActivity.KEY_VIDEO, stream)
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .build()
        notificationManager.notify(notificationTag, 1, notification)
    }

    companion object {
        private const val STREAM_ID_KEY = "live_notification_stream_id"
        private const val STARTED_AT_KEY = "live_notification_started_at"
        private const val MAX_QUICK_RETRIES = 3
        const val GROUP_KEY = "com.github.andreyasadchy.xtra.LIVE_NOTIFICATIONS"
    }
}

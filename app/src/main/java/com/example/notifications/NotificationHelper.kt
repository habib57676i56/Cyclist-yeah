package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.local.AnnouncementEntity

object NotificationHelper {
    const val CHANNEL_ADHAN = "noor_adhan"
    const val CHANNEL_REMINDERS = "noor_reminders"
    const val CHANNEL_ANNOUNCEMENTS = "noor_announcements"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels = listOf(
            NotificationChannel(CHANNEL_ADHAN, "Prayer & Adhan Alerts", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_REMINDERS, "Khatmah & Fasting Reminders", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_ANNOUNCEMENTS, "Admin & Halaqat Announcements", NotificationManager.IMPORTANCE_DEFAULT)
        )
        channels.forEach { nm.createNotificationChannel(it) }
    }

    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notifyPrayerTime(context: Context, prayerName: String): Boolean {
        if (!canPostNotifications(context)) return false
        ensureChannels(context)
        val pi = mainPendingIntent(context, "com.aistudio.noor.SHORTCUT_PRAYER_TIMES")
        val notif = NotificationCompat.Builder(context, CHANNEL_ADHAN)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("NOOR • حان وقت صلاة $prayerName")
            .setContentText("Prayer time has entered ($prayerName)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(context).notify(8100 + prayerName.hashCode().and(0xFF), notif)
        return true
    }

    fun notifyAnnouncement(context: Context, item: AnnouncementEntity): Boolean {
        if (!canPostNotifications(context)) return false
        ensureChannels(context)
        val pi = mainPendingIntent(context, null)
        val notif = NotificationCompat.Builder(context, CHANNEL_ANNOUNCEMENTS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(item.title)
            .setContentText(item.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(context).notify(9000 + item.id.toInt(), notif)
        return true
    }

    fun notifyReminder(context: Context, title: String, body: String, notificationId: Int): Boolean {
        if (!canPostNotifications(context)) return false
        ensureChannels(context)
        val pi = mainPendingIntent(context, null)
        val notif = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notif)
        return true
    }

    private fun mainPendingIntent(context: Context, action: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            if (action != null) this.action = action
        }
        return PendingIntent.getActivity(
            context,
            action?.hashCode() ?: 1,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "NOOR Reminder"
        val body = intent.getStringExtra("body") ?: "Time for your daily Quran Khatmah or fasting check-in."
        val id = intent.getIntExtra("id", 8501)
        NotificationHelper.notifyReminder(context, title, body, id)
    }
}

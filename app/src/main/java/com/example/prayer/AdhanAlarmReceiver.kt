package com.example.prayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.NoorDatabase
import com.example.data.local.NoorPreferences
import com.example.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AdhanAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra("prayer_name") ?: "PRAYER"
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = NoorDatabase.getInstance(context)
                val prefs = NoorPreferences(context).settings.first()
                val voices = db.adminDao().observeAdhanVoices().first()
                val assignedVoice = voices.firstOrNull {
                    it.assignedPrayers.split(",").any { p -> p.trim().equals(prayerName, ignoreCase = true) }
                } ?: voices.firstOrNull { it.id == prefs.selectedAdhanVoiceId }
                ?: voices.firstOrNull { it.isDefault }

                NotificationHelper.notifyPrayerTime(context, prayerName)
                if (assignedVoice != null) {
                    AdhanAudioManager.playAdhanVoice(context, assignedVoice)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

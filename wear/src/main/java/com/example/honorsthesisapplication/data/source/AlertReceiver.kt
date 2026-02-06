package com.example.honorsthesisapplication.data.source

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ACKNOWLEDGE) return

        val alertType = intent.getStringExtra(EXTRA_ALERT_TYPE) ?: "unknown"
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, DEFAULT_NOTIFICATION_ID)

        Log.d("ThesisLog", "USER ACKNOWLEDGED: $alertType at ${System.currentTimeMillis()}")

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Cancel the specific alert that created this button
        notificationManager.cancel(notificationId)
    }

    companion object {
        const val ACTION_ACKNOWLEDGE = "ACTION_ACKNOWLEDGE"

        const val EXTRA_ALERT_TYPE = "alert_type"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        private const val DEFAULT_NOTIFICATION_ID = 202
    }
}
package com.example.honorsthesisapplication.data.source

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.app.NotificationManager

class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val alertType = intent.getStringExtra("alert_type") ?: "unknown"

        if (action == "ACTION_ACKNOWLEDGE") {
            // THIS IS WHERE YOU LOG IT
            Log.d("ThesisLog", "USER ACKNOWLEDGED: $alertType at ${System.currentTimeMillis()}")

            // Dismiss the notification immediately after clicking
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(202) // Use the unique ID we set for alerts
        }
    }
}
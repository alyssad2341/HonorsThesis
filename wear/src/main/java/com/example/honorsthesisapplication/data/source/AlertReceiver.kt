package com.example.honorsthesisapplication.data.source

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.honorsthesisapplication.MainActivity

class AlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ACKNOWLEDGE) return

        val alertType = intent.getStringExtra(EXTRA_ALERT_TYPE) ?: "unknown"
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, DEFAULT_NOTIFICATION_ID)

        // Optional "actual" payload (if the service included it)
        val alertId = intent.getStringExtra(EXTRA_ALERT_ID) ?: "ack_${System.currentTimeMillis()}"
        val actualKey = intent.getStringExtra(EXTRA_ACTUAL_KEY) ?: alertType
        val actualValue = intent.getDoubleExtra(EXTRA_ACTUAL_VALUE, Double.NaN)
        val actualMessage = intent.getStringExtra(EXTRA_ACTUAL_MESSAGE) ?: ""

        Log.d("ThesisLog", "USER ACKNOWLEDGED: $alertType at ${System.currentTimeMillis()}")

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)

        // Launch the app to the survey screen/flow
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_ALERT_SURVEY
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ALERT_ID, alertId)
            putExtra(EXTRA_ACTUAL_KEY, actualKey)
            putExtra(EXTRA_ACTUAL_VALUE, actualValue)
            putExtra(EXTRA_ACTUAL_MESSAGE, actualMessage)
        }

        ContextCompat.startActivity(context, openIntent, null)
    }

    companion object {
        // Existing
        const val ACTION_ACKNOWLEDGE = "ACTION_ACKNOWLEDGE"
        const val EXTRA_ALERT_TYPE = "alert_type"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        // New: matches what MainActivity expects
        const val ACTION_OPEN_ALERT_SURVEY = "ACTION_OPEN_ALERT_SURVEY"
        const val EXTRA_ALERT_ID = "extra_alert_id"
        const val EXTRA_ACTUAL_KEY = "extra_actual_key"
        const val EXTRA_ACTUAL_VALUE = "extra_actual_value"
        const val EXTRA_ACTUAL_MESSAGE = "extra_actual_msg"

        private const val DEFAULT_NOTIFICATION_ID = 202
    }
}

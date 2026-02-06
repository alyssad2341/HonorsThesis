package com.example.honorsthesisapplication.utils

object AlertNav {
    const val EXTRA_ALERT_ID = "extra_alert_id"
    const val EXTRA_ACTUAL_KEY = "extra_actual_key"      // ex: "high_calories"
    const val EXTRA_ACTUAL_VALUE = "extra_actual_value"  // ex: 120.0
    const val EXTRA_ACTUAL_MESSAGE = "extra_actual_msg"  // ex: "Calories last hour: 120.0"
    const val EXTRA_ALERT_TS = "extra_alert_ts"          // timestamp

    // Optional: so you can ignore random launches
    const val ACTION_OPEN_ALERT_SURVEY = "ACTION_OPEN_ALERT_SURVEY"
}


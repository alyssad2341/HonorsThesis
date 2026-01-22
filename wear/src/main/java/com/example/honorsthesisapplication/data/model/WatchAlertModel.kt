package com.example.honorsthesisapplication.data.model


enum class NotificationFrequency(val label: String, val millis: Long) {
    EVERY_30_SEC("Every 30 sec", 30_000),
    EVERY_1_MIN("Every 1 min", 60_000),
    EVERY_5_MIN("Every 5 min", 300_000),
    EVERY_15_MIN("Every 15 min", 900_000),
    EVERY_30_MIN("Every 30 min", 1_800_000);
}

data class WatchAlertModel(
    val subEventId: String,
    val enabled: Boolean,
    val threshold: Float?,
    val frequencyMillis: Long,
    val timings: LongArray,
    val amplitudes: IntArray
)
package com.example.honorsthesisapplication.data.model

data class WatchAlertModel(
    val subEventId: String,
    val enabled: Boolean,
    val threshold: Float?,
    val notificationFrequency: String,
    val timings: LongArray,
    val amplitudes: IntArray
)
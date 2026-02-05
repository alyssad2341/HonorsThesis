package com.example.honorsthesisapplication.data.model

import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class NotificationFrequency(val label: String, val millis: Long) {
    EVERY_30_SEC("Every 30 sec", 30_000L),
    EVERY_1_MIN("Every 1 min", 60_000L),
    EVERY_5_MIN("Every 5 min", 5 * 60_000L),
    EVERY_15_MIN("Every 15 min", 15 * 60_000L),
    EVERY_30_MIN("Every 30 min", 30 * 60_000L)
}

data class PhysEventModel(
    val id: String,
    val title: String,
    val description: String,
    val color: Color,
    var subEvents: List<PhysSubEventModel> = emptyList()
)

data class PhysSubEventModel(
    val id: String,
    val title: String,
    val description: String,
    var enabled: Boolean = false,
    var minThreshold: Float,
    var maxThreshold: Float,
    var setThreshold: Float,
    var notificationFrequency: NotificationFrequency,
    var selectedVibrationId: String? = "VIB000"
)
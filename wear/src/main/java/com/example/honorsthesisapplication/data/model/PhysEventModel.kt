package com.example.honorsthesisapplication.data.model

import androidx.compose.ui.graphics.Color

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
    var enabled: Boolean = false,
    var threshold: Float?,
    var notificationFrequency: String,
    var selectedVibration: VibrationModel = VibrationPatterns.default
)
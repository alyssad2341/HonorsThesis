package com.example.honorsthesisapplication.data.model

import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
    var selectedVibrationId: String? = null
)
package com.example.honorsthesisapplication.data.model

import androidx.compose.ui.graphics.Color

data class PhysEventModel(
    val id: String,
    val title: String,
    val description: String,
    val color: Color,
    var selectedVibration: VibrationModel? = null
)
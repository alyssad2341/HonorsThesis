package com.example.honorsthesisapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.honorsthesisapplication.data.model.PhysEventModel
import com.example.honorsthesisapplication.utils.AlertColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PhysEventViewModel : ViewModel() {
    private val _eventList = MutableStateFlow(
        listOf(
            PhysEventModel("heart_rate","Heart Rate", "Description", AlertColors.HeartRate),
            PhysEventModel("activity", "Activity", "Description", AlertColors.Activity),
            PhysEventModel("stress", "Stress", "Description", AlertColors.Stress),
            PhysEventModel("blood_oxygen", "Blood Oxygen", "Description", AlertColors.BloodOxygen),
            PhysEventModel("skin_temp", "Skin Temperature", "Description", AlertColors.SkinTemperature),
        )
    )
    val eventList = _eventList.asStateFlow()
    var selectedEvent: PhysEventModel? = null
}
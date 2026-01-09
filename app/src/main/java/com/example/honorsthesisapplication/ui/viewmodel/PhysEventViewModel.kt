package com.example.honorsthesisapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.honorsthesisapplication.data.model.PhysEventModel
import com.example.honorsthesisapplication.data.model.PhysSubEventModel
import com.example.honorsthesisapplication.utils.AlertColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PhysEventViewModel : ViewModel() {
    private val _heartRateSubList = MutableStateFlow(
        listOf(
            PhysSubEventModel("high_heart_rate","High Heart Rate", false,  150.0f, "Every 5 minutes"),
            PhysSubEventModel("low_heart_rate", "Low Heart Rate", false, 60.0f, "Every 5 minutes")
        )
    )

    val heartRateSubList = _heartRateSubList.asStateFlow()

    private val _eventList = MutableStateFlow(
        listOf(
            PhysEventModel("heart_rate","Heart Rate", "Description", AlertColors.HeartRate, heartRateSubList.value),
            PhysEventModel("activity", "Activity", "Description", AlertColors.Activity),
            PhysEventModel("stress", "Stress", "Description", AlertColors.Stress),
            PhysEventModel("blood_oxygen", "Blood Oxygen", "Description", AlertColors.BloodOxygen),
            PhysEventModel("skin_temp", "Skin Temperature", "Description", AlertColors.SkinTemperature),
        )
    )
    val eventList = _eventList.asStateFlow()
    var selectedEvent: PhysEventModel? = null
    var selectedSubEvent: PhysSubEventModel? = null

}
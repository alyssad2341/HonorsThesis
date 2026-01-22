package com.example.honorsthesisapplication.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.honorsthesisapplication.data.model.NotificationFrequency
import com.example.honorsthesisapplication.data.model.PhysEventModel
import com.example.honorsthesisapplication.data.model.PhysSubEventModel
import com.example.honorsthesisapplication.data.repository.PhysSettingsRepository
import com.example.honorsthesisapplication.utils.AlertColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PhysEventViewModel(context: Context) : ViewModel() {

    private val repository = PhysSettingsRepository(context)

    private val _heartRateSubList = MutableStateFlow(
        listOf(
            PhysSubEventModel("high_heart_rate","High Heart Rate", false,  150.0f, NotificationFrequency.EVERY_5_MIN),
            PhysSubEventModel("low_heart_rate", "Low Heart Rate", false, 60.0f, NotificationFrequency.EVERY_5_MIN)
        )
    )

    val heartRateSubList = _heartRateSubList.asStateFlow()

    init {
        preloadSavedSettings()
    }

    private fun preloadSavedSettings() {
        viewModelScope.launch {
            val updatedList = _heartRateSubList.value.map { subEvent ->
                repository.loadSubEventSettings(subEvent)
            }
            _heartRateSubList.value = updatedList

            _eventList.value = _eventList.value.map { event ->
                if (event.id == "heart_rate") {
                    event.copy(subEvents = updatedList)
                } else event
            }
        }
    }

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

    fun saveSubEvent(subEvent: PhysSubEventModel) {
        viewModelScope.launch {
            repository.saveSubEventSettings(subEvent)
        }
    }

    fun loadSubEvent(subEvent: PhysSubEventModel, onLoaded: (PhysSubEventModel) -> Unit) {
        viewModelScope.launch {
            val loaded = repository.loadSubEventSettings(subEvent)
            onLoaded(loaded)
        }
    }
}

class PhysEventViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PhysEventViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PhysEventViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
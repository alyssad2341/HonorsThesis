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
            PhysSubEventModel("high_heart_rate","High Heart Rate", "Alert when BPM rises above this value.", false,  90.0f, 160.0f, 120.0f, NotificationFrequency.EVERY_5_MIN),
            PhysSubEventModel("low_heart_rate", "Low Heart Rate", "Alert when BPM falls below this value.", false, 35.0f, 70.0f, 50.0f, NotificationFrequency.EVERY_5_MIN)
        )
    )

    private val _activitySubList = MutableStateFlow(
        listOf(
            PhysSubEventModel("high_activity","High Activity", "Alert when steps in the last hour exceed this value.", false, 700.0f, 4000.0f, 2000.0f, NotificationFrequency.EVERY_5_MIN),
            PhysSubEventModel("low_activity", "Low Activity", "Alert when steps in the last hour fall below this value.", false, 0.0f, 300.0f, 60.0f, NotificationFrequency.EVERY_5_MIN)
        )
    )

    private val _stressSubList = MutableStateFlow(
        listOf(
            PhysSubEventModel("high_stress","High Stress", "Alert when heart rate variability (standard deviation) falls below this value.", false,  1.0f, 4.5f, 3.5f, NotificationFrequency.EVERY_5_MIN),
            PhysSubEventModel("low_stress", "Low Stress", "Alert when heart rate variability (standard deviation) exceeds this value.", false, 5.9f, 10.0f, 7.5f, NotificationFrequency.EVERY_5_MIN)
        )
    )

    val heartRateSubList = _heartRateSubList.asStateFlow()
    val activitySubList = _activitySubList.asStateFlow()
    val stressSubList = _stressSubList.asStateFlow()

    init {
        preloadSavedSettings()
    }

    private fun preloadSavedSettings() {
        viewModelScope.launch {
            // Load saved heart rate subevent settings
            val loadedHeartRateSubs = _heartRateSubList.value.map { subEvent ->
                repository.loadSubEventSettings(subEvent)
            }
            _heartRateSubList.value = loadedHeartRateSubs

            // Load saved activity subevent settings
            val loadedActivitySubs = _activitySubList.value.map { subEvent ->
                repository.loadSubEventSettings(subEvent)
            }
            _activitySubList.value = loadedActivitySubs

            // Load saved stress subevent settings
            val loadedStressSubs = _stressSubList.value.map { subEvent ->
                repository.loadSubEventSettings(subEvent)
            }
            _stressSubList.value = loadedActivitySubs

            // Push both updated subevent lists into the event list
            _eventList.value = _eventList.value.map { event ->
                when (event.id) {
                    "heart_rate" -> event.copy(subEvents = loadedHeartRateSubs)
                    "activity" -> event.copy(subEvents = loadedActivitySubs)
                    "stress" -> event.copy(subEvents = loadedStressSubs)
                    else -> event
                }
            }
        }
    }

    private val _eventList = MutableStateFlow(
        listOf(
            PhysEventModel("heart_rate","Heart Rate", "Description", AlertColors.HeartRate, heartRateSubList.value),
            PhysEventModel("activity", "Activity", "Description", AlertColors.Activity, activitySubList.value),
            PhysEventModel("stress", "Stress", "Description", AlertColors.Stress),
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
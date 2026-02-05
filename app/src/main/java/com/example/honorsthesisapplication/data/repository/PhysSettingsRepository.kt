package com.example.honorsthesisapplication.data.repository

import android.content.Context
import com.example.honorsthesisapplication.data.model.PhysSubEventModel
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.edit
import com.example.honorsthesisapplication.data.model.PhysSettingsKeys
import androidx.datastore.preferences.core.Preferences
import com.example.honorsthesisapplication.data.model.NotificationFrequency
import com.example.honorsthesisapplication.data.model.VibrationPatterns
import com.example.honorsthesisapplication.data.model.dataStore
import com.example.honorsthesisapplication.ui.controller.SmartwatchController

class PhysSettingsRepository(private val context: Context) {

    suspend fun saveSubEventSettings(subEvent: PhysSubEventModel) {
        context.dataStore.edit { prefs ->
            prefs[PhysSettingsKeys.enabled(subEvent.id)] = subEvent.enabled
            subEvent.threshold?.let { prefs[PhysSettingsKeys.threshold(subEvent.id)] = it }
            prefs[PhysSettingsKeys.frequency(subEvent.id)] =
                subEvent.notificationFrequency.name
            subEvent.selectedVibrationId?.let {
                prefs[PhysSettingsKeys.vibration(subEvent.id)] = it
            }
        }
        val controller = SmartwatchController(context)
        controller.sendAlertInfoToWatch(subEvent)
    }

    suspend fun loadSubEventSettings(subEvent: PhysSubEventModel): PhysSubEventModel {
        val prefs = context.dataStore.data.first()
        return subEvent.copy(
            enabled = prefs[PhysSettingsKeys.enabled(subEvent.id)] ?: false,
            threshold = prefs[PhysSettingsKeys.threshold(subEvent.id)],
            notificationFrequency =
                NotificationFrequency.valueOf(
                    prefs[PhysSettingsKeys.frequency(subEvent.id)]
                        ?: NotificationFrequency.EVERY_5_MIN.name
                ),
            selectedVibrationId = prefs[PhysSettingsKeys.vibration(subEvent.id)] ?: "VIB000"
        )
    }
}

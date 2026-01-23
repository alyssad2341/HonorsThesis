package com.example.honorsthesisapplication.data.repository

import android.content.Context
import androidx.datastore.dataStore
import com.example.honorsthesisapplication.data.model.WatchAlertModel
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.edit
import com.example.honorsthesisapplication.data.model.NotificationFrequency
import com.example.honorsthesisapplication.data.model.WatchAlertKeys
import com.example.honorsthesisapplication.data.model.VibrationModel
import com.example.honorsthesisapplication.data.model.watchDataStore
import com.example.honorsthesisapplication.data.model.VibrationPatterns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


class WatchAlertRepository(private val context: Context) {

    suspend fun saveAlertToDataStore(
        id: String,
        enabled: Boolean,
        threshold: Double,
        frequency: Long,
        timings: String,
        amplitudes: String
    ) {
        context.watchDataStore.edit { prefs ->
            prefs[WatchAlertKeys.enabled(id)] = enabled
            if (threshold >= 0) {
                prefs[WatchAlertKeys.threshold(id)] = threshold.toFloat()
            }
            prefs[WatchAlertKeys.frequency(id)] = frequency
            prefs[WatchAlertKeys.timings(id)] = timings
            prefs[WatchAlertKeys.amplitudes(id)] = amplitudes
        }
    }

    suspend fun loadAlert(subEventId: String): WatchAlertModel? {
        val prefs = context.watchDataStore.data.first()

        val enabled = prefs[WatchAlertKeys.enabled(subEventId)] ?: return null

        val timings = prefs[WatchAlertKeys.timings(subEventId)]
            ?.split(",")
            ?.map { it.toLong() }
            ?.toLongArray()
            ?: longArrayOf()

        val amplitudes = prefs[WatchAlertKeys.amplitudes(subEventId)]
            ?.split(",")
            ?.map { it.toInt() }
            ?.toIntArray()
            ?: intArrayOf()

        return WatchAlertModel(
            subEventId = subEventId,
            enabled = enabled,
            threshold = prefs[WatchAlertKeys.threshold(subEventId)],
            frequencyMillis = prefs[WatchAlertKeys.frequency(subEventId)] ?: NotificationFrequency.EVERY_5_MIN.millis,
            timings = timings,
            amplitudes = amplitudes
        )
    }

    fun observeAlert(subEventId: String): Flow<WatchAlertModel?> {
        return context.watchDataStore.data.map { prefs ->
            val enabled = prefs[WatchAlertKeys.enabled(subEventId)] ?: return@map null

            val timings = prefs[WatchAlertKeys.timings(subEventId)]
                ?.split(",")
                ?.map { it.toLong() }
                ?.toLongArray()
                ?: longArrayOf()

            val amplitudes = prefs[WatchAlertKeys.amplitudes(subEventId)]
                ?.split(",")
                ?.map { it.toInt() }
                ?.toIntArray()
                ?: intArrayOf()

            WatchAlertModel(
                subEventId = subEventId,
                enabled = enabled,
                threshold = prefs[WatchAlertKeys.threshold(subEventId)],
                frequencyMillis = prefs[WatchAlertKeys.frequency(subEventId)]
                    ?: NotificationFrequency.EVERY_5_MIN.millis,
                timings = timings,
                amplitudes = amplitudes
            )
        }
    }
}
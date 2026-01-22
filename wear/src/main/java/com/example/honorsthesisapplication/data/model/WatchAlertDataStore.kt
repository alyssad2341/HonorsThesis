package com.example.honorsthesisapplication.data.model

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.watchDataStore by preferencesDataStore(name = "watch_alerts")

object WatchAlertKeys {
    fun enabled(id: String) = booleanPreferencesKey("${id}_enabled")
    fun threshold(id: String) = floatPreferencesKey("${id}_threshold")
    fun frequency(id: String) = longPreferencesKey("${id}_frequency")
    fun timings(id: String) = stringPreferencesKey("${id}_timings")
    fun amplitudes(id: String) = stringPreferencesKey("${id}_amplitudes")
}
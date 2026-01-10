package com.example.honorsthesisapplication.data.model

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore by preferencesDataStore(name = "phys_settings")
object PhysSettingsKeys {

    fun enabled(id: String) = booleanPreferencesKey("${id}_enabled")
    fun threshold(id: String) = floatPreferencesKey("${id}_threshold")
    fun frequency(id: String) = stringPreferencesKey("${id}_frequency")
    fun vibration(id: String) = stringPreferencesKey("${id}_vibration")
}
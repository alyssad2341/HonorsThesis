package com.example.honorsthesisapplication.data.source

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.honorsthesisapplication.data.model.WatchAlertModel
import com.example.honorsthesisapplication.data.repository.WatchAlertRepository
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PhoneDataSource : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d("Watch", "Message received: ${messageEvent.path}")
        if (messageEvent.path == "/vibrate_pattern") {
            val json = String(messageEvent.data, Charsets.UTF_8)

            try {
                val obj = JSONObject(json)
                val timings = obj.getJSONArray("timings")
                val amplitudes = obj.getJSONArray("amplitudes")
                val repeat = obj.getInt("repeat")

                val timingArray = LongArray(timings.length()) { i -> timings.getLong(i) }
                val amplitudeArray = IntArray(amplitudes.length()) { i -> amplitudes.getInt(i) }

                vibrateWatch(timingArray, amplitudeArray, repeat)
            } catch (e: Exception) {
                Log.e("PhoneDataSource", "Invalid vibration JSON: $json")
            }
        }
        else if (messageEvent.path == "/alert_info"){
            val json = String(messageEvent.data, Charsets.UTF_8)
            Log.d("PhoneDataSource", "Raw alert JSON received: $json")

            try {
                val obj = JSONObject(json)

                val id = obj.getString("subEventId")
                val enabled = obj.getBoolean("enabled")
                val threshold = obj.optDouble("threshold", -1.0)
                    .takeIf { it >= 0 }
                    ?.toFloat()

                val frequency = obj.getString("notificationFrequency")

                val vibrationObj = obj.getJSONObject("vibration")
                val timingsJson = vibrationObj.getJSONArray("timings")
                val amplitudesJson = vibrationObj.getJSONArray("amplitudes")

                val timings = LongArray(timingsJson.length()) {
                    timingsJson.getLong(it)
                }

                val amplitudes = IntArray(amplitudesJson.length()) {
                    amplitudesJson.getInt(it)
                }

                val alert = WatchAlertModel(
                    subEventId = id,
                    enabled = enabled,
                    threshold = threshold,
                    notificationFrequency = frequency,
                    timings = timings,
                    amplitudes = amplitudes
                )

                CoroutineScope(Dispatchers.IO).launch {
                    val repo = WatchAlertRepository(this@PhoneDataSource)

                    repo.saveAlertToDataStore(
                        id = alert.subEventId,
                        enabled = alert.enabled,
                        threshold = alert.threshold?.toDouble() ?: -1.0,
                        frequency = alert.notificationFrequency,
                        timings = alert.timings.joinToString(","),
                        amplitudes = alert.amplitudes.joinToString(",")
                    )
                }

                Log.d("PhoneDataSource", "Alert saved for ${alert.subEventId}")

            } catch (e: Exception) {
                Log.e("PhoneDataSource", "Invalid alert JSON", e)
            }
        }
    }

    private fun vibrateWatch(timings: LongArray, amplitudes: IntArray, repeat: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator

            val effect = VibrationEffect.createWaveform(timings, amplitudes, repeat)
            vibrator.vibrate(effect)
        }
    }

}
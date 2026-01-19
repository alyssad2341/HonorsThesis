package com.example.honorsthesisapplication.data.source

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

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
            try {
                Log.d("PhoneDataSource", "Received alert info: $json")
                val obj = JSONObject(json)
                val timings = obj.getJSONArray("timings")
                val amplitudes = obj.getJSONArray("amplitudes")
                val repeat = obj.getInt("repeat")

                val timingArray = LongArray(timings.length()) { i -> timings.getLong(i) }
                val amplitudeArray = IntArray(amplitudes.length()) { i -> amplitudes.getInt(i) }

                //vibrateWatch(timingArray, amplitudeArray, repeat)
            } catch (e: Exception) {
                Log.e("PhoneDataSource", "Invalid vibration JSON: $json")
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
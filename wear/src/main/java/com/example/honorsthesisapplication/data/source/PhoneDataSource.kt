package com.example.honorsthesisapplication.data.source

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class PhoneDataSource : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d("Watch", "Message received: ${messageEvent.path}")
        if (messageEvent.path == "/vibrate") {
            vibrateWatch()
        }
    }

    private fun vibrateWatch() {
        val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        val vibrator = vibratorManager.defaultVibrator

        //type of vibration effect
        val effect = VibrationEffect.createOneShot(
            500,
            VibrationEffect.DEFAULT_AMPLITUDE
        )
        vibrator.vibrate(effect)
    }

}
package com.example.honorsthesisapplication.data.source

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.honorsthesisapplication.R

const val TAG = "WatchHeartRateService"

class HeartRateService: Service() {

    private lateinit var hrSource: HeartRateDataSource
    private var threshold: Int = 60 //TODO: take in threshold data from user

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "HeartRateService started")

        createNotificationChannel()

        val notification = createNotification()

        startForeground(NOTIFICATION_ID, notification)

        hrSource = HeartRateDataSource(this) { bpm ->
            checkHeartRate(bpm)
        }
        hrSource.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        hrSource.stop()
    }

    private fun checkHeartRate(bpm: Int) {
        Log.d(TAG, "User BPM = $bpm")

        if (bpm > threshold) {
            //vibrate watch
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator

                val effect = VibrationEffect.createOneShot(
                    100,  // duration in ms
                    VibrationEffect.DEFAULT_AMPLITUDE
                ) //TODO: Use effect set by user

                vibrator.vibrate(effect)
            }
        }
    }
    
    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.heart_rate_channel_name)
            val descriptionText = "Channel for heart rate monitoring service"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Heart Rate Monitoring")
            .setContentText("Continuously monitoring your heart rate.")
            .setSmallIcon(R.drawable.ic_heart_rate)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "heart_rate_channel"
        private const val NOTIFICATION_ID = 1
    }
}
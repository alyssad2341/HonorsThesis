package com.example.honorsthesisapplication.data.source

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.honorsthesisapplication.R
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

const val TAG = "WatchHeartRateService"

class HeartRateService : Service() {

    // 1. Use MeasureClient for REAL-TIME data (Passive is for background/infrequent data)
    private val measureClient by lazy {
        HealthServices.getClient(this).measureClient
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var threshold: Int = 60

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "HeartRateService created")

        // 2. Acquire WakeLock so CPU doesn't sleep when watch is idle
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HonorsThesis:HRWakeLock").apply {
            acquire()
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // 3. Start real-time measurement
        registerHeartRateCallback()
    }

    private fun registerHeartRateCallback() {
        val callback = object : MeasureCallback {
            override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
                Log.d(TAG, "Sensor availability changed: $availability")
            }

            override fun onDataReceived(data: DataPointContainer) {
                // Get the latest heart rate data points
                val heartRateData = data.getData(DataType.HEART_RATE_BPM)
                heartRateData.forEach { sample ->
                    val bpm = sample.value
                    Log.d(TAG, "REAL-TIME BPM: $bpm")
                    checkHeartRate(bpm.toInt())
                }
            }
        }

        // Register for real-time heart rate updates
        measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
    }

    private fun checkHeartRate(bpm: Int) {
        if (bpm > threshold) {
            vibrateWatch()
        }
    }

    private fun vibrateWatch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator.vibrate(
                VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 4. Release WakeLock and stop measurement to save battery
        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        Log.d(TAG, "Service destroyed, WakeLock released")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Heart Rate Monitoring",
                NotificationManager.IMPORTANCE_LOW // Lower importance as it's a silent background task
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Real-Time HR Logging")
            .setContentText("Monitoring vitals for Thesis...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure this icon exists
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "hr_monitor_channel"
        private const val NOTIFICATION_ID = 101
    }
}
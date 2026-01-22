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
import com.example.honorsthesisapplication.data.model.WatchAlertKeys.amplitudes
import com.example.honorsthesisapplication.data.model.WatchAlertKeys.timings
import com.example.honorsthesisapplication.data.model.WatchAlertModel
import com.example.honorsthesisapplication.data.repository.WatchAlertRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

const val TAG = "WatchHeartRateService"

class HeartRateService : Service() {

    private var lastHighAlertTime = 0L
    private var lastLowAlertTime = 0L
    private val measureClient by lazy {
        HealthServices.getClient(this).measureClient
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var repo: WatchAlertRepository
    private var highAlert: WatchAlertModel? = null
    private var lowAlert: WatchAlertModel? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "HeartRateService created")

        repo = WatchAlertRepository(this)

        // 1. Acquire WakeLock early so CPU stays awake
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HonorsThesis:HRWakeLock").apply {
            acquire()
        }

        // 2. Start foreground notification
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // 3. Load alerts asynchronously, then start heart rate callback
        scope.launch {
            highAlert = repo.loadAlert("high_heart_rate")
            lowAlert = repo.loadAlert("low_heart_rate")

            Log.d(TAG, "High alert loaded: $highAlert")
            Log.d(TAG, "Low alert loaded: $lowAlert")

            // 4. Register the heart rate callback only after alerts are ready
            registerHeartRateCallback()
        }
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
        val now = System.currentTimeMillis()

        highAlert?.let { alert ->
            if (alert.enabled && alert.threshold != null && bpm > alert.threshold) {
                val elapsed = now - lastHighAlertTime
                Log.d(TAG, "HIGH alert check: elapsed=$elapsed ms, threshold=${alert.threshold}, freq=${alert.frequencyMillis}")

                if (lastHighAlertTime == 0L || elapsed >= (alert.frequencyMillis ?: 30_000L)) {
                    lastHighAlertTime = now
                    Log.d(TAG, "HIGH alert triggered at $now, BPM: $bpm")
                    vibrateCustom(alert.timings, alert.amplitudes)
                }
            }
        }

        lowAlert?.let { alert ->
            if (alert.enabled && alert.threshold != null && bpm < alert.threshold) {
                val elapsed = now - lastLowAlertTime
                Log.d(TAG, "LOW alert check: elapsed=$elapsed ms, threshold=${alert.threshold}, freq=${alert.frequencyMillis}")

                if (lastLowAlertTime == 0L || elapsed >= (alert.frequencyMillis ?: 30_000L)) {
                    lastLowAlertTime = now
                    Log.d(TAG, "LOW alert triggered at $now, BPM: $bpm")
                    vibrateCustom(alert.timings, alert.amplitudes)
                }
            }
        }
    }

    private fun vibrateCustom(timings: LongArray, amplitudes: IntArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager

            val effect = VibrationEffect.createWaveform(
                timings,
                amplitudes,
                -1
            )

            vibratorManager.defaultVibrator.vibrate(effect)
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
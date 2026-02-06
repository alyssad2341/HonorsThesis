package com.example.honorsthesisapplication.data.source

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.unregisterMeasureCallback
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.example.honorsthesisapplication.MainActivity
import com.example.honorsthesisapplication.R
import com.example.honorsthesisapplication.data.model.WatchAlertModel
import com.example.honorsthesisapplication.data.repository.WatchAlertRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.max

private const val TAG = "WatchHeartRateService"

/**
 * These must match MainActivity (notification-tap routing)
 */
private const val ACTION_OPEN_ALERT_SURVEY = "ACTION_OPEN_ALERT_SURVEY"
private const val EXTRA_ALERT_ID = "extra_alert_id"
private const val EXTRA_ACTUAL_KEY = "extra_actual_key"
private const val EXTRA_ACTUAL_VALUE = "extra_actual_value"
private const val EXTRA_ACTUAL_MESSAGE = "extra_actual_msg"

private const val ACTION_ALERT_TRIGGERED = "ACTION_ALERT_TRIGGERED"

class HeartRateService : Service() {

    private var lastHighAlertTime = 0L
    private var lastLowAlertTime = 0L

    private var hrCallback: MeasureCallback? = null
    private val measureClient by lazy { HealthServices.getClient(this).measureClient }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var repo: WatchAlertRepository
    private var highAlert: WatchAlertModel? = null
    private var lowAlert: WatchAlertModel? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "HeartRateService created")

        repo = WatchAlertRepository(this)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HonorsThesis:HRWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L)
        }

        createChannels()
        val fgNotification = createForegroundNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                fgNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, fgNotification)
        }

        scope.launch {
            highAlert = repo.loadAlert("high_heart_rate")
            lowAlert = repo.loadAlert("low_heart_rate")
            Log.d(TAG, "High alert loaded: $highAlert")
            Log.d(TAG, "Low alert loaded: $lowAlert")
            startAlertObservers()
        }

        registerHeartRateCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        return START_STICKY
    }

    private fun registerHeartRateCallback() {
        hrCallback = object : MeasureCallback {
            override fun onAvailabilityChanged(
                dataType: DeltaDataType<*, *>,
                availability: Availability
            ) {
                Log.d(TAG, "Sensor availability changed: $availability")
            }

            override fun onDataReceived(data: DataPointContainer) {
                val heartRateData = data.getData(DataType.HEART_RATE_BPM)
                heartRateData.forEach { sample ->
                    val bpm = sample.value.toInt()
                    if (bpm <= 0) return@forEach
                    Log.d(TAG, "REAL-TIME BPM: $bpm")
                    checkHeartRate(bpm)
                }
            }
        }

        try {
            hrCallback?.let {
                measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, it)
                Log.d(TAG, "MeasureCallback registered successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register HR MeasureCallback: ${e.message}", e)
        }
    }

    private fun checkHeartRate(bpm: Int) {
        val now = System.currentTimeMillis()

        highAlert?.let { alert ->
            val threshold = alert.threshold
            val cooldown = max(alert.frequencyMillis ?: 30_000L, 1_000L)

            if (alert.enabled && threshold != null && bpm > threshold) {
                val elapsed = now - lastHighAlertTime
                if (lastHighAlertTime == 0L || elapsed >= cooldown) {
                    lastHighAlertTime = now
                    Log.d(TAG, "HIGH alert triggered, BPM: $bpm (threshold=$threshold)")
                    vibrateCustom(alert.timings, alert.amplitudes)

                    showHealthAlertNotification(
                        actualKey = "high_heart_rate",
                        actualValue = bpm.toDouble(),
                        actualMessage = "BPM: $bpm"
                    )
                }
            }
        }

        lowAlert?.let { alert ->
            val threshold = alert.threshold
            val cooldown = max(alert.frequencyMillis ?: 30_000L, 1_000L)

            if (alert.enabled && threshold != null && bpm < threshold) {
                val elapsed = now - lastLowAlertTime
                if (lastLowAlertTime == 0L || elapsed >= cooldown) {
                    lastLowAlertTime = now
                    Log.d(TAG, "LOW alert triggered, BPM: $bpm (threshold=$threshold)")
                    vibrateCustom(alert.timings, alert.amplitudes)

                    showHealthAlertNotification(
                        actualKey = "low_heart_rate",
                        actualValue = bpm.toDouble(),
                        actualMessage = "BPM: $bpm"
                    )
                }
            }
        }
    }

    private fun startAlertObservers() {
        scope.launch {
            repo.observeAlert("high_heart_rate").collect {
                highAlert = it
                Log.d(TAG, "High alert UPDATED: $it")
            }
        }
        scope.launch {
            repo.observeAlert("low_heart_rate").collect {
                lowAlert = it
                Log.d(TAG, "Low alert UPDATED: $it")
            }
        }
    }

    private fun vibrateCustom(timings: LongArray, amplitudes: IntArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)

            if (Build.VERSION.SDK_INT >= 33) {
                val attributes = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_ALARM)
                    .build()
                vibrator.vibrate(effect, attributes)
            } else {
                vibrator.vibrate(effect)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        hrCallback?.let { callback ->
            try {
                runBlocking {
                    measureClient.unregisterMeasureCallback(DataType.HEART_RATE_BPM, callback)
                    Log.d(TAG, "Successfully unregistered HR sensor")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering sensor: ${e.message}", e)
            }
        }

        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        Log.d(TAG, "Service destroyed, WakeLock released")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- Notifications ----------
    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val fg = NotificationChannel(
            FOREGROUND_CHANNEL_ID,
            "Heart Rate Monitoring",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(fg)

        val alert = NotificationChannel(
            ALERT_CHANNEL_ID,
            "Health Alerts",
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(alert)
    }

    private fun createForegroundNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle("HR Monitor Active")
            .setContentText("Tracking heart rate in background…")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val ongoingActivity = OngoingActivity.Builder(
            applicationContext,
            FOREGROUND_NOTIFICATION_ID,
            builder
        )
            .setStaticIcon(R.drawable.ic_launcher_foreground)
            .setTouchIntent(pendingIntent)
            .setStatus(Status.Builder().addTemplate("Monitoring BPM").build())
            .build()

        ongoingActivity.apply(applicationContext)
        return builder.build()
    }

    private fun showHealthAlertNotification(
        actualKey: String,
        actualValue: Double,
        actualMessage: String
    ) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val now = System.currentTimeMillis()
        val notificationId = (now % Int.MAX_VALUE).toInt()
        val alertId = "hr_$now"

        sendBroadcast(Intent(ACTION_ALERT_TRIGGERED).apply {
            putExtra(EXTRA_ALERT_ID, alertId)
            putExtra(EXTRA_ACTUAL_KEY, actualKey)
            putExtra(EXTRA_ACTUAL_VALUE, actualValue)
            putExtra(EXTRA_ACTUAL_MESSAGE, actualMessage)
        })

        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_ALERT_SURVEY
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ALERT_ID, alertId)
            putExtra(EXTRA_ACTUAL_KEY, actualKey)
            putExtra(EXTRA_ACTUAL_VALUE, actualValue)
            putExtra(EXTRA_ACTUAL_MESSAGE, actualMessage)
        }

        val contentPendingIntent = PendingIntent.getActivity(
            this,
            /* requestCode */ notificationId, // UNIQUE per alert
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val alertNotification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Health Alert!")
            .setContentText("Tap to identify the vibration.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setTimeoutAfter(20_000L)
            .setContentIntent(contentPendingIntent)
            .build()

        notificationManager.notify(notificationId, alertNotification)
    }

    companion object {
        private const val FOREGROUND_CHANNEL_ID = "hr_monitor_channel"
        private const val ALERT_CHANNEL_ID = "health_alerts_channel"

        private const val FOREGROUND_NOTIFICATION_ID = 101
    }
}

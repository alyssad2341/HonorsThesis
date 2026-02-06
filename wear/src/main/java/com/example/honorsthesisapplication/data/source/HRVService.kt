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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.ArrayDeque
import kotlin.math.sqrt

private const val HRV_TAG = "HRVService"

/**
 * These must match MainActivity (notification-tap routing)
 */
private const val ACTION_OPEN_ALERT_SURVEY = "ACTION_OPEN_ALERT_SURVEY"
private const val EXTRA_ALERT_ID = "extra_alert_id"
private const val EXTRA_ACTUAL_KEY = "extra_actual_key"
private const val EXTRA_ACTUAL_VALUE = "extra_actual_value"
private const val EXTRA_ACTUAL_MESSAGE = "extra_actual_msg"

private const val ACTION_ALERT_TRIGGERED = "ACTION_ALERT_TRIGGERED"

class HRVService : Service() {

    private var lastLowHrvAlertTime = 0L
    private var lastHighHrvAlertTime = 0L

    private val CHECK_INTERVAL_MILLIS = 30_000L
    private val WINDOW_MILLIS = 60_000L

    private val bpmWindow = ArrayDeque<Pair<Long, Int>>()

    private var hrCallback: MeasureCallback? = null
    private val measureClient by lazy { HealthServices.getClient(this).measureClient }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var repo: WatchAlertRepository
    private var lowHrvAlert: WatchAlertModel? = null   // high_stress (low variability)
    private var highHrvAlert: WatchAlertModel? = null  // low_stress (high variability)

    override fun onCreate() {
        super.onCreate()
        Log.d(HRV_TAG, "HRVService created")

        repo = WatchAlertRepository(this)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HonorsThesis:HRVWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L)
        }

        createChannels()

        val notification = createForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }

        scope.launch {
            lowHrvAlert = repo.loadAlert("high_stress")
            highHrvAlert = repo.loadAlert("low_stress")
            Log.d(HRV_TAG, "high_stress alert loaded: $lowHrvAlert")
            Log.d(HRV_TAG, "low_stress alert loaded: $highHrvAlert")
            startAlertObservers()
        }

        registerHeartRateCallback()
        startPeriodicChecks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(HRV_TAG, "onStartCommand called")
        return START_STICKY
    }

    private fun registerHeartRateCallback() {
        hrCallback = object : MeasureCallback {
            override fun onAvailabilityChanged(
                dataType: DeltaDataType<*, *>,
                availability: Availability
            ) {
                Log.d(HRV_TAG, "Sensor availability changed: $availability")
            }

            override fun onDataReceived(data: DataPointContainer) {
                val samples = data.getData(DataType.HEART_RATE_BPM)
                val now = System.currentTimeMillis()

                samples.forEach { sample ->
                    val bpm = sample.value.toInt()
                    addBpmSample(now, bpm)
                }

                evaluateVariabilityAndAlert()
            }
        }

        try {
            hrCallback?.let {
                measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, it)
                Log.d(HRV_TAG, "MeasureCallback registered successfully")
            }
        } catch (e: Exception) {
            Log.e(HRV_TAG, "Failed to register HR MeasureCallback: ${e.message}", e)
        }
    }

    private fun addBpmSample(timestamp: Long, bpm: Int) {
        if (bpm <= 0) return
        bpmWindow.addLast(timestamp to bpm)
        trimWindow(timestamp)
        Log.d(HRV_TAG, "BPM sample=$bpm, windowSize=${bpmWindow.size}")
    }

    private fun trimWindow(now: Long) {
        while (bpmWindow.isNotEmpty() && now - bpmWindow.first().first > WINDOW_MILLIS) {
            bpmWindow.removeFirst()
        }
    }

    private fun startPeriodicChecks() {
        scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                trimWindow(now)
                evaluateVariabilityAndAlert()
                delay(CHECK_INTERVAL_MILLIS)
            }
        }
    }

    private fun evaluateVariabilityAndAlert() {
        val now = System.currentTimeMillis()
        if (bpmWindow.size < MIN_SAMPLES) return

        val bpms = bpmWindow.map { it.second }
        val mean = bpms.average()
        val variance = bpms.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)

        Log.d(
            HRV_TAG,
            "window=${bpmWindow.size} meanBpm=${"%.1f".format(mean)} sdBpm=${"%.2f".format(stdDev)}"
        )

        lowHrvAlert?.let { alert ->
            val threshold = alert.threshold
            val cooldown = (alert.frequencyMillis ?: 60_000L).coerceAtLeast(1_000L)

            if (alert.enabled && threshold != null && stdDev < threshold) {
                val elapsed = now - lastLowHrvAlertTime
                if (lastLowHrvAlertTime == 0L || elapsed >= cooldown) {
                    lastLowHrvAlertTime = now
                    Log.d(HRV_TAG, "LOW HRV proxy alert (sdBpm=$stdDev < threshold=$threshold)")
                    vibrateCustom(alert.timings, alert.amplitudes)

                    // NOTE: actualKey here must match MainActivity options
                    showHealthAlertNotification(
                        actualKey = "high_stress",
                        actualValue = stdDev,
                        actualMessage = "Low variability: ${"%.2f".format(stdDev)}"
                    )
                }
            }
        }

        highHrvAlert?.let { alert ->
            val threshold = alert.threshold
            val cooldown = (alert.frequencyMillis ?: 60_000L).coerceAtLeast(1_000L)

            if (alert.enabled && threshold != null && stdDev > threshold) {
                val elapsed = now - lastHighHrvAlertTime
                if (lastHighHrvAlertTime == 0L || elapsed >= cooldown) {
                    lastHighHrvAlertTime = now
                    Log.d(HRV_TAG, "HIGH HRV proxy alert (sdBpm=$stdDev > threshold=$threshold)")
                    vibrateCustom(alert.timings, alert.amplitudes)

                    showHealthAlertNotification(
                        actualKey = "low_stress",
                        actualValue = stdDev,
                        actualMessage = "Variability: ${"%.2f".format(stdDev)}"
                    )
                }
            }
        }
    }

    private fun startAlertObservers() {
        scope.launch {
            repo.observeAlert("high_stress").collect {
                lowHrvAlert = it
                Log.d(HRV_TAG, "high_stress alert UPDATED: $it")
            }
        }
        scope.launch {
            repo.observeAlert("low_stress").collect {
                highHrvAlert = it
                Log.d(HRV_TAG, "low_stress alert UPDATED: $it")
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
                    Log.d(HRV_TAG, "Successfully unregistered HR sensor")
                }
            } catch (e: Exception) {
                Log.e(HRV_TAG, "Error unregistering sensor: ${e.message}", e)
            }
        }

        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        Log.d(HRV_TAG, "HRVService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val fg = NotificationChannel(
            FOREGROUND_CHANNEL_ID,
            "HRV Proxy Monitoring",
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
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle("Stress Monitor Active")
            .setContentText("Monitoring stress (HRV proxy)…")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        val ongoingActivity = OngoingActivity.Builder(
            applicationContext,
            FOREGROUND_NOTIFICATION_ID,
            builder
        )
            .setTouchIntent(pendingIntent)
            .setStatus(Status.Builder().addTemplate("Monitoring Stress").build())
            .build()

        ongoingActivity.apply(applicationContext)
        return builder.build()
    }

    private fun showHealthAlertNotification(
        actualKey: String,
        actualValue: Double,
        actualMessage: String
    ) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val now = System.currentTimeMillis()
        val notificationId = (now % Int.MAX_VALUE).toInt()
        val alertId = "hrv_$now"

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
            /* requestCode */ notificationId, // UNIQUE
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Health Alert!")
            .setContentText("Tap to identify the vibration.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setTimeoutAfter(20_000L)
            .setContentIntent(contentPendingIntent)
            .build()

        manager.notify(notificationId, notification)
    }

    companion object {
        private const val FOREGROUND_CHANNEL_ID = "hrv_monitor_channel"
        private const val ALERT_CHANNEL_ID = "health_alerts_channel"

        private const val FOREGROUND_NOTIFICATION_ID = 301

        private const val MIN_SAMPLES = 12
    }
}

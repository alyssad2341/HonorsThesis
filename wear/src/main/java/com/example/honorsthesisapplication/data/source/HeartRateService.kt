package com.example.honorsthesisapplication.data.source

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.*
import androidx.health.services.client.unregisterMeasureCallback
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.example.honorsthesisapplication.MainActivity
import com.example.honorsthesisapplication.R
import com.example.honorsthesisapplication.data.model.WatchAlertModel
import com.example.honorsthesisapplication.data.repository.WatchAlertRepository
import kotlinx.coroutines.*

private const val TAG = "WatchHeartRateService"

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

        // Keep CPU alive while monitoring in background
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HonorsThesis:HRWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L)
        }

        // ---- Foreground service (HEALTH type) ----
        createChannels()
        val fgNotification = createForegroundNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+: pass explicit FGS type to match manifest foregroundServiceType="health"
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                fgNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, fgNotification)
        }

        // Load alerts + observe changes
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
            val cooldown = (alert.frequencyMillis ?: 30_000L).coerceAtLeast(1_000L)

            if (alert.enabled && threshold != null && bpm > threshold) {
                val elapsed = now - lastHighAlertTime
                if (lastHighAlertTime == 0L || elapsed >= cooldown) {
                    lastHighAlertTime = now
                    Log.d(TAG, "HIGH alert triggered, BPM: $bpm (threshold=$threshold)")
                    vibrateCustom(alert.timings, alert.amplitudes)
                    showHeartRateAlert("High Heart Rate Alert!", "BPM: $bpm")
                }
            }
        }

        lowAlert?.let { alert ->
            val threshold = alert.threshold
            val cooldown = (alert.frequencyMillis ?: 30_000L).coerceAtLeast(1_000L)

            if (alert.enabled && threshold != null && bpm < threshold) {
                val elapsed = now - lastLowAlertTime
                if (lastLowAlertTime == 0L || elapsed >= cooldown) {
                    lastLowAlertTime = now
                    Log.d(TAG, "LOW alert triggered, BPM: $bpm (threshold=$threshold)")
                    vibrateCustom(alert.timings, alert.amplitudes)
                    showHeartRateAlert("Low Heart Rate Alert!", "BPM: $bpm")
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
            "Heart Rate Alerts",
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

    private fun showHeartRateAlert(title: String, message: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val ackIntent = Intent(this, AlertReceiver::class.java).apply {
            action = "ACTION_ACKNOWLEDGE"
            putExtra("alert_type", title)
            putExtra("notification_id", HEART_RATE_ALERT_NOTIFICATION_ID)
        }
        val ackPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            ackIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val alertNotification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setTimeoutAfter(20_000L)
            .addAction(R.drawable.ic_launcher_foreground, "Acknowledge", ackPendingIntent)
            .build()

        notificationManager.notify(HEART_RATE_ALERT_NOTIFICATION_ID, alertNotification)
    }

    companion object {
        private const val FOREGROUND_CHANNEL_ID = "hr_monitor_channel"
        private const val ALERT_CHANNEL_ID = "hr_alerts_channel"

        private const val FOREGROUND_NOTIFICATION_ID = 101
        private const val HEART_RATE_ALERT_NOTIFICATION_ID = 202
    }
}

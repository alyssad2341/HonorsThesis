package com.example.honorsthesisapplication.data.source

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
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
import kotlinx.coroutines.*
import java.util.ArrayDeque
import kotlin.math.sqrt

private const val HRV_TAG = "HRVService"

/**
 * We compute a rolling-window variability metric (std dev of BPM over last N seconds).
 * Alerts are pulled from WatchAlertRepository (same pattern as HeartRateService/ActivityService):
 */
class HRVService : Service() {

    private var lastLowHrvAlertTime = 0L
    private var lastHighHrvAlertTime = 0L

    // How often we evaluate even if samples are sparse
    private val CHECK_INTERVAL_MILLIS = 30_000L

    // Rolling window length for variability calculation (adjust to taste)
    private val WINDOW_MILLIS = 60_000L // 60 seconds

    // (timestamp, bpm)
    private val bpmWindow = ArrayDeque<Pair<Long, Int>>()

    private var hrCallback: MeasureCallback? = null
    private val measureClient by lazy { HealthServices.getClient(this).measureClient }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var repo: WatchAlertRepository
    private var lowHrvAlert: WatchAlertModel? = null
    private var highHrvAlert: WatchAlertModel? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(HRV_TAG, "HRVService created")

        repo = WatchAlertRepository(this)

        // Keep CPU alive while monitoring in background
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HonorsThesis:HRVWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L)
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // Load alerts + start observers (same pattern as your other services)
        scope.launch {
            lowHrvAlert = repo.loadAlert("high_stress")     // "stress" proxy
            highHrvAlert = repo.loadAlert("low_stress")   // optional

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

                // Evaluate immediately when we get fresh data
                evaluateVariabilityAndAlert()
            }
        }

        hrCallback?.let {
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, it)
            Log.d(HRV_TAG, "MeasureCallback registered successfully")
        }
    }

    private fun addBpmSample(timestamp: Long, bpm: Int) {
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

    /**
     * Variability proxy:
     *  - mean BPM over window
     *  - std dev BPM over window (simple variability; NOT RMSSD)
     */
    private fun evaluateVariabilityAndAlert() {
        val now = System.currentTimeMillis()

        // Need a minimum number of points for any meaningful variability
        if (bpmWindow.size < MIN_SAMPLES) return

        val bpms = bpmWindow.map { it.second }
        val mean = bpms.average()
        val variance = bpms.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)

        Log.d(
            HRV_TAG,
            "window=${bpmWindow.size} meanBpm=${"%.1f".format(mean)} sdBpm=${"%.2f".format(stdDev)}"
        )

        // ---- LOW "HRV" alert (stress proxy): sdBpm below threshold ----
        lowHrvAlert?.let { alert ->
            val threshold = alert.threshold // interpret as "min sdBpm"
            if (alert.enabled && threshold != null && stdDev < threshold) {
                val cooldown = (alert.frequencyMillis ?: 60_000L).coerceAtLeast(1_000L)
                val elapsed = now - lastLowHrvAlertTime

                if (lastLowHrvAlertTime == 0L || elapsed >= cooldown) {
                    lastLowHrvAlertTime = now
                    Log.d(HRV_TAG, "LOW HRV proxy alert triggered (sdBpm=$stdDev < threshold=$threshold)")
                    vibrateCustom(alert.timings, alert.amplitudes)
                    showHrvAlert(
                        title = "Possible Stress (Low HRV Proxy)",
                        message = "Low variability: sdBpm=${"%.2f".format(stdDev)}"
                    )
                }
            }
        }

        // ---- HIGH "HRV" alert (optional): sdBpm above threshold ----
        highHrvAlert?.let { alert ->
            val threshold = alert.threshold // interpret as "max sdBpm" trigger
            if (alert.enabled && threshold != null && stdDev > threshold) {
                val cooldown = (alert.frequencyMillis ?: 60_000L).coerceAtLeast(1_000L)
                val elapsed = now - lastHighHrvAlertTime

                if (lastHighHrvAlertTime == 0L || elapsed >= cooldown) {
                    lastHighHrvAlertTime = now
                    Log.d(HRV_TAG, "HIGH HRV proxy alert triggered (sdBpm=$stdDev > threshold=$threshold)")
                    vibrateCustom(alert.timings, alert.amplitudes)
                    showHrvAlert(
                        title = "High Variability",
                        message = "sdBpm=${"%.2f".format(stdDev)}"
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
                Log.e(HRV_TAG, "Error unregistering sensor: ${e.message}")
            }
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        Log.d(HRV_TAG, "HRVService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "HRV Proxy Monitoring",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HRV Proxy Monitor Active")
            .setContentText("Monitoring heart rate variability proxy...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        val ongoingActivity = OngoingActivity.Builder(
            applicationContext,
            NOTIFICATION_ID,
            builder
        )
            .setTouchIntent(pendingIntent)
            .setStatus(Status.Builder().addTemplate("Monitoring HRV Proxy").build())
            .build()

        ongoingActivity.apply(applicationContext)
        return builder.build()
    }

    private fun showHrvAlert(title: String, message: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Same "Acknowledge" pattern you use in HeartRateService
        val ackIntent = Intent(this, AlertReceiver::class.java).apply {
            action = "ACTION_ACKNOWLEDGE"
            putExtra("alert_type", title)
        }
        val ackPendingIntent = PendingIntent.getBroadcast(
            this, 0, ackIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setTimeoutAfter(20_000L)
            .addAction(R.drawable.ic_launcher_foreground, "Acknowledge", ackPendingIntent)
            .build()

        manager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "hrv_proxy_monitor_channel"
        private const val NOTIFICATION_ID = 301
        private const val ALERT_NOTIFICATION_ID = 302

        private const val MIN_SAMPLES = 12 // tweak depending on how often samples arrive
    }
}

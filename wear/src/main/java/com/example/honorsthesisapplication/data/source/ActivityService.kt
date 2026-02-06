package com.example.honorsthesisapplication.data.source

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
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
import java.util.ArrayDeque
import kotlin.math.max

private const val ACTIVITYTAG = "WatchActivityService"

/**
 * These must match MainActivity (notification-tap routing)
 */
private const val ACTION_OPEN_ALERT_SURVEY = "ACTION_OPEN_ALERT_SURVEY"
private const val EXTRA_ALERT_ID = "extra_alert_id"
private const val EXTRA_ACTUAL_KEY = "extra_actual_key"
private const val EXTRA_ACTUAL_VALUE = "extra_actual_value"
private const val EXTRA_ACTUAL_MESSAGE = "extra_actual_msg"

private const val ACTION_ALERT_TRIGGERED = "ACTION_ALERT_TRIGGERED"

class ActivityService : Service(), SensorEventListener {

    private var lastHighAlertTime = 0L
    private var lastLowAlertTime = 0L

    private val CHECK_INTERVAL_MILLIS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var repo: WatchAlertRepository
    private var highAlert: WatchAlertModel? = null
    private var lowAlert: WatchAlertModel? = null

    // Rolling 1-hour step window: (timestamp, deltaSteps)
    private val stepWindow = ArrayDeque<Pair<Long, Long>>()
    private val WINDOW_MILLIS = 60 * 60 * 1000L

    // Sensors
    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var stepDetectorSensor: Sensor? = null
    private var lastStepCounterValue: Float? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(ACTIVITYTAG, "ActivityService created")

        repo = WatchAlertRepository(this)

        // Keep CPU alive while monitoring in background
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HonorsThesis:ActivityWakeLock"
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

        // Load alerts + start observers
        scope.launch {
            highAlert = repo.loadAlert("high_activity")
            lowAlert = repo.loadAlert("low_activity")
            Log.d(ACTIVITYTAG, "High activity alert loaded: $highAlert")
            Log.d(ACTIVITYTAG, "Low activity alert loaded: $lowAlert")
            startAlertObservers()
        }

        registerStepSensors()
        startPeriodicChecks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(ACTIVITYTAG, "onStartCommand called")
        return START_STICKY
    }

    private fun registerStepSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        Log.d(
            ACTIVITYTAG,
            "Sensors: STEP_COUNTER=${stepCounterSensor != null}, STEP_DETECTOR=${stepDetectorSensor != null}"
        )

        val ok = when {
            stepCounterSensor != null ->
                sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL)
            stepDetectorSensor != null ->
                sensorManager.registerListener(this, stepDetectorSensor, SensorManager.SENSOR_DELAY_NORMAL)
            else -> false
        }

        Log.d(ACTIVITYTAG, "Sensor listener registered = $ok")
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val total = event.values.firstOrNull() ?: return
                val prev = lastStepCounterValue
                lastStepCounterValue = total

                if (prev == null) {
                    Log.d(ACTIVITYTAG, "Baseline STEP_COUNTER=$total (since boot)")
                    return
                }

                val delta = (total - prev).toLong()
                if (delta > 0) addDeltaSteps(delta)
            }

            Sensor.TYPE_STEP_DETECTOR -> {
                addDeltaSteps(1)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun addDeltaSteps(deltaSteps: Long) {
        val now = System.currentTimeMillis()

        stepWindow.addLast(now to deltaSteps)
        resetWindow(now)

        val stepsLastHour = stepWindow.sumOf { it.second }.toInt()
        Log.d(ACTIVITYTAG, "delta=$deltaSteps, stepsLastHour=$stepsLastHour")

        checkActivityLevel(stepsLastHour)
    }

    private fun startPeriodicChecks() {
        scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                resetWindow(now)

                val stepsLastHour = stepWindow.sumOf { it.second }.toInt()
                Log.d(ACTIVITYTAG, "[PERIODIC] stepsLastHour=$stepsLastHour")

                checkActivityLevel(stepsLastHour)
                delay(CHECK_INTERVAL_MILLIS)
            }
        }
    }

    private fun resetWindow(now: Long) {
        while (stepWindow.isNotEmpty() && now - stepWindow.first().first > WINDOW_MILLIS) {
            stepWindow.removeFirst()
        }
    }

    private fun checkActivityLevel(stepsLastHour: Int) {
        val now = System.currentTimeMillis()

        highAlert?.let { alert ->
            val threshold = alert.threshold
            val cooldown = max(alert.frequencyMillis ?: 60_000L, 1_000L)

            if (alert.enabled && threshold != null && stepsLastHour > threshold) {
                val elapsed = now - lastHighAlertTime
                if (lastHighAlertTime == 0L || elapsed >= cooldown) {
                    lastHighAlertTime = now
                    Log.d(
                        ACTIVITYTAG,
                        "HIGH activity alert triggered (stepsLastHour=$stepsLastHour, threshold=$threshold)"
                    )
                    vibrateCustom(alert.timings, alert.amplitudes)

                    // Generic notification, but pass actual data as hidden extras
                    showActivityAlert(
                        actualKey = "high_activity",
                        actualMessage = "Steps last hour: $stepsLastHour",
                        actualValue = stepsLastHour.toDouble()
                    )
                }
            }
        }

        lowAlert?.let { alert ->
            val threshold = alert.threshold
            val cooldown = max(alert.frequencyMillis ?: 60_000L, 1_000L)

            if (alert.enabled && threshold != null && stepsLastHour < threshold) {
                val elapsed = now - lastLowAlertTime
                if (lastLowAlertTime == 0L || elapsed >= cooldown) {
                    lastLowAlertTime = now
                    Log.d(
                        ACTIVITYTAG,
                        "LOW activity alert triggered (stepsLastHour=$stepsLastHour, threshold=$threshold)"
                    )
                    vibrateCustom(alert.timings, alert.amplitudes)

                    showActivityAlert(
                        actualKey = "low_activity",
                        actualMessage = "Steps last hour: $stepsLastHour",
                        actualValue = stepsLastHour.toDouble()
                    )
                }
            }
        }
    }

    private fun startAlertObservers() {
        scope.launch {
            repo.observeAlert("high_activity").collect {
                highAlert = it
                Log.d(ACTIVITYTAG, "High activity alert UPDATED: $it")
            }
        }
        scope.launch {
            repo.observeAlert("low_activity").collect {
                lowAlert = it
                Log.d(ACTIVITYTAG, "Low activity alert UPDATED: $it")
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
        if (::sensorManager.isInitialized) sensorManager.unregisterListener(this)
        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        Log.d(ACTIVITYTAG, "ActivityService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val fg = NotificationChannel(
            FOREGROUND_CHANNEL_ID,
            "Activity Monitoring",
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
            .setContentTitle("Activity Monitor Active")
            .setContentText("Tracking steps in background…")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)

        val ongoingActivity = OngoingActivity.Builder(
            applicationContext,
            FOREGROUND_NOTIFICATION_ID,
            builder
        )
            .setTouchIntent(pendingIntent)
            .setStatus(Status.Builder().addTemplate("Monitoring Activity").build())
            .build()

        ongoingActivity.apply(applicationContext)
        return builder.build()
    }

    private fun showActivityAlert(
        actualKey: String,
        actualMessage: String,
        actualValue: Double
    ) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val alertId = "activity_${System.currentTimeMillis()}"
        val requestCode = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()

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

        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // IMPORTANT: generic text only (do NOT reveal actual alert)
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Health Alert!")
            .setContentText("Tap to identify the alert.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setTimeoutAfter(20_000L)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(requestCode, notification) // unique notification id per alert
    }

    companion object {
        private const val FOREGROUND_CHANNEL_ID = "activity_monitor_channel"
        private const val ALERT_CHANNEL_ID = "health_alerts_channel"

        private const val FOREGROUND_NOTIFICATION_ID = 201
    }
}

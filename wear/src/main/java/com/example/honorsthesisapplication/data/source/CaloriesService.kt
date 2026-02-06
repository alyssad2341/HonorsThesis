package com.example.honorsthesisapplication.data.source

import android.annotation.SuppressLint
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
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.CumulativeDataPoint
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseState
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
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

private const val CAL_TAG = "CaloriesService"

/**
 * These must match MainActivity (notification-tap routing)
 */
private const val ACTION_OPEN_ALERT_SURVEY = "ACTION_OPEN_ALERT_SURVEY"
private const val EXTRA_ALERT_ID = "extra_alert_id"
private const val EXTRA_ACTUAL_KEY = "extra_actual_key"
private const val EXTRA_ACTUAL_VALUE = "extra_actual_value"
private const val EXTRA_ACTUAL_MESSAGE = "extra_actual_msg"

private const val ACTION_ALERT_TRIGGERED = "ACTION_ALERT_TRIGGERED"

class CaloriesService : Service() {

    private var lastHighCalAlertTime = 0L
    private var lastLowCalAlertTime = 0L

    private val CHECK_INTERVAL_MILLIS = 30_000L

    // Rolling 1-hour calorie window: (timestamp, deltaCalories)
    private val calWindow = ArrayDeque<Pair<Long, Double>>()
    private val WINDOW_MILLIS = 60 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var repo: WatchAlertRepository
    private var highCalAlert: WatchAlertModel? = null
    private var lowCalAlert: WatchAlertModel? = null

    private val exerciseClient by lazy { HealthServices.getClient(this).exerciseClient }
    private var updateCallback: ExerciseUpdateCallback? = null

    // CALORIES_TOTAL is cumulative since exercise start; we convert to deltas.
    private var lastTotalCalories: Double? = null
    private var exerciseStarted = false

    override fun onCreate() {
        super.onCreate()
        Log.d(CAL_TAG, "CaloriesService created")

        repo = WatchAlertRepository(this)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HonorsThesis:CaloriesWakeLock"
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
            highCalAlert = repo.loadAlert("high_calories")
            lowCalAlert = repo.loadAlert("low_calories")
            Log.d(CAL_TAG, "high_calories alert loaded: $highCalAlert")
            Log.d(CAL_TAG, "low_calories alert loaded: $lowCalAlert")
            startAlertObservers()
        }

        registerExerciseUpdates()
        startExerciseIfPossible()
        startPeriodicChecks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(CAL_TAG, "onStartCommand called")
        return START_STICKY
    }

    private fun registerExerciseUpdates() {
        updateCallback = object : ExerciseUpdateCallback {

            override fun onRegistered() {
                Log.d(CAL_TAG, "ExerciseUpdateCallback onRegistered()")
            }

            override fun onRegistrationFailed(throwable: Throwable) {
                Log.e(CAL_TAG, "Exercise update registration failed: ${throwable.message}", throwable)
            }

            override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
                try {
                    val now = System.currentTimeMillis()

                    val state: ExerciseState = update.exerciseStateInfo.state
                    if (state == ExerciseState.ENDED) {
                        Log.w(CAL_TAG, "Exercise ended; calories will stop updating.")
                        exerciseStarted = false
                        return
                    }

                    val raw: Any? = try {
                        update.latestMetrics.getData(DataType.CALORIES_TOTAL)
                    } catch (e: Exception) {
                        Log.w(CAL_TAG, "getData(CALORIES_TOTAL) threw: ${e.message}")
                        null
                    }

                    if (raw == null) {
                        Log.d(CAL_TAG, "No CALORIES_TOTAL data (raw=null)")
                        return
                    }

                    val totalCalories: Double? = when (raw) {
                        is CumulativeDataPoint<*> -> raw.total.toDouble()

                        is Collection<*> -> {
                            if (raw.isEmpty()) null
                            else {
                                var lastPoint: Any? = null
                                for (p in raw) lastPoint = p
                                if (lastPoint == null) null
                                else {
                                    try {
                                        val m = lastPoint.javaClass.getMethod("getValue")
                                        (m.invoke(lastPoint) as Number).toDouble()
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                            }
                        }

                        else -> {
                            try {
                                val m = raw.javaClass.getMethod("getValue")
                                (m.invoke(raw) as Number).toDouble()
                            } catch (e: Exception) {
                                Log.d(CAL_TAG, "CALORIES_TOTAL unsupported type=${raw.javaClass.name}")
                                null
                            }
                        }
                    }

                    if (totalCalories == null) {
                        Log.d(CAL_TAG, "Could not parse CALORIES_TOTAL (type=${raw.javaClass.name})")
                        return
                    }

                    val prev = lastTotalCalories
                    if (prev == null) {
                        lastTotalCalories = totalCalories
                        Log.d(CAL_TAG, "Baseline CALORIES_TOTAL=$totalCalories")
                        return
                    }

                    val delta = totalCalories - prev
                    lastTotalCalories = totalCalories

                    if (delta > 0.0) {
                        addDeltaCalories(now, delta)
                        evaluateCaloriesAndAlert()
                    } else {
                        Log.d(CAL_TAG, "CALORIES_TOTAL unchanged (total=$totalCalories)")
                    }
                } catch (e: Exception) {
                    Log.e(CAL_TAG, "onExerciseUpdateReceived guard: ${e.message}", e)
                }
            }

            override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {
                Log.d(CAL_TAG, "Availability changed for $dataType: $availability")
            }

            override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {
                // Not needed for calories
            }
        }

        updateCallback?.let { cb ->
            try {
                exerciseClient.setUpdateCallback(cb)
                Log.d(CAL_TAG, "ExerciseUpdateCallback set on client")
            } catch (e: Exception) {
                Log.e(CAL_TAG, "Failed to set ExerciseUpdateCallback: ${e.message}", e)
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun startExerciseIfPossible() {
        scope.launch {
            try {
                val info = exerciseClient.getCurrentExerciseInfoAsync().get()

                when (info.exerciseTrackedStatus) {
                    ExerciseTrackedStatus.OTHER_APP_IN_PROGRESS -> {
                        Log.w(CAL_TAG, "Another app is tracking an exercise. Not starting ours.")
                        return@launch
                    }
                    ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS -> {
                        Log.d(CAL_TAG, "Our exercise already in progress.")
                        exerciseStarted = true
                        return@launch
                    }
                    ExerciseTrackedStatus.NO_EXERCISE_IN_PROGRESS -> {
                        // continue
                    }
                }

                val config = ExerciseConfig(
                    exerciseType = ExerciseType.WORKOUT,
                    dataTypes = setOf(DataType.CALORIES_TOTAL),
                    isAutoPauseAndResumeEnabled = false,
                    isGpsEnabled = false
                )

                Log.d(CAL_TAG, "Starting WORKOUT exercise for calories...")
                exerciseClient.startExerciseAsync(config).get()

                exerciseStarted = true
                lastTotalCalories = null
                Log.d(CAL_TAG, "Exercise started successfully")
            } catch (e: Exception) {
                Log.e(CAL_TAG, "Failed to start exercise: ${e.message}", e)
            }
        }
    }

    private fun addDeltaCalories(timestamp: Long, deltaCalories: Double) {
        if (deltaCalories <= 0.0) return

        calWindow.addLast(timestamp to deltaCalories)
        trimWindow(timestamp)

        val calLastHour = calWindow.sumOf { it.second }
        Log.d(
            CAL_TAG,
            "deltaCal=${"%.3f".format(deltaCalories)}, calLastHour=${"%.2f".format(calLastHour)}"
        )
    }

    private fun startPeriodicChecks() {
        scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                trimWindow(now)

                val calLastHour = calWindow.sumOf { it.second }
                Log.d(
                    CAL_TAG,
                    "[PERIODIC] calLastHour=${"%.2f".format(calLastHour)} (exerciseStarted=$exerciseStarted)"
                )

                evaluateCaloriesAndAlert()
                delay(CHECK_INTERVAL_MILLIS)
            }
        }
    }

    private fun trimWindow(now: Long) {
        while (calWindow.isNotEmpty() && now - calWindow.first().first > WINDOW_MILLIS) {
            calWindow.removeFirst()
        }
    }

    private fun evaluateCaloriesAndAlert() {
        val now = System.currentTimeMillis()
        trimWindow(now)

        val calLastHour = calWindow.sumOf { it.second }

        highCalAlert?.let { alert ->
            val threshold = alert.threshold?.toDouble()
            val cooldown = max(alert.frequencyMillis ?: 60_000L, 1_000L)

            if (alert.enabled && threshold != null && calLastHour > threshold) {
                val elapsed = now - lastHighCalAlertTime
                if (lastHighCalAlertTime == 0L || elapsed >= cooldown) {
                    lastHighCalAlertTime = now
                    Log.d(CAL_TAG, "HIGH calories alert (calLastHour=$calLastHour > threshold=$threshold)")
                    vibrateCustom(alert.timings, alert.amplitudes)

                    showHealthAlertNotification(
                        actualKey = "high_calories",
                        actualValue = calLastHour,
                        actualMessage = "Calories last hour: ${"%.1f".format(calLastHour)}"
                    )
                }
            }
        }

        lowCalAlert?.let { alert ->
            val threshold = alert.threshold?.toDouble()
            val cooldown = max(alert.frequencyMillis ?: 60_000L, 1_000L)

            if (alert.enabled && threshold != null && calLastHour < threshold) {
                val elapsed = now - lastLowCalAlertTime
                if (lastLowCalAlertTime == 0L || elapsed >= cooldown) {
                    lastLowCalAlertTime = now
                    Log.d(CAL_TAG, "LOW calories alert (calLastHour=$calLastHour < threshold=$threshold)")
                    vibrateCustom(alert.timings, alert.amplitudes)

                    showHealthAlertNotification(
                        actualKey = "low_calories",
                        actualValue = calLastHour,
                        actualMessage = "Calories last hour: ${"%.1f".format(calLastHour)}"
                    )
                }
            }
        }
    }

    private fun startAlertObservers() {
        scope.launch {
            repo.observeAlert("high_calories").collect {
                highCalAlert = it
                Log.d(CAL_TAG, "high_calories alert UPDATED: $it")
            }
        }
        scope.launch {
            repo.observeAlert("low_calories").collect {
                lowCalAlert = it
                Log.d(CAL_TAG, "low_calories alert UPDATED: $it")
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

    @SuppressLint("RestrictedApi")
    override fun onDestroy() {
        super.onDestroy()

        scope.launch {
            try {
                val info = exerciseClient.getCurrentExerciseInfoAsync().get()
                if (info.exerciseTrackedStatus == ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS) {
                    exerciseClient.endExerciseAsync().get()
                    Log.d(CAL_TAG, "Ended exercise")
                }
            } catch (e: Exception) {
                Log.w(CAL_TAG, "Could not end exercise cleanly: ${e.message}")
            }
        }

        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        Log.d(CAL_TAG, "CaloriesService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val fg = NotificationChannel(
            FOREGROUND_CHANNEL_ID,
            "Calories Monitoring",
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
            .setContentTitle("Calories Monitor Active")
            .setContentText("Tracking calories during an active workout…")
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
            .setStatus(Status.Builder().addTemplate("Monitoring Calories").build())
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
        val alertId = "calories_$now"

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
            /* requestCode */ notificationId, // unique per alert
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
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(notificationId, notification)
    }

    companion object {
        private const val FOREGROUND_CHANNEL_ID = "calories_monitor_channel"
        private const val ALERT_CHANNEL_ID = "health_alerts_channel"

        private const val FOREGROUND_NOTIFICATION_ID = 401
    }
}

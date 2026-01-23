package com.example.honorsthesisapplication.data.source

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.example.honorsthesisapplication.MainActivity
import com.example.honorsthesisapplication.R
import com.example.honorsthesisapplication.data.model.WatchAlertModel
import com.example.honorsthesisapplication.data.repository.WatchAlertRepository
import kotlinx.coroutines.*
import java.util.ArrayDeque

private const val ACTIVITYTAG = "WatchActivityService"

class ActivityService : Service(), SensorEventListener {

    private var lastHighAlertTime = 0L
    private var lastLowAlertTime = 0L

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

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // Load alerts + start observers
        scope.launch {
            highAlert = repo.loadAlert("high_activity")
            lowAlert = repo.loadAlert("low_activity")
            Log.d(ACTIVITYTAG, "High activity alert loaded: $highAlert")
            Log.d(ACTIVITYTAG, "Low activity alert loaded: $lowAlert")
            startAlertObservers()
        }

        // Register step sensor listener
        registerStepSensors()
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

        // Prefer STEP_COUNTER (best for deltas), fall back to STEP_DETECTOR
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
                // typically 1.0 per step
                addDeltaSteps(1)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    private fun addDeltaSteps(deltaSteps: Long) {
        val now = System.currentTimeMillis()

        stepWindow.addLast(now to deltaSteps)
        resetWindow(now)

        val stepsLastHour = stepWindow.sumOf { it.second }
        Log.d(ACTIVITYTAG, "delta=$deltaSteps, stepsLastHour=$stepsLastHour")

        checkActivityLevel(stepsLastHour.toInt())
    }

    private fun resetWindow(now: Long) {
        while (stepWindow.isNotEmpty() && now - stepWindow.first().first > WINDOW_MILLIS) {
            stepWindow.removeFirst()
        }
    }

    private fun checkActivityLevel(stepsLastHour: Int) {
        val now = System.currentTimeMillis()

        highAlert?.let { alert ->
            if (alert.enabled && alert.threshold != null && stepsLastHour > alert.threshold) {
                val elapsed = now - lastHighAlertTime
                if (lastHighAlertTime == 0L || elapsed >= (alert.frequencyMillis ?: 60_000L)) {
                    lastHighAlertTime = now
                    Log.d(ACTIVITYTAG, "HIGH activity alert triggered")
                    vibrateCustom(alert.timings, alert.amplitudes)
                    showActivityAlert("High Activity Alert!", "Steps last hour: $stepsLastHour")
                }
            }
        }

        lowAlert?.let { alert ->
            if (alert.enabled && alert.threshold != null && stepsLastHour < alert.threshold) {
                val elapsed = now - lastLowAlertTime
                if (lastLowAlertTime == 0L || elapsed >= (alert.frequencyMillis ?: 60_000L)) {
                    lastLowAlertTime = now
                    Log.d(ACTIVITYTAG, "LOW activity alert triggered")
                    vibrateCustom(alert.timings, alert.amplitudes)
                    showActivityAlert("Low Activity Alert!", "Steps last hour: $stepsLastHour")
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
        sensorManager.unregisterListener(this)
        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        Log.d(ACTIVITYTAG, "ActivityService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Activity Monitoring",
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
            .setContentTitle("Activity Monitor Active")
            .setContentText("Tracking steps in background...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)

        val ongoingActivity = OngoingActivity.Builder(
            applicationContext,
            NOTIFICATION_ID,
            builder
        )
            .setTouchIntent(pendingIntent)
            .setStatus(Status.Builder().addTemplate("Monitoring Activity").build())
            .build()

        ongoingActivity.apply(applicationContext)
        return builder.build()
    }

    private fun showActivityAlert(title: String, message: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setTimeoutAfter(20_000L)
            .build()

        manager.notify(303, notification)
    }

    companion object {
        private const val CHANNEL_ID = "activity_monitor_channel"
        private const val NOTIFICATION_ID = 201
    }
}

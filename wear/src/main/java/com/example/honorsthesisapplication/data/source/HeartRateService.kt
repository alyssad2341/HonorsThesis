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
import androidx.wear.ongoing.Status
import android.app.PendingIntent
import android.media.AudioAttributes
import androidx.health.services.client.unregisterMeasureCallback
import androidx.wear.ongoing.OngoingActivity
import com.example.honorsthesisapplication.MainActivity
import com.example.honorsthesisapplication.data.model.WatchAlertModel
import com.example.honorsthesisapplication.data.repository.WatchAlertRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

const val TAG = "WatchHeartRateService"

class HeartRateService : Service() {

    private var lastHighAlertTime = 0L
    private var lastLowAlertTime = 0L

    private var hrCallback: MeasureCallback? = null
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

        // Acquire WakeLock early so CPU stays awake
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HonorsThesis:HRWakeLock"
        ).apply {
            // Non-reference counted means one release() always works
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L)
        }

        // Start foreground notification
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // Load alerts asynchronously, then start heart rate callback
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
        super.onStartCommand(intent, flags, startId)
        // START_STICKY tells the OS to recreate the service if it gets killed
        return START_STICKY
    }

    private fun registerHeartRateCallback() {
        // Assign the object to the class variable hrCallback
        hrCallback = object : MeasureCallback {
            override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
                Log.d(TAG, "Sensor availability changed: $availability")
            }

            override fun onDataReceived(data: DataPointContainer) {
                val heartRateData = data.getData(DataType.HEART_RATE_BPM)
                heartRateData.forEach { sample ->
                    val bpm = sample.value
                    Log.d(TAG, "REAL-TIME BPM: $bpm")
                    checkHeartRate(bpm.toInt())
                }
            }
        }

        // Register for real-time heart rate updates
        hrCallback?.let {
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, it)
            Log.d(TAG, "MeasureCallback registered successfully")
        }
    }

    private fun checkHeartRate(bpm: Int) {
        val now = System.currentTimeMillis()

        highAlert?.let { alert ->
            if (alert.enabled && alert.threshold != null && bpm > alert.threshold) {
                val elapsed = now - lastHighAlertTime
                if (lastHighAlertTime == 0L || elapsed >= (alert.frequencyMillis ?: 30000L)) {
                    lastHighAlertTime = now
                    Log.d(TAG, "HIGH alert triggered at $now, BPM: $bpm")
                    vibrateCustom(alert.timings, alert.amplitudes)
                    showHeartRateAlert("High Heart Rate Alert!", "BPM: $bpm")
                }
            }
        }

        lowAlert?.let { alert ->
            if (alert.enabled && alert.threshold != null && bpm < alert.threshold) {
                val elapsed = now - lastLowAlertTime
                if (lastLowAlertTime == 0L || elapsed >= (alert.frequencyMillis ?: 30000L)) {
                    lastLowAlertTime = now
                    Log.d(TAG, "LOW alert triggered at $now, BPM: $bpm")
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
        // Check if we are on Android 12 (API 31) or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator

            // Create the waveform (same for all versions)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)

            // Branch logic based on Android Version
            if (Build.VERSION.SDK_INT >= 33) {
                // API 33+ (Galaxy Watch 6/7): Use VibrationAttributes
                val attributes = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_ALARM)
                    .build()
                vibrator.vibrate(effect, attributes)

            }else{
                vibrator.vibrate(effect)
            }

            Log.d(TAG, "Vibration sent via background-safe attributes")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the HR sensor synchronously before exiting
        hrCallback?.let { callback ->
            try {
                runBlocking {
                    measureClient.unregisterMeasureCallback(DataType.HEART_RATE_BPM, callback)
                    Log.d(TAG, "Successfully unregistered HR sensor")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering sensor: ${e.message}")
            }
        }
        // Release WakeLock and stop measurement to save battery
        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        Log.d(TAG, "Service destroyed, WakeLock released")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Heart Rate Monitoring",
            NotificationManager.IMPORTANCE_HIGH // Lower importance as it's a silent background task
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        // Create an Intent to open the app if the user taps the icon
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Build the Notification (Standard)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HR Monitor Active")
            .setContentText("Tracking vitals in background...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT) // Critical for Health Services priority
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Wrap it in an Ongoing Activity
        // This puts the icon on the watch face and prevents the OS from killing the sensor
        val ongoingActivity = OngoingActivity.Builder(
            applicationContext,
            NOTIFICATION_ID,
            builder
        )
            .setAnimatedIcon(R.drawable.ic_launcher_foreground) // Use a static icon if you don't have an animated one
            .setStaticIcon(R.drawable.ic_launcher_foreground)
            .setTouchIntent(pendingIntent)
            .setStatus(Status.Builder().addTemplate("Monitoring BPM").build())
            .build()

        ongoingActivity.apply(applicationContext)

        return builder.build()
    }

    private fun showHeartRateAlert(title: String, message: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Create the Intent for the Acknowledge button
        val ackIntent = Intent(this, AlertReceiver::class.java).apply {
            action = "ACTION_ACKNOWLEDGE"
            putExtra("alert_type", title)
        }
        val ackPendingIntent = PendingIntent.getBroadcast(
            this, 0, ackIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Build the Alert Notification
        val alertNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX) // Makes it pop up (heads-up)
            .setAutoCancel(true)
            .setTimeoutAfter(20000L) // MAKE IT GO AWAY AFTER 20 SECONDS
            .addAction(R.drawable.ic_launcher_foreground, "Acknowledge", ackPendingIntent) // THE BUTTON
            .build()

        // Issue the notification (Use a different ID than the foreground service!)
        notificationManager.notify(202, alertNotification)
    }

    companion object {
        private const val CHANNEL_ID = "hr_monitor_channel"
        private const val NOTIFICATION_ID = 101
    }
}
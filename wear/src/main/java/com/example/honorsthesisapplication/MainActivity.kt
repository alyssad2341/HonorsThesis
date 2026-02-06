package com.example.honorsthesisapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.OutlinedButton
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.honorsthesisapplication.data.source.ActivityService
import com.example.honorsthesisapplication.data.source.CaloriesService
import com.example.honorsthesisapplication.data.source.HRVService
import com.example.honorsthesisapplication.data.source.HeartRateService

private const val MAIN_TAG = "MainActivity"

// Must match what you put in the notification PendingIntent extras
private const val ACTION_OPEN_ALERT_SURVEY = "ACTION_OPEN_ALERT_SURVEY"
private const val EXTRA_ALERT_ID = "extra_alert_id"
private const val EXTRA_ACTUAL_KEY = "extra_actual_key"
private const val EXTRA_ACTUAL_VALUE = "extra_actual_value"
private const val EXTRA_ACTUAL_MESSAGE = "extra_actual_msg"

private const val ACTION_ALERT_TRIGGERED = "ACTION_ALERT_TRIGGERED"

class MainActivity : ComponentActivity() {

    private val alertIntentState = mutableStateOf<Intent?>(null)

    // Receives "alert happened" broadcasts while app is open and flips UI to survey
    private val alertBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_ALERT_TRIGGERED) return

            val alertId = intent.getStringExtra(EXTRA_ALERT_ID).orEmpty()
            val actualKey = intent.getStringExtra(EXTRA_ACTUAL_KEY).orEmpty()
            val actualValue = intent.getDoubleExtra(EXTRA_ACTUAL_VALUE, Double.NaN)
            val actualMsg = intent.getStringExtra(EXTRA_ACTUAL_MESSAGE).orEmpty()

            if (alertId.isBlank() || actualKey.isBlank() || actualValue.isNaN()) {
                Log.w(MAIN_TAG, "Ignored ACTION_ALERT_TRIGGERED (missing extras)")
                return
            }

            // Convert the broadcast into the SAME intent shape as a notification-tap launch
            val uiIntent = Intent(this@MainActivity, MainActivity::class.java).apply {
                action = ACTION_OPEN_ALERT_SURVEY
                putExtra(EXTRA_ALERT_ID, alertId)
                putExtra(EXTRA_ACTUAL_KEY, actualKey)
                putExtra(EXTRA_ACTUAL_VALUE, actualValue)
                putExtra(EXTRA_ACTUAL_MESSAGE, actualMsg)
            }

            Log.d(
                MAIN_TAG,
                "Received alert broadcast; showing survey now (alertId=$alertId, key=$actualKey)"
            )
            alertIntentState.value = uiIntent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Handles the case where we were opened from a notification tap
        alertIntentState.value = intent

        // Register receiver so we can pop the survey even if user didn't tap the notification
        registerAlertReceiver()

        setTheme(android.R.style.Theme_DeviceDefault)
        setContent { WearApp("Android", alertIntentState) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handles notification taps while app is already running
        alertIntentState.value = intent
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(alertBroadcastReceiver)
        } catch (_: Exception) {
            // ignore
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerAlertReceiver() {
        val filter = IntentFilter(ACTION_ALERT_TRIGGERED)

        if (Build.VERSION.SDK_INT >= 33) {
            // Safer registration API on Android 13+
            ContextCompat.registerReceiver(
                this,
                alertBroadcastReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(alertBroadcastReceiver, filter)
        }

        Log.d(MAIN_TAG, "Registered ACTION_ALERT_TRIGGERED receiver")
    }
}

@Composable
fun WearApp(greetingName: String, alertIntentState: MutableState<Intent?>) {
    val intent = alertIntentState.value

    val isAlertLaunch = intent?.action == ACTION_OPEN_ALERT_SURVEY
    val alertId = intent?.getStringExtra(EXTRA_ALERT_ID).orEmpty()
    val actualKeyRaw = intent?.getStringExtra(EXTRA_ACTUAL_KEY).orEmpty()
    val actualValue = intent?.getDoubleExtra(EXTRA_ACTUAL_VALUE, Double.NaN) ?: Double.NaN
    val actualMessage = intent?.getStringExtra(EXTRA_ACTUAL_MESSAGE).orEmpty()

    fun normalizeKey(k: String): String =
        k.trim()
            .lowercase()
            .replace("-", "_")
            .replace(" ", "_")
            .replace("heartrate", "heart_rate")
            .replace("__", "_")

    val actualKey = normalizeKey(actualKeyRaw)

    val shouldShowSurvey =
        isAlertLaunch && alertId.isNotBlank() && actualKey.isNotBlank() && !actualValue.isNaN()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        TimeText()

        if (shouldShowSurvey) {
            AlertSurveyScreen(
                alertId = alertId,
                actualKey = actualKey,
                actualValue = actualValue,
                actualMessage = actualMessage,
                onDone = {
                    // Clear so it doesn't keep reopening
                    alertIntentState.value = null
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Greeting(greetingName = greetingName)
                Spacer(Modifier.height(8.dp))
                RequestPermissionsAndStartServices()
            }
        }
    }
}

@Composable
fun Greeting(greetingName: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colors.primary,
        text = stringResource(R.string.hello_world, greetingName)
    )
}

@Composable
fun AlertSurveyScreen(
    alertId: String,
    actualKey: String,
    actualValue: Double,
    actualMessage: String,
    onDone: () -> Unit
) {
    val options: List<Pair<String, String>> = listOf(
        "high_heart_rate" to "High Heart Rate",
        "low_heart_rate" to "Low Heart Rate",
        "high_activity" to "High Activity (Steps)",
        "low_activity" to "Low Activity (Steps)",
        "high_calories" to "High Calories",
        "low_calories" to "Low Calories",
        "high_stress" to "High Stress (Low HRV proxy)",
        "low_stress" to "Low Stress (High HRV proxy)"
    )

    var answered by remember { mutableStateOf(false) }
    var guessKey by remember { mutableStateOf<String?>(null) }
    var isCorrect by remember { mutableStateOf(false) }

    // IMPORTANT: this is now the foundation.lazy state (not deprecated)
    val listState = rememberScalingLazyListState()

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        if (!answered) {
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Health Alert!", textAlign = TextAlign.Center)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "What do you think that vibration meant?",
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }

                items(options.size) { idx ->
                    val (key, label) = options[idx]
                    Button(
                        onClick = {
                            guessKey = key
                            isCorrect = (key == actualKey)
                            answered = true

                            Log.d(
                                "ThesisLog",
                                "ALERT_RESPONSE alertId=$alertId guess=$key actual=$actualKey correct=$isCorrect value=$actualValue msg=$actualMessage"
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                        text = label,
                        fontSize = 10.sp
                    ) }

                    Spacer(Modifier.height(6.dp))
                }

                item {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        } else {
            val actualLabel = options.firstOrNull { it.first == actualKey }?.second ?: actualKey
            val guessedLabel = options.firstOrNull { it.first == guessKey }?.second
                ?: (guessKey ?: "unknown")

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(if (isCorrect) "✅ Correct!" else "❌ Not quite", textAlign = TextAlign.Center)

                Spacer(Modifier.height(8.dp))
                Text("You selected:", textAlign = TextAlign.Center)
                Text(guessedLabel, textAlign = TextAlign.Center)

                Spacer(Modifier.height(10.dp))
                Text("Actual alert:", textAlign = TextAlign.Center)
                Text(actualLabel, textAlign = TextAlign.Center)

                Spacer(Modifier.height(6.dp))
                if (actualMessage.isNotBlank()) Text(actualMessage, textAlign = TextAlign.Center)
                else Text("Value: ${"%.1f".format(actualValue)}", textAlign = TextAlign.Center)

                Spacer(Modifier.height(12.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            }
        }
    }
}

@Composable
fun RequestPermissionsAndStartServices() {
    val context = LocalContext.current

    val startedSteps = remember { mutableStateOf(false) }
    val startedCalories = remember { mutableStateOf(false) }
    val startedHr = remember { mutableStateOf(false) }
    val startedHrv = remember { mutableStateOf(false) }

    fun startStepsService() {
        if (startedSteps.value) return
        startedSteps.value = true
        ContextCompat.startForegroundService(context, Intent(context, ActivityService::class.java))
        Log.d(MAIN_TAG, "Started ActivityService (steps)")
    }

    fun startCaloriesService() {
        if (startedCalories.value) return
        startedCalories.value = true
        ContextCompat.startForegroundService(context, Intent(context, CaloriesService::class.java))
        Log.d(MAIN_TAG, "Started CaloriesService (calories)")
    }

    fun startHeartRateService() {
        if (startedHr.value) return
        startedHr.value = true
        ContextCompat.startForegroundService(context, Intent(context, HeartRateService::class.java))
        Log.d(MAIN_TAG, "Started HeartRateService (HR)")
    }

    fun startHRVService() {
        if (startedHrv.value) return
        startedHrv.value = true
        ContextCompat.startForegroundService(context, Intent(context, HRVService::class.java))
        Log.d(MAIN_TAG, "Started HRVService (HRV proxy)")
    }

    val activityPermission = Manifest.permission.ACTIVITY_RECOGNITION
    val postNotifPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS
        else null
    val heartRatePermission = "android.permission.health.READ_HEART_RATE"

    val permissions = buildList {
        add(activityPermission)
        postNotifPermission?.let { add(it) }
        add(heartRatePermission)
    }.toTypedArray()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val stepsGranted = results[activityPermission] == true
        val notificationsGranted = postNotifPermission?.let { results[it] == true } ?: true
        val hrGranted = results[heartRatePermission] == true

        if (stepsGranted) startStepsService() else Log.e(MAIN_TAG, "Steps permission denied.")
        if (!notificationsGranted) Log.e(MAIN_TAG, "Notification permission denied.")

        if (hrGranted) {
            startHeartRateService()
            startHRVService()
            startCaloriesService()
        } else {
            Log.e(MAIN_TAG, "HR permission denied.")
        }

        Log.d(MAIN_TAG, "Permission results: $results")
    }

    LaunchedEffect(Unit) {
        val stepsGranted =
            ContextCompat.checkSelfPermission(context, activityPermission) == PackageManager.PERMISSION_GRANTED

        val notificationsGranted =
            postNotifPermission?.let {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            } ?: true

        val hrGranted =
            ContextCompat.checkSelfPermission(context, heartRatePermission) == PackageManager.PERMISSION_GRANTED

        if (stepsGranted) startStepsService()
        if (hrGranted) {
            startHeartRateService()
            startHRVService()
            startCaloriesService()
        }

        if (!stepsGranted || !notificationsGranted || !hrGranted) {
            launcher.launch(permissions)
        }
    }
}

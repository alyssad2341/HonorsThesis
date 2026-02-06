package com.example.honorsthesisapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.honorsthesisapplication.data.source.ActivityService
import com.example.honorsthesisapplication.data.source.CaloriesService
import com.example.honorsthesisapplication.data.source.HRVService
import com.example.honorsthesisapplication.data.source.HeartRateService

private const val MAIN_TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)
        setContent { WearApp("Android") }
    }
}

@Composable
fun WearApp(greetingName: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        TimeText()
        Greeting(greetingName = greetingName)
        RequestPermissionsAndStartServices()
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
fun RequestPermissionsAndStartServices() {
    val context = LocalContext.current

    // Prevent re-starting services repeatedly across recompositions
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

    // Permissions
    val activityPermission = Manifest.permission.ACTIVITY_RECOGNITION

    val postNotifPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS
        else null

    // Keep as a string since it's not in Manifest constants
    val heartRatePermission = "android.permission.health.READ_HEART_RATE"

    val permissions = buildList {
        add(activityPermission)
        postNotifPermission?.let { add(it) }
        add(heartRatePermission) // HR + HRV + Calories (Health Services) rely on this in your app
    }.toTypedArray()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val stepsGranted = results[activityPermission] == true
        val notificationsGranted = postNotifPermission?.let { results[it] == true } ?: true
        val hrGranted = results[heartRatePermission] == true

        if (stepsGranted) startStepsService()
        else Log.e(MAIN_TAG, "Steps permission denied (ActivityService not started).")

        if (!notificationsGranted) {
            Log.e(MAIN_TAG, "Notification permission denied (alerts may not show).")
        }

        // Start Health-based services when HR permission is granted
        if (hrGranted) {
            startHeartRateService()
            startHRVService()
            startCaloriesService()
        } else {
            Log.e(
                MAIN_TAG,
                "HR permission denied (HeartRateService/HRVService/CaloriesService not started)."
            )
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

        // Start services that are already allowed
        if (stepsGranted) startStepsService()

        // Health-based services share the same permission in your approach
        if (hrGranted) {
            startHeartRateService()
            startHRVService()
            startCaloriesService()
        }

        // Request anything missing
        if (!stepsGranted || !notificationsGranted || !hrGranted) {
            launcher.launch(permissions)
        }
    }
}

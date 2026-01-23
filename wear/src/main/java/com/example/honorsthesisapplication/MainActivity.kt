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
import com.example.honorsthesisapplication.data.source.HeartRateService

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

    fun startStepsService() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, ActivityService::class.java)
        )
        Log.d("MainActivity", "Started ActivityService (steps)")
    }

    fun startHeartRateService() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, HeartRateService::class.java)
        )
        Log.d("MainActivity", "Started HeartRateService (HR)")
    }

    // Only runtime permissions we truly need here
    val permissions = buildList {
        add(Manifest.permission.ACTIVITY_RECOGNITION) // steps sensor access

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Optional: keep this if you want, but don't let it block steps.
        add("android.permission.health.READ_HEART_RATE")
    }.toTypedArray()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->

        val stepsGranted = results[Manifest.permission.ACTIVITY_RECOGNITION] == true

        val notificationsGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                results[Manifest.permission.POST_NOTIFICATIONS] == true
            else true

        val hrGranted = results["android.permission.health.READ_HEART_RATE"] == true

        // Start what we can
        if (stepsGranted) startStepsService() else Log.e("MainActivity", "Steps permission denied.")
        if (notificationsGranted) {
            // nothing to start; just means alerts can show
        } else {
            Log.e("MainActivity", "Notification permission denied (alerts may not show).")
        }

        // HR: only start if granted (or you can start regardless if it works on your watch)
        if (hrGranted) startHeartRateService()
        else Log.e("MainActivity", "HR permission denied (HR service not started).")

        Log.d("MainActivity", "Permission results: $results")
    }

    LaunchedEffect(Unit) {
        val stepsGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

        val notificationsGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true

        val hrGranted = ContextCompat.checkSelfPermission(
            context,
            "android.permission.health.READ_HEART_RATE"
        ) == PackageManager.PERMISSION_GRANTED

        // Start services that are already allowed
        if (stepsGranted) startStepsService()
        if (hrGranted) startHeartRateService()

        // If anything is missing that we care about, request all at once.
        // (steps is the important one; notifications/hr are secondary)
        if (!stepsGranted || !notificationsGranted || !hrGranted) {
            launcher.launch(permissions)
        }
    }
}

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
import com.example.honorsthesisapplication.data.source.HeartRateService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            WearApp("Android")
        }
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

        // This launches the permission flow
        RequestForegroundServicePermission()
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
fun RequestForegroundServicePermission() {
    val context = LocalContext.current

    // API 36 uses granular Health Connect permissions.
    // Galaxy Watch 7 (API 34/35) uses BODY_SENSORS.
    val sensorPermission = if (Build.VERSION.SDK_INT >= 36) {
        "android.permission.health.READ_HEART_RATE"
    } else {
        Manifest.permission.BODY_SENSORS
    }

    val permissions = arrayOf(
        sensorPermission,
        Manifest.permission.POST_NOTIFICATIONS
    )

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val sensorGranted = results[sensorPermission] ?: false
        val notificationsGranted = results[Manifest.permission.POST_NOTIFICATIONS] ?: false

        if (sensorGranted) {
            Log.d("MainActivity", "Sensor permission granted. Starting Service...")
            context.startForegroundService(Intent(context, HeartRateService::class.java))
        } else {
            Log.e("MainActivity", "Sensor permission denied. Service cannot track HR.")
        }
    }

    LaunchedEffect(Unit) {
        val hasSensor = ContextCompat.checkSelfPermission(context, sensorPermission) == PackageManager.PERMISSION_GRANTED
        val hasNotifications = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        if (hasSensor && hasNotifications) {
            // Permissions already exist, start the service
            context.startForegroundService(Intent(context, HeartRateService::class.java))
        } else {
            // Trigger the system dialogs
            launcher.launch(permissions)
        }
    }
}
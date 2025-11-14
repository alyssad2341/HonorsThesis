package com.example.honorsthesisapplication.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.honorsthesisapplication.ui.controller.SmartwatchController

@Composable
fun PhysEventDetailComposable(
    eventId: String?,
    watchController: SmartwatchController
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Event: $eventId")

        Spacer(Modifier.height(20.dp))

        Button(onClick = {
            watchController.sendMessageToWatch("/vibrate")
        }) {
            Text("Send Vibration")
        }
    }
}
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
import com.example.honorsthesisapplication.data.model.PhysEventModel
import com.example.honorsthesisapplication.ui.controller.SmartwatchController

@Composable
fun PhysEventDetailComposable(
    aEvent: PhysEventModel,
    aWatchController: SmartwatchController
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Event: ${aEvent.title}")

        Spacer(Modifier.height(20.dp))

        Button(onClick = {
            aWatchController.sendMessageToWatch(
                timings = longArrayOf(0, 300, 100, 300, 100, 600),
                amplitudes = intArrayOf(0, 255, 0, 255, 0, 255),
                repeat = -1
            )
        }) {
            Text("Send Vibration")
        }
    }
}
package com.example.honorsthesisapplication

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlertButtonView(
                onSendAlert = { sendMessageToWatch("/vibrate") }
            )
        }
    }

    private fun sendMessageToWatch(path: String) {
        Thread {
            val nodes = Tasks.await(
                Wearable.getNodeClient(this).connectedNodes
            )
            Log.d("Node: ", "here")
            for(node in nodes) {
                Log.d("Node: ", node.id)
                Wearable.getMessageClient(this)
                    .sendMessage(node.id, path, null)
            }
        }.start()
    }

}

@Composable
fun AlertButtonView(onSendAlert: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Button( onClick = { onSendAlert() }) {
            Text("Send Alert")
        }
    }
}

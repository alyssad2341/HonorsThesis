package com.example.honorsthesisapplication.ui.controller

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable

class SmartwatchController (private val context: Context) {

    fun sendMessageToWatch(
        timings: LongArray,
        amplitudes: IntArray,
        repeat: Int = -1
    ) {
        Thread {
            val json = """
                {
                    "timings": ${timings.joinToString(prefix="[", postfix="]")},
                    "amplitudes": ${amplitudes.joinToString(prefix="[", postfix="]")},
                    "repeat": $repeat
                }
            """.trimIndent()

            val bytes = json.toByteArray(Charsets.UTF_8)

            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
            Log.d("SmartwatchController", "Nodes found: $nodes")
            for (node in nodes) {
                Log.d("SmartwatchController", "Sending message to ${node.id}")
                Wearable.getMessageClient(context).sendMessage(node.id, "/vibrate_pattern", bytes)
            }
        }.start()
    }

}
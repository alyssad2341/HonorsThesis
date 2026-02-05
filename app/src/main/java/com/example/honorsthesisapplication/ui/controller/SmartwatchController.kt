package com.example.honorsthesisapplication.ui.controller

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.example.honorsthesisapplication.data.model.VibrationModel
import com.example.honorsthesisapplication.data.model.VibrationPatterns
import com.example.honorsthesisapplication.data.model.PhysEventModel
import com.example.honorsthesisapplication.data.model.PhysSubEventModel

class SmartwatchController (private val context: Context) {

    fun sendVibrationToWatch(
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
                Log.d("SmartwatchController", "Sending vibration to ${node.id}")
                Wearable.getMessageClient(context).sendMessage(node.id, "/vibrate_pattern", bytes)
            }
        }.start()
    }

    fun sendAlertInfoToWatch(
        subEvent: PhysSubEventModel
    ) {
        val pattern = VibrationPatterns.getById(subEvent.selectedVibrationId)
        val timings = pattern?.timings ?: longArrayOf()
        val amplitudes = pattern?.amplitudes ?: intArrayOf()
        val repeat = -1

        Thread {
            val json = """
            {
                "subEventId": "${subEvent.id}",
                "enabled": ${subEvent.enabled},
                "threshold": ${subEvent.setThreshold},
                "notificationFrequency": ${subEvent.notificationFrequency.millis},
                "vibration": {
                    "timings": ${timings.joinToString(prefix = "[", postfix = "]")},
                    "amplitudes": ${amplitudes.joinToString(prefix = "[", postfix = "]")},
                    "repeat": $repeat
                }
            }
        """.trimIndent()

            val bytes = json.toByteArray(Charsets.UTF_8)

            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
            Log.d("SmartwatchController", "Nodes found: $nodes")
            for (node in nodes) {
                Log.d("SmartwatchController", "Sending alert info to ${node.id}")
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, "/alert_info", bytes)
            }
        }.start()
    }

}
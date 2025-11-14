package com.example.honorsthesisapplication.ui.controller

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable

class SmartwatchController (private val context: Context) {

    fun sendMessageToWatch(path: String) {
        Thread {
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
            Log.d("SmartwatchController", "Connected Wearable Nodes: ${nodes.size}")
            for (node in nodes) {
                Log.d("SmartwatchController", "Sending message to ${node.id}")
                Wearable.getMessageClient(context).sendMessage(node.id, path, null)
            }
        }.start()
    }

}
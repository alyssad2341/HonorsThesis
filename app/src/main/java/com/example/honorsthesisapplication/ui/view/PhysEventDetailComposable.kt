package com.example.honorsthesisapplication.ui.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.honorsthesisapplication.data.model.PhysEventModel
import com.example.honorsthesisapplication.data.model.PhysSubEventModel
import com.example.honorsthesisapplication.ui.controller.SmartwatchController
import com.example.honorsthesisapplication.data.model.VibrationModel
import com.example.honorsthesisapplication.data.model.VibrationPatterns
import com.example.honorsthesisapplication.ui.viewmodel.PhysEventViewModel
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.graphics.graphicsLayer


@Composable
fun PhysEventDetailComposable(
    aEvent: PhysEventModel,
    aOnSelectVibration: (PhysSubEventModel) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "Event: ${aEvent.title}",
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Set Your Custom Vibration Alerts!",
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 18.dp)
            )

            LazyColumn {
                items(aEvent.subEvents) { subEvent ->
                    PhysSubEventCard(
                        aEvent = subEvent,
                        aOutlineColor = aEvent.color,
                        aOnEditVibration = { aOnSelectVibration(subEvent) }
                    )
                }
            }
        }
    }
}

@Composable
fun PhysSubEventCard(
    aEvent: PhysSubEventModel,
    aOutlineColor: Color,
    aOnEditVibration: () -> Unit
) {

    var enabled by remember { mutableStateOf(aEvent.enabled) }
    var thresholdValue by remember { mutableStateOf(aEvent.threshold ?: 0f) }
    var expanded by remember { mutableStateOf(false) }

    val disabledAlpha = if (enabled) 1f else 0.6f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(3.dp, aOutlineColor),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
        ) {

            /* ---------- Header ---------- */
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        aEvent.enabled = it
                    }
                )

                Text(
                    text = aEvent.title,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            /* ---------- Body (DIMMABLE) ---------- */
            Column(
                modifier = Modifier.graphicsLayer {
                    alpha = if (enabled) 1f else 0.45f
                }
            ) {

                /* ---------- Threshold ---------- */
                Text("Threshold")

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = thresholdValue.toInt().toString(),
                        onValueChange = {
                            it.toFloatOrNull()?.let { value ->
                                thresholdValue = value
                                aEvent.threshold = value
                            }
                        },
                        enabled = enabled,
                        modifier = Modifier.width(90.dp),
                        singleLine = true
                    )

                    Spacer(Modifier.width(12.dp))

                    androidx.compose.material3.Slider(
                        value = thresholdValue,
                        onValueChange = {
                            thresholdValue = it
                            aEvent.threshold = it
                        },
                        enabled = enabled,
                        valueRange = 0f..200f,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(16.dp))

                /* ---------- Selected Vibration ---------- */
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Selected Vibration Pattern:")
                        Text(
                            text = aEvent.selectedVibration
                                ?.metaphors
                                ?.joinToString()
                                ?: "None",
                            fontSize = 14.sp
                        )
                    }

                    aEvent.selectedVibration?.let {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(it.imagePath),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp)
                        )
                    }

                    Button(
                        onClick = aOnEditVibration,
                        enabled = enabled
                    ) {
                        Text("Edit / Update")
                    }
                }

                Spacer(Modifier.height(16.dp))

                /* ---------- Notification Frequency ---------- */
                Text("Notification Frequency:")

                Box {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) { expanded = true },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Text(
                            text = aEvent.notificationFrequency,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = expanded && enabled,
                        onDismissRequest = { expanded = false }
                    ) {
                        listOf(
                            "Every 30 sec",
                            "Every 1 min",
                            "Every 5 min"
                        ).forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    aEvent.notificationFrequency = option
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.example.honorsthesisapplication.ui.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.honorsthesisapplication.data.model.NotificationFrequency
import com.example.honorsthesisapplication.data.model.PhysEventModel
import com.example.honorsthesisapplication.data.model.PhysSubEventModel
import com.example.honorsthesisapplication.data.model.VibrationPatterns
import com.example.honorsthesisapplication.ui.viewmodel.PhysEventViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhysEventDetailComposable(
    aViewModel: PhysEventViewModel,
    aEvent: PhysEventModel,
    aOnSelectVibration: (PhysSubEventModel) -> Unit,
    aOnBack: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = aEvent.title) },
                navigationIcon = {
                    IconButton(onClick = aOnBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = "Set Your Custom Vibration Alerts!",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 18.dp)
                )

                LazyColumn(
                    Modifier.padding(bottom = 50.dp)
                ) {
                    items(aEvent.subEvents) { subEvent ->
                        PhysSubEventCard(
                            aViewModel = aViewModel,
                            aEvent = subEvent,
                            aOutlineColor = aEvent.color,
                            aOnEditVibration = { aOnSelectVibration(subEvent) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PhysSubEventCard(
    aViewModel: PhysEventViewModel,
    aEvent: PhysSubEventModel,
    aOutlineColor: Color,
    aOnEditVibration: () -> Unit
) {
    var enabled by remember { mutableStateOf(aEvent.enabled) }

    // Use initialThreshold as the default if threshold hasn't been set yet.
    var thresholdValue by remember {
        mutableStateOf(aEvent.setThreshold)
    }

    var expanded by remember { mutableStateOf(false) }

    val selectedVibration = VibrationPatterns.getById(aEvent.selectedVibrationId)
    val disabledAlpha = if (enabled) 1f else 0.6f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(3.dp, aOutlineColor),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            /* ---------- Header ---------- */
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        aEvent.enabled = it
                        aViewModel.saveSubEvent(aEvent)
                    }
                )

                Text(
                    text = aEvent.title,
                    fontSize = 16.sp
                )
            }

            // Description label (requested)
            Spacer(Modifier.height(6.dp))
            Text(
                text = aEvent.description,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            /* ---------- Body (DIMMABLE) ---------- */
            Column(
                modifier = Modifier.graphicsLayer {
                    alpha = if (enabled) 1f else disabledAlpha
                }
            ) {

                /* ---------- Threshold ---------- */
                Text("Threshold")

                Spacer(Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {

                    // If thresholds are whole-number-ish (steps/BPM), show an int.
                    // If they’re decimals (HRV proxy), show one decimal.
                    val isWholeNumberRange =
                        (aEvent.minThreshold % 1f == 0f) && (aEvent.maxThreshold % 1f == 0f)

                    val displayText = if (isWholeNumberRange) {
                        thresholdValue.roundToInt().toString()
                    } else {
                        String.format("%.1f", thresholdValue)
                    }

                    TextField(
                        value = displayText,
                        onValueChange = { raw ->
                            raw.toFloatOrNull()?.let { value ->
                                val clamped = value.coerceIn(aEvent.minThreshold, aEvent.maxThreshold)
                                thresholdValue = clamped
                                aEvent.setThreshold = clamped
                                aViewModel.saveSubEvent(aEvent)
                            }
                        },
                        enabled = enabled,
                        modifier = Modifier.width(90.dp),
                        singleLine = true
                    )

                    Spacer(Modifier.width(12.dp))

                    androidx.compose.material3.Slider(
                        value = thresholdValue.coerceIn(aEvent.minThreshold, aEvent.maxThreshold),
                        onValueChange = { v ->
                            val clamped = v.coerceIn(aEvent.minThreshold, aEvent.maxThreshold)
                            thresholdValue = clamped
                            aEvent.setThreshold = clamped
                            aViewModel.saveSubEvent(aEvent)
                        },
                        enabled = enabled,
                        valueRange = aEvent.minThreshold..aEvent.maxThreshold,
                        modifier = Modifier
                            .height(24.dp)
                            .weight(1f)
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
                            text = selectedVibration?.metaphors?.joinToString() ?: "None",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.width(5.dp))

                    selectedVibration?.let {
                        androidx.compose.foundation.Image(
                            painter = painterResource(it.imagePath),
                            contentDescription = null,
                            modifier = Modifier.size(100.dp)
                        )
                    }

                    Spacer(Modifier.width(5.dp))

                    Button(
                        onClick = aOnEditVibration,
                        enabled = enabled,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Edit/Update", fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))

                /* ---------- Notification Frequency ---------- */
                Text("Notification Frequency:")

                Spacer(Modifier.height(5.dp))

                Box {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) { expanded = true },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Text(
                            text = aEvent.notificationFrequency.label,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = expanded && enabled,
                        onDismissRequest = { expanded = false }
                    ) {
                        NotificationFrequency.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    aEvent.notificationFrequency = option
                                    expanded = false
                                    aViewModel.saveSubEvent(aEvent)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

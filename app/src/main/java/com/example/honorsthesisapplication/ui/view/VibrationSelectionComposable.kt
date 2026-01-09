package com.example.honorsthesisapplication.ui.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.honorsthesisapplication.data.model.PhysEventModel
import com.example.honorsthesisapplication.ui.controller.SmartwatchController
import com.example.honorsthesisapplication.data.model.VibrationModel
import com.example.honorsthesisapplication.data.model.VibrationPatterns

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StringDropdown(
    label: String,
    items: List<String>,
    selected: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var textFieldWidth by remember { mutableStateOf(0) }

    Box(modifier = modifier) {
        TextField(
            value = selected ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontSize = 12.sp) },
            textStyle = TextStyle(fontSize = 12.sp),
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    textFieldWidth = coords.size.width
                }
                .clickable { expanded = !expanded }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(with(LocalDensity.current) { textFieldWidth.toDp() })
        ) {
            DropdownMenuItem(
                text = { Text("Any", fontSize = 12.sp) },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )

            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item, fontSize = 12.sp) },
                    onClick = {
                        onSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun VibrationSelectionComposable(
    aEvent: PhysEventModel,
    aWatchController: SmartwatchController,
    aOnVibrationSelected: (VibrationModel) -> Unit
) {

    val allPatterns = VibrationPatterns.all
    val allSensations = allPatterns.flatMap { it.sensationTags }.distinct()
    val allEmotions = allPatterns.flatMap { it.emotionTags }.distinct()
    val allMetaphors = allPatterns.flatMap { it.metaphors }.distinct()
    val allUsages = allPatterns.flatMap { it.usageExamples }.distinct()

    var selectedSensation by remember { mutableStateOf<String?>(null) }
    var selectedEmotion by remember { mutableStateOf<String?>(null) }
    var selectedMetaphor by remember { mutableStateOf<String?>(null) }
    var selectedUsage by remember { mutableStateOf<String?>(null) }

    // filtering
    val filteredPatterns = remember(
        selectedSensation,
        selectedEmotion,
        selectedMetaphor,
        selectedUsage,
        allPatterns
    ) {
        allPatterns.filter { pattern ->
            (selectedSensation == null || selectedSensation in pattern.sensationTags) &&
                    (selectedEmotion == null || selectedEmotion in pattern.emotionTags) &&
                    (selectedMetaphor == null || selectedMetaphor in pattern.metaphors) &&
                    (selectedUsage == null || selectedUsage in pattern.usageExamples)
        }
    }
    Scaffold() { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Event: ${aEvent.title}", style = MaterialTheme.typography.headlineSmall)

            Spacer(Modifier.height(20.dp))

            //--------------------------------------------
            // FILTER DROPDOWNS
            // -------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StringDropdown(
                    label = "Sensation Tag",
                    items = allSensations,
                    selected = selectedSensation,
                    onSelected = { selectedSensation = it },
                    modifier = Modifier.width(180.dp)
                        .height(80.dp)
                )

                Spacer(Modifier.height(10.dp))

                StringDropdown(
                    label = "Emotion Tag",
                    items = allEmotions,
                    selected = selectedEmotion,
                    onSelected = { selectedEmotion = it },
                    modifier = Modifier.width(180.dp)
                        .height(80.dp)
                )

                Spacer(Modifier.height(10.dp))

                StringDropdown(
                    label = "Metaphor",
                    items = allMetaphors,
                    selected = selectedMetaphor,
                    onSelected = { selectedMetaphor = it },
                    modifier = Modifier.width(180.dp)
                        .height(80.dp)
                )

                Spacer(Modifier.height(10.dp))

                StringDropdown(
                    label = "Usage Example",
                    items = allUsages,
                    selected = selectedUsage,
                    onSelected = { selectedUsage = it },
                    modifier = Modifier.width(180.dp)
                                        .height(80.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "Matching Patterns (${filteredPatterns.size})",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(10.dp))

            // list of matching patterns
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredPatterns) { pattern ->
                    PatternItem(
                        pattern = pattern,
                        onSend = {
                            aWatchController.sendMessageToWatch(
                                timings = pattern.timings,
                                amplitudes = pattern.amplitudes,
                                repeat = -1
                            )
                            aOnVibrationSelected(pattern)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PatternItem(
    pattern: VibrationModel,
    onSend: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            //Text("Metaphors: ${pattern.metaphors.joinToString()}", style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = pattern.imagePath),
                    contentDescription = "Vibration pattern visualization",
                    modifier = Modifier
                        .width(150.dp)
                        .padding(end = 8.dp),
                    contentScale = ContentScale.Fit
                )

                Text(
                    text = "Metaphors: ${pattern.metaphors.joinToString()}",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("Sensation: ")
                    }
                    append(pattern.sensationTags.joinToString())
                }
            )

            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("Emotion: ")
                    }
                    append(pattern.emotionTags.joinToString())
                }
            )

            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("Usage: ")
                    }
                    append(pattern.usageExamples.joinToString())
                }
            )

            Spacer(Modifier.height(10.dp))

            Button(onClick = onSend) {
                Text("Select Pattern")
            }
        }
    }
}
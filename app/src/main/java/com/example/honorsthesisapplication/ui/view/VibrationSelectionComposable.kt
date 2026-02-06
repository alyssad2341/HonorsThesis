package com.example.honorsthesisapplication.ui.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.honorsthesisapplication.data.model.PhysSubEventModel
import com.example.honorsthesisapplication.data.model.VibrationModel
import com.example.honorsthesisapplication.data.model.VibrationPatterns
import com.example.honorsthesisapplication.ui.controller.SmartwatchController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibrationSelectionComposable(
    aWatchController: SmartwatchController,
    aSubEvent: PhysSubEventModel,
    aOnVibrationSelected: (VibrationModel) -> Unit,
    aOnBack: () -> Unit
) {
    val allPatterns = VibrationPatterns.all
    val allSensations = allPatterns.flatMap { it.sensationTags }.distinct()
    val allEmotions = allPatterns.flatMap { it.emotionTags }.distinct()
    val allMetaphors = allPatterns.flatMap { it.metaphors }.distinct()
    val allUsages = allPatterns.flatMap { it.usageExamples }.distinct()

    var selectedSensations by remember { mutableStateOf(setOf<String>()) }
    var selectedEmotions by remember { mutableStateOf(setOf<String>()) }
    var selectedMetaphors by remember { mutableStateOf(setOf<String>()) }
    var selectedUsages by remember { mutableStateOf(setOf<String>()) }

    var showFilters by remember { mutableStateOf(false) }

    val filteredPatterns = remember(
        selectedSensations,
        selectedEmotions,
        selectedMetaphors,
        selectedUsages,
        allPatterns
    ) {
        allPatterns.filter { pattern ->
            selectedSensations.all { it in pattern.sensationTags } &&
                    selectedEmotions.all { it in pattern.emotionTags } &&
                    selectedMetaphors.all { it in pattern.metaphors } &&
                    selectedUsages.all { it in pattern.usageExamples }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Event: ${aSubEvent.title}", style = MaterialTheme.typography.headlineSmall) },
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(bottom = 20.dp, start = 20.dp, end = 20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SelectedFiltersTextBar(
                sensations = selectedSensations,
                emotions = selectedEmotions,
                metaphors = selectedMetaphors,
                usages = selectedUsages,
                onOpenFilters = { showFilters = true }
            )

            if (showFilters) {
                FiltersDialog(
                    allSensations = allSensations,
                    allEmotions = allEmotions,
                    allMetaphors = allMetaphors,
                    allUsages = allUsages,
                    initialSensations = selectedSensations,
                    initialEmotions = selectedEmotions,
                    initialMetaphors = selectedMetaphors,
                    initialUsages = selectedUsages,
                    onDismiss = { showFilters = false },
                    onApply = { s, e, m, u ->
                        selectedSensations = s
                        selectedEmotions = e
                        selectedMetaphors = m
                        selectedUsages = u
                        showFilters = false
                    }
                )
            }

            Spacer(Modifier.padding(top = 20.dp))

            Text(
                "Matching Patterns (${filteredPatterns.size})",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.padding(top = 10.dp))

            val listState = rememberLazyListState()
            val canScrollUp by remember { derivedStateOf { listState.canScrollBackward } }
            val canScrollDown by remember { derivedStateOf { listState.canScrollForward } }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredPatterns) { pattern ->
                        PatternItem(
                            pattern = pattern,
                            onSend = {
                                aWatchController.sendVibrationToWatch(
                                    timings = pattern.timings,
                                    amplitudes = pattern.amplitudes,
                                    repeat = -1
                                )
                                aOnVibrationSelected(pattern)
                            }
                        )
                    }
                }

                ScrollFadeOverlays(
                    showTop = canScrollUp,
                    showBottom = canScrollDown,
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.matchParentSize(),
                    fadeHeight = 40.dp
                )
            }
        }
    }
}

@Composable
private fun SelectedFiltersTextBar(
    sensations: Set<String>,
    emotions: Set<String>,
    metaphors: Set<String>,
    usages: Set<String>,
    onOpenFilters: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {

        TextButton(onClick = onOpenFilters) {
            Text("Edit Filters", fontSize = 15.sp)
        }

        Spacer(Modifier.padding(top = 2.dp))

        SelectedFiltersRow(
            metaphors = metaphors,
            sensations = sensations,
            emotions = emotions,
            usages = usages
        )
    }
}

@Composable
private fun SelectedFiltersRow(
    metaphors: Set<String>,
    sensations: Set<String>,
    emotions: Set<String>,
    usages: Set<String>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SelectedInlineItem(
            label = "Metaphor",
            values = metaphors,
            modifier = Modifier.weight(1f)
        )
        SelectedInlineItem(
            label = "Sensation",
            values = sensations,
            modifier = Modifier.weight(1f)
        )
        SelectedInlineItem(
            label = "Emotion",
            values = emotions,
            modifier = Modifier.weight(1f)
        )
        SelectedInlineItem(
            label = "Usage",
            values = usages,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SelectedInlineItem(
    label: String,
    values: Set<String>,
    modifier: Modifier = Modifier
) {
    val text = if (values.isEmpty()) "Any" else values.joinToString()

    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append("$label\n")
            }
            append(text)
        },
        modifier = modifier,
        fontSize = 12.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun FiltersDialog(
    allSensations: List<String>,
    allEmotions: List<String>,
    allMetaphors: List<String>,
    allUsages: List<String>,
    initialSensations: Set<String>,
    initialEmotions: Set<String>,
    initialMetaphors: Set<String>,
    initialUsages: Set<String>,
    onDismiss: () -> Unit,
    onApply: (Set<String>, Set<String>, Set<String>, Set<String>) -> Unit
) {
    var tmpSensations by remember { mutableStateOf(initialSensations) }
    var tmpEmotions by remember { mutableStateOf(initialEmotions) }
    var tmpMetaphors by remember { mutableStateOf(initialMetaphors) }
    var tmpUsages by remember { mutableStateOf(initialUsages) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filters") },
        text = {
            val scrollState = rememberScrollState()

            val canScrollUp by remember { derivedStateOf { scrollState.value > 0 } }
            val canScrollDown by remember { derivedStateOf { scrollState.value < scrollState.maxValue } }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    MultiSelectSection(
                        title = "Metaphor",
                        options = allMetaphors,
                        selected = tmpMetaphors,
                        onToggle = { v ->
                            tmpMetaphors = if (v in tmpMetaphors) tmpMetaphors - v else tmpMetaphors + v
                        },
                        onClear = { tmpMetaphors = emptySet() }
                    )

                    MultiSelectSection(
                        title = "Sensation",
                        options = allSensations,
                        selected = tmpSensations,
                        onToggle = { v ->
                            tmpSensations = if (v in tmpSensations) tmpSensations - v else tmpSensations + v
                        },
                        onClear = { tmpSensations = emptySet() }
                    )

                    MultiSelectSection(
                        title = "Emotion",
                        options = allEmotions,
                        selected = tmpEmotions,
                        onToggle = { v ->
                            tmpEmotions = if (v in tmpEmotions) tmpEmotions - v else tmpEmotions + v
                        },
                        onClear = { tmpEmotions = emptySet() }
                    )

                    MultiSelectSection(
                        title = "Usage",
                        options = allUsages,
                        selected = tmpUsages,
                        onToggle = { v ->
                            tmpUsages = if (v in tmpUsages) tmpUsages - v else tmpUsages + v
                        },
                        onClear = { tmpUsages = emptySet() }
                    )
                }

                ScrollFadeOverlays(
                    showTop = canScrollUp,
                    showBottom = canScrollDown,
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.matchParentSize(),
                    fadeHeight = 50.dp
                )
            }
        },
        confirmButton = {
            Button(onClick = { onApply(tmpSensations, tmpEmotions, tmpMetaphors, tmpUsages) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun MultiSelectSection(
    title: String,
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 14.sp
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onClear,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 8.dp,
                    vertical = 0.dp
                )
            ) {
                Text("Clear", fontSize = 12.sp)
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { opt ->
                FilterChip(
                    selected = opt in selected,
                    onClick = { onToggle(opt) },
                    label = { Text(opt, fontSize = 12.sp) }
                )
            }
        }
    }
}

@Composable
private fun ScrollFadeOverlays(
    showTop: Boolean,
    showBottom: Boolean,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    fadeHeight: Dp
) {
    Box(modifier = modifier) {
        if (showTop) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(fadeHeight)
                    .drawWithContent {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    backgroundColor,
                                    Color.Transparent
                                )
                            )
                        )
                    }
            )
        }

        if (showBottom) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(fadeHeight)
                    .drawWithContent {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    backgroundColor
                                )
                            )
                        )
                    }
            )
        }
    }
}


private fun Modifier.fadeBrush(brush: Brush): Modifier =
    this.drawWithContent {
        drawContent()
        drawRect(brush = brush)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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

            Spacer(Modifier.padding(top = 6.dp))

            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Sensation: ") }
                    append(pattern.sensationTags.joinToString())
                }
            )

            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Emotion: ") }
                    append(pattern.emotionTags.joinToString())
                }
            )

            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Usage: ") }
                    append(pattern.usageExamples.joinToString())
                }
            )

            Spacer(Modifier.padding(top = 10.dp))

            Button(onClick = onSend) {
                Text("Select Pattern")
            }
        }
    }
}
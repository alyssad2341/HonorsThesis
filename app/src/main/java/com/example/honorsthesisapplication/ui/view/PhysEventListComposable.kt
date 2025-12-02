package com.example.honorsthesisapplication.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.honorsthesisapplication.ui.viewmodel.PhysEventViewModel
import com.example.honorsthesisapplication.data.model.PhysEventModel


@Composable
fun PhysEventListComposable(
    aViewModel: PhysEventViewModel = viewModel(),
    aOnEventSelected: (PhysEventModel) -> Unit
) {
    val eventList by aViewModel.eventList.collectAsState()

    Box (
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 100.dp)
        ) {
            Text(
                text = "Select Your Health Alerts",
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 30.dp)
            )

            LazyColumn {
                items(eventList) { event ->
                    PhysEventCard(event = event, onClick = { aOnEventSelected(event) })
                }
            }
        }
    }
}

@Composable
fun PhysEventCard(
    event: PhysEventModel,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors( containerColor = Color.White ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
        ) {
            // Left color bar
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .background(event.color)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // PhysEvent title text
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                Text(
                    text = event.title,
                    fontSize = 18.sp,
                    color = Color.Black
                )
            }

            // Arrow icon
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Go to details",
                tint = Color.Gray,
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.CenterVertically)
            )
        }
    }
}
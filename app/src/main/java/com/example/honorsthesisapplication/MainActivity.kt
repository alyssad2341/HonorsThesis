package com.example.honorsthesisapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.honorsthesisapplication.ui.controller.SmartwatchController
import com.example.honorsthesisapplication.ui.view.PhysEventDetailComposable
import com.example.honorsthesisapplication.ui.view.PhysEventListComposable
import com.example.honorsthesisapplication.ui.view.VibrationSelectionComposable
import com.example.honorsthesisapplication.ui.viewmodel.PhysEventViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val theWatchController = SmartwatchController(this)
        setContent {
            MainAppNavigation(aWatchController = theWatchController)
        }
    }
}

@Composable
fun MainAppNavigation(aWatchController: SmartwatchController) {
    val theNavController = rememberNavController()
    val theViewModel: PhysEventViewModel = viewModel()

    NavHost(
        navController = theNavController,
        startDestination = "phys_event_list"
    ) {
        composable(
            route = "phys_event_list",
            enterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) }
        ) {
            PhysEventListComposable(
                aOnEventSelected = { event ->
                    theViewModel.selectedEvent = event
                    theNavController.navigate("phys_event_detail")
                }
            )
        }

        composable(
            route = "phys_event_detail",
            enterTransition = {
                when (initialState.destination.route) {
                    "phys_event_list" ->
                        slideInHorizontally(
                            initialOffsetX = { it }, // from right
                            animationSpec = tween(300)
                        )
                    "vibration_selection" ->
                        slideInHorizontally(
                            initialOffsetX = { -it }, // from left
                            animationSpec = tween(300)
                        )
                    else -> null
                }
            },
            exitTransition = {
                when (targetState.destination.route) {
                    "phys_event_list" ->
                        slideOutHorizontally(
                            targetOffsetX = { it }, // to right
                            animationSpec = tween(300)
                        )
                    "vibration_selection" ->
                        slideOutHorizontally(
                            targetOffsetX = { -it }, // to left
                            animationSpec = tween(300)
                        )
                    else -> null
                }
            },
            popEnterTransition = {
                when (initialState.destination.route) {
                    "vibration_selection" ->
                        slideInHorizontally(
                            initialOffsetX = { -it }, // from left
                            animationSpec = tween(300)
                        )
                    else -> null
                }
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it }, // back to list
                    animationSpec = tween(300)
                )
            }
        ) {
            val theEvent = theViewModel.selectedEvent
            theEvent?.let {
                PhysEventDetailComposable(
                    aEvent = it,
                    aOnSelectVibration = { theNavController.navigate("vibration_selection") }
                )
            }
        }

        composable(
            route = "vibration_selection",
            enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
        ) {
            val theEvent = theViewModel.selectedEvent
            theEvent?.let {
                VibrationSelectionComposable(
                    aWatchController = aWatchController,
                    aEvent = it,
                    aOnVibrationSelected = { pattern ->
                        it.selectedVibration = pattern
                    }
                )
            }
        }
    }
}

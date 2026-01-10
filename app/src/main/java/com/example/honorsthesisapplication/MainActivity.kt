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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.honorsthesisapplication.ui.controller.SmartwatchController
import com.example.honorsthesisapplication.ui.view.PhysEventDetailComposable
import com.example.honorsthesisapplication.ui.view.PhysEventListComposable
import com.example.honorsthesisapplication.ui.view.VibrationSelectionComposable
import com.example.honorsthesisapplication.ui.viewmodel.PhysEventViewModel
import com.example.honorsthesisapplication.ui.viewmodel.PhysEventViewModelFactory

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
    val context = LocalContext.current
    val theViewModel: PhysEventViewModel = viewModel(
        factory = PhysEventViewModelFactory(context)
    )

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
                aViewModel = theViewModel,
                aOnEventSelected = { subEvent ->
                    theViewModel.selectedEvent = subEvent
                    theNavController.navigate("phys_event_detail"){
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = "phys_event_detail",
            enterTransition = {
                when (initialState.destination.route) {
                    "phys_event_list" ->
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(300)
                        )
                    "vibration_selection" ->
                        slideInHorizontally(
                            initialOffsetX = { -it },
                            animationSpec = tween(300)
                        )
                    else -> null
                }
            },
            exitTransition = {
                when (targetState.destination.route) {
                    "phys_event_list" ->
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(300)
                        )
                    "vibration_selection" ->
                        slideOutHorizontally(
                            targetOffsetX = { -it },
                            animationSpec = tween(300)
                        )
                    else -> null
                }
            },
            popEnterTransition = {
                when (initialState.destination.route) {
                    "vibration_selection" ->
                        slideInHorizontally(
                            initialOffsetX = { -it },
                            animationSpec = tween(300)
                        )
                    else -> null
                }
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300)
                )
            }
        ) {
            val theEvent = theViewModel.selectedEvent
            theEvent?.let {
                PhysEventDetailComposable(
                    aViewModel = theViewModel,
                    aEvent = it,
                    aOnSelectVibration = { subevent ->
                        theViewModel.selectedSubEvent = subevent
                        theNavController.navigate("vibration_selection"){
                            launchSingleTop = true
                        }
                    }
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
            val selectedSubEvent = theViewModel.selectedSubEvent

            selectedSubEvent?.let {
                VibrationSelectionComposable(
                    aWatchController = aWatchController,
                    aSubEvent = it,
                    aOnVibrationSelected = { pattern ->
                        it.selectedVibrationId = pattern.id
                        theViewModel.saveSubEvent(it)
                    }
                )
            }
        }
    }
}

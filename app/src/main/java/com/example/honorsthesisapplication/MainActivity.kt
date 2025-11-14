package com.example.honorsthesisapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.runtime.Composable
import com.example.honorsthesisapplication.ui.controller.SmartwatchController
import com.example.honorsthesisapplication.ui.view.PhysEventDetailComposable
import com.example.honorsthesisapplication.ui.view.PhysEventListComposable

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

    NavHost(
        navController = theNavController,
        startDestination = "phys_event_list"
    ) {
        composable("phys_event_list") {
            PhysEventListComposable(
                aOnEventSelected = { eventId ->
                    theNavController.navigate("phys_event_detail/$eventId")
                }
            )
        }

        composable("phys_event_detail/{eventId}") { backStackEntry ->
            val theEventId = backStackEntry.arguments?.getString("eventId")
            PhysEventDetailComposable(
                eventId = theEventId,
                watchController = aWatchController
            )
        }
    }
}

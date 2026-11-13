package com.vergis.vegs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vergis.vegs.ui.screens.AuthScreen
import com.vergis.vegs.ui.screens.CreateRoomScreen
import com.vergis.vegs.ui.screens.GameScreen
import com.vergis.vegs.ui.screens.LobbyScreen
import com.vergis.vegs.ui.screens.RoomsScreen
import com.vergis.vegs.ui.theme.DarkBg
import com.vergis.vegs.ui.theme.VergisTheme
import com.vergis.vegs.viewmodel.AuthViewModel
import com.vergis.vegs.viewmodel.GameViewModel
import com.vergis.vegs.viewmodel.RoomsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VergisTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                    VergisNav()
                }
            }
        }
    }
}

@Composable
fun VergisNav() {
    val navController = rememberNavController()
    val authVm: AuthViewModel = viewModel()
    val isLoggedIn by authVm.isLoggedIn.collectAsState()

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) "rooms" else "auth"
    ) {
        composable("auth") {
            AuthScreen(
                viewModel = authVm,
                onSuccess = {
                    navController.navigate("rooms") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }
        composable("rooms") {
            val roomsVm: RoomsViewModel = viewModel()
            RoomsScreen(
                viewModel = roomsVm,
                authViewModel = authVm,
                onCreateRoom = { navController.navigate("create") },
                onJoinRoom = { roomId ->
                    navController.navigate("lobby/$roomId")
                },
                onLogout = {
                    authVm.logout()
                    navController.navigate("auth") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable("create") {
            CreateRoomScreen(
                onCreated = { roomId ->
                    navController.navigate("lobby/$roomId") {
                        popUpTo("rooms")
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            "lobby/{roomId}",
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
            val gameVm: GameViewModel = viewModel()
            LobbyScreen(
                roomId = roomId,
                viewModel = gameVm,
                onGameStarted = {
                    navController.navigate("game/$roomId") {
                        popUpTo("rooms")
                    }
                },
                onLeave = {
                    navController.navigate("rooms") {
                        popUpTo("rooms") { inclusive = true }
                    }
                }
            )
        }
        composable(
            "game/{roomId}",
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
            val gameVm: GameViewModel = viewModel()
            GameScreen(
                roomId = roomId,
                viewModel = gameVm,
                onLeave = {
                    navController.navigate("rooms") {
                        popUpTo("rooms") { inclusive = true }
                    }
                }
            )
        }
    }
}

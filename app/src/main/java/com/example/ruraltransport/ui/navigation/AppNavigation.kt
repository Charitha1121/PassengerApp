package com.example.ruraltransport.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ruraltransport.data.model.AuthState
import com.example.ruraltransport.ui.auth.AuthViewModel
import com.example.ruraltransport.ui.auth.LoginScreen
import com.example.ruraltransport.ui.auth.RegisterScreen
import com.example.ruraltransport.ui.profile.ProfileScreen
import com.example.ruraltransport.ui.history.RideHistoryScreen

object NavRoutes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val PROFILE = "profile"
    const val HISTORY = "history"
    const val MAP = "map"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = viewModel(),
    homeContent: @Composable (onNavigateToProfile: () -> Unit, onNavigateToMap: () -> Unit) -> Unit,
    mapContent: @Composable (onBack: () -> Unit) -> Unit
) {
    val authState by authViewModel.authState.collectAsState()

    val startDestination = if (authState is AuthState.Authenticated) {
        NavRoutes.HOME
    } else {
        NavRoutes.LOGIN
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(NavRoutes.LOGIN) {
            LoginScreen(
                authViewModel = authViewModel,
                onNavigateToRegister = {
                    navController.navigate(NavRoutes.REGISTER)
                },
                onLoginSuccess = {
                    navController.navigate(NavRoutes.HOME) {
                        popUpTo(NavRoutes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.REGISTER) {
            RegisterScreen(
                authViewModel = authViewModel,
                onNavigateToLogin = {
                    navController.popBackStack()
                },
                onRegisterSuccess = {
                    navController.navigate(NavRoutes.HOME) {
                        popUpTo(NavRoutes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.HOME) {
            homeContent(
                {
                    navController.navigate(NavRoutes.PROFILE)
                },
                {
                    navController.navigate(NavRoutes.MAP)
                }
            )
        }

        composable(NavRoutes.PROFILE) {
            ProfileScreen(
                authViewModel = authViewModel,
                onBack = {
                    navController.popBackStack()
                },
                onLoggedOut = {
                    navController.navigate(NavRoutes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToHistory = {
                    navController.navigate(NavRoutes.HISTORY)
                }
            )
        }

        composable(NavRoutes.HISTORY) {
            RideHistoryScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(NavRoutes.MAP) {
            mapContent {
                navController.popBackStack()
            }
        }
    }
}

package app.aura.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.aura.ui.screens.*

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home)
    object Chat : Screen("chat", "Chat", Icons.Filled.Chat)
    object Activity : Screen("activity", "Activity", Icons.Filled.PlayArrow)
    object Memory : Screen("memory", "Memory", Icons.Filled.Memory)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}

val bottomNavScreens = listOf(
    Screen.Home,
    Screen.Chat,
    Screen.Activity,
    Screen.Memory,
    Screen.Settings,
)

@Composable
fun AuraNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = app.aura.ui.theme.AuraSurface,
                contentColor = app.aura.ui.theme.AuraOnSurface,
            ) {
                bottomNavScreens.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label, style = MaterialTheme.typography.labelSmall) },
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = app.aura.ui.theme.AuraCyan,
                            selectedTextColor = app.aura.ui.theme.AuraCyan,
                            unselectedIconColor = app.aura.ui.theme.AuraOnSurfaceMuted,
                            unselectedTextColor = app.aura.ui.theme.AuraOnSurfaceMuted,
                            indicatorColor = app.aura.ui.theme.AuraSurfaceVariant,
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(200))
            },
            exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(200))
            },
            popEnterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(200))
            },
            popExitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(200))
            }
        ) {
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Chat.route) { ChatScreen() }
            composable(Screen.Activity.route) { ActivityScreen() }
            composable(Screen.Memory.route) { MemoryScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}

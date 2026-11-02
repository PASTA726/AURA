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
import app.aura.ui.theme.AuraCyan
import app.aura.ui.theme.LocalAuraPalette

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
            val palette = LocalAuraPalette.current
            NavigationBar(
                containerColor = palette.surface,
                contentColor = palette.onSurface,
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
                            selectedIconColor = AuraCyan,
                            selectedTextColor = AuraCyan,
                            unselectedIconColor = palette.onSurfaceMuted,
                            unselectedTextColor = palette.onSurfaceMuted,
                            indicatorColor = palette.surfaceVariant,
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
            composable(Screen.Home.route) { HomeScreen(onNavigate = { route -> navController.navigate(route) }) }
            composable(Screen.Chat.route) { ChatScreen() }
            composable(Screen.Activity.route) { ActivityScreen() }
            composable(Screen.Memory.route) { MemoryScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}

package com.dentrain.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dentrain.ui.admin.AdminScreen
import com.dentrain.ui.auth.AuthScreen
import com.dentrain.ui.history.HistoryScreen
import com.dentrain.ui.history.WorkoutDetailScreen
import com.dentrain.ui.profile.ProfileScreen
import com.dentrain.ui.records.RecordsScreen
import com.dentrain.ui.settings.SettingsScreen
import com.dentrain.ui.stopwatch.StopwatchScreen
import com.dentrain.ui.trainer.TrainerScreen
import com.dentrain.ui.workout.SessionScreen
import com.dentrain.ui.workout.WorkoutEditorScreen
import com.dentrain.ui.workout.WorkoutHomeScreen
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/** Основные вкладки нижней панели. */
enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Workouts("Workouts", Icons.Filled.FitnessCenter),
    Progress("Progress", Icons.Filled.BarChart),
    Profile("Profile", Icons.Filled.Person)
}

private const val ROUTE_AUTH = "auth"
private const val ROUTE_MAIN = "main_tabs"
private const val ROUTE_EDITOR = "editor/{id}"
private const val ROUTE_SESSION = "session/{id}"
private const val ROUTE_DETAIL = "detail/{id}"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_ADMIN = "admin"
private const val ROUTE_TRAINER = "trainer"

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val isMainTabs = currentRoute == ROUTE_MAIN || currentRoute == null

    val currentUser = FirebaseAuth.getInstance().currentUser
    val isVerifiedUser = currentUser != null
    val startDest = if (isVerifiedUser) ROUTE_MAIN else ROUTE_AUTH

    val pagerState = rememberPagerState(pageCount = { Tab.entries.size })
    val scope = rememberCoroutineScope()
    val idArg = listOf(navArgument("id") { type = NavType.LongType })

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (isMainTabs) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Кнопки 0 и 1 (Home, Workouts)
                        Tab.entries.take(2).forEachIndexed { index, tab ->
                            NavItem(
                                tab = tab,
                                isSelected = pagerState.currentPage == index,
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(index, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy))
                                    }
                                }
                            )
                        }

                        // Центральная кнопка FAB "+"
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { nav.navigate("editor/0") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Add, "Add Workout", tint = Color.Black, modifier = Modifier.size(32.dp))
                        }

                        // Кнопки 2 и 3 (Progress, Profile)
                        Tab.entries.drop(2).forEachIndexed { dropIndex, tab ->
                            val index = dropIndex + 2
                            NavItem(
                                tab = tab,
                                isSelected = pagerState.currentPage == index,
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(index, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = startDest,
            modifier = Modifier.padding(bottom = pad.calculateBottomPadding()),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(180)) }
        ) {
            composable(ROUTE_AUTH) {
                AuthScreen(
                    onAuthSuccess = {
                        nav.navigate(ROUTE_MAIN) {
                            popUpTo(ROUTE_AUTH) { inclusive = true }
                        }
                    }
                )
            }

            composable(ROUTE_MAIN) {
                Column(Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        modifier = Modifier.weight(1f)
                    ) { page ->
                        when (page) {
                            0 -> WorkoutHomeScreen(
                                onCreate = { nav.navigate("editor/0") },
                                onEdit = { nav.navigate("editor/$it") },
                                onOpenSession = { nav.navigate("session/$it") },
                                onOpenDetail = { nav.navigate("detail/$it") },
                                onSettings = { nav.navigate(ROUTE_SETTINGS) },
                                onOpenTimer = { nav.navigate("stopwatch") }
                            )
                            1 -> HistoryScreen(
                                onOpenDetail = { nav.navigate("detail/$it") },
                                onSettings = { nav.navigate(ROUTE_SETTINGS) }
                            )
                            2 -> RecordsScreen(onSettings = { nav.navigate(ROUTE_SETTINGS) })
                            3 -> ProfileScreen(
                                onLogout = {
                                    nav.navigate(ROUTE_AUTH) { popUpTo(0) { inclusive = true } }
                                },
                                onOpenAdmin = { nav.navigate(ROUTE_ADMIN) },
                                onOpenTrainer = { nav.navigate(ROUTE_TRAINER) },
                                onOpenSettings = { nav.navigate(ROUTE_SETTINGS) }
                            )
                        }
                    }
                }
            }

            composable(ROUTE_EDITOR, arguments = idArg) { e ->
                WorkoutEditorScreen(workoutId = e.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
            }
            composable(ROUTE_SESSION, arguments = idArg) { e ->
                val id = e.arguments!!.getLong("id")
                SessionScreen(
                    workoutId = id,
                    onBack = { nav.popBackStack() },
                    onFinished = {
                        nav.navigate("detail/$id") { popUpTo(ROUTE_SESSION) { inclusive = true } }
                    }
                )
            }
            composable(ROUTE_DETAIL, arguments = idArg) { e ->
                val id = e.arguments!!.getLong("id")
                WorkoutDetailScreen(
                    workoutId = id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("editor/$id") },
                    onRepeated = { newId -> nav.navigate("editor/$newId") }
                )
            }
            composable(ROUTE_SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }, onLogout = { FirebaseAuth.getInstance().signOut() }) }
            composable(ROUTE_ADMIN) { AdminScreen(onBack = { nav.popBackStack() }) }
            composable(ROUTE_TRAINER) { TrainerScreen(onBack = { nav.popBackStack() }) }
            composable("stopwatch") { StopwatchScreen(onSettings = { nav.navigate(ROUTE_SETTINGS) }) }
        }
    }
}

@Composable
private fun NavItem(tab: Tab, isSelected: Boolean, onClick: () -> Unit) {
    val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
            .width(56.dp)
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(tab.label, color = color, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}

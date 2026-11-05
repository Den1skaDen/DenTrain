package com.dentrain.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dentrain.ui.auth.AuthScreen
import com.dentrain.ui.components.GlassBackground
import com.dentrain.ui.components.ScreenHeader
import com.dentrain.ui.history.HistoryScreen
import com.dentrain.ui.history.WorkoutDetailScreen
import com.dentrain.ui.records.RecordsScreen
import com.dentrain.ui.settings.SettingsScreen
import com.dentrain.ui.stopwatch.StopwatchScreen
import com.dentrain.ui.theme.LocalGradient
import com.dentrain.ui.workout.SessionScreen
import com.dentrain.ui.workout.WorkoutEditorScreen
import com.dentrain.ui.workout.WorkoutHomeScreen
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Четыре основные вкладки нижней панели. */
enum class Tab(val label: String, val icon: ImageVector) {
    Workout("Тренировка", Icons.Filled.FitnessCenter),
    History("История", Icons.Filled.History),
    Timer("Секундомер", Icons.Filled.Timer),
    Records("Рекорды", Icons.Filled.EmojiEvents)
}

private const val ROUTE_AUTH = "auth"
private const val ROUTE_MAIN = "main_tabs"
private const val ROUTE_EDITOR = "editor/{id}"
private const val ROUTE_SESSION = "session/{id}"
private const val ROUTE_DETAIL = "detail/{id}"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val isMainTabs = currentRoute == ROUTE_MAIN || currentRoute == null

    // Определяем стартовый экран: если пользователь авторизован, сразу на главную, иначе на экран входа
    val startDest = if (FirebaseAuth.getInstance().currentUser != null) ROUTE_MAIN else ROUTE_AUTH


    val pagerState = rememberPagerState(pageCount = { Tab.entries.size })
    val scope = rememberCoroutineScope()
    val idArg = listOf(navArgument("id") { type = NavType.LongType })

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (isMainTabs) {
                // Точная непрерывная позиция пальца пользователя во время свайпа
                val pageFraction = pagerState.currentPage + pagerState.currentPageOffsetFraction

                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEachIndexed { index, tab ->
                        val selectionProgress = (1f - abs(pageFraction - index)).coerceIn(0f, 1f)
                        val isSelected = selectionProgress > 0.5f

                        val iconColor = lerp(
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            Color.White,
                            selectionProgress
                        )
                        val textColor = lerp(
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            MaterialTheme.colorScheme.primary,
                            selectionProgress
                        )

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(
                                        index,
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
                                    )
                                }
                            },
                            icon = {
                                Box(
                                    Modifier
                                        .width(56.dp)
                                        .size(width = 56.dp, height = 32.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .graphicsLayer { alpha = selectionProgress }
                                        .background(LocalGradient.current.brush),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        tab.icon, tab.label,
                                        tint = iconColor
                                    )
                                }
                            },
                            label = {
                                Text(
                                    tab.label,
                                    color = textColor,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
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
                GlassBackground {
                    Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        // СТАТИЧНАЯ ВЕРХНЯЯ ШАПКА С ЦЕНТРИРОВАННЫМ ЛОГОТИПОМ DT
                        ScreenHeader(
                            actions = {
                                IconButton(onClick = { nav.navigate(ROUTE_SETTINGS) }) {
                                    Icon(Icons.Filled.Settings, "Настройки")
                                }
                            }
                        )

                        // ПЛАВНЫЙ СВАЙП ЭКРАНОВ 120 FPS
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
                                    onSettings = { nav.navigate(ROUTE_SETTINGS) }
                                )
                                1 -> HistoryScreen(
                                    onOpenDetail = { nav.navigate("detail/$it") },
                                    onSettings = { nav.navigate(ROUTE_SETTINGS) }
                                )
                                2 -> StopwatchScreen(onSettings = { nav.navigate(ROUTE_SETTINGS) })
                                3 -> RecordsScreen(onSettings = { nav.navigate(ROUTE_SETTINGS) })
                            }
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
            composable(ROUTE_SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

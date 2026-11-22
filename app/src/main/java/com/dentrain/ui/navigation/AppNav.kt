package com.dentrain.ui.navigation

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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import com.dentrain.ui.icons.filled.BarChart
import com.dentrain.ui.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dentrain.DenTrainApp
import com.dentrain.ui.admin.AdminScreen
import com.dentrain.ui.auth.AuthScreen
import com.dentrain.ui.components.appViewModel
import com.dentrain.ui.history.HistoryScreen
import com.dentrain.ui.history.HistoryViewModel
import com.dentrain.ui.history.WorkoutDetailScreen
import com.dentrain.ui.profile.ProfileScreen
import com.dentrain.ui.records.RecordsScreen
import com.dentrain.ui.records.RecordsViewModel
import com.dentrain.ui.settings.SettingsScreen
import com.dentrain.ui.stopwatch.StopwatchScreen
import com.dentrain.ui.theme.LocalSplashActive
import com.dentrain.ui.trainer.TrainerScreen
import com.dentrain.ui.workout.SessionScreen
import com.dentrain.ui.workout.WorkoutEditorScreen
import com.dentrain.ui.workout.WorkoutHomeScreen
import kotlinx.coroutines.flow.first

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
private const val ROUTE_TRAINER_REQUESTS = "trainer/requests"

@Composable
fun AppNav(startAtHome: Boolean, loginLogoModifier: Modifier = Modifier) {
    val nav = rememberNavController()
    val currentLoginLogoModifier by rememberUpdatedState(loginLogoModifier)
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val isMainTabs = currentRoute == ROUTE_MAIN || (currentRoute == null && startAtHome)

    val app = LocalContext.current.applicationContext as DenTrainApp
    val account = app.container
    LaunchedEffect(account.uid) {
        if (account.uid == null && nav.currentDestination?.route != ROUTE_AUTH) {
            nav.navigate(ROUTE_AUTH) { popUpTo(nav.graph.id) { inclusive = true } }
        }
    }

    val pagerState = rememberPagerState(pageCount = { Tab.entries.size })
    val focusManager = LocalFocusManager.current
    val selectTab: (Int) -> Unit = { index ->
        if (index != pagerState.currentPage) focusManager.clearFocus()
        pagerState.requestScrollToPage(index)
    }
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
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().selectableGroup(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Кнопки 0 и 1 (Home, Workouts)
                        Tab.entries.take(2).forEachIndexed { index, tab ->
                            NavItem(
                                tab = tab,
                                isSelected = pagerState.currentPage == index,
                                onClick = { selectTab(index) },
                                modifier = Modifier.weight(1f)
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
                                onClick = { selectTab(index) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = if (startAtHome) ROUTE_MAIN else ROUTE_AUTH,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(bottom = pad.calculateBottomPadding()),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(180)) }
        ) {
            composable(ROUTE_AUTH) {
                AuthScreen(
                    logoModifier = currentLoginLogoModifier,
                    onAuthSuccess = {
                        pagerState.requestScrollToPage(Tab.Home.ordinal)
                        nav.navigate(ROUTE_MAIN) {
                            popUpTo(ROUTE_AUTH) { inclusive = true }
                        }
                    }
                )
            }

            composable(ROUTE_MAIN) {
                val tabAccount = app.container
                if (tabAccount.uid == null) return@composable
                key(tabAccount) {
                    val history = appViewModel { HistoryViewModel(it.repository, it.screenCache) }
                    val records = appViewModel { RecordsViewModel(it.repository, it.screenCache) }
                    val splashActive = LocalSplashActive.current
                    var pagesToKeep by remember { mutableIntStateOf(0) }
                    LaunchedEffect(splashActive) {
                        if (!splashActive) {
                            // Give the visible page a frame before laying out each group of offscreen pages.
                            while (pagesToKeep < Tab.entries.lastIndex) {
                                snapshotFlow { pagerState.isScrollInProgress }.first { !it }
                                withFrameNanos { }
                                if (!pagerState.isScrollInProgress) pagesToKeep++
                            }
                        }
                    }
                    LaunchedEffect(pagerState, focusManager) {
                        var previous = pagerState.settledPage
                        snapshotFlow { pagerState.settledPage }.collect { page ->
                            if (previous == Tab.Progress.ordinal && page != previous) focusManager.clearFocus()
                            previous = page
                        }
                    }
                    Column(Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            beyondViewportPageCount = pagesToKeep,
                            modifier = Modifier.weight(1f)
                        ) { page ->
                            when (page) {
                                0 -> WorkoutHomeScreen(
                                    onCreate = { nav.navigate("editor/0") },
                                    onEdit = { nav.navigate("editor/$it") },
                                    onOpenSession = { nav.navigate("session/$it") },
                                    onOpenDetail = { nav.navigate("detail/$it") },
                                    onSettings = { nav.navigate(ROUTE_SETTINGS) },
                                    onOpenTimer = { nav.navigate("stopwatch") },
                                    onOpenTrainerRequests = { nav.navigate(ROUTE_TRAINER_REQUESTS) }
                                )
                                1 -> HistoryScreen(
                                    vm = history,
                                    onOpenDetail = { nav.navigate("detail/$it") },
                                    onSettings = { nav.navigate(ROUTE_SETTINGS) }
                                )
                                2 -> RecordsScreen(vm = records, onSettings = { nav.navigate(ROUTE_SETTINGS) })
                                3 -> ProfileScreen(
                                    onLogout = {
                                        nav.navigate(ROUTE_AUTH) {
                                            popUpTo(ROUTE_MAIN) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    onOpenAdmin = { nav.navigate(ROUTE_ADMIN) },
                                    onOpenTrainer = { nav.navigate(ROUTE_TRAINER) },
                                    onOpenSettings = { nav.navigate(ROUTE_SETTINGS) }
                                )
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
            composable(ROUTE_ADMIN) { AdminScreen(onBack = { nav.popBackStack() }) }
            composable(ROUTE_TRAINER) { TrainerScreen(onBack = { nav.popBackStack() }) }
            composable(ROUTE_TRAINER_REQUESTS) {
                TrainerScreen(onBack = { nav.popBackStack() }, openRequests = true)
            }
            composable("stopwatch") { StopwatchScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

@Composable
private fun NavItem(tab: Tab, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 8.dp)
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            text = tab.label,
            color = color,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

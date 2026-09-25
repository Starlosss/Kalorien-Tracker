package com.kalorientracker.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import com.kalorientracker.app.ui.theme.motionSpec
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kalorientracker.app.ui.add.AddFlowViewModel
import com.kalorientracker.app.ui.add.AddHomeScreen
import com.kalorientracker.app.ui.add.analysis.AnalyzingScreen
import com.kalorientracker.app.ui.add.analysis.FollowUpScreen
import com.kalorientracker.app.ui.add.barcode.BarcodeScreen
import com.kalorientracker.app.ui.add.camera.CameraScreen
import com.kalorientracker.app.ui.add.description.DescriptionScreen
import com.kalorientracker.app.ui.add.ingredients.IngredientReviewScreen
import com.kalorientracker.app.ui.add.manual.CustomFoodScreen
import com.kalorientracker.app.ui.add.manual.FoodSearchScreen
import com.kalorientracker.app.ui.add.review.MealSummaryScreen
import com.kalorientracker.app.ui.meal.MealDetailScreen
import com.kalorientracker.app.ui.onboarding.OnboardingScreen
import com.kalorientracker.app.ui.settings.SettingsScreen
import com.kalorientracker.app.ui.settings.SettingsSectionScreen
import com.kalorientracker.app.ui.statistics.StatisticsScreen
import com.kalorientracker.app.ui.theme.HapticEvent
import com.kalorientracker.app.ui.theme.LocalHaptics
import com.kalorientracker.app.ui.theme.Motion
import com.kalorientracker.app.ui.theme.Palette
import com.kalorientracker.app.ui.today.TodayScreen
import com.kalorientracker.app.ui.weight.WeightScreen
import com.kalorientracker.app.ui.workout.WorkoutScreen

@Composable
fun AppNavHost(onboardingCompleted: Boolean) {
    val navController = rememberNavController()
    val startDestination = remember { if (onboardingCompleted) Routes.TODAY else Routes.ONBOARDING }
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    LaunchedEffect(onboardingCompleted) {
        val current = navController.currentDestination?.route
        if (!onboardingCompleted && current != null && current != Routes.ONBOARDING) {
            navController.navigate(Routes.ONBOARDING) { popUpTo(0) { inclusive = true } }
        } else if (onboardingCompleted && current == Routes.ONBOARDING) {
            navController.navigate(Routes.TODAY) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
        }
    }

    Scaffold(
        containerColor = Palette.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = route in Routes.bottomBarRoutes,
                enter = slideInVertically(tween(Motion.SCREEN_MS)) { it } + fadeIn(),
                exit = slideOutVertically(tween(Motion.SCREEN_MS)) { it } + fadeOut(),
            ) {
                BottomBar(route) { target -> navController.navigateTab(target) }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize().padding(padding),
            enterTransition = { fadeIn(tween(Motion.SCREEN_MS)) },
            exitTransition = { fadeOut(tween(Motion.SCREEN_MS / 2)) },
            popEnterTransition = { fadeIn(tween(Motion.SCREEN_MS)) },
            popExitTransition = { fadeOut(tween(Motion.SCREEN_MS / 2)) },
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinished = {
                        navController.navigate(Routes.TODAY) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                    },
                )
            }
            composable(Routes.TODAY) {
                TodayScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenMeal = { navController.navigate(Routes.meal(it)) },
                    onOpenWeight = { navController.navigate(Routes.WEIGHT) },
                    onOpenWorkout = { navController.navigate(Routes.WORKOUT) },
                    onOpenPlanSettings = { navController.navigate(Routes.settingsSection(SettingsSection.PLAN)) },
                    onAdd = { navController.navigateTab(Routes.ADD_GRAPH) },
                )
            }
            composable(Routes.STATISTICS) {
                StatisticsScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
            }
            addFlowGraph(navController)
            composable(Routes.MEAL, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                MealDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.WEIGHT) { WeightScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.WORKOUT) { WorkoutScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSection = { navController.navigate(Routes.settingsSection(it)) },
                )
            }
            composable(
                Routes.SETTINGS_SECTION,
                arguments = listOf(navArgument("section") { type = NavType.StringType }),
            ) { entry ->
                val section = SettingsSection.valueOf(entry.arguments?.getString("section") ?: SettingsSection.PROFILE.name)
                SettingsSectionScreen(
                    section = section,
                    onBack = { navController.popBackStack() },
                    onOpenWorkout = { navController.navigate(Routes.WORKOUT) },
                )
            }
        }
    }
}

private fun androidx.navigation.NavGraphBuilder.addFlowGraph(navController: NavHostController) {
    navigation(startDestination = Routes.ADD_HOME, route = Routes.ADD_GRAPH) {
        composable(Routes.ADD_HOME) { entry ->
            val vm = entry.addFlowViewModel(navController)
            AddHomeScreen(
                viewModel = vm,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onCamera = { navController.navigate(Routes.ADD_CAMERA) },
                onGalleryImported = { navController.navigate(Routes.ADD_DESCRIBE) },
                onBarcode = { navController.navigate(Routes.ADD_BARCODE) },
                onManual = { navController.navigate(Routes.addSearch()) },
                onReview = { navController.navigate(Routes.ADD_REVIEW) },
            )
        }
        composable(Routes.ADD_CAMERA) { entry ->
            CameraScreen(
                viewModel = entry.addFlowViewModel(navController),
                onBack = { navController.popBackStack() },
                onContinue = { navController.navigate(Routes.ADD_DESCRIBE) },
            )
        }
        composable(Routes.ADD_DESCRIBE) { entry ->
            DescriptionScreen(
                viewModel = entry.addFlowViewModel(navController),
                onBack = { navController.popBackStack() },
                onAddPhotos = { navController.navigate(Routes.ADD_CAMERA) { launchSingleTop = true } },
                onAnalyze = { navController.navigate(Routes.ADD_ANALYZE) },
            )
        }
        composable(Routes.ADD_ANALYZE) { entry ->
            AnalyzingScreen(
                viewModel = entry.addFlowViewModel(navController),
                onBack = { navController.popBackStack() },
                onQuestions = {
                    navController.navigate(Routes.ADD_FOLLOW_UP) { popUpTo(Routes.ADD_ANALYZE) { inclusive = true } }
                },
                onResult = {
                    navController.navigate(Routes.ADD_REVIEW) { popUpTo(Routes.ADD_ANALYZE) { inclusive = true } }
                },
            )
        }
        composable(Routes.ADD_FOLLOW_UP) { entry ->
            FollowUpScreen(
                viewModel = entry.addFlowViewModel(navController),
                onBack = { navController.popBackStack() },
                onDone = {
                    navController.navigate(Routes.ADD_REVIEW) { popUpTo(Routes.ADD_FOLLOW_UP) { inclusive = true } }
                },
            )
        }
        composable(Routes.ADD_REVIEW) { entry ->
            IngredientReviewScreen(
                viewModel = entry.addFlowViewModel(navController),
                onBack = { navController.popBackStack() },
                onAddIngredient = { navController.navigate(Routes.addSearch()) },
                onSwapIngredient = { key -> navController.navigate(Routes.addSearch(key)) },
                onContinue = { navController.navigate(Routes.ADD_SUMMARY) },
                onDiscard = { navController.popBackStack(Routes.ADD_HOME, inclusive = false) },
            )
        }
        composable(Routes.ADD_SUMMARY) { entry ->
            MealSummaryScreen(
                viewModel = entry.addFlowViewModel(navController),
                onEdit = { navController.popBackStack() },
                onSaved = {
                    navController.navigate(Routes.TODAY) {
                        popUpTo(Routes.TODAY) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            Routes.ADD_SEARCH,
            arguments = listOf(navArgument("swap") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            FoodSearchScreen(
                addFlow = entry.addFlowViewModel(navController),
                swapKey = entry.arguments?.getString("swap"),
                onBack = { navController.popBackStack() },
                onCreateCustom = { navController.navigate(Routes.addCustomFood()) },
                onDone = { navController.toReview() },
            )
        }
        composable(Routes.ADD_BARCODE) { entry ->
            BarcodeScreen(
                viewModel = entry.addFlowViewModel(navController),
                onBack = { navController.popBackStack() },
                onCreateCustom = { code -> navController.navigate(Routes.addCustomFood(code)) },
                onSearch = { navController.navigate(Routes.addSearch()) },
                onDone = { navController.toReview() },
            )
        }
        composable(
            Routes.ADD_CUSTOM_FOOD,
            arguments = listOf(navArgument("barcode") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            CustomFoodScreen(
                addFlow = entry.addFlowViewModel(navController),
                barcode = entry.arguments?.getString("barcode"),
                onBack = { navController.popBackStack() },
                onDone = { navController.toReview() },
            )
        }
    }
}

/** Returns to the ingredient review, creating it if the flow started from search or barcode. */
private fun NavHostController.toReview() {
    val popped = popBackStack(Routes.ADD_REVIEW, inclusive = false)
    if (!popped) {
        navigate(Routes.ADD_REVIEW) { popUpTo(Routes.ADD_HOME) { inclusive = false } }
    }
}

@Composable
private fun NavBackStackEntry.addFlowViewModel(navController: NavHostController): AddFlowViewModel {
    val parent = remember(this) { navController.getBackStackEntry(Routes.ADD_GRAPH) }
    return hiltViewModel(parent)
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(Routes.TODAY) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private data class Tab(val route: String, val graphRoute: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TODAY, Routes.TODAY, "Heute", Icons.Outlined.RadioButtonUnchecked),
    Tab(Routes.ADD_HOME, Routes.ADD_GRAPH, "Hinzufügen", Icons.Outlined.Add),
    Tab(Routes.STATISTICS, Routes.STATISTICS, "Statistik", Icons.Outlined.BarChart),
)

/** Three equal-width tabs, icon above label, so labels never wrap on narrow screens. */
@Composable
private fun BottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Background)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Outline, RoundedCornerShape(30.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { tab ->
            val selected = currentRoute == tab.route
            val isAdd = tab.graphRoute == Routes.ADD_GRAPH
            val background by animateColorAsState(
                if (selected) Palette.TextPrimary else Color.Transparent,
                motionSpec(Motion.snappy()),
                label = "tabBackground",
            )
            val fg by animateColorAsState(
                if (selected) Palette.Background else Palette.TextSecondary,
                motionSpec(Motion.snappy()),
                label = "tabContent",
            )
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(background)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (!selected) haptics.perform(HapticEvent.Tap)
                        onSelect(tab.graphRoute)
                    }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (isAdd && !selected) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(Palette.TextPrimary), contentAlignment = Alignment.Center) {
                        Icon(tab.icon, contentDescription = null, tint = Palette.Background, modifier = Modifier.size(16.dp))
                    }
                } else {
                    Icon(tab.icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.size(3.dp))
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, lineHeight = 14.sp),
                    color = fg,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

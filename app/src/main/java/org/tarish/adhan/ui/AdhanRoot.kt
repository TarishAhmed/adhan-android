package org.tarish.adhan.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.Flow
import org.tarish.adhan.R
import org.tarish.adhan.data.UpdateInfo
import org.tarish.adhan.ui.advanced.BatterySheet
import org.tarish.adhan.ui.components.AppSnackbar
import org.tarish.adhan.ui.components.LocalAppSnackbar
import org.tarish.adhan.ui.components.LocalSnackbarHost
import org.tarish.adhan.ui.home.HomeScreen
import org.tarish.adhan.ui.location.LocationScreen
import org.tarish.adhan.ui.settings.SettingsScreen
import org.tarish.adhan.ui.navigation.Home
import org.tarish.adhan.ui.navigation.Location
import org.tarish.adhan.ui.navigation.Main
import org.tarish.adhan.ui.navigation.Notifications
import org.tarish.adhan.ui.navigation.Onboarding
import org.tarish.adhan.ui.navigation.Qibla
import org.tarish.adhan.ui.navigation.Quran
import org.tarish.adhan.ui.navigation.QuranSearch
import org.tarish.adhan.ui.navigation.SurahReader
import org.tarish.adhan.ui.quran.QuranHomeScreen
import org.tarish.adhan.ui.quran.QuranSearchScreen
import org.tarish.adhan.ui.quran.QuranSettingsSection
import org.tarish.adhan.ui.quran.SurahReaderScreen
import org.tarish.adhan.ui.navigation.Settings
import org.tarish.adhan.ui.notifications.NotificationsScreen
import org.tarish.adhan.ui.onboarding.OnboardingScreen
import org.tarish.adhan.ui.qibla.QiblaScreen
import org.tarish.adhan.ui.theme.AdhanTheme
import org.tarish.adhan.ui.theme.Motion
import org.tarish.adhan.ui.update.ForceUpdateScreen
import org.tarish.adhan.ui.update.GeneralUpdateDialog
import org.tarish.adhan.ui.util.openExactAlarmSettings
import kotlin.reflect.KClass

private data class Tab(val route: Any, val cls: KClass<*>, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

private val TABS = listOf(
    Tab(Home, Home::class, R.string.nav_home, Icons.Outlined.WbSunny, Icons.Rounded.WbSunny),
    Tab(Quran, Quran::class, R.string.nav_quran, Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Rounded.MenuBook),
    Tab(Qibla, Qibla::class, R.string.nav_qibla, Icons.Outlined.Explore, Icons.Rounded.Explore),
    Tab(Settings, Settings::class, R.string.nav_settings, Icons.Outlined.Settings, Icons.Rounded.Settings),
)

@Composable
fun AdhanRoot(vm: RootViewModel, deepLinks: Flow<Unit>) {
    val theme by vm.themeMode.collectAsStateWithLifecycle()
    val start by vm.startOnboarding.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val dialog by vm.dialog.collectAsStateWithLifecycle()
    val startOnboarding = start ?: return
    val mode = theme ?: return

    AdhanTheme(mode) {
        val snackbarHost = remember { SnackbarHostState() }
        val rootScope = rememberCoroutineScope()
        val appSnackbar = remember { AppSnackbar(snackbarHost, rootScope) }
        val nav = rememberNavController()

        LaunchedEffect(Unit) { vm.checkUpdates() }
        LifecycleResumeEffect(Unit) { vm.onResume(); onPauseOrDispose { } }
        LaunchedEffect(nav) { deepLinks.collect { nav.switchTab(Home) } }

        CompositionLocalProvider(LocalSnackbarHost provides snackbarHost, LocalAppSnackbar provides appSnackbar) {
            Box(Modifier.fillMaxSize()) {
                Shell(nav, startOnboarding, mode, vm)
                when (dialog) {
                    GlobalDialog.GENERAL_UPDATE -> GeneralUpdateDialog(update, vm::startUpdate, vm::dismissDialog)
                    GlobalDialog.EXACT_ALARM -> ExactAlarmDialog(vm::dismissDialog)
                    GlobalDialog.BATTERY -> BatterySheet(vm::dismissDialog)
                    GlobalDialog.NONE -> Unit
                }
                if (update.info.type == UpdateInfo.Type.FORCE) ForceUpdateScreen(update, vm::startUpdate)
            }
        }
    }
}

@Composable
private fun ExactAlarmDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Alarm, null) },
        title = { Text(stringResource(R.string.exact_title)) },
        text = { Text(stringResource(R.string.exact_body)) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_not_now)) } },
        confirmButton = { TextButton(onClick = { ctx.openExactAlarmSettings(); onDismiss() }) { Text(stringResource(R.string.action_allow)) } },
    )
}

/** Standard bottom-nav switch; re-selecting the current tab does nothing. */
private fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun tabEnter(): EnterTransition =
    fadeIn(tween(210, delayMillis = 90, easing = Motion.emphasizedDecelerate)) +
        scaleIn(tween(210, delayMillis = 90, easing = Motion.emphasizedDecelerate), initialScale = 0.94f)

private fun tabExit(): ExitTransition = fadeOut(tween(90))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushEnter(): EnterTransition =
    fadeIn(tween(350, easing = Motion.emphasizedDecelerate)) +
        slideInVertically(tween(350, easing = Motion.emphasizedDecelerate)) { (it * 0.06f).toInt() }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushExit(): ExitTransition =
    fadeOut(tween(300)) + slideOutVertically(tween(300)) { (it * 0.06f).toInt() }

@Composable
private fun Shell(nav: NavHostController, startOnboarding: Boolean, mode: org.tarish.adhan.domain.model.ThemeMode, vm: RootViewModel) {
    val haptic = LocalHapticFeedback.current
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val showBar = TABS.any { t -> dest?.hierarchy?.any { it.hasRoute(t.cls) } == true }

    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbarHost.current) },
        bottomBar = {
            if (showBar) NavigationBar {
                TABS.forEach { t ->
                    val selected = dest?.hierarchy?.any { it.hasRoute(t.cls) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                nav.switchTab(t.route)
                            }
                        },
                        icon = { Icon(if (selected) t.selectedIcon else t.icon, null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (startOnboarding) Onboarding else Main,
            modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
            enterTransition = { pushEnter() },
            exitTransition = { fadeOut(tween(90)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { pushExit() },
        ) {
            composable<Onboarding> {
                OnboardingScreen { openLocation, reliabilityShown ->
                    vm.finishOnboarding(reliabilityShown)
                    nav.navigate(Main) { popUpTo(Onboarding) { inclusive = true } }
                    if (openLocation) nav.navigate(Location)
                }
            }
            navigation<Main>(startDestination = Home) {
                composable<Home>(enterTransition = { tabEnter() }, exitTransition = { tabExit() }, popEnterTransition = { tabEnter() }) {
                    LaunchedEffect(Unit) { vm.onHomeShown() }
                    HomeScreen(onOpenLocation = { nav.navigate(Location) })
                }
                composable<Quran>(enterTransition = { tabEnter() }, exitTransition = { tabExit() }, popEnterTransition = { tabEnter() }) {
                    QuranHomeScreen(
                        onOpen = { nav.navigate(SurahReader(it.surah, it.verse)) },
                        onSearch = { nav.navigate(QuranSearch(it)) },
                    )
                }
                composable<Qibla>(enterTransition = { tabEnter() }, exitTransition = { tabExit() }, popEnterTransition = { tabEnter() }) {
                    QiblaScreen(onChooseCity = { nav.navigate(Location) })
                }
                composable<Settings>(enterTransition = { tabEnter() }, exitTransition = { tabExit() }, popEnterTransition = { tabEnter() }) {
                    SettingsScreen(
                        themeMode = mode,
                        onThemeChange = vm::setTheme,
                        onNotifications = { nav.navigate(Notifications) },
                        onLocation = { nav.navigate(Location) },
                        quranSection = { QuranSettingsSection() },
                    )
                }
            }
            composable<Notifications> { NotificationsScreen(onBack = { nav.popBackStack() }) }
            composable<Location> { LocationScreen(onBack = { nav.popBackStack() }) }
            composable<SurahReader> { SurahReaderScreen(onBack = { nav.popBackStack() }) }
            composable<QuranSearch> {
                QuranSearchScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(SurahReader(it.surah, it.verse)) })
            }
        }
    }
}

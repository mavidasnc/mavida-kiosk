package it.mavida.dashboardalert.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import it.mavida.dashboardalert.AppContainer
import it.mavida.dashboardalert.ui.browser.BrowserScreen
import it.mavida.dashboardalert.ui.browser.BrowserViewModel
import it.mavida.dashboardalert.ui.log.LogScreen
import it.mavida.dashboardalert.ui.log.LogViewModel
import it.mavida.dashboardalert.ui.monitors.MonitorEditScreen
import it.mavida.dashboardalert.ui.monitors.MonitorEditViewModel
import it.mavida.dashboardalert.ui.monitors.MonitorListScreen
import it.mavida.dashboardalert.ui.monitors.MonitorListViewModel
import it.mavida.dashboardalert.ui.settings.SettingsScreen
import it.mavida.dashboardalert.ui.settings.SettingsViewModel
import it.mavida.dashboardalert.ui.settings.UpdateViewModel

/** Rotte di navigazione dell'app. */
object Routes {
    const val MONITORS = "monitors"
    const val MONITOR_EDIT = "monitor_edit/{monitorId}"
    const val BROWSER = "browser"
    const val LOG = "log"
    const val SETTINGS = "settings"

    fun monitorEdit(id: Long) = "monitor_edit/$id"
}

/**
 * Grafo di navigazione principale.
 * I ViewModel prendono le dipendenze dall'AppContainer (DI manuale).
 */
@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()

    // All'avvio, se è configurato almeno un URL dashboard, apri subito il browser
    // kiosk; la rotta monitors resta nel back stack (il tasto indietro torna alla lista).
    LaunchedEffect(Unit) {
        if (container.settingsRepository.settings.value.browserUrls.isNotEmpty()) {
            navController.navigate(Routes.BROWSER)
        }
    }

    NavHost(navController = navController, startDestination = Routes.MONITORS) {
        composable(Routes.MONITORS) {
            val vm: MonitorListViewModel = viewModel(
                factory = viewModelFactory { MonitorListViewModel(container.monitorRepository) },
            )
            MonitorListScreen(
                viewModel = vm,
                onEditMonitor = { id -> navController.navigate(Routes.monitorEdit(id)) },
                onOpenBrowser = { navController.navigate(Routes.BROWSER) },
                onOpenLog = { navController.navigate(Routes.LOG) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            Routes.MONITOR_EDIT,
            arguments = listOf(navArgument("monitorId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val monitorId = backStackEntry.arguments?.getLong("monitorId") ?: 0L
            val vm: MonitorEditViewModel = viewModel(
                factory = viewModelFactory {
                    MonitorEditViewModel(container.monitorRepository, monitorId)
                },
            )
            MonitorEditScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable(Routes.BROWSER) {
            val vm: BrowserViewModel = viewModel(
                factory = viewModelFactory { BrowserViewModel(container.settingsRepository) },
            )
            BrowserScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        // Placeholder sostituiti dalle schermate reali.
        composable(Routes.LOG) {
            val vm: LogViewModel = viewModel(
                factory = viewModelFactory { LogViewModel(container.logRepository) },
            )
            LogScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    SettingsViewModel(container.settingsRepository, container.configTransfer)
                },
            )
            val appContext = LocalContext.current.applicationContext
            val updateVm: UpdateViewModel = viewModel(
                factory = viewModelFactory { UpdateViewModel(appContext, container.appUpdater) },
            )
            SettingsScreen(viewModel = vm, updateViewModel = updateVm, onBack = { navController.popBackStack() })
        }
    }
}

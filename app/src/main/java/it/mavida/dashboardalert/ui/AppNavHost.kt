package it.mavida.dashboardalert.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import it.mavida.dashboardalert.AppContainer
import it.mavida.dashboardalert.ui.monitors.MonitorEditScreen
import it.mavida.dashboardalert.ui.monitors.MonitorEditViewModel
import it.mavida.dashboardalert.ui.monitors.MonitorListScreen
import it.mavida.dashboardalert.ui.monitors.MonitorListViewModel

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
        // Placeholder: implementati nelle fasi successive (browser kiosk, log, impostazioni).
        composable(Routes.BROWSER) { PlaceholderScreen("Modalita' browser — in arrivo") }
        composable(Routes.LOG) { PlaceholderScreen("Storico eventi — in arrivo") }
        composable(Routes.SETTINGS) { PlaceholderScreen("Impostazioni — in arrivo") }
    }
}

@Composable
private fun PlaceholderScreen(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

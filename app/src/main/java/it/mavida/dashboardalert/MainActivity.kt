package it.mavida.dashboardalert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import it.mavida.dashboardalert.ui.AppNavHost
import it.mavida.dashboardalert.ui.theme.AppTheme

/**
 * Activity principale (singleTask: una sola istanza, coerente con l'uso kiosk).
 * La UI e' interamente in Jetpack Compose; la navigazione e' in [AppNavHost].
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as DashboardAlertApp).container
        setContent {
            AppTheme {
                AppNavHost(container)
            }
        }
    }
}

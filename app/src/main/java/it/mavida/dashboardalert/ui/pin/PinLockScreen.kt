package it.mavida.dashboardalert.ui.pin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.mavida.dashboardalert.system.PinLock

/**
 * Tastierino numerico a schermo intero per sbloccare l'app quando il PIN
 * e' impostato. Cifre grandi: pensato per essere usato da lontano/in piedi.
 */
@Composable
fun PinLockScreen() {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "App bloccata",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "●".repeat(entered.length).ifEmpty { "Inserisci il PIN" },
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (error) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(24.dp))

            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("C", "0", "⌫"),
            )
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { key ->
                        Button(
                            onClick = {
                                error = false
                                when (key) {
                                    "C" -> entered = ""
                                    "⌫" -> entered = entered.dropLast(1)
                                    else -> if (entered.length < 12) entered += key
                                }
                            },
                            modifier = Modifier.size(88.dp).padding(4.dp),
                        ) {
                            Text(key, fontSize = 26.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            // Conferma esplicita: il PIN puo' avere lunghezza qualsiasi.
            Button(
                onClick = {
                    if (!PinLock.tryUnlock(entered)) {
                        error = true
                        entered = ""
                    }
                },
                enabled = entered.isNotEmpty(),
            ) {
                Text("Sblocca", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

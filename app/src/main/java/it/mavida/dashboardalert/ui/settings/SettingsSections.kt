package it.mavida.dashboardalert.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.mavida.dashboardalert.data.SettingsRepository
import it.mavida.dashboardalert.system.OemBatteryHelper
import kotlin.math.roundToInt

/**
 * Sezioni "stabilita' 24/7" e comfort della schermata impostazioni (spec §3.5, §3.6).
 */

/** Esenzione dall'ottimizzazione batteria standard Android. */
@Composable
fun BatteryCard() {
    val context = LocalContext.current
    // Lo stato viene riletto ad ogni ricomposizione (es. tornando dalle impostazioni).
    var ignored by remember { mutableStateOf(OemBatteryHelper.isBatteryOptimizationIgnored(context)) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ottimizzazione batteria", style = MaterialTheme.typography.titleMedium)
            Text(
                if (ignored) {
                    "L'app e' esentata dall'ottimizzazione batteria."
                } else {
                    "Per un funzionamento 24/7 affidabile, esenta l'app " +
                        "dall'ottimizzazione batteria."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!ignored) {
                Button(onClick = {
                    runCatching {
                        context.startActivity(OemBatteryHelper.batteryExemptionIntent(context))
                    }
                    ignored = OemBatteryHelper.isBatteryOptimizationIgnored(context)
                }) {
                    Text("Richiedi esenzione")
                }
            }
        }
    }
}

/** Istruzioni specifiche per il risparmio energetico aggressivo dell'OEM. */
@Composable
fun OemCard() {
    val profile = remember { OemBatteryHelper.detect() }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Risparmio energetico del produttore",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Rilevato: ${profile.manufacturerLabel}. " +
                    "Questo produttore puo' terminare le app in background: " +
                    "verifica questi passaggi:",
                style = MaterialTheme.typography.bodyMedium,
            )
            profile.steps.forEachIndexed { index, step ->
                Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** PIN opzionale anti-modifiche accidentali (kiosk esposto). */
@Composable
fun PinCard(viewModel: SettingsViewModel, pinSet: Boolean) {
    var pinInput by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Blocco con PIN", style = MaterialTheme.typography.titleMedium)
            Text(
                if (pinSet) {
                    "PIN attivo: all'avvio l'app chiede il PIN prima delle modifiche."
                } else {
                    "Imposta un PIN per evitare modifiche accidentali " +
                        "toccando lo schermo del kiosk."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = pinInput,
                onValueChange = { pinInput = it.filter(Char::isDigit) },
                label = { Text(if (pinSet) "PIN attuale per rimuovere" else "Nuovo PIN (min 4 cifre)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (pinSet) {
                    Button(
                        onClick = {
                            viewModel.clearPin(pinInput)
                            pinInput = ""
                        },
                        enabled = pinInput.isNotEmpty(),
                    ) { Text("Rimuovi PIN") }
                } else {
                    Button(
                        onClick = {
                            viewModel.setPin(pinInput)
                            pinInput = ""
                        },
                        enabled = pinInput.length >= 4,
                    ) { Text("Imposta PIN") }
                }
            }
        }
    }
}

/** Tema notturno/dimming a fasce orarie con controllo luminosita'. */
@Composable
fun NightModeCard(viewModel: SettingsViewModel, settings: SettingsRepository.Settings) {
    var startHour by remember(settings.nightStartHour) {
        mutableStateOf(settings.nightStartHour.toString())
    }
    var endHour by remember(settings.nightEndHour) {
        mutableStateOf(settings.nightEndHour.toString())
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dimming notturno", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Riduce la luminosita' nella fascia oraria impostata",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = settings.nightModeEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.setNightMode(
                            enabled,
                            startHour.toIntOrNull() ?: 22,
                            endHour.toIntOrNull() ?: 7,
                            settings.nightBrightnessPercent,
                        )
                    },
                )
            }
            if (settings.nightModeEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startHour,
                        onValueChange = { v ->
                            startHour = v.filter(Char::isDigit).take(2)
                            startHour.toIntOrNull()?.let {
                                viewModel.setNightMode(
                                    true, it,
                                    endHour.toIntOrNull() ?: 7,
                                    settings.nightBrightnessPercent,
                                )
                            }
                        },
                        label = { Text("Da (ora)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endHour,
                        onValueChange = { v ->
                            endHour = v.filter(Char::isDigit).take(2)
                            endHour.toIntOrNull()?.let {
                                viewModel.setNightMode(
                                    true,
                                    startHour.toIntOrNull() ?: 22, it,
                                    settings.nightBrightnessPercent,
                                )
                            }
                        },
                        label = { Text("A (ora)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("Luminosita' notturna: ${settings.nightBrightnessPercent}%")
                Slider(
                    value = settings.nightBrightnessPercent / 100f,
                    onValueChange = { v ->
                        viewModel.setNightMode(
                            true,
                            startHour.toIntOrNull() ?: 22,
                            endHour.toIntOrNull() ?: 7,
                            (v * 100).roundToInt().coerceIn(1, 100),
                        )
                    },
                )
            }
            Spacer(Modifier.height(0.dp))
        }
    }
}

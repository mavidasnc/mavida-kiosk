package it.mavida.dashboardalert.ui.monitors

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import it.mavida.dashboardalert.domain.model.CompareOperator
import it.mavida.dashboardalert.domain.model.ConditionParams
import it.mavida.dashboardalert.domain.model.HeaderEntry
import it.mavida.dashboardalert.domain.model.HttpMethod
import it.mavida.dashboardalert.domain.model.Rule
import it.mavida.dashboardalert.domain.model.StatusOperator
import it.mavida.dashboardalert.domain.model.TriggerConfig
import it.mavida.dashboardalert.domain.model.TriggerDef
import kotlin.math.roundToInt

/**
 * Editor di regole e trigger dentro la schermata di modifica monitor
 * (spec §3.2, §3.3). Componenti controllati: ogni modifica risale al
 * ViewModel che aggiorna il draft; niente stato nascosto.
 */

// ---------------------------------------------------------------------------
// Sezione regole
// ---------------------------------------------------------------------------

@Composable
fun RulesSection(viewModel: MonitorEditViewModel, rules: List<Rule>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Regole di valutazione",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::addRule) {
                    Icon(Icons.Default.Add, contentDescription = "Aggiungi regola")
                }
            }
            if (rules.isEmpty()) {
                Text(
                    "Nessuna regola: il monitor registra solo le risposte nello storico.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            rules.forEachIndexed { index, rule ->
                RuleCard(rule = rule, ruleIndex = index, viewModel = viewModel)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun RuleCard(rule: Rule, ruleIndex: Int, viewModel: MonitorEditViewModel) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = rule.name,
                    onValueChange = { v -> viewModel.updateRule(ruleIndex) { it.copy(name = v) } },
                    label = { Text("Nome regola") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { v -> viewModel.updateRule(ruleIndex) { it.copy(enabled = v) } },
                )
                IconButton(onClick = { viewModel.removeRule(ruleIndex) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Elimina regola",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            ConditionEditor(
                condition = rule.condition,
                onChange = { c -> viewModel.updateRule(ruleIndex) { it.copy(condition = c) } },
            )
            Spacer(Modifier.height(8.dp))

            // --- Trigger della regola ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Trigger",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.addTrigger(ruleIndex) }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Aggiungi")
                }
            }
            rule.triggers.forEachIndexed { triggerIndex, trigger ->
                TriggerCard(
                    trigger = trigger,
                    ruleIndex = ruleIndex,
                    triggerIndex = triggerIndex,
                    viewModel = viewModel,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Editor della condizione
// ---------------------------------------------------------------------------

private enum class ConditionKind(val label: String) {
    STATUS("Status code"),
    JSON_PATH("Campo JSON (JSONPath)"),
    REGEX("Regex sul body"),
    CONTAINS("Testo nel body"),
    CONN_ERROR("Errore/timeout/nessuna risposta"),
}

private fun kindOf(condition: ConditionParams): ConditionKind = when (condition) {
    is ConditionParams.StatusCode -> ConditionKind.STATUS
    is ConditionParams.JsonPathCondition -> ConditionKind.JSON_PATH
    is ConditionParams.RegexMatch -> ConditionKind.REGEX
    is ConditionParams.BodyContains -> ConditionKind.CONTAINS
    ConditionParams.ConnectionError -> ConditionKind.CONN_ERROR
}

private fun defaultCondition(kind: ConditionKind): ConditionParams = when (kind) {
    ConditionKind.STATUS -> ConditionParams.StatusCode()
    ConditionKind.JSON_PATH -> ConditionParams.JsonPathCondition()
    ConditionKind.REGEX -> ConditionParams.RegexMatch("")
    ConditionKind.CONTAINS -> ConditionParams.BodyContains("")
    ConditionKind.CONN_ERROR -> ConditionParams.ConnectionError
}

@Composable
private fun ConditionEditor(
    condition: ConditionParams,
    onChange: (ConditionParams) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EnumDropdown(
            label = "Tipo di condizione",
            options = ConditionKind.entries.map { it to it.label },
            selected = kindOf(condition),
            onSelected = { onChange(defaultCondition(it)) },
        )

        when (condition) {
            is ConditionParams.StatusCode -> {
                EnumDropdown(
                    label = "Operatore",
                    options = listOf(
                        StatusOperator.EQUALS to "uguale a",
                        StatusOperator.NOT_EQUALS to "diverso da",
                        StatusOperator.RANGE to "nel range",
                    ),
                    selected = condition.operator,
                    onSelected = { onChange(condition.copy(operator = it)) },
                )
                if (condition.operator == StatusOperator.RANGE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(
                            value = condition.rangeMin,
                            onValueChange = { onChange(condition.copy(rangeMin = it)) },
                            label = "Min",
                            modifier = Modifier.weight(1f),
                        )
                        NumberField(
                            value = condition.rangeMax,
                            onValueChange = { onChange(condition.copy(rangeMax = it)) },
                            label = "Max",
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    NumberField(
                        value = condition.value,
                        onValueChange = { onChange(condition.copy(value = it)) },
                        label = "Status code",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            is ConditionParams.JsonPathCondition -> {
                OutlinedTextField(
                    value = condition.path,
                    onValueChange = { onChange(condition.copy(path = it)) },
                    label = { Text("JSONPath (es. $.sensore.valore)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                EnumDropdown(
                    label = "Operatore",
                    options = listOf(
                        CompareOperator.EQUALS to "==",
                        CompareOperator.NOT_EQUALS to "!=",
                        CompareOperator.GREATER to ">",
                        CompareOperator.LESS to "<",
                        CompareOperator.GREATER_OR_EQUAL to ">=",
                        CompareOperator.LESS_OR_EQUAL to "<=",
                        CompareOperator.CONTAINS to "contains",
                    ),
                    selected = condition.operator,
                    onSelected = { onChange(condition.copy(operator = it)) },
                )
                OutlinedTextField(
                    value = condition.expected,
                    onValueChange = { onChange(condition.copy(expected = it)) },
                    label = { Text("Valore atteso") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            is ConditionParams.RegexMatch -> {
                OutlinedTextField(
                    value = condition.pattern,
                    onValueChange = { onChange(condition.copy(pattern = it)) },
                    label = { Text("Espressione regolare") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            is ConditionParams.BodyContains -> {
                OutlinedTextField(
                    value = condition.text,
                    onValueChange = { onChange(condition.copy(text = it)) },
                    label = { Text("Testo da cercare nel body") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = condition.negate,
                        onCheckedChange = { onChange(condition.copy(negate = it)) },
                    )
                    Text("Inverti (vero se il body NON contiene il testo)")
                }
            }
            ConditionParams.ConnectionError -> {
                Text(
                    "Vera quando la richiesta fallisce (rete assente, timeout, " +
                        "server irraggiungibile). Utile per rilevare la perdita di connettivita'.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Editor dei trigger
// ---------------------------------------------------------------------------

private enum class TriggerKind(val label: String) {
    HTTP("Chiamata HTTP in uscita"),
    SOUND("Riproduci suono (MP3)"),
    NOTIFY("Notifica di sistema"),
    VIBRATE("Vibrazione"),
    FULLSCREEN("Avviso a schermo pieno"),
}

private fun kindOf(config: TriggerConfig): TriggerKind = when (config) {
    is TriggerConfig.HttpCall -> TriggerKind.HTTP
    is TriggerConfig.PlaySound -> TriggerKind.SOUND
    is TriggerConfig.Notify -> TriggerKind.NOTIFY
    is TriggerConfig.Vibrate -> TriggerKind.VIBRATE
    is TriggerConfig.FullScreenAlert -> TriggerKind.FULLSCREEN
}

private fun defaultTrigger(kind: TriggerKind): TriggerConfig = when (kind) {
    TriggerKind.HTTP -> TriggerConfig.HttpCall(urlTemplate = "https://")
    TriggerKind.SOUND -> TriggerConfig.PlaySound(uri = "")
    TriggerKind.NOTIFY -> TriggerConfig.Notify(title = "Avviso", message = "Regola scattata su {{monitor}}")
    TriggerKind.VIBRATE -> TriggerConfig.Vibrate()
    TriggerKind.FULLSCREEN -> TriggerConfig.FullScreenAlert(
        title = "ATTENZIONE",
        message = "Regola scattata su {{monitor}}",
    )
}

@Composable
private fun TriggerCard(
    trigger: TriggerDef,
    ruleIndex: Int,
    triggerIndex: Int,
    viewModel: MonitorEditViewModel,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Trigger ${triggerIndex + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = trigger.enabled,
                    onCheckedChange = { v ->
                        viewModel.updateTrigger(ruleIndex, triggerIndex) { it.copy(enabled = v) }
                    },
                )
                IconButton(onClick = { viewModel.removeTrigger(ruleIndex, triggerIndex) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Elimina trigger",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            EnumDropdown(
                label = "Tipo di trigger",
                options = TriggerKind.entries.map { it to it.label },
                selected = kindOf(trigger.config),
                onSelected = { kind ->
                    viewModel.updateTrigger(ruleIndex, triggerIndex) {
                        it.copy(config = defaultTrigger(kind))
                    }
                },
            )

            TriggerConfigEditor(
                config = trigger.config,
                onChange = { cfg ->
                    viewModel.updateTrigger(ruleIndex, triggerIndex) { it.copy(config = cfg) }
                },
            )

            // Politica di scatto (spec §3.3: edge-trigger + cooldown / ripeti).
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    value = trigger.cooldownSeconds,
                    onValueChange = { v ->
                        viewModel.updateTrigger(ruleIndex, triggerIndex) { it.copy(cooldownSeconds = v) }
                    },
                    label = "Cooldown (s)",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = trigger.repeatWhileTrue,
                    onCheckedChange = { v ->
                        viewModel.updateTrigger(ruleIndex, triggerIndex) { it.copy(repeatWhileTrue = v) }
                    },
                )
                Text("Ripeti finche' la condizione resta vera")
            }
            if (trigger.repeatWhileTrue) {
                NumberField(
                    value = trigger.repeatIntervalSeconds,
                    onValueChange = { v ->
                        viewModel.updateTrigger(ruleIndex, triggerIndex) {
                            it.copy(repeatIntervalSeconds = v)
                        }
                    },
                    label = "Intervallo ripetizione (s)",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TriggerConfigEditor(
    config: TriggerConfig,
    onChange: (TriggerConfig) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (config) {
            is TriggerConfig.HttpCall -> {
                OutlinedTextField(
                    value = config.urlTemplate,
                    onValueChange = { onChange(config.copy(urlTemplate = it)) },
                    label = { Text("URL (supporta {{placeholder}})") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                EnumDropdown(
                    label = "Metodo",
                    options = HttpMethod.entries.map { it to it.name },
                    selected = config.method,
                    onSelected = { onChange(config.copy(method = it)) },
                )
                OutlinedTextField(
                    value = config.bodyTemplate.orEmpty(),
                    onValueChange = { onChange(config.copy(bodyTemplate = it.ifBlank { null })) },
                    label = { Text("Body (supporta {{placeholder}})") },
                    modifier = Modifier.fillMaxWidth(),
                )
                HeaderListEditor(
                    headers = config.headers,
                    onChange = { onChange(config.copy(headers = it)) },
                )
            }
            is TriggerConfig.PlaySound -> {
                SoundPicker(
                    uri = config.uri,
                    onUriChange = { onChange(config.copy(uri = it)) },
                )
                Text("Volume: ${config.volumePercent}%")
                Slider(
                    value = config.volumePercent / 100f,
                    onValueChange = { onChange(config.copy(volumePercent = (it * 100).roundToInt())) },
                )
            }
            is TriggerConfig.Notify -> {
                OutlinedTextField(
                    value = config.title,
                    onValueChange = { onChange(config.copy(title = it)) },
                    label = { Text("Titolo") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = config.message,
                    onValueChange = { onChange(config.copy(message = it)) },
                    label = { Text("Messaggio (supporta {{placeholder}})") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is TriggerConfig.Vibrate -> {
                OutlinedTextField(
                    value = config.pattern.joinToString(","),
                    onValueChange = { v ->
                        val pattern = v.split(",")
                            .mapNotNull { it.trim().toLongOrNull() }
                            .ifEmpty { listOf(0L, 500L) }
                        onChange(config.copy(pattern = pattern))
                    },
                    label = { Text("Pattern ms (attesa,vibra,attesa,vibra...)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            is TriggerConfig.FullScreenAlert -> {
                OutlinedTextField(
                    value = config.title,
                    onValueChange = { onChange(config.copy(title = it)) },
                    label = { Text("Titolo") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = config.message,
                    onValueChange = { onChange(config.copy(message = it)) },
                    label = { Text("Messaggio (supporta {{placeholder}})") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = config.colorHex,
                        onValueChange = { onChange(config.copy(colorHex = it)) },
                        label = { Text("Colore (#RRGGBB)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    NumberField(
                        value = config.durationSeconds,
                        onValueChange = { onChange(config.copy(durationSeconds = it)) },
                        label = "Durata (s)",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Selettore di file audio via Storage Access Framework (spec §3.3.2):
 * ACTION_OPEN_DOCUMENT + permesso URI persistente, nessun permesso storage.
 */
@Composable
private fun SoundPicker(uri: String, onUriChange: (String) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { selectedUri ->
        if (selectedUri != null) {
            // Permesso persistente: l'URI resta leggibile anche dopo il reboot.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    selectedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onUriChange(selectedUri.toString())
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { launcher.launch(arrayOf("audio/*")) }) {
            Text("Scegli file audio")
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (uri.isBlank()) "Nessun file selezionato" else "File selezionato",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// ---------------------------------------------------------------------------
// Editor riusabile della lista header (monitor e trigger HTTP)
// ---------------------------------------------------------------------------

@Composable
fun HeaderListEditor(
    headers: List<HeaderEntry>,
    onChange: (List<HeaderEntry>) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Header personalizzati",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onChange(headers + HeaderEntry(name = "", value = "")) }) {
                Icon(Icons.Default.Add, contentDescription = "Aggiungi header")
            }
        }
        headers.forEach { header ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = header.name,
                    onValueChange = { v ->
                        onChange(headers.map { if (it.id == header.id) it.copy(name = v) else it })
                    },
                    label = { Text("Nome") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = header.value,
                    onValueChange = { v ->
                        onChange(headers.map { if (it.id == header.id) it.copy(value = v) else it })
                    },
                    label = { Text(if (header.secret) "Valore (cifrato)" else "Valore") },
                    modifier = Modifier.weight(1.2f),
                    singleLine = true,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Checkbox(
                        checked = header.secret,
                        onCheckedChange = { v ->
                            onChange(headers.map { if (it.id == header.id) it.copy(secret = v) else it })
                        },
                    )
                    Text("Segreto", style = MaterialTheme.typography.labelLarge)
                }
                IconButton(
                    onClick = { onChange(headers.filterNot { it.id == header.id }) },
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Rimuovi header")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Componenti di base riusabili
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> EnumDropdown(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: selected.toString()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun NumberField(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { v -> v.toIntOrNull()?.let(onValueChange) },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

package it.mavida.dashboardalert.ui.alert

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Avviso visivo a schermo pieno (spec §3.3.5): overlay colorato ben visibile
 * da lontano, con fade-out automatico dopo N secondi.
 *
 * Viene mostrata sopra qualsiasi altra schermata (inclusa la modalita'
 * browser kiosk) perche' e' un'Activity a parte, lanciata via full-screen
 * intent dalla notifica del trigger.
 *
 * Manifest: showWhenLocked + turnScreenOn, cosi' l'avviso appare anche a
 * schermo spento/bloccato (scenario kiosk sempre acceso).
 */
class AlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Doppia ridondanza (manifest + flag runtime) per massima compatibilita'
        // tra versioni Android e OEM.
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
        )

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        val colorHex = intent.getStringExtra(EXTRA_COLOR) ?: "#D32F2F"
        val durationSeconds = intent.getIntExtra(EXTRA_DURATION, 30)

        val background = runCatching { Color(AndroidColor.parseColor(colorHex)) }
            .getOrDefault(Color(0xFFD32F2F))

        setContent {
            AlertScreen(
                title = title,
                message = message,
                background = background,
                durationSeconds = durationSeconds,
                onDismiss = { finish() },
            )
        }
    }

    companion object {
        const val EXTRA_TITLE = "alert_title"
        const val EXTRA_MESSAGE = "alert_message"
        const val EXTRA_COLOR = "alert_color"
        const val EXTRA_DURATION = "alert_duration"
    }
}

@Composable
private fun AlertScreen(
    title: String,
    message: String,
    background: Color,
    durationSeconds: Int,
    onDismiss: () -> Unit,
) {
    var remaining by remember { mutableIntStateOf(durationSeconds.coerceAtLeast(1)) }
    var fadingOut by remember { mutableStateOf(false) }

    // Countdown: negli ultimi 2 secondi parte il fade-out, poi la chiusura.
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining--
            if (remaining <= 2 && !fadingOut) fadingOut = true
        }
        onDismiss()
    }

    val alpha by animateFloatAsState(
        targetValue = if (fadingOut) 0f else 1f,
        animationSpec = tween(durationMillis = 2000),
        label = "fadeOut",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = title.ifBlank { "AVVISO" },
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                color = Color.White,
                fontSize = 28.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = background,
                ),
            ) {
                Text("Chiudi ($remaining)", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

package de.tapcontrol

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Screen() }
            }
        }
    }
}

@Composable
private fun Screen() {
    val context = LocalContext.current
    val prefs = remember { TapState.prefs(context) }

    val running by TapState.running.collectAsStateWithLifecycle()
    val lastGesture by TapState.lastGesture.collectAsStateWithLifecycle()
    val lastStrength by TapState.lastTapStrength.collectAsStateWithLifecycle()

    var threshold by remember {
        mutableFloatStateOf(prefs.getFloat(TapState.KEY_THRESHOLD, TapDetector.DEFAULT_THRESHOLD))
    }
    var vibrate by remember { mutableStateOf(prefs.getBoolean(TapState.KEY_VIBRATE, true)) }
    var screenOffOnly by remember { mutableStateOf(prefs.getBoolean(TapState.KEY_SCREEN_OFF_ONLY, true)) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { TapService.start(context) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("TapControl", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Klopfe ans Handy, während Spotify (oder ein anderer Player) läuft:\n" +
                "1× Play/Pause · 2× Nächster Titel · 3× Titel zurück",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (running) {
            OutlinedButton(onClick = { TapService.stop(context) }, Modifier.fillMaxWidth()) {
                Text("Klopfsteuerung stoppen")
            }
        } else {
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= 33) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    TapService.start(context)
                }
            }, Modifier.fillMaxWidth()) {
                Text("Klopfsteuerung starten")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Schwelle: %.1f m/s²".format(threshold), style = MaterialTheme.typography.titleMedium)
                Text(
                    "Weiter links = empfindlicher, aber mehr Fehlauslöser.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = threshold,
                    onValueChange = {
                        threshold = it
                        prefs.edit().putFloat(TapState.KEY_THRESHOLD, it).apply()
                    },
                    valueRange = 1.5f..12f,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Vibration als Bestätigung", Modifier.weight(1f))
                    Switch(checked = vibrate, onCheckedChange = {
                        vibrate = it
                        prefs.edit().putBoolean(TapState.KEY_VIBRATE, it).apply()
                    })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Nur bei ausgeschaltetem Display")
                        Text(
                            "Zum Testen und Einstellen der Schwelle ausschalten.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = screenOffOnly, onCheckedChange = {
                        screenOffOnly = it
                        prefs.edit().putBoolean(TapState.KEY_SCREEN_OFF_ONLY, it).apply()
                    })
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Zuletzt erkannt", style = MaterialTheme.typography.titleMedium)
                Text(lastGesture ?: "–")
                Text(
                    "Stärke des letzten Klopfers: " +
                        (lastStrength?.let { "%.1f m/s²".format(it) } ?: "–"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            "Tipp: Schalte „Nur bei ausgeschaltetem Display“ kurz aus, leg das Handy hin und klopf " +
                "ein paar Mal, um die Stärke abzulesen. Stell die Schwelle dann knapp unter diesen Wert.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

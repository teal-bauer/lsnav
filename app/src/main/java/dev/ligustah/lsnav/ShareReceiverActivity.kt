package dev.ligustah.lsnav

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class ShareReceiverActivity : ComponentActivity() {
    private val model: ShareViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        configureNavigationWindow(window, dark)
        val text = runCatching { MapsIntentParser.extractIntentText(intent) }.getOrNull()
        if (text.isNullOrBlank() || text.length > MapsIntentParser.MAX_INPUT_LENGTH) { finish(); return }
        model.start(text)
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            NavigationTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.background,
                        contentWindowInsets = WindowInsets.safeDrawing
                    ) { padding ->
                        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Confirm destination", style = MaterialTheme.typography.headlineMedium)
                        if (state.sent) Text("Destination sent to ${state.configuration.scooterName}.") else {
                            Text("Nothing is sent until you confirm.")
                            if (state.configuration.isAuthenticated) {
                                Text(if (state.configuration.isReady) "Scooter · ${state.configuration.scooterName}" else "Choose a scooter")
                                ScooterPicker(state.configuration, state.scooters.value.orEmpty(), !state.scooters.loading && !state.sending, model::selectScooter)
                            } else Text("Connect Sunshine to continue. Your shared destination stays here until you retry.")
                            if (state.places.loading || state.sending || state.scooters.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                            state.places.value.orEmpty().forEachIndexed { index, place ->
                                Row(Modifier.fillMaxWidth()) {
                                    RadioButton(state.selected == index, onClick = { model.select(index) }, enabled = !state.sending)
                                    Column(Modifier.padding(top = 12.dp)) { Text(place.label); Text(place.coordinates.display(), style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Button(onClick = model::confirm, enabled = !state.places.loading && !state.sending && state.configuration.isReady && state.places.value.orEmpty().isNotEmpty()) { Text("Send destination") }
                            OutlinedButton(onClick = model::retry, enabled = !state.places.loading && !state.sending) { Text("Retry") }
                        }
                        TextButton(onClick = { startActivity(Intent(this@ShareReceiverActivity, SettingsActivity::class.java)) }, enabled = !state.sending) { Text(if (state.configuration.isAuthenticated) "Account settings" else "Connect Sunshine") }
                        TextButton(onClick = { finish() }) { Text(if (state.sent) "Close" else "Cancel") }
                        }
                    }
                }
            }
        }
    }
}

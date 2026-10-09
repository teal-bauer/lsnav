package dev.ligustah.lsnav

import android.content.Intent
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
        val text = runCatching {
            MapsIntentParser.extractIntentText(intent)
        }.getOrNull()
        if (text == null || text.isBlank() || text.length > MapsIntentParser.MAX_INPUT_LENGTH) {
            finish()
            return
        }
        model.start(text)
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            MaterialTheme {
                Scaffold { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Confirm destination", style = MaterialTheme.typography.headlineSmall)
                        if (state.sent) Text("Destination sent to scooter.") else {
                            ScooterPicker(state.configuration, state.scooters, !state.loading && !state.sending, model::selectScooter)
                            if (state.configuration.isAuthenticated && state.configuration.scooterId == null) {
                                Text(if (state.scooters.isEmpty()) "No accessible scooters loaded. Retry or check Settings." else "Choose a scooter before sending.")
                            }
                            if (state.loading || state.sending) CircularProgressIndicator()
                            state.places.forEachIndexed { index, place ->
                                Row(Modifier.fillMaxWidth()) {
                                    RadioButton(state.selected == index, onClick = { model.select(index) }, enabled = !state.sending)
                                    Text("${place.label}\n${place.coordinates.latitude}, ${place.coordinates.longitude}", Modifier.padding(top = 12.dp))
                                }
                            }
                            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Button(onClick = model::confirm, enabled = !state.loading && !state.sending && state.configuration.isReady && state.places.isNotEmpty()) { Text("Send destination") }
                            OutlinedButton(onClick = model::retry, enabled = !state.loading && !state.sending) { Text("Resolve again / retry") }
                        }
                        TextButton(onClick = { startActivity(Intent(this@ShareReceiverActivity, SettingsActivity::class.java)) }, enabled = !state.sending) { Text("Settings") }
                        TextButton(onClick = { finish() }) { Text(if (state.sent) "Close" else "Cancel") }
                    }
                }
            }
        }
    }
}

package dev.ligustah.lsnav

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsScreen(model: SettingsViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Librescoot Navigation Settings", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(state.draft.baseUrl, model::updateUrl, label = { Text("HTTPS server URL") },
            enabled = !state.loading && !state.saving, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.draft.token, model::updateToken, label = { Text("API token") },
            visualTransformation = PasswordVisualTransformation(), enabled = !state.loading && !state.saving,
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = model::fetch, enabled = !state.loading && !state.saving && state.draft.token.isNotBlank()) { Text("Fetch scooters / retry") }
        if (state.loading || state.saving) CircularProgressIndicator()
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.scooters.isNotEmpty()) Text("Select scooter:")
        state.scooters.forEach { scooter ->
            Row(Modifier.fillMaxWidth()) {
                RadioButton(selected = scooter.id == state.draft.scooterId,
                    onClick = { model.select(scooter) }, enabled = !state.saving && scooter.id != null)
                Text(scooter.name, Modifier.padding(top = 12.dp))
            }
        }
        if (state.draft.scooterId != null) Text("Selected: ${state.draft.scooterName}")
        Button(onClick = model::save, enabled = !state.loading && !state.saving, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        state.message?.let { Text(it) }
    }
}

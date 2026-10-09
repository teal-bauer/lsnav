package dev.ligustah.lsnav

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsScreen(model: SettingsViewModel = viewModel(), onSignIn: (String, String) -> Unit = { _, _ -> }, onBack: () -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    var advanced by rememberSaveable { mutableStateOf(false) }
    var editConnection by rememberSaveable { mutableStateOf(false) }
    val authenticated = state.profile.configuration.isAuthenticated
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text("‹  ${if (authenticated) "Back" else "Welcome"}") }
        Text(if (authenticated) "Account & preferences" else "Connect Sunshine", style = MaterialTheme.typography.headlineMedium)
        if (!authenticated) {
            Text("Sign in securely in your browser. LSNav uses Sunshine to find your scooters and places.")
            Button(onClick = { if (model.beginLogin()) onSignIn(state.draft.baseUrl, state.draft.oauthClientId) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Continue with Sunshine") }
            TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide advanced options" else "Advanced connection options") }
            if (advanced) {
                OutlinedTextField(state.draft.baseUrl, model::updateUrl, label = { Text("Sunshine server (HTTPS)") }, enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(state.draft.oauthClientId, model::updateClientId, label = { Text("Public OAuth client ID") }, enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                HorizontalDivider()
                Text("Manual token", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(state.draft.token, model::updateToken, label = { Text("API token") }, visualTransformation = PasswordVisualTransformation(),
                    enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = model::connectManual, enabled = !state.busy && state.draft.token.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Connect with token") }
            }
        } else {
            Text("Connected to ${state.profile.configuration.baseUrl}", style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { editConnection = !editConnection }) { Text(if (editConnection) "Hide connection options" else "Change connection or reconnect") }
            if (editConnection) {
                OutlinedTextField(state.draft.baseUrl, model::updateUrl, label = { Text("Sunshine server (HTTPS)") }, enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(state.draft.oauthClientId, model::updateClientId, label = { Text("Public OAuth client ID") }, enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { if (model.beginLogin()) onSignIn(state.draft.baseUrl, state.draft.oauthClientId) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Reconnect with Sunshine") }
                HorizontalDivider()
                Text("Or replace the connection with a manual API token.")
                OutlinedTextField(state.draft.token, model::updateToken, label = { Text("API token") }, visualTransformation = PasswordVisualTransformation(),
                    enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = model::connectManual, enabled = !state.busy && state.draft.token.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Connect with token") }
            }
            Text("Choose an optional default scooter. You can choose a different scooter for each destination.")
            Text("Default scooter", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth()) {
                RadioButton(selected = state.draft.scooterId == null, onClick = model::chooseOnDemand, enabled = !state.busy)
                Text("Choose each time", Modifier.padding(top = 12.dp))
            }
            state.scooters.value.orEmpty().forEach { scooter ->
                Row(Modifier.fillMaxWidth()) {
                    RadioButton(selected = scooter.id == state.draft.scooterId, onClick = { model.select(scooter) }, enabled = !state.busy)
                    Text(scooter.name, Modifier.padding(top = 12.dp))
                }
            }
            if (state.scooters.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.scooters.error?.let { error -> Row(horizontalArrangement = Arrangement.SpaceBetween) {
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f)); TextButton(onClick = { model.fetch(true) }) { Text("Retry") }
            } }
            if (state.scooters.value == null && state.scooters.error == null && !state.scooters.loading) TextButton(onClick = { model.fetch(true) }) { Text("Load scooters") }
            Button(onClick = model::save, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Save preference") }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            TextButton(onClick = model::signOut, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

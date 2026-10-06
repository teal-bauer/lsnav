package dev.ligustah.lsnav

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun NavigationScreen(model: NavigationViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var clearRequested by rememberSaveable { mutableStateOf(false) }
    val destination = state.destination

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Navigation", style = MaterialTheme.typography.headlineSmall)
        if (!state.configuration.isReady) {
            Text("Enter your API token and select a scooter in Settings.")
        } else {
            Text("Scooter: ${state.configuration.scooterName.ifBlank { state.configuration.scooterId.toString() }}")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current destination", style = MaterialTheme.typography.titleMedium)
                    if (state.loading) CircularProgressIndicator()
                    else if (destination?.latitude != null && destination.longitude != null) {
                        Text(destination.address ?: "${destination.latitude}, ${destination.longitude}")
                        OutlinedButton(onClick = { clearRequested = true }, enabled = !state.sending) { Text("Clear destination") }
                    } else if (state.error == null) Text("No active destination.")
                    OutlinedButton(onClick = model::refresh, enabled = !state.loading && !state.sending) { Text("Refresh") }
                }
            }
            OutlinedTextField(value = state.query, onValueChange = model::updateQuery,
                label = { Text("Address, coordinates, or Maps link") }, modifier = Modifier.fillMaxWidth(),
                enabled = !state.sending)
            Button(onClick = model::search, enabled = state.query.isNotBlank() && !state.searching && !state.sending) { Text("Search / retry") }
            if (state.searching || state.sending) CircularProgressIndicator()
            state.results.forEachIndexed { index, place ->
                OutlinedButton(onClick = { selected = index }, enabled = !state.sending, modifier = Modifier.fillMaxWidth()) {
                    Text("${place.label}\n${place.coordinates.latitude}, ${place.coordinates.longitude}")
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    selected?.let { index ->
        state.results.getOrNull(index)?.let { place ->
            AlertDialog(onDismissRequest = { selected = null }, title = { Text("Send destination?") },
                text = { Text("${state.configuration.scooterName}\n${place.label}\n${place.coordinates.latitude}, ${place.coordinates.longitude}") },
                confirmButton = { TextButton(onClick = { selected = null; model.send(place) }, enabled = !state.sending) { Text("Send") } },
                dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } })
        }
    }
    if (clearRequested) AlertDialog(onDismissRequest = { clearRequested = false },
        title = { Text("Clear destination?") }, text = { Text("Scooter: ${state.configuration.scooterName}") },
        confirmButton = { TextButton(onClick = { clearRequested = false; model.send(null) }, enabled = !state.sending) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { clearRequested = false }) { Text("Cancel") } })
}

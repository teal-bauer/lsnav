package dev.ligustah.lsnav

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun NavigationScreen(model: NavigationViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<Pair<AppConfiguration, PlaceResult>?>(null) }
    var pendingClear by remember { mutableStateOf<AppConfiguration?>(null) }
    val destination = state.destination
    LaunchedEffect(state.configuration) {
        pending = null
        pendingClear = null
    }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Navigation", style = MaterialTheme.typography.headlineSmall)
        if (!state.configuration.isAuthenticated) {
            Text("Sign in or enter an API token in Settings.")
        } else {
            ScooterPicker(state.configuration, state.scooters, !state.sending && !state.loadingScooters, model::selectScooter)
            if (state.loadingScooters) CircularProgressIndicator()
            OutlinedButton(onClick = model::fetchScooters, enabled = !state.loadingScooters && !state.sending) { Text("Fetch scooters / retry") }
            if (!state.configuration.isReady) Text("Choose a scooter before sending a destination. No default scooter is required.")
            if (state.configuration.isReady) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Current destination", style = MaterialTheme.typography.titleMedium)
                        if (state.loading) CircularProgressIndicator()
                        else if (destination?.latitude != null && destination.longitude != null) {
                            Text(destination.address ?: "${destination.latitude}, ${destination.longitude}")
                            OutlinedButton(onClick = { pendingClear = state.configuration }, enabled = !state.sending) { Text("Clear destination") }
                        } else if (state.error == null) Text("No active destination.")
                        OutlinedButton(onClick = model::refresh, enabled = !state.loading && !state.sending) { Text("Refresh") }
                    }
                }
                Text("Saved locations", style = MaterialTheme.typography.titleMedium)
                if (!state.loading && state.savedLocations.isEmpty() && state.savedLocationsError == null) Text("No saved locations on this scooter.")
                state.savedLocationsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.savedLocations.forEach { place ->
                    OutlinedButton(onClick = { pending = state.configuration to place }, enabled = !state.sending && !state.loading, modifier = Modifier.fillMaxWidth()) {
                        Text("${place.label}\n${place.coordinates.latitude}, ${place.coordinates.longitude}")
                    }
                }
            }
            OutlinedTextField(value = state.query, onValueChange = model::updateQuery,
                label = { Text("Address, coordinates, or Maps link") }, modifier = Modifier.fillMaxWidth(), enabled = !state.sending)
            Button(onClick = model::search, enabled = state.query.isNotBlank() && !state.searching && !state.sending) { Text("Search / retry") }
            if (state.searching || state.sending) CircularProgressIndicator()
            state.results.forEach { place ->
                OutlinedButton(onClick = { pending = state.configuration to place }, enabled = !state.sending && state.configuration.isReady, modifier = Modifier.fillMaxWidth()) {
                    Text("${place.label}\n${place.coordinates.latitude}, ${place.coordinates.longitude}")
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    pending?.takeIf { it.first == state.configuration }?.let { (configuration, place) ->
        AlertDialog(onDismissRequest = { pending = null }, title = { Text("Send destination?") },
            text = { Text("${configuration.scooterName}\n${place.label}\n${place.coordinates.latitude}, ${place.coordinates.longitude}") },
            confirmButton = { TextButton(onClick = { pending = null; model.send(place, configuration) }, enabled = !state.sending) { Text("Send") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } })
    }
    pendingClear?.takeIf { it == state.configuration }?.let { configuration ->
        AlertDialog(onDismissRequest = { pendingClear = null }, title = { Text("Clear destination?") },
            text = { Text("Scooter: ${configuration.scooterName}") },
            confirmButton = { TextButton(onClick = { pendingClear = null; model.send(null, configuration) }, enabled = !state.sending) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { pendingClear = null }) { Text("Cancel") } })
    }
}

package dev.ligustah.lsnav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun NavigationScreen(onSettings: () -> Unit, model: NavigationViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<Pair<AppConfiguration, PlaceResult>?>(null) }
    var pendingClear by remember { mutableStateOf<AppConfiguration?>(null) }
    LaunchedEffect(state.configuration.accountKey) { pending = null; pendingClear = null }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("Navigate", style = MaterialTheme.typography.headlineMedium); Text("Choose a place to send to your scooter") }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Account and settings") }
        }
        if (!state.configuration.isAuthenticated) {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Welcome to LSNav", style = MaterialTheme.typography.titleLarge)
                Text("Connect your Sunshine account to find your scooters and navigate.")
                Button(onClick = onSettings) { Text("Get started") }
            } }
        } else {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (state.configuration.isReady) "Scooter · ${state.configuration.scooterName}" else "Choose a scooter", style = MaterialTheme.typography.titleMedium)
                ScooterPicker(state.configuration, state.scooters.value.orEmpty(), !state.sending && !state.scooters.loading, model::selectScooter)
                if (state.scooters.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                ResourceFeedback(state.scooters.error, state.scooters.value != null, { model.fetchScooters(true) })
            } }
            OutlinedTextField(value = state.query, onValueChange = model::updateQuery, label = { Text("Search places or paste a map link") },
                modifier = Modifier.fillMaxWidth(), enabled = !state.sending, singleLine = true)
            Button(onClick = model::search, enabled = state.query.isNotBlank() && !state.searching && !state.sending, modifier = Modifier.fillMaxWidth()) { Text(if (state.searching) "Searching…" else "Search") }
            if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.results.forEach { place -> PlaceRow(place.label, place.coordinates.display(), onClick = { pending = state.configuration.accountKey to place }) }

            val personal = state.personalPlaces
            if (personal.loading && personal.value == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            ResourceFeedback(personal.error, personal.value != null, { model.refreshPersonalPlaces(true) })
            personal.value?.let { saved ->
                if (saved.favorites.isNotEmpty()) {
                    Text("Favorites", style = MaterialTheme.typography.titleLarge)
                    saved.favorites.forEach { row -> PlaceRow(row.place.label, row.place.coordinates.display(), { pending = state.configuration to row.place }) }
                }
                if (saved.recent.isNotEmpty()) {
                    Text("Recent", style = MaterialTheme.typography.titleLarge)
                    saved.recent.forEach { row -> PlaceRow(row.place.label, row.place.coordinates.display(), { pending = state.configuration to row.place }) }
                }
                if (saved.favorites.isEmpty() && saved.recent.isEmpty()) Text("Your favorites and recent places will appear here.")
            }
            if (state.configuration.isReady) {
                Text("Scooter places", style = MaterialTheme.typography.titleLarge)
                val places = state.savedLocations
                if (places.loading && places.value == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                ResourceFeedback(places.error, places.value != null, { model.refresh() })
                places.value?.let { list ->
                    if (list.isEmpty()) Text("No places saved on this scooter.")
                    list.forEach { place -> PlaceRow(place.label, place.coordinates.display(), { pending = state.configuration.accountKey to place }) }
                }
                val destination = state.destination
                Text("Current destination", style = MaterialTheme.typography.titleLarge)
                when {
                    destination.loading && destination.value == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    destination.error != null -> ResourceFeedback(destination.error, destination.value != null, { model.refresh() })
                    destination.value?.place != null -> Text(destination.value!!.place!!.label)
                    else -> Text("No active destination")
                }
                OutlinedButton(onClick = { pendingClear = state.configuration }, enabled = !state.sending && destination.value?.place != null) { Text("Clear destination") }
            }
            if (state.sending) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    pending?.takeIf { it.first == state.configuration.accountKey }?.let { (_, place) ->
        AlertDialog(onDismissRequest = { pending = null }, title = { Text("Confirm destination") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(place.label); Text(place.coordinates.display())
                Text("Send to ${state.configuration.scooterName.ifBlank { "a scooter you choose" }}")
                ScooterPicker(state.configuration, state.scooters.value.orEmpty(), !state.sending, model::selectScooter)
            } },
            confirmButton = { TextButton(onClick = { pending = null; model.send(place, state.configuration) }, enabled = !state.sending && state.configuration.isReady) { Text("Send destination") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } })
    }
    pendingClear?.takeIf { it == state.configuration }?.let { configuration ->
        AlertDialog(onDismissRequest = { pendingClear = null }, title = { Text("Clear destination?") }, text = { Text("Scooter: ${configuration.scooterName}") },
            confirmButton = { TextButton(onClick = { pendingClear = null; model.send(null, configuration) }, enabled = !state.sending) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { pendingClear = null }) { Text("Cancel") } })
    }
}

@Composable
private fun PlaceRow(title: String, detail: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalAlignment = Alignment.Start) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ResourceFeedback(error: String?, hasValue: Boolean, retry: () -> Unit) {
    if (error != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(if (hasValue) "Showing saved data · $error" else error, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
        TextButton(onClick = retry) { Text("Retry") }
    }
}

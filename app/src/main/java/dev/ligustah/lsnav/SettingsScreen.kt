package dev.ligustah.lsnav

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.ligustah.lsnav.api.generated.models.Scooter

@Composable
fun SettingsScreen(appSettings: AppSettings) {
    val coroutineScope = rememberCoroutineScope()
    
    val initialToken by appSettings.token.collectAsState(initial = "")
    val initialBaseUrl by appSettings.baseUrl.collectAsState(initial = AppSettings.DEFAULT_BASE_URL)
    val initialScooterId by appSettings.scooterId.collectAsState(initial = null)
    val initialScooterName by appSettings.scooterName.collectAsState(initial = "")

    var token by remember(initialToken) { mutableStateOf(initialToken ?: "") }
    var baseUrl by remember(initialBaseUrl) { mutableStateOf(initialBaseUrl) }
    var scooters by remember { mutableStateOf<List<Scooter>>(emptyList()) }
    var selectedScooterId by remember(initialScooterId) { mutableStateOf<Long?>(initialScooterId) }
    var selectedScooterName by remember(initialScooterName) { mutableStateOf<String>(initialScooterName ?: "") }

    var isFetching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("LibreScoot Navigation Settings", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Base URL") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("API Token") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                isFetching = true
                errorMessage = null
                coroutineScope.launch {
                    try {
                        val api = ApiClientProvider(baseUrl, token).getScootersApi()
                        val result = withContext(Dispatchers.IO) {
                            api.listScooters()
                        }
                        scooters = result
                        isFetching = false
                    } catch (e: Exception) {
                        e.printStackTrace()
                        errorMessage = "Failed to fetch scooters: ${e.message}"
                        isFetching = false
                    }
                }
            },
            enabled = token.isNotEmpty() && baseUrl.isNotEmpty() && !isFetching,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isFetching) "Fetching..." else "Fetch Scooters")
        }

        if (errorMessage != null) {
            Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (scooters.isNotEmpty()) {
            Text("Select Scooter:")
            scooters.forEach { scooter ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    RadioButton(
                        selected = scooter.id == selectedScooterId,
                        onClick = {
                            selectedScooterId = scooter.id
                            selectedScooterName = scooter.name ?: "Unknown"
                        }
                    )
                    Text(scooter.name ?: "Unknown Scooter ID: ${scooter.id}", modifier = Modifier.padding(start = 8.dp, top = 12.dp))
                }
            }
        } else if (selectedScooterName.isNotEmpty()) {
            Text("Selected: $selectedScooterName")
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                coroutineScope.launch {
                    appSettings.saveToken(token)
                    appSettings.saveBaseUrl(baseUrl)
                    selectedScooterId?.let { id ->
                        appSettings.saveScooter(id, selectedScooterName)
                    }
                    successMessage = "Settings saved!"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }

        if (successMessage != null) {
            Text(successMessage!!, color = MaterialTheme.colorScheme.primary)
        }
    }
}

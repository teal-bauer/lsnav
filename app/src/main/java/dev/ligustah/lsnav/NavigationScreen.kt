package dev.ligustah.lsnav

import android.location.Address
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ligustah.lsnav.api.generated.models.Destination
import dev.ligustah.lsnav.api.generated.models.DestinationInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NavigationScreen(appSettings: AppSettings) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val token by appSettings.token.collectAsState(initial = "")
    val baseUrl by appSettings.baseUrl.collectAsState(initial = "")
    val scooterId by appSettings.scooterId.collectAsState(initial = null)
    
    var isFetching by remember { mutableStateOf(false) }
    var currentDestination by remember { mutableStateOf<Destination?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var destinationInputText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Address>>(emptyList()) }
    
    if (token.isNullOrBlank() || baseUrl.isNullOrBlank() || scooterId == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(
                text = "Please set up your API Token and select a Scooter in Settings first.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val safeScooterId = scooterId!!
    val resolver = remember { CoordinateResolver(context) }
    
    LaunchedEffect(safeScooterId) {
        isFetching = true
        try {
            val api = ApiClientProvider(baseUrl!!, token!!).getNavigationApi()
            currentDestination = withContext(Dispatchers.IO) { api.getDestination(safeScooterId) }
        } catch (e: Exception) {
            errorMessage = "Failed to load destination: ${e.message}"
        } finally {
            isFetching = false
        }
    }
    
    // Auto-search Debouncer
    LaunchedEffect(destinationInputText) {
        if (destinationInputText.length > 2) {
            delay(500) // Debounce half a second
            searchResults = resolver.searchLocalAddress(destinationInputText)
        } else {
            searchResults = emptyList()
        }
    }
    
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("Navigation Dashboard", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Current Destination", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                if (isFetching && currentDestination == null) {
                    CircularProgressIndicator()
                } else if (currentDestination?.latitude != null && currentDestination?.longitude != null) {
                    Text("Lat: ${currentDestination?.latitude}")
                    Text("Lng: ${currentDestination?.longitude}")
                    if (currentDestination?.address != null) {
                        Text("Address: ${currentDestination?.address}")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    isFetching = true
                                    val api = ApiClientProvider(baseUrl!!, token!!).getNavigationApi()
                                    withContext(Dispatchers.IO) { api.clearDestination(safeScooterId) }
                                    currentDestination = null
                                } catch (e: Exception) {
                                    errorMessage = "Failed to clear: ${e.message}"
                                } finally {
                                    isFetching = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Clear Destination")
                    }
                } else {
                    Text("No active destination.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Set Manually", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = destinationInputText,
            onValueChange = { destinationInputText = it },
            label = { Text("Search location or coords in URL form") },
            modifier = Modifier.fillMaxWidth()
        )
        
        if (searchResults.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    searchResults.forEachIndexed { index, address ->
                        val addressString = (0..address.maxAddressLineIndex)
                            .mapNotNull { address.getAddressLine(it) }
                            .joinToString(", ")
                            .takeIf { it.isNotBlank() } ?: address.featureName ?: "Unknown Location"
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        try {
                                            isFetching = true
                                            errorMessage = null
                                            val newDest = DestinationInput(latitude = address.latitude, longitude = address.longitude, address = addressString)
                                            val api = ApiClientProvider(baseUrl!!, token!!).getNavigationApi()
                                            withContext(Dispatchers.IO) { api.setDestination(safeScooterId, newDest) }
                                            currentDestination = Destination(latitude = newDest.latitude, longitude = newDest.longitude, address = newDest.address)
                                            destinationInputText = ""
                                            searchResults = emptyList()
                                        } catch (e: Exception) {
                                            errorMessage = "Failed to set destination: ${e.message}"
                                        } finally {
                                            isFetching = false
                                        }
                                    }
                                }
                                .padding(16.dp)
                        ) {
                            Text(addressString)
                        }
                        if (index < searchResults.size - 1) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
        
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
        }
    }
}

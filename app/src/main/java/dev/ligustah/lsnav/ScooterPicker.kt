package dev.ligustah.lsnav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ligustah.lsnav.api.generated.models.Scooter

@Composable
fun ScooterPicker(configuration: AppConfiguration, scooters: List<Scooter>, enabled: Boolean, onSelect: (Scooter) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled && scooters.isNotEmpty()) {
            Text(if (configuration.scooterId == null) "Choose scooter" else "Scooter: ${configuration.scooterName.ifBlank { configuration.scooterId.toString() }}")
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            scooters.forEach { scooter ->
                DropdownMenuItem(text = { Text(scooter.name) }, onClick = { expanded = false; onSelect(scooter) })
            }
        }
    }
}

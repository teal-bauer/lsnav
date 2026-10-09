package dev.ligustah.lsnav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ScooterPicker(configuration: AppConfiguration, scooters: List<ScooterSummary>, enabled: Boolean, onSelect: (ScooterSummary) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled && scooters.isNotEmpty()) {
            Text(if (configuration.scooterId == null) "Choose a scooter" else configuration.scooterName.ifBlank { "Scooter ${configuration.scooterId}" })
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            scooters.forEach { scooter ->
                DropdownMenuItem(text = { Text(scooter.name) }, onClick = { expanded = false; onSelect(scooter) })
            }
        }
    }
}

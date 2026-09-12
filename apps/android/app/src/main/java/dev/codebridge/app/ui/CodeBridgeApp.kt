package dev.codebridge.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.codebridge.app.data.CodeBridgeSettings
import dev.codebridge.app.data.SettingsStore
import dev.codebridge.app.net.RelayClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CodeBridgeApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ConnectionScreen()
        }
    }
}

@Composable
private fun ConnectionScreen() {
    val context = LocalContext.current
    val store = remember { SettingsStore(context.applicationContext) }
    val relay = remember { RelayClient() }
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(store.read()) }
    var status by remember { mutableStateOf("Ready to pair with your Mac.") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("CodeBridge", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("Android receives codes. Mac copies them.")

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = settings.host,
            onValueChange = { settings = settings.copy(host = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Mac host") },
            placeholder = { Text("192.168.1.8") },
            singleLine = true
        )

        OutlinedTextField(
            value = settings.port,
            onValueChange = { settings = settings.copy(port = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Port") },
            singleLine = true
        )

        OutlinedTextField(
            value = settings.token,
            onValueChange = { settings = settings.copy(token = it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Token") },
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Forward SMS codes")
                Text(
                    if (settings.forwardingEnabled) "Enabled" else "Paused",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = settings.forwardingEnabled,
                onCheckedChange = { settings = settings.copy(forwardingEnabled = it) }
            )
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                store.save(settings)
                status = "Settings saved."
            }
        ) {
            Text("Save")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = settings.isReady,
            onClick = {
                store.save(settings)
                status = "Sending test code..."
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        relay.sendTest(settings)
                    }
                    status = result.fold(
                        onSuccess = { "Test code sent. Check your Mac clipboard." },
                        onFailure = { "Send failed: ${it.message ?: "unknown error"}" }
                    )
                }
            }
        ) {
            Text("Send Test Code")
        }

        Spacer(Modifier.height(8.dp))
        Text(status)

        Spacer(Modifier.height(16.dp))
        Text("Permissions", style = MaterialTheme.typography.titleMedium)
        Text("Grant SMS permissions when Android asks. Some phones also need battery optimization disabled for reliable background delivery.")
    }
}

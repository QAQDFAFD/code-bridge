package dev.codebridge.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.codebridge.app.camera.QrAnalyzer
import dev.codebridge.app.data.CodeBridgeSettings
import dev.codebridge.app.data.PairedDeviceStore
import dev.codebridge.app.data.SettingsStore
import dev.codebridge.app.net.DeviceMonitor
import dev.codebridge.app.net.PairingPayloadParser
import dev.codebridge.app.net.RelayClient
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CodeBridgeApp(monitor: DeviceMonitor) {
    var showScanner by remember { mutableStateOf(false) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (showScanner) {
                ScanPairingScreen(onDone = { showScanner = false })
            } else {
                ConnectionScreen(
                    monitor = monitor,
                    onScan = { showScanner = true }
                )
            }
        }
    }
}

@Composable
private fun ConnectionScreen(monitor: DeviceMonitor, onScan: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context.applicationContext) }
    val deviceStore = remember { PairedDeviceStore(context.applicationContext) }
    val relay = remember { RelayClient() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var settings by remember { mutableStateOf(store.read()) }
    var status by remember { mutableStateOf("Ready to pair with your Mac.") }
    var devices by remember { mutableStateOf(deviceStore.devices()) }
    val connection by monitor.state.collectAsState()

    // Re-read permission state whenever the screen comes back to the foreground
    // (e.g. after returning from system permission settings).
    var permissionsTick by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionsTick = !permissionsTick
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val smsGranted = remember(permissionsTick) { hasSmsPermissions(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsTick = !permissionsTick }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "CodeBridge",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text("Android receives codes. Mac copies them.")

        Text(
            text = when (val state = connection) {
                is DeviceMonitor.State.Connected -> "Connected to ${state.name} (${state.host})"
                is DeviceMonitor.State.NoneReachable -> "Paired Mac not reachable on this Wi-Fi."
                DeviceMonitor.State.Checking -> "Looking for a paired Mac…"
                DeviceMonitor.State.NoDevices -> "Not paired yet — scan your Mac's QR code."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (connection is DeviceMonitor.State.Connected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Button(modifier = Modifier.fillMaxWidth(), onClick = onScan) {
            Text("Scan QR Code to Pair")
        }

        if (devices.isNotEmpty()) {
            Text("Paired Macs", style = MaterialTheme.typography.titleMedium)
            devices.forEach { device ->
                val active = connection is DeviceMonitor.State.Connected &&
                    (connection as DeviceMonitor.State.Connected).host == device.host
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (active) "${device.name}  •  active" else device.name,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Text(
                            "${device.host}:${device.port}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(onClick = {
                        deviceStore.activate(device)
                        settings = store.read()
                        monitor.checkNow()
                    }) { Text("Use") }
                    TextButton(onClick = {
                        deviceStore.remove(device.id)
                        devices = deviceStore.devices()
                        monitor.checkNow()
                    }) { Text("Forget") }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Manual setup", style = MaterialTheme.typography.titleMedium)

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
                focusManager.clearFocus()
                status = "Settings saved."
                monitor.checkNow()
            }
        ) {
            Text("Save")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = settings.isReady,
            onClick = {
                focusManager.clearFocus()
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

        Text(status)

        Spacer(Modifier.height(8.dp))
        Text("Permissions", style = MaterialTheme.typography.titleMedium)
        if (smsGranted) {
            Text("SMS permissions: granted. Codes will be forwarded automatically.")
        } else {
            Text("SMS permissions: missing. Grant them so codes can be forwarded automatically.")
            OutlinedButton(onClick = {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
                )
            }) {
                Text("Grant SMS Permissions")
            }
        }
        Text(
            "Some phones also need battery optimization disabled for reliable background delivery.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ScanPairingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val deviceStore = remember { PairedDeviceStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var message by remember { mutableStateOf("Point the camera at the QR code in your Mac's Settings.") }
    var paired by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    var lastHandledAt by remember { mutableStateOf(0L) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    BackHandler { onDone() }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { analyzerExecutor.shutdown() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Scan Pairing QR", style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        val previewView = PreviewView(viewContext)
                        val providerFuture = ProcessCameraProvider.getInstance(viewContext)
                        providerFuture.addListener({
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(analyzerExecutor, QrAnalyzer { text ->
                                val now = android.os.SystemClock.elapsedRealtime()
                                if (processing || paired || now - lastHandledAt < 2000) return@QrAnalyzer
                                lastHandledAt = now

                                val device = PairingPayloadParser.parse(text)
                                if (device == null) {
                                    message = "Not a CodeBridge QR code."
                                    return@QrAnalyzer
                                }

                                processing = true
                                message = "Pairing with ${device.name} (${device.host})…"
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        RelayClient().ping(
                                            CodeBridgeSettings(
                                                host = device.host,
                                                port = device.port,
                                                token = device.token
                                            )
                                        )
                                    }
                                    result.fold(
                                        onSuccess = { name ->
                                            deviceStore.upsert(device)
                                            deviceStore.activate(device)
                                            paired = true
                                            message = "Paired with $name. Codes will now go to this Mac."
                                        },
                                        onFailure = { error ->
                                            message = "Could not reach ${device.host}: ${error.message ?: "unknown error"}"
                                        }
                                    )
                                    processing = false
                                }
                            })
                            runCatching {
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    viewContext as androidx.lifecycle.LifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis
                                )
                            }
                        }, ContextCompat.getMainExecutor(viewContext))
                        previewView
                    }
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Camera permission required to scan.", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Camera Access")
                    }
                }
            }
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (paired) Toast.makeText(context, "Paired successfully", Toast.LENGTH_SHORT).show()
                onDone()
            }
        ) {
            Text(if (paired) "Done — Paired" else "Close")
        }
    }
}

private fun hasSmsPermissions(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
        PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
        PackageManager.PERMISSION_GRANTED

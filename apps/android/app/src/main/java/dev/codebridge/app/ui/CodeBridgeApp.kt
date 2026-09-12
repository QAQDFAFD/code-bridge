package dev.codebridge.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.codebridge.app.camera.QrAnalyzer
import dev.codebridge.app.data.CodeBridgeSettings
import dev.codebridge.app.data.PairedDeviceStore
import dev.codebridge.app.data.PairedMac
import dev.codebridge.app.data.SettingsStore
import dev.codebridge.app.net.DeviceMonitor
import dev.codebridge.app.net.PairingPayloadParser
import dev.codebridge.app.net.RelayClient
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGE_SCAN_QR = 0
private const val PAGE_MANUAL = 1
private const val PAGE_COUNT = 2

@Composable
fun CodeBridgeApp(monitor: DeviceMonitor) {
    var showAddScreen by remember { mutableStateOf(false) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (showAddScreen) {
                AddMacScreen(
                    monitor = monitor,
                    onDone = { showAddScreen = false }
                )
            } else {
                PairedMacsScreen(
                    monitor = monitor,
                    onAddMac = { showAddScreen = true }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Main screen: paired Macs first, "Add Mac" opens the pairing flow.
// ---------------------------------------------------------------------------

@Composable
private fun PairedMacsScreen(monitor: DeviceMonitor, onAddMac: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context.applicationContext) }
    val deviceStore = remember { PairedDeviceStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var devices by remember { mutableStateOf(deviceStore.devices()) }
    var activeId by remember { mutableStateOf(deviceStore.activeId()) }
    val connection by monitor.state.collectAsState()

    var permissionsTick by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionsTick = !permissionsTick
                devices = deviceStore.devices()
                activeId = deviceStore.activeId()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val smsGranted = remember(permissionsTick) { hasSmsPermissions(context) }
    val ignoringBatteryOptimizations = remember(permissionsTick) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsTick = !permissionsTick }

    fun refresh() {
        devices = deviceStore.devices()
        activeId = deviceStore.activeId()
    }

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
                DeviceMonitor.State.NoDevices -> "No Macs paired yet."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (connection is DeviceMonitor.State.Connected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        if (devices.isEmpty()) {
            Text(
                "Pair your first Mac to get started — scan the QR code shown in its menu bar Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(modifier = Modifier.fillMaxWidth(), onClick = onAddMac) {
                Text("Add Mac")
            }
        } else {
            Text("Paired Macs", style = MaterialTheme.typography.titleMedium)
            devices.forEach { device ->
                PairedMacRow(
                    device = device,
                    isActive = device.id == activeId,
                    isConnected = connection is DeviceMonitor.State.Connected &&
                        (connection as DeviceMonitor.State.Connected).host == device.host,
                    onClick = {
                        deviceStore.activate(device)
                        store.read()
                        refresh()
                        monitor.checkNow()
                    },
                    onForget = {
                        deviceStore.remove(device.id)
                        refresh()
                        monitor.checkNow()
                    }
                )
            }

            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onAddMac) {
                Text("Add Mac")
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Permissions", style = MaterialTheme.typography.titleMedium)
        if (smsGranted) {
            Text(
                "SMS permissions: granted. Codes will be forwarded automatically.",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text(
                "SMS permissions: missing. Grant them so codes can be forwarded automatically.",
                style = MaterialTheme.typography.bodySmall
            )
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
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!ignoringBatteryOptimizations) {
            Text(
                "Battery optimization is still on — the system may kill CodeBridge in the background and codes won't be forwarded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            }) {
                Text("Disable Battery Optimization")
            }
        } else {
            Text(
                "Battery optimization: disabled. Background forwarding is allowed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "On OPPO/OnePlus/Xiaomi phones, also enable Auto-start for CodeBridge in system settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PairedMacRow(
    device: PairedMac,
    isActive: Boolean,
    isConnected: Boolean,
    onClick: () -> Unit,
    onForget: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isActive) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            )
            .border(
                width = if (isActive) 1.dp else 0.dp,
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    if (isActive && isConnected) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                )
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    device.name,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (isActive) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                "${device.host}:${device.port}",
                style = MaterialTheme.typography.bodySmall,
                color = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        TextButton(onClick = onForget) { Text("Forget") }
    }
}

// ---------------------------------------------------------------------------
// Add flow: swipe between Scan QR (default) and Manual Setup.
// ---------------------------------------------------------------------------

@Composable
private fun AddMacScreen(monitor: DeviceMonitor, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = PAGE_SCAN_QR) { PAGE_COUNT }

    BackHandler { onDone() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDone) { Text("Back") }
            Text(
                "Add Mac",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        TabRow(selectedTabIndex = pagerState.settledPage) {
            listOf("Scan QR" to PAGE_SCAN_QR, "Manual Setup" to PAGE_MANUAL).forEach { (title, page) ->
                Tab(
                    selected = pagerState.settledPage == page,
                    onClick = { scope.launch { pagerState.animateScrollToPage(page) } },
                    text = { Text(title) }
                )
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                PAGE_SCAN_QR -> ScanQrPage(
                    onPaired = {
                        monitor.checkNow()
                        // Let the success overlay show, then return to the list.
                        scope.launch {
                            delay(1400)
                            onDone()
                        }
                    }
                )
                PAGE_MANUAL -> ManualSetupPage(
                    onAdded = {
                        monitor.checkNow()
                        onDone()
                    }
                )
            }
        }
    }
}

@Composable
private fun ManualSetupPage(onAdded: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context.applicationContext) }
    val deviceStore = remember { PairedDeviceStore(context.applicationContext) }
    val relay = remember { RelayClient() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var settings by remember { mutableStateOf(store.read()) }
    var status by remember { mutableStateOf("Enter your Mac's host, port, and token.") }
    var adding by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
            enabled = settings.isReady && !adding,
            onClick = {
                focusManager.clearFocus()
                adding = true
                status = "Adding ${settings.host}…"
                scope.launch {
                    val reachableName = withContext(Dispatchers.IO) {
                        relay.ping(settings).getOrNull()
                    }
                    val device = PairedMac(
                        id = PairedMac.makeId(settings.host, settings.port),
                        name = reachableName ?: settings.host,
                        host = settings.host,
                        port = settings.port,
                        token = settings.token
                    )
                    deviceStore.upsert(device)
                    deviceStore.activate(device)
                    store.save(settings)
                    onAdded()
                }
            }
        ) {
            Text(if (adding) "Adding…" else "Add Mac")
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = settings.isReady && !adding,
            onClick = {
                focusManager.clearFocus()
                status = "Sending test code..."
                scope.launch {
                    val result = withContext(Dispatchers.IO) { relay.sendTest(settings) }
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
    }
}

@Composable
private fun ScanQrPage(onPaired: () -> Unit) {
    val context = LocalContext.current
    val deviceStore = remember { PairedDeviceStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var message by remember { mutableStateOf("Point the camera at the QR code in your Mac's Settings.") }
    var isError by remember { mutableStateOf(false) }
    var paired by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    var lastHandledAt by remember { mutableStateOf(0L) }
    var flashError by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(flashError) {
        if (flashError) {
            delay(600)
            flashError = false
        }
    }

    fun handleScannedText(text: String) {
        if (paired || processing) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastHandledAt < 2000) return
        lastHandledAt = now

        val device = PairingPayloadParser.parse(text)
        if (device == null) {
            isError = true
            flashError = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            message = "Not a CodeBridge QR code."
            return
        }

        processing = true
        isError = false
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    isError = false
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    message = "Paired with $name — codes will go to this Mac."
                    onPaired()
                },
                onFailure = { error ->
                    isError = true
                    flashError = true
                    message = "Could not reach ${device.host}: ${error.message ?: "unknown error"}"
                }
            )
            processing = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                paired -> MaterialTheme.colorScheme.primary
                isError -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
            ) {
                if (hasCameraPermission) {
                QrCameraPreview(onCode = ::handleScannedText)

                if (!paired) {
                    ScanLineOverlay()
                }
                if (flashError) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0x66FF5252))
                    )
                }

                PairedSuccessOverlay(
                    visible = paired,
                    modifier = Modifier.align(Alignment.Center)
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
        }
    }
}

/** Camera preview bound to this page's lifecycle; released when the page leaves the pager. */
@Composable
private fun QrCameraPreview(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            val previewView = PreviewView(viewContext)
            val providerFuture = ProcessCameraProvider.getInstance(viewContext)
            providerFuture.addListener({
                val provider = providerFuture.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analyzerExecutor, QrAnalyzer(onCode))
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }
            }, ContextCompat.getMainExecutor(viewContext))
            previewView
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProvider?.unbindAll() }
            analyzerExecutor.shutdown()
        }
    }
}

/** Green check overlay shown on the camera when pairing succeeds. */
@Composable
private fun PairedSuccessOverlay(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + scaleIn(
            initialScale = 0.6f,
            animationSpec = tween(250)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .background(Color(0xB3000000), RoundedCornerShape(20.dp))
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "✓",
                color = Color(0xFF66BB6A),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Paired",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Codes will be sent to this Mac",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp
            )
        }
    }
}

/** Moving scan line shown while looking for a QR code. */
@Composable
private fun ScanLineOverlay() {
    val transition = rememberInfiniteTransition(label = "scan")
    val progress by transition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanProgress"
    )

    Canvas(Modifier.fillMaxSize()) {
        val y = size.height * progress
        drawLine(
            color = Color(0x4066BB6A),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 18f
        )
        drawLine(
            color = Color(0xFF66BB6A),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 4f
        )
    }
}

private fun hasSmsPermissions(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
        PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
        PackageManager.PERMISSION_GRANTED

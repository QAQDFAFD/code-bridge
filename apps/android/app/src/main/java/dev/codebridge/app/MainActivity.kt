package dev.codebridge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.codebridge.app.net.DeviceMonitor
import dev.codebridge.app.net.DeviceProbe
import dev.codebridge.app.ui.CodeBridgeApp

class MainActivity : ComponentActivity() {
    private lateinit var monitor: DeviceMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monitor = DeviceMonitor(this)
        // Kick off the background connectivity-triggered probe chain; it keeps
        // re-scheduling itself so paired Macs auto-connect without opening the app.
        DeviceProbe.schedule(this)
        setContent {
            CodeBridgeApp(monitor = monitor)
        }
    }

    override fun onStart() {
        super.onStart()
        monitor.start()
    }

    override fun onStop() {
        super.onStop()
        monitor.stop()
    }
}

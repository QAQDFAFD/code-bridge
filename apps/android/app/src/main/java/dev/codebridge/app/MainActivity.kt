package dev.codebridge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.codebridge.app.net.DeviceMonitor
import dev.codebridge.app.ui.CodeBridgeApp

class MainActivity : ComponentActivity() {
    private lateinit var monitor: DeviceMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monitor = DeviceMonitor(this)
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

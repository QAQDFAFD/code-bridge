package dev.codebridge.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dev.codebridge.app.data.CodeBridgeSettings
import dev.codebridge.app.data.PairedDeviceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Watches connectivity and picks the first paired Mac that answers a ping.
 * This gives "auto-connect when I'm back home": whenever the phone joins a
 * network, paired Macs on that network become the active receiver again.
 */
class DeviceMonitor(
    context: Context,
    private val store: PairedDeviceStore = PairedDeviceStore(context.applicationContext),
    private val relay: RelayClient = RelayClient()
) {
    sealed interface State {
        data object NoDevices : State
        data object Checking : State
        data class Connected(val name: String, val host: String) : State
        data class NoneReachable(val devices: Int) : State
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.Checking)
    val state: StateFlow<State> = _state.asStateFlow()
    private val checking = AtomicBoolean(false)
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = checkNow()
        }
        runCatching { connectivity.registerNetworkCallback(request, networkCallback) }
        callback = networkCallback
        checkNow()
    }

    fun stop() {
        callback?.let { networkCallback ->
            runCatching {
                appContext.getSystemService(ConnectivityManager::class.java)
                    ?.unregisterNetworkCallback(networkCallback)
            }
        }
        callback = null
    }

    /** Pings every paired Mac in order; the first one that answers becomes active. */
    fun checkNow() {
        if (!checking.compareAndSet(false, true)) return
        scope.launch {
            try {
                val devices = store.devices()
                _state.value = when {
                    devices.isEmpty() -> State.NoDevices
                    else -> State.Checking
                }

                val reachable = devices.firstOrNull { device ->
                    relay.ping(CodeBridgeSettings(host = device.host, port = device.port, token = device.token))
                        .isSuccess
                }

                _state.value = when {
                    reachable != null -> {
                        store.activate(reachable)
                        State.Connected(name = reachable.name, host = reachable.host)
                    }
                    devices.isNotEmpty() -> State.NoneReachable(devices.size)
                    else -> State.NoDevices
                }
            } finally {
                checking.set(false)
            }
        }
    }
}

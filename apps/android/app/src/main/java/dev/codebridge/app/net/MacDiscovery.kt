package dev.codebridge.app.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Discovers CodeBridge Macs advertising `_codebridge._tcp` over mDNS.
 * Used to auto-heal paired devices whose IP changed (DHCP lease renewal,
 * moving between networks) — the device name is the stable join key.
 */
class MacDiscovery(context: Context) {
    private val nsdManager =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    data class Found(val name: String, val host: String, val port: Int)

    /** Blocking browse + resolve; safe to call from a worker thread. */
    fun discover(timeoutMs: Long = 3_000): List<Found> {
        val found = Collections.synchronizedList(mutableListOf<Found>())
        val stopped = CountDownLatch(1)

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopped.countDown()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                stopped.countDown()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = info.host?.hostAddress ?: return
                        val name = info.serviceName ?: return
                        found.add(Found(name, host, info.port))
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
        }

        return try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            stopped.await(timeoutMs, TimeUnit.MILLISECONDS)
            found.toList()
        } catch (_: Exception) {
            emptyList()
        } finally {
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
    }

    companion object {
        const val SERVICE_TYPE = "_codebridge._tcp."
    }
}

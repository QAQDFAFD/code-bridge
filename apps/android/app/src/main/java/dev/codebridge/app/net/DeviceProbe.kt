package dev.codebridge.app.net

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.codebridge.app.data.CodeBridgeSettings
import dev.codebridge.app.data.PairedDeviceStore
import dev.codebridge.app.data.PairedMac
import java.util.concurrent.TimeUnit

/**
 * Shared reachability probe used by both the foreground [DeviceMonitor] and a
 * background worker. The worker self-reschedules with a CONNECTED network
 * constraint, so every network change (e.g. arriving home, joining Wi-Fi)
 * re-probes paired Macs and activates the reachable one — no app launch needed.
 */
object DeviceProbe {

    fun probeDevices(devices: List<PairedMac>, relay: RelayClient): PairedMac? =
        devices.firstOrNull { device ->
            relay.ping(
                CodeBridgeSettings(host = device.host, port = device.port, token = device.token)
            ).isSuccess
        }

    suspend fun probeAndActivate(
        context: Context,
        store: PairedDeviceStore,
        relay: RelayClient
    ): PairedMac? {
        val reachable = probeDevices(store.devices(), relay)
        if (reachable != null) {
            store.activate(reachable)
        }
        return reachable
    }

    /** Enqueues the next connectivity-triggered probe. Safe to call repeatedly. */
    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<DeviceProbeWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private const val WORK_NAME = "codebridge-device-probe"
}

class DeviceProbeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        DeviceProbe.probeAndActivate(
            context,
            PairedDeviceStore(context),
            RelayClient()
        )
        DeviceProbe.schedule(context)
        return Result.success()
    }
}

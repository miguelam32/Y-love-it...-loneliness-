package com.agupta07505.smartisland.util

import android.content.Context
import android.net.wifi.WifiManager
import com.agupta07505.smartisland.data.INotificationRepository
import com.agupta07505.smartisland.model.IslandMode
import com.agupta07505.smartisland.model.IslandNotification
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Hotspot detection for ROMs that never post a tethering notification (ZTE/Nubia MyOS).
 * Polls WifiManager.isWifiApEnabled (reflection) and, if that is blocked, looks for an AP-style
 * network interface that is up with an IPv4 address. Posts/removes a synthetic Hotspot card.
 */
class HotspotPollMonitor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val repository: INotificationRepository,
    private val enabled: () -> Boolean
) {
    private var job: Job? = null
    private var posted = false

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            runSuspendCatchingLogged(TAG, "Hotspot poll loop failed") { loop() }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        clearCard()
    }

    private suspend fun loop() {
        while (true) {
            val realCard = repository.notifications.value.any { it.mode == IslandMode.Hotspot && it.key != KEY }
            if (!enabled() || realCard) {
                clearCard()
            } else {
                val via = detect()
                val present = repository.notifications.value.any { it.key == KEY }
                if (via != null && (!posted || !present)) {
                    if (posted) AppLogRecorder.w(TAG, "Hotspot card vanished, posting it again")
                    posted = true
                    AppLogRecorder.i(TAG, "Hotspot ON via $via")
                    repository.postNotification(
                        IslandNotification(
                            key = KEY,
                            packageName = "android",
                            appName = "Hotspot",
                            title = "Hotspot active",
                            text = "Sharing mobile data",
                            timeMillis = System.currentTimeMillis(),
                            category = "hotspot_poll",
                            mode = IslandMode.Hotspot
                        )
                    )
                } else if (via == null && posted) {
                    AppLogRecorder.i(TAG, "Hotspot OFF")
                    clearCard()
                }
            }
            delay(POLL_MS)
        }
    }

    /** Returns which signal saw the hotspot, or null when it is off. */
    private fun detect(): String? {
        val flag = runCatching {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            WifiManager::class.java.getMethod("isWifiApEnabled").invoke(wifi) as Boolean
        }.getOrNull()
        if (flag == true) return "WifiManager.isWifiApEnabled"
        val iface = runCatching {
            val all = NetworkInterface.getNetworkInterfaces()?.let { Collections.list(it) }.orEmpty()
            all.firstOrNull { ni ->
                ni.isUp && !ni.isLoopback && AP_NAMES.matches(ni.name) &&
                    Collections.list(ni.inetAddresses).any { it is Inet4Address }
            }?.name
        }.getOrNull()
        return iface?.let { "interface $it" }
    }

    private fun clearCard() {
        if (posted) repository.removeNotification(KEY)
        posted = false
    }

    companion object {
        private const val TAG = "HotspotPollMonitor"
        const val KEY = "system_hotspot_poll"
        private const val POLL_MS = 3_000L
        private val AP_NAMES = Regex("^(ap\\d+|swlan\\d+|softap\\d+|wlan[1-9]\\d*)$")
    }
}

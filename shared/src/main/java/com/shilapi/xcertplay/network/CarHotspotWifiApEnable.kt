package com.shilapi.xcertplay.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

/**
 * Starts the car's own hotspot through the pre-Android-10 hidden API `setWifiApEnabled`.
 *
 * Wuling/SGMW Ling OS units (RTL8822/MTK automotive builds) ship no user-facing hotspot switch and
 * their `cmd tethering` extensions are absent, but the legacy call is still on the platform's
 * grey list on API 28: the platform logs a warning instead of rejecting it, which is why the
 * commercial apps keep working there. Android 10 moved the same call behind a system permission, so
 * this path is only ever attempted below API 29.
 *
 * The active AP configuration is read first and passed back untouched: this never reconfigures a
 * replacement hotspot, so the SSID, passphrase, band and channel stay exactly as the car saved them.
 * Nothing is enabled while the AP state cannot be observed.
 */
@SuppressLint("PrivateApi")
internal object CarHotspotWifiApEnable {
    private const val TAG = "DiPlay-Hotspot"

    fun supported(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /** Blocking, bounded by [deadline]; returns true once the enabled AP state is observed. */
    fun start(
        context: Context,
        deadline: Long,
        cancelled: () -> Boolean,
        state: () -> Boolean?,
        log: (String) -> Unit,
    ): Boolean {
        if (!supported()) return false
        fun stopped() = cancelled() || Thread.currentThread().isInterrupted || System.nanoTime() >= deadline
        if (stopped()) return false
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return false
        if (state() == true) return true
        val configuration = readActiveConfiguration(wifi)
        if (configuration == null) {
            log("car hotspot: firmware hides the saved AP configuration; not starting a replacement AP")
            return false
        }
        if (stopped()) return false
        try {
            val enabled = WifiManager::class.java
                .getMethod("setWifiApEnabled", WifiConfiguration::class.java, Boolean::class.javaPrimitiveType)
                .invoke(wifi, configuration, true) as? Boolean ?: false
            log("car hotspot: legacy setWifiApEnabled accepted=$enabled (SDK ${Build.VERSION.SDK_INT})")
            if (!enabled) return false
        } catch (error: Throwable) {
            Log.i(TAG, "car hotspot: legacy setWifiApEnabled unavailable", error)
            return false
        }
        while (!stopped()) {
            if (state() == true) {
                log("car hotspot: AP enabled state confirmed after legacy request")
                return true
            }
            try {
                Thread.sleep(250)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return false
    }

    private fun readActiveConfiguration(wifi: WifiManager): WifiConfiguration? = runCatching {
        WifiManager::class.java.getMethod("getWifiApConfiguration").invoke(wifi) as? WifiConfiguration
    }.getOrNull()
}

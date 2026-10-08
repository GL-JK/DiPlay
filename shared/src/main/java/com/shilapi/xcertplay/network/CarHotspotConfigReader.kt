package com.shilapi.xcertplay.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

/**
 * Reads the car's saved hotspot credentials so connection setup can fill them in instead of asking
 * the driver to retype the SSID and passphrase from the car's own settings screen.
 *
 * The values come from the same firmware-owned AP configuration the hotspot itself uses; nothing is
 * modified and no replacement AP is created. Some firmwares hide the passphrase, in which case only
 * the SSID is returned and the caller keeps asking for the secret.
 */
@SuppressLint("PrivateApi")
object CarHotspotConfigReader {
    private const val TAG = "DiPlay-Hotspot"

    data class Configuration(val ssid: String, val passphrase: String?, val channel: Int, val band: Int)

    fun read(context: Context): Configuration? = runCatching {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return null
        legacyConfiguration(wifi) ?: softApConfiguration(wifi)
    }.onFailure { Log.i(TAG, "car hotspot: saved configuration unavailable", it) }.getOrNull()

    private fun legacyConfiguration(wifi: WifiManager): Configuration? {
        val configuration = WifiManager::class.java.getMethod("getWifiApConfiguration")
            .invoke(wifi) as? WifiConfiguration ?: return null
        val ssid = unquote(configuration.SSID)?.takeIf { it.isNotBlank() } ?: return null
        val passphrase = configuration.preSharedKey?.let(::unquote)?.takeIf { it.isNotEmpty() }
        val channel = intField(configuration, "apChannel")
        val band = intField(configuration, "apBand")
        return Configuration(ssid, passphrase, channel, band)
    }

    private fun softApConfiguration(wifi: WifiManager): Configuration? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val configuration = WifiManager::class.java.getMethod("getSoftApConfiguration")
            .invoke(wifi) ?: return null
        val clazz = configuration.javaClass
        val ssid = clazz.getMethod("getSsid").invoke(configuration) as? String
            ?: return null
        if (ssid.isBlank()) return null
        val passphrase = runCatching {
            clazz.getMethod("getPassphrase").invoke(configuration) as? String
        }.getOrNull()?.takeIf { it.isNotEmpty() }
        val channel = (runCatching {
            clazz.getMethod("getChannel").invoke(configuration) as? Number
        }.getOrNull())?.toInt() ?: 0
        val band = (runCatching {
            clazz.getMethod("getBand").invoke(configuration) as? Number
        }.getOrNull())?.toInt() ?: 0
        return Configuration(ssid, passphrase, channel, band)
    }

    private fun intField(configuration: WifiConfiguration, name: String): Int = runCatching {
        WifiConfiguration::class.java.getField(name).getInt(configuration)
    }.getOrDefault(0)

    private fun unquote(value: String?): String? = value?.let {
        if (it.length >= 2 && it.first() == '"' && it.last() == '"') it.substring(1, it.length - 1) else it
    }
}

package com.shilapi.xcertplay.network

import android.content.Context
import com.shilapi.xcertplay.adb.LocalAdb
import com.shilapi.xcertplay.adb.localAdb

/**
 * Reads the running car-hotspot configuration (SSID, passphrase, channel) from the head unit over
 * its own adbd, so the operator does not have to transcribe it by hand. Read-only: it never writes
 * the AP configuration. Every field is best-effort and independently optional.
 */
object CarHotspotConfigReader {

    data class Reading(
        val ssid: String? = null,
        val passphrase: String? = null,
        val channel: Int? = null,
    ) {
        val isEmpty: Boolean get() = ssid == null && passphrase == null && channel == null
    }

    /** Returns whatever could be read; never throws. Uses the operator-configured local adbd port. */
    fun read(
        context: Context,
        iface: String = "ap0",
    ): Reading {
        if (!iface.matches(Regex("(?:ap|wlan|swlan|softap)[0-9]+"))) return Reading()
        return try {
            localAdb(context).use { adb ->
                if (adb.connect(mayAsk = false) != LocalAdb.Access.READY) return Reading()
                val status = adb.shell("hostapd_cli -i $iface status", 5_000)
                val config = adb.shell("hostapd_cli -i $iface get_config", 5_000)
                val softap = adb.shell("cat /data/misc/wifi/softap.conf 2>/dev/null", 5_000)
                Reading(
                    ssid = firstGroup(config, "(?m)^ssid=(.+)$")?.trim('"'),
                    passphrase = passphrase(status, config, softap),
                    channel = firstGroup(status, "(?m)^channel=(\\d+)\\s*$")?.toIntOrNull()
                        ?.takeIf { it in 1..196 },
                )
            }
        } catch (_: Throwable) {
            Reading()
        }
    }

    /** hostapd never prints wpa_passphrase in status/get_config, so fall back to softap.conf. */
    private fun passphrase(status: String?, config: String?, softap: String?): String? {
        firstGroup(config, "(?m)^wpa_passphrase=(.+)$")?.let { return it }
        firstGroup(status, "(?m)^wpa_passphrase=(.+)$")?.let { return it }
        // softap.conf is a small binary blob: <channel:i32><ssid><hidden><sec><passphrase>.
        val blob = softap ?: return null
        // The passphrase is the last printable run that is not the SSID and looks like a WPA key.
        val runs = Regex("[\\x20-\\x7e]{8,63}").findAll(blob).map { it.value }.toList()
        return runs.lastOrNull { it.matches(Regex("[\\x20-\\x7e]{8,63}")) && !it.startsWith("ssid") }
    }

    private fun firstGroup(text: String?, regex: String): String? =
        text?.let { Regex(regex).find(it)?.groupValues?.get(1) }?.takeIf { it.isNotBlank() }
}

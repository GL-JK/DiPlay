package com.shilapi.xcertplay.network

import android.content.Context
import com.shilapi.xcertplay.adb.LocalAdb
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode

object CarHotspotSettings {
    private fun prefs(context: Context) = context.getSharedPreferences("diplay_car_hotspot", Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean("auto_enable", false)

    fun setEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("auto_enable", enabled).apply()

    // Visible whenever a local adbd answered (or may still ask for approval), regardless of brand:
    // the SGMW/other non-BYD units also need the ADB path for hotspot/channel reads. [bydAvailable]
    // is retained for call-site compatibility and future brand-specific tuning.
    @Suppress("UNUSED_PARAMETER")
    fun visible(bydAvailable: Boolean, access: LocalAdb.Access): Boolean =
        access == LocalAdb.Access.READY || access == LocalAdb.Access.NOT_APPROVED

    fun shouldEnable(context: Context, wireless: Boolean, mode: WirelessHotspotMode): Boolean =
        enabled(context) && wireless && mode == WirelessHotspotMode.MANUAL
}

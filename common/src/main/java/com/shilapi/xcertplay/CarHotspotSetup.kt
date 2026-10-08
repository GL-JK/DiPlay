package com.shilapi.xcertplay

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.shilapi.xcertplay.adb.AdbKeys
import com.shilapi.xcertplay.adb.LocalAdb
import com.shilapi.xcertplay.adb.LocalAdbPorts
import com.shilapi.xcertplay.hud.BydOutputSettings
import com.shilapi.xcertplay.network.CarHotspotSettings
import com.shilapi.xcertplay.orchestration.ManualHotspotValidation

/** Each grant is requested explicitly from settings; startup never calls this authorization path. */
internal object CarHotspotSetup {
    // Older BYD units report QUALCOMM/qti and have no supported navigation-output service.
    fun isBydHeadUnit(context: Context): Boolean = BydOutputSettings.navigationAvailable(context) || runCatching {
        context.packageManager.getApplicationInfo("com.byd.carsettings", 0).flags and ApplicationInfo.FLAG_SYSTEM != 0
    }.getOrDefault(false)

    /**
     * BYD is not the only family whose hotspot is reachable through local ADB. Wuling/SGMW Ling OS
     * units (and other MCE/MTK automotive builds) expose the same loopback daemon on a vendor port
     * and, like the older DiLink units, ship no user-facing hotspot switch. The setup card is offered
     * whenever the unit is a BYD, carries a known car-head-unit marker, or publishes a network-ADB
     * port; the ADB probe still decides whether the switches are actually shown.
     */
    fun hotspotSetupAvailable(context: Context): Boolean =
        isBydHeadUnit(context) || isVendorCarHeadUnit(context) || LocalAdbPorts.advertisedPort() != null

    private fun isVendorCarHeadUnit(context: Context): Boolean =
        CAR_HEAD_UNIT_PACKAGES.any { name ->
            runCatching {
                context.packageManager.getApplicationInfo(name, 0).flags and ApplicationInfo.FLAG_SYSTEM != 0
            }.getOrDefault(false)
        } || CAR_HEAD_UNIT_MARKERS.any { marker ->
            listOf(Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.DEVICE, Build.PRODUCT)
                .any { it?.contains(marker, ignoreCase = true) == true }
        }

    private val CAR_HEAD_UNIT_PACKAGES = listOf("com.sgmw.carlink", "com.sgmw.carplay")

    // "spm8666p2_64_mce" and comparable MTK automotive builds ship without a user-facing AP switch.
    private val CAR_HEAD_UNIT_MARKERS = listOf("sgmw", "wuling", "ling os", "_mce")

    enum class Permission(val appOp: String) {
        HOTSPOT("WRITE_SETTINGS"), BOOT_LAUNCH("SYSTEM_ALERT_WINDOW");

        fun granted(context: Context): Boolean = when (this) {
            HOTSPOT -> Settings.System.canWrite(context)
            BOOT_LAUNCH -> Settings.canDrawOverlays(context)
        }
    }

    fun check(context: Context, adb: LocalAdb = defaultAdb(context)): LocalAdb.Access = adb.use {
        it.connect(mayAsk = false)
    }

    fun grant(context: Context, permissions: List<Permission>, adb: LocalAdb = defaultAdb(context)): LocalAdb.Access =
        adb.use {
            val access = it.connect(mayAsk = true)
            Log.i("DiPlay-ADB", "switch connection: $access")
            if (access == LocalAdb.Access.READY) {
                for (permission in permissions) {
                    if (!permission.granted(context)) {
                        Log.i("DiPlay-ADB", "request permission: ${permission.appOp}")
                        it.shell("appops set ${context.packageName} ${permission.appOp} allow")
                    }
                    val granted = permission.granted(context)
                    Log.i("DiPlay-ADB", "permission ${permission.appOp}: granted=$granted")
                    if (!granted) break
                }
            }
            access
        }

    /** Loopback clients probe the firmware-advertised port before the vendor defaults. */
    private fun defaultAdb(context: Context) =
        LocalAdb(AdbKeys.load(context), candidatePorts = LocalAdbPorts.candidates())

    fun shouldStartOnLaunch(context: Context, hasSession: Boolean): Boolean =
        !hasSession && CarHotspotSettings.shouldEnable(context,
            AirPlayPersistence.loadWirelessEnabled(context), AirPlayPersistence.loadWirelessHotspotMode(context)) &&
            ManualHotspotValidation.error(AirPlayPersistence.loadManualHotspotSsid(context),
                AirPlayPersistence.loadManualHotspotPassphrase(context)) == null
}

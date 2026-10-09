package com.shilapi.xcertplay.adb

import android.content.Context

/**
 * Persisted local-adbd port for this head unit.
 *
 * Most units expose adbd on 5555, but several car firmwares move it (the SGMW unit uses 5557). The
 * operator can enter the port explicitly; when unset, [candidatePorts] still probes the common
 * values so the feature keeps working out of the box.
 */
object AdbPortSettings {
    private const val PREFS = "diplay_adb"
    private const val KEY_PORT = "port"

    /** The operator-configured port, or 5555 when unset/invalid. */
    fun port(context: Context): Int =
        prefs(context).getInt(KEY_PORT, 5555).takeIf { it in 1..65535 } ?: 5555

    /** True when the operator typed a port (vs. relying on the default). */
    fun configured(context: Context): Boolean = prefs(context).contains(KEY_PORT)

    fun setPort(context: Context, port: Int) =
        prefs(context).edit().putInt(KEY_PORT, port.coerceIn(1, 65535)).apply()

    fun clear(context: Context) = prefs(context).edit().remove(KEY_PORT).apply()

    /** Ports to try after the configured one, covering the common vendor values. */
    fun candidatePorts(context: Context): List<Int> =
        listOf(5555, 5557, 5556).filter { it != port(context) }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Builds a [LocalAdb] bound to the operator-configured port plus the common fallbacks. */
fun localAdb(context: Context): LocalAdb = LocalAdb(
    AdbKeys.load(context.applicationContext),
    port = AdbPortSettings.port(context),
    candidatePorts = AdbPortSettings.candidatePorts(context),
)

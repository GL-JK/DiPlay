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

    /** Every port worth probing, in order: the configured one first, then the common values. */
    fun probePorts(context: Context): List<Int> =
        (listOf(port(context)) + listOf(5555, 5557, 5556)).distinct()

    /**
     * A real local-adbd endpoint: the loopback address DiPlay connects to, the matching
     * LAN-facing address on this head unit's own interface, and the port adbd is listening on.
     */
    data class Endpoint(val loopback: String, val lanAddress: String?, val port: Int) {
        val loopbackText: String get() = "$loopback:$port"
        val lanText: String? get() = lanAddress?.let { "$it:$port" }
    }

    /**
     * Reads the port adbd actually listens on, preferring the persistent property and then the
     * running service property before falling back to probing. Returns null when unknown.
     */
    fun systemAdbPort(): Int? {
        for (prop in listOf("persist.adb.tcp.port", "service.adb.tcp.port")) {
            val value = readProp(prop)?.trim()?.toIntOrNull()
            if (value != null && value in 1..65535) return value
        }
        return null
    }

    private fun readProp(name: String): String? = try {
        val p = Runtime.getRuntime().exec(arrayOf("getprop", name))
        p.inputStream.bufferedReader().use { it.readText() }.also { p.waitFor() }
    } catch (_: Throwable) {
        null
    }

    /** The LAN-facing IPv4 of this head unit (its own interface address), or null when unknown. */
    fun lanAddress(): String? = try {
        java.net.NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<java.net.Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
    } catch (_: Throwable) {
        null
    }

    /** The currently adopted endpoint (loopback + LAN + port). */
    fun endpoint(context: Context): Endpoint =
        Endpoint("127.0.0.1", lanAddress(), port(context))

    /**
     * Probes the local adbd ports and returns the first that answers, or null if none did.
     * Blocking; call off the main thread. [mayAsk] controls whether a pairing dialog may appear.
     */
    fun probe(context: Context, mayAsk: Boolean = false): Int? {
        val key = AdbKeys.load(context.applicationContext)
        // Try ports in a stable order: the system-advertised one first, then the configured value,
        // then the common vendor values, so a real property always beats a stale saved port.
        val ports = (listOfNotNull(systemAdbPort()) + probePorts(context)).distinct()
        for (candidate in ports) {
            val access = LocalAdb(key, port = candidate).use { it.connect(mayAsk = mayAsk) }
            when (access) {
                LocalAdb.Access.READY -> return candidate
                LocalAdb.Access.NOT_APPROVED -> return candidate
                else -> Unit
            }
        }
        return null
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Builds a [LocalAdb] bound to the operator-configured port plus the common fallbacks. */
fun localAdb(context: Context): LocalAdb = LocalAdb(
    AdbKeys.load(context.applicationContext),
    port = AdbPortSettings.port(context),
    candidatePorts = AdbPortSettings.candidatePorts(context),
)

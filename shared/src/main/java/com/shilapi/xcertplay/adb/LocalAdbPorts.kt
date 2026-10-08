package com.shilapi.xcertplay.adb

/**
 * Head units disagree on the loopback port that carries "ADB over network": AOSP images listen on
 * 5555, Wuling/SGMW Ling OS units publish 5557 and emulator-style images use 5556. Answering on the
 * firmware-advertised port first avoids depending on a vendor default that the unit does not use,
 * and the extra candidates cost nothing when the first one answers (a closed loopback port is
 * refused immediately).
 */
object LocalAdbPorts {
    private const val ADB_PORT_PROPERTY = "service.adb.tcp.port"

    /** Ordered candidates: the advertised port first, then the known vendor defaults. */
    fun candidates(): List<Int> = LinkedHashSet<Int>().apply {
        advertisedPort()?.let { add(it) }
        add(5555)
        add(5557)
        add(5556)
    }.toList()

    /**
     * The live port when the firmware publishes it. Reading it is one reflection call and no socket;
     * a set property is also the strongest vendor-neutral signal that the unit exposes local ADB.
     */
    fun advertisedPort(): Int? = runCatching {
        val properties = Class.forName("android.os.SystemProperties")
        val raw = runCatching {
            properties.getMethod("get", String::class.java).invoke(null, ADB_PORT_PROPERTY)
        }.getOrNull() ?: properties
            .getMethod("get", String::class.java, String::class.java)
            .invoke(null, ADB_PORT_PROPERTY, "")
        (raw as? String)?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 }
    }.getOrNull()
}

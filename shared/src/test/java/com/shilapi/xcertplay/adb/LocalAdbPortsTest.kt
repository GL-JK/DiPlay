package com.shilapi.xcertplay.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAdbPortsTest {
    @Test fun vendorPortsAreProbedAfterTheAdvertisedOneWithoutDuplicates() {
        val ports = LocalAdbPorts.candidates()
        assertEquals("candidates must not repeat a port", ports.distinct(), ports)
        assertTrue(5555 in ports)
        assertTrue(5557 in ports)
        assertTrue(5556 in ports)
    }

    @Test fun aVendorPortIsPreservedAtTheFrontWhenTheFirmwareAdvertisesIt() {
        val advertised = LocalAdbPorts.advertisedPort()
        if (advertised == null) return
        assertEquals(advertised, LocalAdbPorts.candidates().first())
    }
}

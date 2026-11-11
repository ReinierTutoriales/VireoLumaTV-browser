package com.reiniertutoriales.vireolumatv.activity.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkAccessTest {
    @Test fun privateLinkLocalUniqueLocalAndMdnsAddressesAreLocal() {
        for (host in listOf("192.168.1.1", "10.0.0.5", "172.16.4.2", "169.254.10.1",
                "[fe80::1]", "fd12:3456::1", "nas.local", "Printer.LOCAL")) {
            assertTrue(host, LocalNetworkAccess.isLocalName(host))
        }
    }

    @Test fun publicLoopbackAndNamesThatNeedResolvingAreNot() {
        for (host in listOf("8.8.8.8", "172.32.0.1", "127.0.0.1", "[::1]", "2001:4860::8888",
                "example.com", "laptophp", "999.1.1.1")) {
            assertFalse(host, LocalNetworkAccess.isLocalName(host))
        }
    }
}

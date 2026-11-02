package com.reiniertutoriales.vireolumatv.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkWatcherTest {
    private val reports = mutableListOf<Pair<Boolean, Boolean>>()
    private val watcher = NetworkWatcher { online, restored -> reports += online to restored }

    @Test fun theFirstReportAfterRegisteringIsARestoredNetwork() {
        watcher.update("wifi", online = true, validated = true)
        assertEquals(listOf(true to true), reports)
    }

    @Test fun aCaptivePortalIsOfflineAndSigningInRestoresIt() {
        watcher.update("wifi", online = false, validated = false)
        watcher.update("wifi", online = true, validated = true)
        assertEquals(listOf(false to false, true to true), reports)
    }

    @Test fun aNetworkThatNeverValidatesStillCountsAndValidationLaterRestoresAgain() {
        watcher.update("wifi", online = true, validated = false)
        watcher.update("wifi", online = true, validated = true)
        assertEquals(listOf(true to true, true to true), reports)
    }

    @Test fun repeatedReportsChangeNothingAndLosingValidationIsNoRestore() {
        watcher.update("wifi", online = true, validated = true)
        watcher.update("wifi", online = true, validated = true)
        watcher.update("wifi", online = true, validated = false)
        assertEquals(listOf(true to true, true to false), reports)
    }

    @Test fun aReplacedDefaultNetworkStaysOnlineAndTheOldOnesLossIsIgnored() {
        watcher.update("mobile", online = true, validated = true)
        watcher.update("wifi", online = true, validated = true)
        watcher.lost("mobile")
        assertEquals(listOf(true to true, true to false), reports)
        watcher.lost("wifi")
        watcher.update("wifi", online = true, validated = true)
        assertEquals(listOf(true to true, true to false, false to false, true to true), reports)
    }

    @Test fun resetMakesTheNextReportARestoreAgain() {
        watcher.update("wifi", online = true, validated = true)
        watcher.reset()
        watcher.update("wifi", online = true, validated = true)
        assertEquals(listOf(true to true, true to true), reports)
    }
}

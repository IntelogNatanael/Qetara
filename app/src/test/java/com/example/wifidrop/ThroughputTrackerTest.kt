package com.example.wifidrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThroughputTrackerTest {
    @Test fun resumeDoesNotPretendPreviouslyReceivedBytesWereJustTransferred() {
        var now = 100L
        val tracker = ThroughputTracker { now }
        assertEquals(0L, tracker.update(50_000_000L, 100_000_000L).averageBps)
        now += 1000L
        val sample = tracker.update(51_000_000L, 100_000_000L)
        assertEquals(1_000_000L, sample.averageBps)
        assertEquals(49L, sample.etaSeconds)
    }

    @Test fun retryStartingEarlierResetsSpeedAndEta() {
        var now = 100L
        val tracker = ThroughputTracker { now }
        tracker.update(0L, 100L)
        now += 1000L
        assertEquals(50L, tracker.update(50L, 100L).averageBps)
        now += 1000L
        val retried = tracker.update(10L, 100L)
        assertEquals(0L, retried.averageBps)
        assertNull(retried.etaSeconds)
    }
}

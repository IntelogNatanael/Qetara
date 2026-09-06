package com.example.wifidrop

import org.junit.Assert.*
import org.junit.Test

class UxSessionPersistencePolicyTest {
    @Test fun staleSettingsSnapshotCannotReopenAnExplicitlyClosedSession() {
        val staleUi = UxPreferences(sessionEnabled = true, compactMode = true, fontScale = 1.1f)
        val saved = UxPreferencesStore.prepareForSave(staleUi, currentSessionEnabled = false)
        assertFalse(saved.sessionEnabled)
        assertTrue(saved.compactMode)
        assertEquals(1.1f, saved.fontScale, 0.001f)
    }

    @Test fun staleClosedSnapshotCannotUndoExplicitActivation() {
        val staleUi = UxPreferences(sessionEnabled = false, joinedGlobalLan = true)
        val saved = UxPreferencesStore.prepareForSave(staleUi, currentSessionEnabled = true)
        assertTrue(saved.sessionEnabled)
        assertTrue(saved.joinedGlobalLan)
    }
}

package com.example.wifidrop

import org.junit.Assert.assertEquals
import org.junit.Test

class QetaraAppearanceTest {
    @Test
    fun automaticAppearanceFollowsTheCurrentSystemMode() {
        assertEquals(QetaraAppearance.BLUE_GRAY, QetaraAppearance.SYSTEM.resolved(systemDark = false))
        assertEquals(QetaraAppearance.DARK, QetaraAppearance.SYSTEM.resolved(systemDark = true))
    }

    @Test
    fun persistedExplicitChoicesSurviveEitherSystemMode() {
        val choices = mapOf(
            "IVORY" to QetaraAppearance.IVORY,
            "BLUE_GRAY" to QetaraAppearance.BLUE_GRAY,
            "DARK" to QetaraAppearance.DARK
        )
        choices.forEach { (stored, expected) ->
            val appearance = QetaraAppearance.fromStored(stored)
            assertEquals(expected, appearance.resolved(systemDark = false))
            assertEquals(expected, appearance.resolved(systemDark = true))
        }
    }

    @Test
    fun missingOrUnrecognizedPreferencesFallBackToAutomaticAppearance() {
        listOf(null, "", "UNKNOWN", "dark").forEach { stored ->
            val appearance = QetaraAppearance.fromStored(stored)
            assertEquals(QetaraAppearance.SYSTEM, appearance)
            assertEquals(QetaraAppearance.BLUE_GRAY, appearance.resolved(systemDark = false))
            assertEquals(QetaraAppearance.DARK, appearance.resolved(systemDark = true))
        }
        assertEquals(QetaraAppearance.SYSTEM, QetaraAppearance.fromStored("SYSTEM"))
    }
}

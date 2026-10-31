package com.example.wifidrop

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class FlashTextDraftSyncTest {
    @Test
    fun anOlderEchoCannotEraseTheNextCharacterOrItsComposition() {
        val editing = TextFieldValue("Mat", TextRange(2), TextRange(1, 3))
        assertSame(editing, reconcileFlashTextDraft(editing, observedDraft = "Ma", currentDraft = "Mat"))
    }

    @Test
    fun rapidEditsSurviveDelayedAndCoalescedEchoesExactly() {
        val expected = "Material listo para revisar en equipo."
        var editing = TextFieldValue("")
        val published = mutableListOf("")
        for (character in expected) {
            val next = editing.text + character
            editing = TextFieldValue(next, TextRange(next.length))
            published += next
            // A collected value from an earlier keystroke can run after this synchronous write.
            for (oldEcho in published.dropLast(1).takeLast(3)) {
                editing = reconcileFlashTextDraft(editing, oldEcho, currentDraft = next)
            }
        }
        assertEquals(expected, editing.text)
        assertEquals(38, editing.text.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun currentEchoPreservesSelectionAndImeComposition() {
        val editing = TextFieldValue("texto", TextRange(1, 4), TextRange(0, 5))
        assertSame(editing, reconcileFlashTextDraft(editing, "texto", "texto"))
    }

    @Test
    fun aCurrentDeliveryAckClearsTheUneditedDraftAndComposition() {
        val state = FlashAndroidState(textDraft = "enviado", sendingText = true).completeTextDraft("enviado")
        val editing = TextFieldValue("enviado", TextRange(3), TextRange(0, 7))
        val reconciled = reconcileFlashTextDraft(editing, state.textDraft, state.textDraft)
        assertEquals("", reconciled.text)
        assertEquals(TextRange.Zero, reconciled.selection)
        assertNull(reconciled.composition)
    }

    @Test
    fun lateAckOrClearEchoCannotEraseEditsMadeWhileSending() {
        val state = FlashAndroidState(textDraft = "enviado y nuevo", sendingText = true).completeTextDraft("enviado")
        val editing = TextFieldValue(state.textDraft, TextRange(14))
        assertSame(editing, reconcileFlashTextDraft(editing, observedDraft = "", currentDraft = state.textDraft))
        assertSame(editing, reconcileFlashTextDraft(editing, observedDraft = "enviado", currentDraft = state.textDraft))
    }

    @Test
    fun anExternalReplacementAppliesOnlyWhileItIsStillTheCurrentDraft() {
        val editing = TextFieldValue("local", TextRange(2), TextRange(1, 4))
        assertSame(editing, reconcileFlashTextDraft(editing, "anterior", "local"))
        val restored = reconcileFlashTextDraft(editing, "restaurado", "restaurado")
        assertEquals("restaurado", restored.text)
        assertEquals(TextRange(10), restored.selection)
        assertNull(restored.composition)
    }
}

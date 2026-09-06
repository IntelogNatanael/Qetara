package com.example.wifidrop.protocol

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransferValidationTest {
    @Test fun emptyFileCannotCarryBytes() {
        assertFailsWith<IllegalArgumentException> { requireValidFileChunk(0L, 0L, 1) }
        assertEquals(0L, requireValidResumeOffset(0L, 0L))
    }

    @Test fun chunkCannotCrossDeclaredBoundary() {
        assertEquals(10, requireValidFileChunk(20L, 10L, 10))
        assertFailsWith<IllegalArgumentException> { requireValidFileChunk(20L, 10L, 11) }
        assertFailsWith<IllegalArgumentException> { requireValidFileChunk(Long.MAX_VALUE, Long.MAX_VALUE - 1, 2) }
    }

    @Test fun rejectsMalformedChunkSizesBeforeAllocating() {
        for (length in listOf(-1, 0, MAX_SECURE_FILE_CHUNK_BYTES + 1, Int.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { requireValidFileChunk(Long.MAX_VALUE, 0L, length) }
        }
        assertFailsWith<IllegalArgumentException> { requireValidFileChunk(-1L, 0L, 1) }
        assertFailsWith<IllegalArgumentException> { requireValidFileChunk(100L, -1L, 1) }
    }

    @Test fun invalidResumeOffsetsAreRejectedInsteadOfSilentlyClamped() {
        assertEquals(35L, requireValidResumeOffset(35L, 100L))
        for (offset in listOf(-1L, 101L, Long.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { requireValidResumeOffset(offset, 100L) }
        }
    }

    @Test fun hashesMustContainExactly64HexCharacters() {
        val valid = "AB".repeat(32)
        assertEquals(valid.lowercase(), requireValidFileHash(valid))
        for (invalid in listOf(valid + "junk", valid.drop(1), "zz".repeat(32), " $valid")) {
            assertFailsWith<IllegalArgumentException> { requireValidFileHash(invalid) }
        }
    }

    @Test fun digestValidationRejectsChangesAndMalformedResponses() {
        val expected = computeDigest("FILE", "client", "server", "id", "TOKEN", "123456")
        assertTrue(digestMatches(expected.uppercase(), expected))
        assertFalse(digestMatches(expected.drop(1), expected))
        assertFalse(digestMatches(computeDigest("FILE", "client", "other", "id", "TOKEN", "123456"), expected))
    }

    @Test fun secretsUsePortableAlphabetAndSupportedLengths() {
        repeat(100) {
            assertTrue(randomToken().matches(Regex("[A-Z2-9]{8}")))
            assertTrue(randomPin().matches(Regex("[0-9]{6}")))
            assertEquals(64, randomNonce(1000).length)
        }
        assertEquals("123456", normalizePin("1 2 3-4 5 6"))
        assertEquals("", normalizePin("\u0661\u0662\u0663\u0664\u0665\u0666"))
    }

    @Test fun securityNormalizationDoesNotDependOnDeviceLocale() {
        val prior = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("FILE", normalizeToken("file"))
            assertEquals(
                computeDigest("FILE", "a", "b", "id", "ABCD", "123456"),
                computeDigest("file", "a", "b", "id", "ABCD", "123456")
            )
        } finally { Locale.setDefault(prior) }
    }

    @Test fun noPlaintextCredentialFallbackIsAllowedEvenForTrustedIds() {
        assertTrue(requiresEncryptedCredentials(PACKET_CREDENTIALS_REQUEST))
        assertFalse(requiresEncryptedCredentials(PACKET_SECURE_CREDENTIALS_REQUEST))
        assertFalse(isPinnedIdentityCompatible("known-key", "replacement-key"))
        assertFalse(isPinnedIdentityCompatible("known-key", ""))
        assertTrue(isPinnedIdentityCompatible("known-key", "known-key"))
    }

    @Test fun receiveCapacityKeepsOperatingSystemSpaceAndAllowsCompleteResume() {
        assertFailsWith<IllegalStateException> { requireReceiveCapacity(1024L, 0L, 512L) }
        requireReceiveCapacity(1024L, 1024L, 512L)
        requireReceiveCapacity(1024L, 0L, 16L * 1024 * 1024)
    }
}

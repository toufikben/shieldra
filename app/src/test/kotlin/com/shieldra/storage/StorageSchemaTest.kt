package com.shieldra.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StorageSchemaTest {
    @Test
    fun schemaIsVersionedAndHasNoDestructiveDefaultMigration() {
        assertEquals(1, StorageSchema.VERSION)
        assertTrue(StorageSchema.CREATE_STATEMENTS.any { it.contains("CREATE TABLE security_events") })
        assertTrue(StorageSchema.CREATE_STATEMENTS.any { it.contains("FOREIGN KEY (event_id)") })
        assertTrue(StorageMigrations.all.isEmpty())
    }

    @Test
    fun invalidSchemaVersionsAreRejected() {
        assertFailsWith<IllegalArgumentException> { requireSupportedSchemaVersion(0) }
        assertFailsWith<IllegalArgumentException> { requireSupportedSchemaVersion(2) }
    }
}

class EncryptionPolicyTest {
    @Test
    fun policyRequiresExplicitReviewedCandidate() {
        val policy = EncryptionPolicy(
            keySizeBits = 256,
            requireUserAuthentication = true,
            userAuthenticationValiditySeconds = 30,
            invalidateOnBiometricEnrollment = true,
        )
        assertEquals("AES/GCM/NoPadding", EncryptionPolicy.TRANSFORMATION)
        assertEquals(256, policy.keySizeBits)
    }

    @Test
    fun authenticatedPolicyRequiresAValidityWindow() {
        assertFailsWith<IllegalArgumentException> {
            EncryptionPolicy(
                keySizeBits = 256,
                requireUserAuthentication = true,
                userAuthenticationValiditySeconds = 0,
                invalidateOnBiometricEnrollment = true,
            )
        }
    }

    @Test
    fun encryptedPayloadRoundTripsItsEnvelope() {
        val original = EncryptedPayload(
            version = 1,
            iv = ByteArray(12) { it.toByte() },
            ciphertext = byteArrayOf(1, 2, 3, 4),
        )
        val restored = EncryptedPayload.fromByteArray(original.toByteArray())
        assertEquals(original.version, restored.version)
        assertTrue(original.iv.contentEquals(restored.iv))
        assertTrue(original.ciphertext.contentEquals(restored.ciphertext))
    }
}

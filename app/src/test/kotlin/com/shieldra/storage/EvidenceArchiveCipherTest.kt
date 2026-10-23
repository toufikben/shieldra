package com.shieldra.storage

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class EvidenceArchiveCipherTest {

    private val pass = "correct horse battery staple".toCharArray()

    @Test
    fun `round-trips the plaintext under the right passphrase`() {
        val plaintext = "evidence bytes \u0000\u0001ÿ".toByteArray()
        val blob = EvidenceArchiveCipher.encrypt(plaintext, pass)
        assertContentEquals(plaintext, EvidenceArchiveCipher.decrypt(blob, pass.copyOf()))
    }

    @Test
    fun `the blob is not the plaintext`() {
        val plaintext = ByteArray(64) { it.toByte() }
        val blob = EvidenceArchiveCipher.encrypt(plaintext, pass.copyOf())
        assertFalse(blob.copyOfRange(blob.size - plaintext.size, blob.size).contentEquals(plaintext))
    }

    @Test
    fun `a wrong passphrase fails instead of returning plaintext`() {
        val blob = EvidenceArchiveCipher.encrypt("secret".toByteArray(), pass.copyOf())
        assertFailsWith<Exception> {
            EvidenceArchiveCipher.decrypt(blob, "wrong passphrase".toCharArray())
        }
    }

    @Test
    fun `tampering with the ciphertext fails the tag`() {
        val blob = EvidenceArchiveCipher.encrypt("secret".toByteArray(), pass.copyOf())
        blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte()
        assertFailsWith<Exception> {
            EvidenceArchiveCipher.decrypt(blob, pass.copyOf())
        }
    }

    @Test
    fun `two encryptions of the same input differ (random salt and iv)`() {
        val plaintext = "same".toByteArray()
        val a = EvidenceArchiveCipher.encrypt(plaintext, pass.copyOf())
        val b = EvidenceArchiveCipher.encrypt(plaintext, pass.copyOf())
        assertFalse(a.contentEquals(b))
    }
}

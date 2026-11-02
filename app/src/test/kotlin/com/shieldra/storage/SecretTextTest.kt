package com.shieldra.storage

import kotlin.test.Testimport kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A Keystore stand-in for the JVM: the same envelope and the same AAD discipline as the real cipher,
 * with an XOR in place of AES so a sealed value can be opened, a wrong field can be detected, and a
 * key that cannot be used can be made to fail on demand. It is deliberately not a general cipher —
 * these tests are about the rules around the seal, not about AES.
 */
private class ReversingEncryptor(private val refusesToEncrypt: Boolean = false) : AndroidKeystoreEncryptor() {
    override fun encrypt(
        plaintext: ByteArray,
        keyAlias: String,
        policy: EncryptionPolicy,
        associatedData: ByteArray?,
    ): EncryptedPayload {
        if (refusesToEncrypt) throw KeyMaterialUnavailableException("no key can be created on this phone")
        val aad = boundField(associatedData)
        return EncryptedPayload(
            version = EncryptionPolicy.PAYLOAD_VERSION,
            // The field the value was bound to is kept in the IV, the way the real cipher keeps it in
            // the GCM tag: an opening under another field can be detected and refused.
            iv = ByteArray(EncryptionPolicy.GCM_IV_BYTES) { index ->
                if (index == 0) fingerprint(aad) else (index % 7).toByte()
            },
            ciphertext = bind(plaintext, aad),
        )
    }

    override fun decrypt(
        payload: EncryptedPayload,
        keyAlias: String,
        policy: EncryptionPolicy,
        associatedData: ByteArray?,
    ): ByteArray {
        val aad = boundField(associatedData)
        if (payload.iv[0] != fingerprint(aad)) {
            throw KeyMaterialUnavailableException("this value was sealed for another field")
        }
        return bind(payload.ciphertext, aad)
    }

    private fun boundField(associatedData: ByteArray?): ByteArray =
        requireNotNull(associatedData) { "a sealed value must be bound to its field" }
            .also { require(it.isNotEmpty()) { "a sealed value must be bound to a named field" } }

    private fun fingerprint(aad: ByteArray): Byte =
        aad.fold(0x5A) { accumulator, value -> (accumulator xor value.toInt()).toByte() }

    private fun bind(bytes: ByteArray, aad: ByteArray): ByteArray =
        bytes.mapIndexed { index, value -> (value.toInt() xor aad[index % aad.size].toInt()).toByte() }.toByteArray()
}

/**
 * The seal an owner's stored position now goes through (audit B-2/§5.13): a value that cannot be
 * sealed is never written in the clear, a line that cannot be opened reads as absent, and a sealed
 * value belongs to exactly one field.
 */
class SecretTextTest {

    private val location = "36.7538;3.0588;12.0;1730000000000;gps"

    @Test
    fun a_sealed_value_reads_back_as_what_was_written() {
        val seal = SecretText(ReversingEncryptor(), "queued_alert_location")

        val sealed = seal.seal(location)

        assertNotNull(sealed)
        assertTrue(SecretText.isSealed(sealed))
        assertEquals(location, seal.open(sealed))
    }

    @Test
    fun a_sealed_line_is_not_the_plain_value_anymore() {
        val sealed = assertNotNull(SecretText(ReversingEncryptor(), "queued_alert_location").seal(location))

        assertFalse(sealed.contains("36.7538"))
        assertFalse(sealed.contains(location))
    }

    @Test
    fun a_value_that_cannot_be_sealed_is_refused_rather_than_written_in_the_clear() {
        val seal = SecretText(ReversingEncryptor(refusesToEncrypt = true), "queued_alert_location")

        assertNull(seal.seal(location))
    }

    @Test
    fun a_plain_line_written_by_an_older_build_is_never_taken_for_a_sealed_one() {
        val seal = SecretText(ReversingEncryptor(), "queued_alert_location")

        assertFalse(SecretText.isSealed(location))
        assertNull(seal.open(location))
    }

    @Test
    fun a_damaged_sealed_line_reads_as_absent_and_never_throws() {
        val seal = SecretText(ReversingEncryptor(), "queued_alert_location")

        assertNull(seal.open(SecretText.SEALED_PREFIX + "not-base64-at-all!!"))
        assertNull(seal.open(SecretText.SEALED_PREFIX))
        assertNull(seal.open(""))
    }

    @Test
    fun a_sealed_value_does_not_open_for_another_field() {
        val writer = SecretText(ReversingEncryptor(), "queued_alert_location")
        val elsewhere = SecretText(ReversingEncryptor(), "telegram_chat")
        val sealed = assertNotNull(writer.seal(location))

        // The payload is intact; only the field binding differs, so the reader sees no value at all
        // rather than a position that belongs to something else.
        assertNull(elsewhere.open(sealed))
        assertEquals(location, writer.open(sealed))
    }

    @Test
    fun a_sealed_line_survives_the_character_the_alert_queue_splits_on() {
        val sealed = assertNotNull(
            SecretText(ReversingEncryptor(), "queued_alert_location").seal("36.75;3.05;|;1730000000000;gps"),
        )

        assertFalse(sealed.contains("|"))
    }
}
